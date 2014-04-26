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
import java.net.*;
import java.util.*;
import org.jgroups.*;

class MVNCChannelReceiver extends ExtendedReceiverAdapter{
	rfbMultiProto rfb;
	int members_count = 0;
	
	MVNCChannelReceiver(rfbMultiProto rfb){this.rfb = rfb;} 
	public void receive(Message msg){}
	public void viewAccepted(View newview){
		int new_members_count = newview.getMembers().size();
		if(new_members_count >= members_count)
			rfb.newClient = true;
		members_count = new_members_count;
	}
}

public class InputStreamWithMulticastOutput extends DataInputStream
{   	
    ByteArrayOutputStream buffer;
    DataOutputStream out;

    JChannel channel;
    Timer timer;
    rfbMultiProto rfb;
    
    DataOutputStream file;
    String fileName;
    boolean fileOutput;
    long startTime;
    
    public InputStreamWithMulticastOutput(rfbMultiProto rfb) throws IOException
    {
        this.rfb = rfb;
        in = new java.io.DataInputStream(new BufferedInputStream(rfb.sock.getInputStream(),16384));
        buffer = new ByteArrayOutputStream();
        out = new DataOutputStream(buffer);
        
        try{
        	File udpxml = new File("udp.xml");
        	if(udpxml.exists()){
        		System.out.println("Using udp.xml config");
        		channel = new JChannel(udpxml);
        	}else{
        		System.out.println("Using default config");
        		channel = new JChannel();
        	}
        	channel.setReceiver(new MVNCChannelReceiver(rfb));
        	channel.connect("MVNC01");
        }catch(Exception e){
        	System.out.println("JChannel connect error...");
        	e.printStackTrace();
        }
        
        fileName = rfb.viewer.readParameter("FILE",false);
        if(fileName!=null) try{
            PlaybackOutputStream pb = new PlaybackOutputStream(rfb,fileName);
            file = new DataOutputStream(pb);
            startTime = pb.startTime;
            fileOutput = true;
        }catch(FileNotFoundException e){System.out.println("Can't open file '"+fileName+"' for output: "+e);}
        
        //new UpdateRequestDaemon(rfb);
        new Proxy(rfb);
    }
    
    
    /* send multicast datagram */
    public void flush()
    {
        long time = System.currentTimeMillis();
        
        if(buffer.size()>0) {                
            byte[] b = buffer.toByteArray();

            try{
            	
            	channel.send(new Message(null, null, b));
            }catch(ChannelClosedException ex){
            	ex.printStackTrace();
            }catch(ChannelNotConnectedException ex){
            	ex.printStackTrace();
            }
            
            // file output
            if(fileOutput)
                try{
                    file.writeInt((int)(time-startTime));
                    file.writeInt(b.length);
                    file.write(b);
                }catch(IOException e){
                	System.out.println("Can't write to file '"+fileName+"': "+e);
                }
             
            buffer.reset();
            b = null;
        }
    }
    
    public int read() throws IOException
    {
        int i = in.read();
        if(rfb.inNormalProtocol) out.write(i);
        return i;
    }

    public int read(byte[] b) throws IOException
    {
        int i = in.read(b);
        if(rfb.inNormalProtocol) out.write(b);
        return i;
    }

    public int readInt() throws IOException
    {
        int i = in.readInt();
        if(rfb.inNormalProtocol) out.writeInt(i);
        return i;
    }

    public byte readByte() throws IOException
    {
        byte i = in.readByte();
        if(rfb.inNormalProtocol) out.writeByte(i);
        return i;
    }

    public int readUnsignedShort() throws IOException
    {
        int i = in.readUnsignedShort();
        if(rfb.inNormalProtocol) out.writeShort(i);
        return i;
    }

    public int readUnsignedByte() throws IOException
    {
        int i = in.readUnsignedByte();
        if(rfb.inNormalProtocol) out.writeByte(i);
        return i;
    }
    
    public void readFully(byte[] b) throws IOException
    {
        in.readFully(b);
        if(rfb.inNormalProtocol) out.write(b);
    }

    public void readFully(byte[] b,int off,int len) throws IOException
    {
        in.readFully(b,off,len);
        if(rfb.inNormalProtocol) out.write(b,off,len);
    }
    
    public void write(byte[] b) throws IOException{
    	if(rfb.inNormalProtocol) out.write(b,0,b.length);
    }
    
    public void write(byte[] b, int off, int len) throws IOException
    {
    	if(rfb.inNormalProtocol) out.write(b,off,len);
    }
}