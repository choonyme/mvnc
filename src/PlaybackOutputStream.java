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

public class PlaybackOutputStream extends FileOutputStream
{
    String fileName;
    rfbMultiProto rfb;
    long startTime;
    
    public PlaybackOutputStream(rfbMultiProto rfb,String fileName) throws IOException
    {
        super(fileName);
     
        this.rfb = rfb;
        this.fileName = fileName;

        // starting time
        startTime = System.currentTimeMillis();
        byte[] b = new byte[8];
        b[0]  = (byte) ((startTime >> 56) & 0xff);
        b[1]  = (byte) ((startTime >> 48) & 0xff);
        b[2]  = (byte) ((startTime >> 40) & 0xff);
        b[3]  = (byte) ((startTime >> 32) & 0xff);
        b[4]  = (byte) ((startTime >> 24) & 0xff);
        b[5]  = (byte) ((startTime >> 16) & 0xff);
        b[6]  = (byte) ((startTime >> 8) & 0xff);
        b[7]  = (byte) (startTime & 0xff);
        write(b);

        writeVersionMsg();
        
        // no authentication needed
        b = new byte[4];
        b[0]  = (byte) ((rfb.AuthNone >> 24) & 0xff);
        b[1]  = (byte) ((rfb.AuthNone >> 16) & 0xff);
        b[2]  = (byte) ((rfb.AuthNone >> 8) & 0xff);
        b[3]  = (byte) (rfb.AuthNone & 0xff);
        write(b);
        
        String name = "VNC Playback: "+fileName;
        writeServerInit(rfb.framebufferWidth,rfb.framebufferHeight,
                    8, 8, false, true, 7, 7, 3, 0, 3, 6, name);                            
    }
    
    void writeVersionMsg() throws IOException 
    {
        byte[] b = new byte[12];
        rfb.versionMsg.getBytes(0, 12, b, 0);
        write(b);
    }

    void writeServerInit(int framebufferWidth, int framebufferHeight, 
                int bitsPerPixel, int depth, boolean bigEndian,
                boolean trueColour,
                int redMax, int greenMax, int blueMax,
                int redShift, int greenShift, int blueShift, String name)
            throws IOException
    {
        byte[] b = new byte[20];

        b[0]  = (byte) ((framebufferWidth >> 8) & 0xff);
        b[1]  = (byte) (framebufferWidth & 0xff);
        b[2]  = (byte) ((framebufferHeight >> 8) & 0xff);
        b[3]  = (byte) (framebufferHeight & 0xff);
        b[4]  = (byte) bitsPerPixel;
        b[5]  = (byte) depth;
        b[6]  = (byte) (bigEndian ? 1 : 0);
        b[7]  = (byte) (trueColour ? 1 : 0);
        b[8]  = (byte) ((redMax >> 8) & 0xff);
        b[9]  = (byte) (redMax & 0xff);
        b[10] = (byte) ((greenMax >> 8) & 0xff);
        b[11] = (byte) (greenMax & 0xff);
        b[12] = (byte) ((blueMax >> 8) & 0xff);
        b[13] = (byte) (blueMax & 0xff);
        b[14] = (byte) redShift;
        b[15] = (byte) greenShift;
        b[16] = (byte) blueShift;
        
        write(b);
        int len = name.length();
        b = new byte[4];
        b[0]  = (byte) ((len >> 24) & 0xff);
        b[1]  = (byte) ((len >> 16) & 0xff);
        b[2]  = (byte) ((len >> 8) & 0xff);
        b[3]  = (byte) (len & 0xff);
        write(b);

        write(name.getBytes());
    }
}
