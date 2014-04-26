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
import org.jgroups.*;

public class MulticastInputStream extends DataInputStream
{
	JChannel channel;
    byte[] buffer;
    InetAddress sender;
    int multiSenderPort;
    
    DatagramPacket packet;
    
    public MulticastInputStream(rfbMultiProto rfb,int multiSenderPort) throws IOException
    {
        try{
        	File udpxml = new File("udp.xml");
        	if(udpxml.exists()){
        		System.out.println("Using udp.xml config");
        		channel = new JChannel(udpxml);
        	}else{
        		System.out.println("Using default config");
        		channel = new JChannel();
        	}
    		channel.connect("MVNC01");
    	}catch(ChannelException ex){
    		System.out.println("Can't create new channel!");
    	}
    	
        sender = InetAddress.getByName(rfb.host);
        this.multiSenderPort = multiSenderPort;
        
        System.out.println("Receive MulticastVNC from "+sender+" port "+multiSenderPort);
        
        buffer = new byte[0];
        in = new java.io.DataInputStream(new ByteArrayInputStream(buffer,0,0));
    }
    
    public void finalized(){
    	channel.close();
    }
    
    void getDatagram() throws IOException
    {
    	try{
    		Object obj;
    		Message msg;
    		
    		obj = channel.receive(0);
    		while(! (obj instanceof Message) ){
    			obj = channel.receive(0);
    		}
    		
    		msg = (Message) obj;
    		in = new java.io.DataInputStream(new ByteArrayInputStream(msg.getBuffer(), 0, (int) msg.size()));
    	}catch(ChannelClosedException ex){
    		System.out.println("Cannot receive: Channel closed.");
    	}catch(ChannelNotConnectedException ex){
    		System.out.println("Cannot receive: Channel not connected.");
    	}catch(TimeoutException ex){
    		System.out.println("Cannot receive: Timeout.");
    	}
    }
    
    
    public int read() throws IOException
    {   
        while(in.available()==0) getDatagram();
        return in.read();
    }

    public int read(byte[] b) throws IOException
    {   return in.read(b);   }
    public byte readByte() throws IOException
    {   return in.readByte();   }
    public void readFully(byte[] b) throws IOException
    {   in.readFully(b);   }
    public void readFully(byte[] b, int off, int len) throws IOException
    {   in.readFully(b,off,len);   }
    public int readInt() throws IOException
    {   return in.readInt();  }
    public int readUnsignedByte() throws IOException
    {   if(in.available() == 0) getDatagram();
    	return in.readUnsignedByte();   }
    public long readLong() throws IOException{
    	if(in.available() == 0) getDatagram();
    	return in.readLong();
    }
    public int readUnsignedShort() throws IOException
    {   return in.readUnsignedShort();   }    
}