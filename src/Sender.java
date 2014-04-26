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

import java.net.*;
import java.io.*;

public class Sender extends Thread
{
    MulticastSocket socket;
    InetAddress address;
    int port;
    byte ttl;

    public Sender(MulticastSocket socket, InetAddress address, int port, byte ttl) throws IOException
    {
        this.socket = socket;
        this.address = address;
        this.port = port;
        this.ttl = ttl;

        socket.send(new DatagramPacket(new byte[0],0,address,port),ttl);
        System.out.println("sending datagram to "+address+" port "+port+" with ttl "+ttl);
    }

    public void run()
    {
        while(true) {
            try{
                System.in.read();
                socket.send(new DatagramPacket(new byte[0],0,address,port),ttl);
                System.out.println("sending datagram to "+address+" port "+port+" with ttl "+ttl);
            }catch(IOException e){System.err.println(e);}
        }
    }    
}
