//	Copyright (C) 2008 Choon Jin Ng  All Rights Reserved.
//  Copyright (C) 2003 Constantin Kaplinsky.  All Rights Reserved.
//  Copyright (C) 2002-2005 RealVNC Ltd.  All Rights Reserved.
//  Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
//
//  This is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This software is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this software; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
//  USA.
//

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import javax.swing.*;

public class PlaybackInputStream extends DataInputStream implements ActionListener
{
    String fileName;
    RandomAccessFile file;
    byte[] buffer;
    rfbMultiProto rfb;
    long startRec;
    long last,lastAbsDelta;
    double speedFactor = 1.0;
    int headerSize;
    
    Button playButton,stopButton,fastForwardButton,backButton,
            forwardButton,back2Button,forward2Button,reverseButton;
    Label timeLabel,modeLabel;
    
    Stack pos;
    
    public PlaybackInputStream(rfbMultiProto rfb) throws IOException
    {
        this.rfb = rfb;
        
        fileName = rfb.viewer.readParameter("FILE",false);
        if(fileName!=null) {
            try{
                file = new RandomAccessFile(fileName,"r");
            }catch(FileNotFoundException e){System.err.println("Can't open file '"+fileName+"' for input: "+e);}

            in = new java.io.DataInputStream(new FileInputStream(file.getFD()));
            file.seek(8+12+4+20);                   // skip header
            headerSize = 8+12+4+20+4+file.readInt();  // calculate size of header
            
            file.seek(0);
            startRec = in.readLong();
            last = System.currentTimeMillis();
            lastAbsDelta = 0;
            pos = new Stack();
            
            stopButton = new Button("Stop");
            stopButton.setActionCommand("Stop");
            stopButton.addActionListener(this);
            rfb.viewer.buttonPanel.add(stopButton);

            back2Button = new Button("-1min.");
            back2Button.setActionCommand("step back 2");
            back2Button.addActionListener(this);
            rfb.viewer.buttonPanel.add(back2Button);

            backButton = new Button("-10sec.");
            backButton.setActionCommand("step back");
            backButton.addActionListener(this);
            rfb.viewer.buttonPanel.add(backButton);

            forwardButton = new Button("+10sec.");
            forwardButton.setActionCommand("step forward");
            forwardButton.addActionListener(this);
            rfb.viewer.buttonPanel.add(forwardButton);

            forward2Button = new Button("+1min.");
            forward2Button.setActionCommand("step forward 2");
            forward2Button.addActionListener(this);
            rfb.viewer.buttonPanel.add(forward2Button);
            
            playButton = new Button("");
            if(play) playButton.setLabel("Pause");
            else playButton.setLabel("Play");
            playButton.addActionListener(this);
            rfb.viewer.buttonPanel.add(playButton);

            fastForwardButton = new Button("FF ("+(factor+1)+"x)");
            fastForwardButton.setActionCommand("fast forward");
            fastForwardButton.addActionListener(this);
            rfb.viewer.buttonPanel.add(fastForwardButton);

            timeLabel = new Label();
            setTimeLabel(0);
            rfb.viewer.buttonPanel.add(timeLabel);

            modeLabel = new Label("");
            rfb.viewer.buttonPanel.add(modeLabel);
            if(play) modeLabel.setText("Mode: Play      ");
            else modeLabel.setText("Mode: Pause     ");

            reverseButton = new Button("reverse");
            reverseButton.addActionListener(this);
//             rfb.viewer.buttonPanel.add(reverseButton);
        } else { System.err.println("FILE parameter not specified."); }
    }

boolean forward=true;
boolean play = true;
boolean reset = false;
int lastPos;

    void reset() throws IOException
    {
        if(forward) {
            file.seek(headerSize);  // skip header
            last = System.currentTimeMillis();
            lastAbsDelta = 0;
            setTimeLabel(0);
            pos = new Stack();
        } else {
            file.seek(((Long)pos.pop()).intValue());
        }
        reset = false;
    }

boolean fast=false;
int previewTime = 130000;

    public void flush() throws IOException
    {   // EOF can only appear in readServerMessageType() which calls flush()
        // so reopen file, skip header and start playback again (loop)
        while(!play) { 
            synchronized(this) {
                try {
                    if(reset) setTimeLabel(0);
                    wait();
                } catch (InterruptedException e) {}
            }
        }

        // EOF
        while((in.available()==0) || reset) {
            reset();
            setPlayTime = -1;
            fast = false;
        }
         
        // searching correct packet if a new time to play is set
        // estimated that preview time is needed to have a full image
        if(setPlayTime < -1) setPlayTime=0;
        if(setPlayTime != -1) {
            if(!fast) { // setPlayTime set newly
            
                if(setPlayTime<lastAbsDelta) { // step back
                    do{ // search needed packet in stack
                        if(pos.empty()) break;
                        file.seek(((Long)pos.pop()).intValue());
                    }while(setPlayTime < in.readInt());
                    
                    if(pos.empty()) { // start from to beginning if stack is empty
                        reset();
                        setPlayTime = -1;
                        fast = false;
                    } else {  
                        do{ // search needed packet for preview
                            if(pos.empty()) break; // no more packets available
                            file.seek(((Long)pos.pop()).intValue());
                        }while(setPlayTime-previewTime < in.readInt());

                        file.seek(file.getFilePointer()-4); // set to beginning of packet
                        fast = true; // no sleep till preview for full image is done
                    }
                    
                } else { // step forward
                    while(in.readInt() < setPlayTime-previewTime) { // search needed packet for preview
                        in.skip(in.readInt()); // skip packet
                        if(in.available()==0) break;
                    }
                    if(in.available()!=0) file.seek(file.getFilePointer()-4); // set to beginning of packet
                    else  { // start from to beginning if end of file
                        reset();
                        setPlayTime = -1;
                        fast = false;
                    }
                    fast = true; // no sleep till preview for full image is done
                }
            } else { // still calculating full images
                if(setPlayTime<lastAbsDelta) {
                    // back on track - continue to normal processing
                    setPlayTime = -1;
                    fast = false;                
                    last = System.currentTimeMillis() - (in.readInt()-lastAbsDelta);
                    file.seek(file.getFilePointer()-4);
                    modeLabel.setText("Mode: Play");
                    forward = true;
                    speedFactor = 1.0;
                    play = true;
                }
            }
        }

        if(forward) {
            pos.push(new Long(file.getFilePointer()));

            long absDelta = in.readInt();
            int size = readInt();
        
            long current = System.currentTimeMillis();

            long diff = (absDelta-lastAbsDelta) - (current-last);
                        
            if(!fast)
                if(diff>0) try{Thread.sleep((long)(diff*speedFactor));}catch(InterruptedException e){}


            last += (absDelta-lastAbsDelta)*speedFactor;
            lastAbsDelta = absDelta;
        } else {            
            file.seek(((Long)pos.pop()).intValue());
            long absDelta = in.readInt();
            int size = in.readInt();
            
            long current = System.currentTimeMillis();

            long diff = -(absDelta-lastAbsDelta) - (current-last);
            
            if(!fast)
                if(diff>0) try{Thread.sleep((long)(diff*speedFactor));}catch(InterruptedException e){}

            last += -(absDelta-lastAbsDelta)*speedFactor;
            lastAbsDelta = absDelta;
            
            if(pos.empty()) forward = true;
        }
        setTimeLabel((int)lastAbsDelta);
    }
    
int setPlayTime = -1;
int factor=1;
    public synchronized void actionPerformed(ActionEvent e)
    {
//         System.out.println("Command: "+e.getActionCommand());
        if(e.getActionCommand().equals("Play")) {
            play();
            factor=1;
        } else if(e.getActionCommand().equals("Pause")) {
            pause();
            factor=1;
        } else if(e.getActionCommand().equals("fast forward")) {
            fastForward(++factor);
        } else if(e.getActionCommand().equals("Stop")) {
            stop();
            factor=1;
        } else if(e.getActionCommand().equals("step back 2")) {
            setPlayTime((int)lastAbsDelta-60000);
        } else if(e.getActionCommand().equals("step back")) {
            setPlayTime((int)lastAbsDelta-10000);
        } else if(e.getActionCommand().equals("step forward")) {
            setPlayTime((int)lastAbsDelta+10000);
        } else if(e.getActionCommand().equals("step forward 2")) {
            setPlayTime((int)lastAbsDelta+60000);
        } else if(e.getActionCommand().equals("reverse")) {
            forward = !forward;
        }
        fastForwardButton.setLabel("FF ("+(factor+1)+"x)");    
        if(play) playButton.setLabel("Pause");
        else playButton.setLabel("Play");
    }
    
    void play()
    {
        modeLabel.setText("Mode: Play");
        forward = true;
        speedFactor = 1.0;
        play = true;
        notify();
    }
    void pause()
    {
        modeLabel.setText("Mode: Pause");
        play = false;
    }
    void stop()
    {
        modeLabel.setText("Mode: Stop");
        reset = true;
        play = false;
    }
    void fastForward(int factor)
    {
        modeLabel.setText("Mode: FF "+factor+"x");
        forward = true;
        speedFactor = 1.0/factor;
        play = true;
        notify();
        fastForwardButton.setLabel("FF ("+(factor+1)+"x)");    
    }
    void setPlayTime(int time)
    {
        setPlayTime = time;
        modeLabel.setText("Mode: Search");
        factor=1;
        speedFactor = 1.0/factor;
        play = true;
        notify();
    }
    
    void setTimeLabel(int msec)
    {
        int sec = msec/1000%60;
        int min = msec/60000%60;
        int h   = msec/3600000;
        timeLabel.setText(" "+h+":"+(min/10)+(min%10)+":"+(sec/10)+(sec%10)+" ");
    }
}
