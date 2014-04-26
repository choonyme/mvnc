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

//
// A ZlibInStream reads from a zlib.io.InputStream
//

import com.jcraft.jzlib.*;

public class ZlibInStream extends InStream {

  static final int defaultBufSize = 16384;

  public ZlibInStream(int bufSize_) {
    bufSize = bufSize_;
    b = new byte[bufSize];
    ptr = end = ptrOffset = 0;
    inflater = new ZStream();
    int err = inflater.inflateInit();
    CHECK_ERR(inflater, err, "ZRLE inflater init");
  }

  public ZlibInStream() { this(defaultBufSize); }

  public void setUnderlying(InStream is, int bytesIn_) {
    underlying = is;
    bytesIn = bytesIn_;
    ptr = end = 0;
  }

  public void reset() throws Exception, java.util.zip.DataFormatException {
    ptr = end = 0;
    if (underlying == null) return;

    while (bytesIn > 0) {
      decompress();
      end = 0; // throw away any data
    }
    underlying = null;
  }

  public int pos() { return ptrOffset + ptr; }

  public ZStream getDecoder(){
	  return inflater;
  }
  
  protected int overrun(int itemSize, int nItems) throws Exception, java.util.zip.DataFormatException {
    if (itemSize > bufSize)
      throw new Exception("ZlibInStream overrun: max itemSize exceeded");
    if (underlying == null)
      throw new Exception("ZlibInStream overrun: no underlying stream");

    if (end - ptr != 0)
      System.arraycopy(b, ptr, b, 0, end - ptr);

    ptrOffset += ptr;
    end -= ptr;
    ptr = 0;

    while (end < itemSize) {
      decompress();
    }

    if (itemSize * nItems > end)
      nItems = end / itemSize;

    return nItems;
  }

  // decompress() calls the decompressor once.  Note that this won't
  // necessarily generate any output data - it may just consume some input
  // data.  Returns false if wait is false and we would block on the underlying
  // stream.

  private void decompress() throws Exception {
    //try {
      underlying.check(1);
      int avail_in = underlying.getend() - underlying.getptr();
      if (avail_in > bytesIn)
        avail_in = bytesIn;

      if(inflater.avail_in <= 0){
    	  inflater.next_in = underlying.getbuf();
    	  inflater.next_in_index = underlying.getptr();
    	  inflater.avail_in = avail_in;
      }

  	  inflater.next_out = b;
  	  inflater.next_out_index = end;
  	  inflater.avail_out = bufSize - end;
  	  int destlen = inflater.avail_out;
  	  
  	  int err = inflater.inflate(JZlib.Z_PARTIAL_FLUSH);
  	  CHECK_ERR(inflater, err, "ZRLE inflater decompress");
  	  if(err != JZlib.Z_OK){
  		  System.out.println("Throwing DFE");
  		  inflater.avail_in = 0;
  		  throw new java.util.zip.DataFormatException();
  	  }
  	  
  	  int  n = destlen - inflater.avail_out;

      end += n;
      if(inflater.avail_in <= 0){
        bytesIn -= avail_in;
        underlying.setptr(underlying.getptr() + avail_in);
      }
    /*} catch (java.util.zip.DataFormatException e) {
      throw new Exception("ZlibInStream: inflate failed");
    }*/
  }

  //
  // Check zlib error message
  //

  void CHECK_ERR(ZStream z, int err, String msg){
  	if(err != JZlib.Z_OK){
  		if(z.msg != null) System.out.println(z.msg + " ");
  		System.out.println(msg + " error: " + err);
  		//System.exit(0);
  	}
  }
  
  //
  // Set dictionary
  //
  
  public void setDictionary(byte[] b, int len){
	  inflater.inflateSetDictionary(b, len);
  }
  
  private InStream underlying;
  private int bufSize;
  private int ptrOffset;
  private ZStream inflater;
  private int bytesIn;
}
