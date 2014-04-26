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

import org.jgroups.*;
import java.io.*;
import java.nio.*;

public class MVNCStream extends ReceiverAdapter{
	final int BUFSIZE = 1048576; //1MB
	ByteBuffer buf = ByteBuffer.allocate(BUFSIZE);
	OutputStream os = newOutputStream(buf);
	InputStream is = newInputStream(buf);
	
	public MVNCStream(){		
	}
	
	public OutputStream newOutputStream(final ByteBuffer buf){
		return new OutputStream(){
			public synchronized void write(byte b[]) throws IOException{
				buf.put(b);
			}
			
			public synchronized void write(int b) throws IOException{
				buf.put((byte) b);
			}
			
			public synchronized void write(byte[] bytes, int off, int len) throws IOException{
				buf.put(bytes, off, len);
			}
		};
	}
	
	public InputStream newInputStream(final ByteBuffer buf){
		return new InputStream(){
			public synchronized int read() throws IOException{
				if(!buf.hasRemaining()) return -1;
				
				return buf.get();
			}
			
			public synchronized int read(byte[] bytes, int off, int len) throws IOException{
				len = Math.min(len, buf.remaining());
				buf.get(bytes, off, len);
				return len;
			}
		};
	}
	
	public void receive(Message msg){
		try{
			//need to handle overflow cases. Eg. use wait().
			os.write(msg.getBuffer());
		}catch(IOException ex){
			
		}
	}
	
	public void viewAccepted(View view){
		
	}
	
	public InputStream getInputStream(){
		return is;
	}
}