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

/**
 * Connects a new client.
 * Does initialisation includinge deliverance of multicast address/port.
 * Disconnects after initialisation is done because unicast connection
 * is no longer needed.
 * 
 * @author Peter Ziewer - University of Trier - ziewer@psi.uni-trier.de 
 * @version Aug.09/2001
 */
public class Connector extends Thread
{
    static final String versionMsg = "RFB 003.006\n";    
    static final int ConnFailed = 0, NoAuth = 1, VncAuth = 2;

    rfbMultiProto rfb;
    Socket socket;
    DataInputStream in;
    DataOutputStream out;
   
    public Connector(rfbMultiProto r,Socket s)
    {
        rfb = r;
        socket = s;
        start();
    }

    public void finalized(){
    	try{
    		socket.close();
    	}catch(IOException ex){
    		ex.printStackTrace();
    	}
    }
    
    /**
     * Does rfb initialisation.
     * Delieveres information about multicast group/port using the field for the desktop name.
     * Also transmits the sender's port to determine the correct source of packets.
     */    
    public void run()
    {
      try {
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(socket.getOutputStream());
        
        writeVersionMsg();
        if(readVersionMsg()) if(doAuthentication()) {
            readClientInit();
            int multicastSocketPort = 0;// = ((InputStreamWithMulticastOutput)rfb.is).socket.getLocalPort();
            // multicast group:port/senderport
            String name = "MulticastVNC "+rfb.multiGroup.getHostAddress()+":"+rfb.multiPort+":"+multicastSocketPort;
            writeServerInit(rfb.framebufferWidth,rfb.framebufferHeight,
                    8, 8, false, true, 7, 7, 3, 0, 3, 6,
                    name);
//             writeServerInit(rfb.framebufferWidth,rfb.framebufferHeight,rfb.bitsPerPixel, rfb.depth, rfb.bigEndian, rfb.trueColour, 
//                     rfb.redMax, rfb.greenMax, rfb.blueMax, rfb.redShift, rfb.greenShift, rfb.blueShift,name);
            //rfb.newClient = true;
            System.out.println("\nNew connection from "+socket.getInetAddress());
        }
        
        //pipe client's input stream to VNC server's output stream
        while(true){
        	int messageType = in.readUnsignedByte();
        	byte[] packet = null;
        	switch(messageType){
        		case rfbProto.SetPixelFormat:
        			packet = new byte[20];
        			packet[0] = rfbProto.SetPixelFormat;
        			in.read(packet, 1, 19);
        			break;
        		case rfbProto.SetEncodings:
        			packet = new byte[4];
        			packet[0] = rfbProto.SetEncodings;
        			in.read(packet, 1, 3);
        			break;
        		case rfbProto.FramebufferUpdateRequest:
        			byte[] nullpacket = new byte[10];
        			in.read(nullpacket, 0, 9);
        			break;
	        	case rfbProto.KeyboardEvent:
	        		packet = new byte[8];
	        		packet[0] = rfbProto.KeyboardEvent;
	        		in.read(packet, 1, 7);
	        		break;
	        	case rfbProto.PointerEvent:
	        		packet = new byte[6];
	        		packet[0] = rfbProto.PointerEvent;
	        		in.read(packet, 1, 5);
	        		break;
	        	case rfbProto.ClientCutText: //need to verify!!! check endian.
	        		packet = new byte[8];
	        		in.read(packet, 0, 3);
	        		int msgLength = 0;
	        		msgLength |= (byte) in.readUnsignedByte() << 6;
	        		msgLength |= (byte) in.readUnsignedByte() << 4;
	        		msgLength |= (byte) in.readUnsignedByte() << 2;
	        		msgLength |= (byte) in.readUnsignedByte();
	        		packet = new byte[8 + msgLength];
	        		packet[0] = rfbProto.ClientCutText;
	        		packet[4] = (byte) ((msgLength & 0xff000000) >> 6);
	        		packet[5] = (byte) ((msgLength & 0xff0000) >> 4);
	        		packet[6] = (byte) ((msgLength & 0xff00) >> 2);
	        		packet[7] = (byte) ((msgLength & 0xff)); 
	        		in.read(packet, 8, msgLength);
	        		break;
	        	default:
	        		System.out.println("Unknown server message received!");
        	}
        	
        	if(messageType == rfbProto.KeyboardEvent || messageType == rfbProto.PointerEvent){
	        	synchronized(rfb.os){
	        		rfb.os.write(packet);
	        	}
        	}
        }
      }catch(IOException e){ 
    	  System.err.println(e);
      }        
    }
   
    //
    // Write our protocol version message
    //
    void writeVersionMsg() throws IOException 
    {
        byte[] b = new byte[12];
        versionMsg.getBytes(0, 12, b, 0);
        out.write(b);
    }

    //
    // Read server's protocol version message
    //
    boolean readVersionMsg() throws IOException 
    {
        byte[] b = new byte[12];
        in.readFully(b);
        
        if ((b[0] != 'R') || (b[1] != 'F') || (b[2] != 'B') || (b[3] != ' ') 
        || (b[4] < '0') || (b[4] > '9') || (b[5] < '0') || (b[5] > '9')
        || (b[6] < '0') || (b[6] > '9') || (b[7] != '.')
        || (b[8] < '0') || (b[8] > '9') || (b[9] < '0') || (b[9] > '9')
        || (b[10] < '0') || (b[10] > '9') || (b[11] != '\n'))
        {
            System.out.println("This not an RFB client");
            return false;
        }
        return true;
    }
    
    boolean doAuthentication() throws IOException
    {
        out.writeInt(NoAuth);      // no authentication needed
        return true;
    }

    void readClientInit() throws IOException
    {
        in.readByte(); // ignoring shared-flag
    }


  //
  // Write a ServerInitialisation message
  //
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

    out.write(b);
    out.writeInt(name.length());
    out.writeBytes(name);
  }
}
