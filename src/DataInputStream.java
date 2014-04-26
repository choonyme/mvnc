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

/**
 * Write a description of class DataInputStream here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class DataInputStream
{
    java.io.DataInputStream in;
    
    public DataInputStream()
    {
    }
    public DataInputStream(InputStream in)
    {
        this.in = new java.io.DataInputStream(in);
    }
    
    public void flush() throws IOException
    {   }
    
    
    public int read() throws IOException
    {   return in.read();   }
    public int read(byte[] b) throws IOException
    {   return in.read(b);   }
    public int read(byte[] b, int off, int len) throws IOException
    {   return in.read(b,off,len);   }
//     public boolean readBoolean() throws IOException
//     {   return in.readBoolean();   }
    public byte readByte() throws IOException
    {   return in.readByte();   }
//     public char readChar() throws IOException
//     {   return in.readChar();   }
//     public double readDouble() throws IOException
//     {   return in.readDouble();   }
//     public float readFloat() throws IOException
//     {   return in.readFloat();   }    
    public void readFully(byte[] b) throws IOException
    {   in.readFully(b);   }
    public void readFully(byte[] b, int off, int len) throws IOException
    {   in.readFully(b,off,len);   }
    public int readInt() throws IOException
    {   return in.readInt();   }
//     public String readLine() throws IOException
//     {   return in.readLine();   }
     public long readLong() throws IOException
     {   return in.readLong();   }
//     public short readShort() throws IOException
//     {   return in.readShort();   }
    public int readUnsignedByte() throws IOException
    {   return in.readUnsignedByte();   }
    public int readUnsignedShort() throws IOException
    {   return in.readUnsignedShort();   }
    public int available() throws IOException{
    	return in.available();
    }
    public long skip(long l) throws IOException{
    	return in.skip(l);
    }
}
