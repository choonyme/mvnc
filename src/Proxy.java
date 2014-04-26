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

/**
 * Delivers a port for new client connections.
 * Initialisation of a new client is done in class Connector,
 * which runs as a thread so that bad clients can't block.
 * 
 * @author Peter Ziewer - University of Trier - ziewer@psi.uni-trier.de
 * @version Aug.09/2001
 */
public class Proxy extends Thread
{
    rfbMultiProto rfb;
    
    /**
     * Creates a new Proxy.
     */
    public Proxy(rfbMultiProto rfb)
    {
        this.rfb = rfb;
        start();
    }
    
    /**
     * Calculate port for MulticastVNC server/proxy
     */
    int calculatePort() //throws IOException
    {
        String s = rfb.viewer.readParameter("PROXYPORT",false);
        if(s!=null) {
            try{ 
                return Integer.parseInt(s);
            } catch(NumberFormatException e) {}
        }
        return 4444; // if parameter PROXYPORT not specified
    }
 
   
    /**
     * Listen for new clients and connect them.
     */
    public void run()
    {
        int port = calculatePort();
        ServerSocket listen;
        
        try{ listen = new ServerSocket(port); } catch(IOException e) {System.out.println("Can NOT start proxy: "+e);return;}
        
        // wait until ready to broadcast
        while(!rfb.inNormalProtocol) try{sleep(1);}catch(InterruptedException e){}; 

        System.out.println("\nMulticastVNC server started (port "+port+")\n");

        while(true)
            try {
                Socket socket = listen.accept(); // waits for new connection
                new Connector(this.rfb,socket);  // initialisation is done in an own thread
            }catch(IOException e){ System.err.println("Proxy says: "+e); }
    }
}
