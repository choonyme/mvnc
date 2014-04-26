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

import org.jgroups.JChannel;

/**
 * Write a description of class rfbMultiProto here.
 * 
 * @author (Peter Ziewer) 
 * @version (Aug.2001)
 */
public class rfbMultiProto extends rfbProto 
{
    static final String multiAddressDefault = "230.0.0.42";
    static final int multiPortDefault = 4442;
    static final int multiTTLDefault = 1;
    static final int unicastViewer=0, multicastViewer=1, multicastProxy=2, playbackViewer=3;
    int status;	
    
    int expectedBufferSize = 65500;
    boolean newClient = false;  // for update daemon
    
    InetAddress multiGroup;
    int multiPort; //=-1;
    int multiTTL;
    
    public rfbMultiProto(String host, int port, Mvncviewer viewer) throws IOException
    {
        super(host,port,viewer);
        
        multicastProxyCheck();
        
        if((status!=multicastProxy) && viewer.readParameter("FILE",false)!=null) 
        {   
            status=playbackViewer;
            is = new PlaybackInputStream(this);
            os = new NoOutputStream();
        }
    }


    void multicastProxyCheck() throws IOException
    {
        // calculate multicast address and port
        String s = viewer.readParameter("MULTICAST",false);
        if(s!=null) {
            String multiAddress;
            
            int x = s.indexOf(':');
            if(x!=-1) {
                multiAddress = s.substring(0,x);
                int y = s.indexOf('/');
                if(y!=-1) {
                    multiPort = Integer.parseInt(s.substring(x+1,y));
                    multiTTL  = Integer.parseInt(s.substring(y+1));
                } else {
                    multiPort = Integer.parseInt(s.substring(x+1));
                    multiTTL  = multiTTLDefault;
                }
            } else {
                multiAddress = s;
                multiPort    = multiPortDefault;
                multiTTL     = multiTTLDefault;
            }
            if(multiTTL<=0 || 255<=multiTTL) multiTTL     = multiTTLDefault;

            if(multiAddress.equals("")) multiAddress = multiAddressDefault;


            try{
                multiGroup = InetAddress.getByName(multiAddress);
            } catch(UnknownHostException e)
            {
                System.out.println("unkown address: "+multiAddress);
                multiAddress = multiAddressDefault;
                multiGroup = InetAddress.getByName(multiAddress);
            }
            
            //change xml config here... 
          	File udpxml = new File("udp.xml");
        	if (!udpxml.exists()){
        		System.out.println("can't open udp.xml config file!");
        		System.exit(0);
        	}else{
        		System.out.println("writing configs");
        		JChannelProfileDOM jdom = new JChannelProfileDOM(udpxml);
        		jdom.setMcastAddr(multiAddress);
        		jdom.setMcastPort(multiPort);
        		jdom.setTTL(multiTTL);
        		jdom.saveFile();
        	}
            
            status = multicastProxy;        
        }
    }
    
    
    int readServerMessageType() throws IOException
    {
        if(status==multicastProxy) is.flush(); // sends multicast datagram if needed
        if(status==playbackViewer) is.flush(); // synchronises time
        return super.readServerMessageType();
    }


    void readServerInit() throws IOException
    {
        super.readServerInit();
        
        if(desktopName.startsWith("MulticastVNC")) status = multicastViewer;
        
        initIOStreams(); // Initialisation done.
    }
    
    
    void initIOStreams() throws IOException
    {        
        switch(status) {
            case unicastViewer:
                System.out.println("\nnormal VNC Viewer\n");
                break;

            case multicastViewer:
                multiPort = multiPortDefault;
                multiGroup = InetAddress.getByName(multiAddressDefault);
                    
                String multiAddress = desktopName.substring(desktopName.indexOf(' ')+1,desktopName.indexOf(':'));
                multiGroup = InetAddress.getByName(multiAddress);
                multiPort = Integer.parseInt(desktopName.substring(desktopName.indexOf(':')+1,desktopName.lastIndexOf(':')));
                int multiSenderPort = Integer.parseInt(desktopName.substring(desktopName.lastIndexOf(':')+1));

                System.out.println("\nMulticastVNC Viewer");
                System.out.println("MulticastVNC using group "+multiGroup.getHostAddress()+" and port "+multiPort+"\n");
                is = new MulticastInputStream(this,multiSenderPort);
                //os = new NoOutputStream();
                break;
                
            case multicastProxy:
                System.out.println("\nMulticastVNC Proxy/Server");
                System.out.println("MulticastVNC using group "+multiGroup.getHostAddress()+" and port "+multiPort+"\n");
                is = new InputStreamWithMulticastOutput(this);
                //os goes to the VNC server
                break;

            case playbackViewer:
                System.out.println("\nplayback VNC Viewer\n");
                // Streams are already set in constructor
                break;
        }
    }
}
