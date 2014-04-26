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

import java.util.TimerTask;
import java.io.IOException;
import java.util.Timer;

public class UpdateRequestDaemon extends TimerTask
{
    rfbMultiProto rfb;

    UpdateRequestDaemon(rfbMultiProto rfb)
    {
        this(rfb,10000);
    }
    
    UpdateRequestDaemon(rfbMultiProto rfb, int interval)
    {
        this.rfb = rfb;
        
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(this,5000,interval);
    }
   
    public void run()
    {
        /*try {
            if(rfb.newClient) {// full request
            	System.out.println("Requesting FULL update!");
            	//send zlib dictionary here... need to acceptView() first
                //rfb.writeFramebufferUpdateRequest(0, 0, 
                  //      rfb.framebufferWidth, rfb.framebufferHeight, false);
            } else{
            	//System.out.println("Requesting update...");
                //rfb.writeFramebufferUpdateRequest(0, 0, 
                       // rfb.framebufferWidth, rfb.framebufferHeight, true);
            }
        } catch(IOException e){ System.err.println(e); }*/
    }
}
