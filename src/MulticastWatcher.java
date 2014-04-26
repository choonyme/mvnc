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

public class MulticastWatcher {
    
    MulticastSocket socket;
    InetAddress address;
    DatagramPacket packet;
    String group;
    int port;
    byte ttl;
    byte[] buf;
    int total;
    Sender sender;

    public static void main(String[] args) throws IOException 
    {
        MulticastWatcher mw = new MulticastWatcher(args);
        mw.watch();
    }
    
    MulticastWatcher(String[] args) throws IOException
    {
        System.out.println("\nLists multicast datagrams");
        System.out.println("Usage: MulticastWatcher [group [port]]\n");
        
        group = "230.0.0.42";
        port = 4442;
        ttl = 16;
        if(args.length>0) group = args[0]; 
        if(args.length>1) port = Integer.parseInt(args[1]); 
        if(args.length>2) ttl = Byte.parseByte(args[2]); 
        
        total=0;

        socket = new MulticastSocket(port);
        address = InetAddress.getByName(group);

//         socket.setReceiveBufferSize(65535);
//         socket.setSendBufferSize(65535);
        socket.setReceiveBufferSize(262144);
        socket.setSendBufferSize(262144);
//         socket.setReceiveBufferSize(10000000);
//         socket.setSendBufferSize(10000000);

        System.out.println("\nWatching multicast group "+address+" port "+port+" ttl "+ttl+"\n");

        System.out.println("ReceiveBufferSize: "+socket.getReceiveBufferSize());
        System.out.println("SendBufferSize: "+socket.getSendBufferSize());

        buf = new byte[socket.getReceiveBufferSize()];
        socket.joinGroup(address);
        
//        socket.send(new DatagramPacket(buf,1,address,port),ttl);
        sender = new Sender(socket,address,port,ttl);
    }
    
    public void watch()
    {        
        sender.start();
        while(true) {
            try{
                packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                int len = packet.getLength();
                System.out.println("received msg: "+buf[0]+" length: "+len+"("+(total+=len)+")"
                                +" from "+packet.getAddress()+" port "+packet.getPort());
            }catch(IOException e){System.err.println(e);}                   
        }
    }
    
}
