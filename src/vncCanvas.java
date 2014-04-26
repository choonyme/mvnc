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

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.*;
import java.io.*;
import com.jcraft.jzlib.*;

//
// vncCanvas is a subclass of Canvas which draws a VNC desktop on it.
//

class vncCanvas extends Canvas implements KeyListener, MouseListener, MouseMotionListener
{
  boolean paint=true;
  
  Mvncviewer viewer;
  rfbMultiProto rfb;
  
  ColorModel cm8, cm24;
  Color[] colors; 
  int bytesPixel;

  int maxWidth = 0, maxHeight = 0;
  int scalingFactor;
  int scaledWidth, scaledHeight;

  Image memImage;
  Graphics memGraphics;

  Image rawPixelsImage;
  MemoryImageSource pixelsSource;
  byte[] pixels8;
  int[] pixels24;

  // ZRLE encoder's data.
  byte[] zrleBuf;
  int zrleBufLen = 0;
  byte[] zrleTilePixels8;
  int[] zrleTilePixels24;
  ZlibInStream zrleInStream;
  boolean zrleRecWarningShown = false;

  // Zlib encoder's data.
  byte[] zlibBuf;
  int zlibBufLen = 0;
  ZStream zlibIStream;
  
  // Tight encoder's data.
  final static int tightZlibBufferSize = 512;
  ZStream[] tightIStreams;

  // Since JPEG images are loaded asynchronously, we have to remember
  // their position in the framebuffer. Also, this jpegRect object is
  // used for synchronization between the rfbThread and a JVM's thread
  // which decodes and loads JPEG images.
  Rectangle jpegRect;

  // True if we process keyboard and mouse events.
  boolean inputEnabled;
  
  vncCanvas(Mvncviewer v) throws IOException {
    viewer = v;
    rfb = v.rfb;

    maxWidth = rfb.framebufferWidth;
    maxHeight = rfb.framebufferHeight;

    rfb = viewer.rfb;
    //scalingFactor = viewer.options.scalingFactor;
    scalingFactor = 100;

    tightIStreams = new ZStream[4];
    for(int i=0; i < 4; i++){
    	tightIStreams[i] = new ZStream();
    	int err = tightIStreams[i].inflateInit();
    	CHECK_ERR(tightIStreams[i], err, "inflateInit");
    }

    cm8 = new DirectColorModel(8, 7, (7 << 3), (3 << 6));
    cm24 = new DirectColorModel(24, 0xFF0000, 0x00FF00, 0x0000FF);

    colors = new Color[256];
    for (int i = 0; i < 256; i++)
      colors[i] = new Color(cm8.getRGB(i));

    setPixelFormat();

    inputEnabled = false;
    
    addMouseListener(this);
    addMouseMotionListener(this);

    // Keyboard listener is enabled even in view-only mode, to catch
    // 'r' or 'R' key presses used to request screen update.
    addKeyListener(this);
  }

  public Dimension preferredSize() {
    return new Dimension(rfb.framebufferWidth, rfb.framebufferHeight);
  }

  public Dimension minimumSize() {
    return new Dimension(rfb.framebufferWidth, rfb.framebufferHeight);
  }

  //
  // All painting is performed here.
  //

  public void update(Graphics g) {
    paint(g);
  }

  public void paint(Graphics g) {
    synchronized(memImage) {
      if (rfb.framebufferWidth == scaledWidth) {
        g.drawImage(memImage, 0, 0, null);
      }
    }
  }

  //
  // Override the ImageObserver interface method to handle drawing of
  // JPEG-encoded data.
  //

  public boolean imageUpdate(Image img, int infoflags,
                             int x, int y, int width, int height) {
    if ((infoflags & (ALLBITS | ABORT)) == 0) {
      return true;		// We need more image data.
    } else {
      // If the whole image is available, draw it now.
      if ((infoflags & ALLBITS) != 0) {
	if (jpegRect != null) {
	  synchronized(jpegRect) {
	    memGraphics.drawImage(img, jpegRect.x, jpegRect.y, null);
	    scheduleRepaint(jpegRect.x, jpegRect.y,
			    jpegRect.width, jpegRect.height);
	    jpegRect.notify();
	  }
	}
      }
      return false;		// All image data was processed.
    }
  }

  public void setPixelFormat() throws IOException {
	    if (viewer.options.eightBitColors) {
	      rfb.writeSetPixelFormat(8, 8, false, true, 7, 7, 3, 0, 3, 6);
	      bytesPixel = 1;
	    } else {
	      rfb.writeSetPixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);
	      bytesPixel = 4;
	    }
	    updateFramebufferSize();
	  }

	  void updateFramebufferSize() {

	    // Useful shortcuts.
	    int fbWidth = rfb.framebufferWidth;
	    int fbHeight = rfb.framebufferHeight;

	    // Calculate scaling factor for auto scaling.
	    if (maxWidth > 0 && maxHeight > 0) {
	      int f1 = maxWidth * 100 / fbWidth;
	      int f2 = maxHeight * 100 / fbHeight;
	      scalingFactor = Math.min(f1, f2);
	      if (scalingFactor > 100)
		scalingFactor = 100;
	      System.out.println("Scaling desktop at " + scalingFactor + "%");
	    }

	    // Update scaled framebuffer geometry.
	    scaledWidth = (fbWidth * scalingFactor + 50) / 100;
	    scaledHeight = (fbHeight * scalingFactor + 50) / 100;

	    // Create new off-screen image either if it does not exist, or if
	    // its geometry should be changed. It's not necessary to replace
	    // existing image if only pixel format should be changed.
	    if (memImage == null) {
	      memImage = viewer.createImage(fbWidth, fbHeight);
	      memGraphics = memImage.getGraphics();
	    } else if (memImage.getWidth(null) != fbWidth ||
		       memImage.getHeight(null) != fbHeight) {
	      synchronized(memImage) {
		memImage = viewer.createImage(fbWidth, fbHeight);
		memGraphics = memImage.getGraphics();
	      }
	    }

	    // Images with raw pixels should be re-allocated on every change
	    // of geometry or pixel format.
	    if (bytesPixel == 1) {

	      pixels24 = null;
	      pixels8 = new byte[fbWidth * fbHeight];

	      pixelsSource =
		new MemoryImageSource(fbWidth, fbHeight, cm8, pixels8, 0, fbWidth);

	      zrleTilePixels24 = null;
	      zrleTilePixels8 = new byte[64 * 64];

	    } else {

	      pixels8 = null;
	      pixels24 = new int[fbWidth * fbHeight];

	      pixelsSource =
		new MemoryImageSource(fbWidth, fbHeight, cm24, pixels24, 0, fbWidth);

	      zrleTilePixels8 = null;
	      zrleTilePixels24 = new int[64 * 64];

	    }
	    pixelsSource.setAnimated(true);
	    rawPixelsImage = Toolkit.getDefaultToolkit().createImage(pixelsSource);

	      setSize(scaledWidth, scaledHeight);
	  }



  //
  // processNormalProtocol() - executed by the rfbThread to deal with the
  // RFB socket.
  //

  public void processNormalProtocol() throws Exception {

	    rfb.writeFramebufferUpdateRequest(0, 0, rfb.framebufferWidth,
					      rfb.framebufferHeight, false);

	    //
	    // main dispatch loop
	    //

	    while (true) {
	      if(rfb.is instanceof InputStreamWithMulticastOutput && rfb.newClient){
	    	  rfb.newClient = false;
	    		
	    	  byte decoderState[] = handleEncodeState();
	    	  if(decoderState == null)
	    		continue;
	    	
	   		  System.out.println("State data: " + decoderState[0] + " " + decoderState[1] + " " + decoderState[decoderState.length - 1] + " " + decoderState[decoderState.length - 2]);
	   		  ((InputStreamWithMulticastOutput) rfb.is).write(decoderState);
	    	  System.out.println("Sent state transfer");
	    		
	    	  rfb.writeFramebufferUpdateRequest(0, 0, rfb.framebufferWidth, rfb.framebufferHeight, false);
	      }
	      
	      // Read message type from the server.
	      int msgType = rfb.readServerMessageType();

	      // Process the message depending on its type.
	      switch (msgType) {
	      case rfbProto.FramebufferUpdate:
		rfb.readFramebufferUpdate();

		//boolean cursorPosReceived = false;

		for (int i = 0; i < rfb.updateNRects; i++) {
		  rfb.readFramebufferUpdateRectHdr();
		  int rx = rfb.updateRectX, ry = rfb.updateRectY;
		  int rw = rfb.updateRectW, rh = rfb.updateRectH;

		  if (rfb.updateRectEncoding == rfb.EncodingLastRect)
		    break;

		  if (rfb.updateRectEncoding == rfb.EncodingNewFBSize) {
		    rfb.setFramebufferSize(rw, rh);
		    updateFramebufferSize();
		    break;
		  }

	      rfb.startTiming();	      
//System.out.println(rfb.updateRectEncoding);
		  switch (rfb.updateRectEncoding) {
		  case rfbProto.EncodingRaw:
		    handleRawRect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingCopyRect:
		    handleCopyRect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingRRE:
		    handleRRERect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingCoRRE:
		    handleCoRRERect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingHextile:
		    handleHextileRect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingZRLE:
		    handleZRLERect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingZlib:
	        handleZlibRect(rx, ry, rw, rh);
		    break;
		  case rfbProto.EncodingTight:
			handleTightRect(rx, ry, rw, rh);
		    break;
		  default:
		    throw new Exception("Unknown RFB rectangle encoding " +
					rfb.updateRectEncoding);
		  }

	          rfb.stopTiming();
		}

		boolean fullUpdateNeeded = false;

		// Defer framebuffer update request if necessary. But wake up
		// immediately on keyboard or mouse event. Also, don't sleep
		// if there is some data to receive, or if the last update
		// included a PointerPos message.
		if (viewer.deferUpdateRequests > 0 &&
		    rfb.is.available() == 0) {
		  synchronized(rfb) {
		    try {
		      rfb.wait(viewer.deferUpdateRequests);
		    } catch (InterruptedException e) {
		    }
		  }
		}

		// Before requesting framebuffer update, check if the pixel
		// format should be changed. If it should, request full update
		// instead of an incremental one.
		if (viewer.options.eightBitColors != (bytesPixel == 1)) {
		  setPixelFormat();
		  fullUpdateNeeded = true;
		}

		rfb.writeFramebufferUpdateRequest(0, 0, rfb.framebufferWidth,
						  rfb.framebufferHeight,
						  !fullUpdateNeeded);

		break;

	      case rfbProto.SetColourMapEntries:
		throw new Exception("Can't handle SetColourMapEntries message");

	      case rfbProto.Bell:
	        Toolkit.getDefaultToolkit().beep();
		break;

	      case rfbProto.ServerCutText:
	    	  String s = rfb.readServerCutText();
	    	  viewer.clipboard.setCutText(s);
	    	  break;
		
	      case rfbProto.DecoderState:
	    	  handleDecoderState();
	    	  //storeDecoderState();
	    	  break;

	      default:
		throw new Exception("Unknown RFB message type " + msgType);
	      }
	    }

  }


  //
  // Handle a raw rectangle. The second form with paint==false is used
  // by the Hextile decoder for raw-encoded tiles.
  //

  void handleRawRect(int x, int y, int w, int h) throws IOException {
    handleRawRect(x, y, w, h, true);
  }

  void handleRawRect(int x, int y, int w, int h, boolean paint)
    throws IOException {

    if (bytesPixel == 1) {
      for (int dy = y; dy < y + h; dy++) {
	rfb.readFully(pixels8, dy * rfb.framebufferWidth + x, w);
	if (rfb.rec != null) {
	  rfb.rec.write(pixels8, dy * rfb.framebufferWidth + x, w);
	}
      }
    } else {
      byte[] buf = new byte[w * 4];
      int i, offset;
      for (int dy = y; dy < y + h; dy++) {
	rfb.readFully(buf);
	if (rfb.rec != null) {
	  rfb.rec.write(buf);
	}
	offset = dy * rfb.framebufferWidth + x;
	for (i = 0; i < w; i++) {
	  pixels24[offset + i] =
	    (buf[i * 4 + 2] & 0xFF) << 16 |
	    (buf[i * 4 + 1] & 0xFF) << 8 |
	    (buf[i * 4] & 0xFF);
	}
      }
    }

    handleUpdatedPixels(x, y, w, h);
    if (paint)
      scheduleRepaint(x, y, w, h);
  }

  //
  // Handle a CopyRect rectangle.
  //

  void handleCopyRect(int x, int y, int w, int h) throws IOException {

    rfb.readCopyRect();
    memGraphics.copyArea(rfb.copyRectSrcX, rfb.copyRectSrcY, w, h,
			 x - rfb.copyRectSrcX, y - rfb.copyRectSrcY);

    scheduleRepaint(x, y, w, h);
  }

  //
  // Handle an RRE-encoded rectangle.
  //

  void handleRRERect(int x, int y, int w, int h) throws IOException {

    int nSubrects = rfb.is.readInt();

    byte[] bg_buf = new byte[bytesPixel];
    rfb.readFully(bg_buf);
    Color pixel;
    if (bytesPixel == 1) {
      pixel = colors[bg_buf[0] & 0xFF];
    } else {
      pixel = new Color(bg_buf[2] & 0xFF, bg_buf[1] & 0xFF, bg_buf[0] & 0xFF);
    }
    memGraphics.setColor(pixel);
    memGraphics.fillRect(x, y, w, h);

    byte[] buf = new byte[nSubrects * (bytesPixel + 8)];
    rfb.readFully(buf);
    DataInputStream ds = new DataInputStream(new ByteArrayInputStream(buf));

    if (rfb.rec != null) {
      rfb.rec.writeIntBE(nSubrects);
      rfb.rec.write(bg_buf);
      rfb.rec.write(buf);
    }

    int sx, sy, sw, sh;

    for (int j = 0; j < nSubrects; j++) {
      if (bytesPixel == 1) {
	pixel = colors[ds.readUnsignedByte()];
      } else {
	ds.skip(4);
	pixel = new Color(buf[j*12+2] & 0xFF,
			  buf[j*12+1] & 0xFF,
			  buf[j*12]   & 0xFF);
      }
      sx = x + ds.readUnsignedShort();
      sy = y + ds.readUnsignedShort();
      sw = ds.readUnsignedShort();
      sh = ds.readUnsignedShort();

      memGraphics.setColor(pixel);
      memGraphics.fillRect(sx, sy, sw, sh);
    }

    scheduleRepaint(x, y, w, h);
  }

  //
  // Handle a CoRRE-encoded rectangle.
  //

  void handleCoRRERect(int x, int y, int w, int h) throws IOException {
    int nSubrects = rfb.is.readInt();

    byte[] bg_buf = new byte[bytesPixel];
    rfb.readFully(bg_buf);
    Color pixel;
    if (bytesPixel == 1) {
      pixel = colors[bg_buf[0] & 0xFF];
    } else {
      pixel = new Color(bg_buf[2] & 0xFF, bg_buf[1] & 0xFF, bg_buf[0] & 0xFF);
    }
    memGraphics.setColor(pixel);
    memGraphics.fillRect(x, y, w, h);

    byte[] buf = new byte[nSubrects * (bytesPixel + 4)];
    rfb.readFully(buf);

    if (rfb.rec != null) {
      rfb.rec.writeIntBE(nSubrects);
      rfb.rec.write(bg_buf);
      rfb.rec.write(buf);
    }

    int sx, sy, sw, sh;
    int i = 0;

    for (int j = 0; j < nSubrects; j++) {
      if (bytesPixel == 1) {
	pixel = colors[buf[i++] & 0xFF];
      } else {
	pixel = new Color(buf[i+2] & 0xFF, buf[i+1] & 0xFF, buf[i] & 0xFF);
	i += 4;
      }
      sx = x + (buf[i++] & 0xFF);
      sy = y + (buf[i++] & 0xFF);
      sw = buf[i++] & 0xFF;
      sh = buf[i++] & 0xFF;

      memGraphics.setColor(pixel);
      memGraphics.fillRect(sx, sy, sw, sh);
    }

    scheduleRepaint(x, y, w, h);
  }

  //
  // Handle a Hextile-encoded rectangle.
  //

  // These colors should be kept between handleHextileSubrect() calls.
  private Color hextile_bg, hextile_fg;

  void handleHextileRect(int x, int y, int w, int h) throws IOException {

    hextile_bg = new Color(0);
    hextile_fg = new Color(0);

    for (int ty = y; ty < y + h; ty += 16) {
      int th = 16;
      if (y + h - ty < 16)
	th = y + h - ty;

      for (int tx = x; tx < x + w; tx += 16) {
	int tw = 16;
	if (x + w - tx < 16)
	  tw = x + w - tx;

	handleHextileSubrect(tx, ty, tw, th);
      }

      // Finished with a row of tiles, now let's show it.
      scheduleRepaint(x, y, w, h);
    }
  }

  //
  // Handle one tile in the Hextile-encoded data.
  //

  void handleHextileSubrect(int tx, int ty, int tw, int th)
    throws IOException {

    int subencoding = rfb.is.readUnsignedByte();
    if (rfb.rec != null) {
      rfb.rec.writeByte(subencoding);
    }

    // Is it a raw-encoded sub-rectangle?
    if ((subencoding & rfb.HextileRaw) != 0) {
      handleRawRect(tx, ty, tw, th, false);
      return;
    }

    // Read and draw the background if specified.
    byte[] cbuf = new byte[bytesPixel];
    if ((subencoding & rfb.HextileBackgroundSpecified) != 0) {
      rfb.readFully(cbuf);
      if (bytesPixel == 1) {
	hextile_bg = colors[cbuf[0] & 0xFF];
      } else {
	hextile_bg = new Color(cbuf[2] & 0xFF, cbuf[1] & 0xFF, cbuf[0] & 0xFF);
      }
      if (rfb.rec != null) {
	rfb.rec.write(cbuf);
      }
    }
    memGraphics.setColor(hextile_bg);
    memGraphics.fillRect(tx, ty, tw, th);

    // Read the foreground color if specified.
    if ((subencoding & rfb.HextileForegroundSpecified) != 0) {
      rfb.readFully(cbuf);
      if (bytesPixel == 1) {
	hextile_fg = colors[cbuf[0] & 0xFF];
      } else {
	hextile_fg = new Color(cbuf[2] & 0xFF, cbuf[1] & 0xFF, cbuf[0] & 0xFF);
      }
      if (rfb.rec != null) {
	rfb.rec.write(cbuf);
      }
    }

    // Done with this tile if there is no sub-rectangles.
    if ((subencoding & rfb.HextileAnySubrects) == 0)
      return;

    int nSubrects = rfb.is.readUnsignedByte();
    int bufsize = nSubrects * 2;
    if ((subencoding & rfb.HextileSubrectsColoured) != 0) {
      bufsize += nSubrects * bytesPixel;
    }
    byte[] buf = new byte[bufsize];
    rfb.readFully(buf);
    if (rfb.rec != null) {
      rfb.rec.writeByte(nSubrects);
      rfb.rec.write(buf);
    }

    int b1, b2, sx, sy, sw, sh;
    int i = 0;

    if ((subencoding & rfb.HextileSubrectsColoured) == 0) {

      // Sub-rectangles are all of the same color.
      memGraphics.setColor(hextile_fg);
      for (int j = 0; j < nSubrects; j++) {
	b1 = buf[i++] & 0xFF;
	b2 = buf[i++] & 0xFF;
	sx = tx + (b1 >> 4);
	sy = ty + (b1 & 0xf);
	sw = (b2 >> 4) + 1;
	sh = (b2 & 0xf) + 1;
	memGraphics.fillRect(sx, sy, sw, sh);
      }
    } else if (bytesPixel == 1) {

      // BGR233 (8-bit color) version for colored sub-rectangles.
      for (int j = 0; j < nSubrects; j++) {
	hextile_fg = colors[buf[i++] & 0xFF];
	b1 = buf[i++] & 0xFF;
	b2 = buf[i++] & 0xFF;
	sx = tx + (b1 >> 4);
	sy = ty + (b1 & 0xf);
	sw = (b2 >> 4) + 1;
	sh = (b2 & 0xf) + 1;
	memGraphics.setColor(hextile_fg);
	memGraphics.fillRect(sx, sy, sw, sh);
      }

    } else {

      // Full-color (24-bit) version for colored sub-rectangles.
      for (int j = 0; j < nSubrects; j++) {
	hextile_fg = new Color(buf[i+2] & 0xFF,
			       buf[i+1] & 0xFF,
			       buf[i] & 0xFF);
	i += 4;
	b1 = buf[i++] & 0xFF;
	b2 = buf[i++] & 0xFF;
	sx = tx + (b1 >> 4);
	sy = ty + (b1 & 0xf);
	sw = (b2 >> 4) + 1;
	sh = (b2 & 0xf) + 1;
	memGraphics.setColor(hextile_fg);
	memGraphics.fillRect(sx, sy, sw, sh);
      }

    }
  }

  //
  // Handle a ZRLE-encoded rectangle.
  //
  // FIXME: Currently, session recording is not fully supported for ZRLE.
  //

  void handleZRLERect(int x, int y, int w, int h) throws Exception {

    if (zrleInStream == null)
      zrleInStream = new ZlibInStream();

    int nBytes = rfb.is.readInt();
    if (nBytes > 64 * 1024 * 1024)
      throw new Exception("ZRLE decoder: illegal compressed data size");

    if (zrleBuf == null || zrleBufLen < nBytes) {
      zrleBufLen = nBytes + 4096;
      zrleBuf = new byte[zrleBufLen];
    }

    // FIXME: Do not wait for all the data before decompression.
    rfb.readFully(zrleBuf, 0, nBytes);

    if (rfb.rec != null) {
      if (rfb.recordFromBeginning) {
        rfb.rec.writeIntBE(nBytes);
        rfb.rec.write(zrleBuf, 0, nBytes);
      } else if (!zrleRecWarningShown) {
        System.out.println("Warning: ZRLE session can be recorded" +
                           " only from the beginning");
        System.out.println("Warning: Recorded file may be corrupted");
        zrleRecWarningShown = true;
      }
    }

    zrleInStream.setUnderlying(new MemInStream(zrleBuf, 0, nBytes), nBytes);

    for (int ty = y; ty < y+h; ty += 64) {

      int th = Math.min(y+h-ty, 64);

      for (int tx = x; tx < x+w; tx += 64) {

        int tw = Math.min(x+w-tx, 64);

        int mode;
        try{
        	mode = zrleInStream.readU8();
        }catch(java.util.zip.DataFormatException ex){
        	return;
        }

        boolean rle = (mode & 128) != 0;
        int palSize = mode & 127;
        int[] palette = new int[128];

        readZrlePalette(palette, palSize);

        if (palSize == 1) {
          int pix = palette[0];
          Color c = (bytesPixel == 1) ?
            colors[pix] : new Color(0xFF000000 | pix);
          memGraphics.setColor(c);
          memGraphics.fillRect(tx, ty, tw, th);
          continue;
        }

        if (!rle) {
          if (palSize == 0) {
            readZrleRawPixels(tw, th);
          } else {
            readZrlePackedPixels(tw, th, palette, palSize);
          }
        } else {
          if (palSize == 0) {
            readZrlePlainRLEPixels(tw, th);
          } else {
            readZrlePackedRLEPixels(tw, th, palette);
          }
        }
        handleUpdatedZrleTile(tx, ty, tw, th);
      }
    }

    zrleInStream.reset();

    scheduleRepaint(x, y, w, h);
  }

  int readPixel(InStream is) throws Exception {
    int pix;
    if (bytesPixel == 1) {
      pix = is.readU8();
    } else {
      int p1 = is.readU8();
      int p2 = is.readU8();
      int p3 = is.readU8();
      pix = (p3 & 0xFF) << 16 | (p2 & 0xFF) << 8 | (p1 & 0xFF);
    }
    return pix;
  }

  void readPixels(InStream is, int[] dst, int count) throws Exception {
    int pix;
    if (bytesPixel == 1) {
      byte[] buf = new byte[count];
      is.readBytes(buf, 0, count);
      for (int i = 0; i < count; i++) {
        dst[i] = (int)buf[i] & 0xFF;
      }
    } else {
      byte[] buf = new byte[count * 3];
      is.readBytes(buf, 0, count * 3);
      for (int i = 0; i < count; i++) {
        dst[i] = ((buf[i*3+2] & 0xFF) << 16 |
                  (buf[i*3+1] & 0xFF) << 8 |
                  (buf[i*3] & 0xFF));
      }
    }
  }

  void readZrlePalette(int[] palette, int palSize) throws Exception {
    readPixels(zrleInStream, palette, palSize);
  }

  void readZrleRawPixels(int tw, int th) throws Exception {
    if (bytesPixel == 1) {
      zrleInStream.readBytes(zrleTilePixels8, 0, tw * th);
    } else {
      readPixels(zrleInStream, zrleTilePixels24, tw * th); ///
    }
  }

  void readZrlePackedPixels(int tw, int th, int[] palette, int palSize)
    /*throws Exception*/ {

    int bppp = ((palSize > 16) ? 8 :
                ((palSize > 4) ? 4 : ((palSize > 2) ? 2 : 1)));
    int ptr = 0;

    for (int i = 0; i < th; i++) {
      int eol = ptr + tw;
      int b = 0;
      int nbits = 0;

      while (ptr < eol) {
        if (nbits == 0) {
        	try{
        		b = zrleInStream.readU8();
        	}catch(Exception e){
        		return;
        	}
          nbits = 8;
        }
        nbits -= bppp;
        int index = (b >> nbits) & ((1 << bppp) - 1) & 127;
        if (bytesPixel == 1) {
          zrleTilePixels8[ptr++] = (byte)palette[index];
        } else {
          zrleTilePixels24[ptr++] = palette[index];
        }
      }
    }
  }

  void readZrlePlainRLEPixels(int tw, int th) throws Exception {
    int ptr = 0;
    int end = ptr + tw * th;
    while (ptr < end) {
      int pix = readPixel(zrleInStream);
      int len = 1;
      int b;
      do {
        b = zrleInStream.readU8();
        len += b;
      } while (b == 255);

      if (!(len <= end - ptr)){
    	 System.out.println(len + " < " + (end - ptr));
    	 len = end - ptr - 1;
        //throw new Exception("ZRLE decoder: assertion failed" + " (len <= end-ptr)");
      }
      
      if (bytesPixel == 1) {
        while (len-- > 0) zrleTilePixels8[ptr++] = (byte)pix;
      } else {
        while (len-- > 0) zrleTilePixels24[ptr++] = pix;
      }
    }
  }

  void readZrlePackedRLEPixels(int tw, int th, int[] palette)
    throws Exception {

    int ptr = 0;
    int end = ptr + tw * th;
    while (ptr < end) {
      int index = zrleInStream.readU8();
      int len = 1;
      if ((index & 128) != 0) {
        int b;
        do {
          b = zrleInStream.readU8();
          len += b;
        } while (b == 255);
        
        if (!(len <= end - ptr)){
        	len = end - ptr - 1;
          //throw new Exception("ZRLE decoder: assertion failed" + " (len <= end - ptr)");
        }
        
      }

      index &= 127;
      int pix = palette[index];

      if (bytesPixel == 1) {
        while (len-- > 0) zrleTilePixels8[ptr++] = (byte)pix;
      } else {
        while (len-- > 0) zrleTilePixels24[ptr++] = pix;
      }
    }
  }

  //
  // Copy pixels from zrleTilePixels8 or zrleTilePixels24, then update.
  //

  void handleUpdatedZrleTile(int x, int y, int w, int h) {
    Object src, dst;
    if (bytesPixel == 1) {
      src = zrleTilePixels8; dst = pixels8;
    } else {
      src = zrleTilePixels24; dst = pixels24;
    }
    int offsetSrc = 0;
    int offsetDst = (y * rfb.framebufferWidth + x);
    for (int j = 0; j < h; j++) {
      System.arraycopy(src, offsetSrc, dst, offsetDst, w);
      offsetSrc += w;
      offsetDst += rfb.framebufferWidth;
    }
    handleUpdatedPixels(x, y, w, h);
  }

  //
  // Handle a Zlib-encoded rectangle.
  //

  void handleZlibRect(int x, int y, int w, int h) throws Exception {
	int err;
    int nBytes = rfb.is.readInt();

    if (zlibBuf == null || zlibBufLen < nBytes) {
      zlibBufLen = nBytes * 2;
      zlibBuf = new byte[zlibBufLen];
    }
    //System.out.println("zlib len " + nBytes);
    rfb.readFully(zlibBuf, 0, nBytes);

    if (rfb.rec != null && rfb.recordFromBeginning) {
      rfb.rec.writeIntBE(nBytes);
      rfb.rec.write(zlibBuf, 0, nBytes);
    }

    if(zlibIStream == null){
    	zlibIStream = new ZStream();
        err = zlibIStream.inflateInit();
        CHECK_ERR(zlibIStream, err, "inflateInit");
    }
    
    zlibIStream.next_in = zlibBuf;
    zlibIStream.next_in_index = 0;
    zlibIStream.avail_in = nBytes;
    
    if (bytesPixel == 1) {
      for (int dy = y; dy < y + h; dy++) {
    	zlibIStream.next_out = pixels8;
    	zlibIStream.next_out_index = dy * rfb.framebufferWidth + x;
    	zlibIStream.avail_out = w;
    	err = zlibIStream.inflate(JZlib.Z_PARTIAL_FLUSH);
    	CHECK_ERR(zlibIStream, err, "inflate partial flush");
		if (rfb.rec != null && !rfb.recordFromBeginning)
		  rfb.rec.write(pixels8, dy * rfb.framebufferWidth + x, w);
      }
    }else{
      byte[] buf = new byte[w * 4];
      int i, offset;
      for (int dy = y; dy < y + h; dy++) {
    	  zlibIStream.next_out = buf;
    	  zlibIStream.next_out_index = 0;
    	  zlibIStream.avail_out = buf.length;
    	  err = zlibIStream.inflate(JZlib.Z_PARTIAL_FLUSH);
    	  CHECK_ERR(zlibIStream, err, "inflate all");
    	  offset = dy * rfb.framebufferWidth + x;
    	  for (i = 0; i < w; i++) {
    		  pixels24[offset + i] =
 			  (buf[i * 4 + 2] & 0xFF) << 16 |
 			  (buf[i * 4 + 1] & 0xFF) << 8 |
 			  (buf[i * 4] & 0xFF);
    	  }
    	  if (rfb.rec != null && !rfb.recordFromBeginning)
    		  rfb.rec.write(buf);
      }
    }

    handleUpdatedPixels(x, y, w, h);
    scheduleRepaint(x, y, w, h);
  }

  //
  // Handle a Tight-encoded rectangle.
  //

  void handleTightRect(int x, int y, int w, int h) throws Exception {
	int err;
    int comp_ctl = rfb.is.readUnsignedByte();
    if (rfb.rec != null) {
      if (rfb.recordFromBeginning ||
	  comp_ctl == (rfb.TightFill << 4) ||
	  comp_ctl == (rfb.TightJpeg << 4)) {
    	  // Send data exactly as received.
    	  rfb.rec.writeByte(comp_ctl);
      } else {
    	  // Tell the decoder to flush each of the four zlib streams.
    	  rfb.rec.writeByte(comp_ctl | 0x0F);
      }
    }

    // Flush zlib streams if we are told by the server to do so.
    for (int stream_id = 0; stream_id < 4; stream_id++) {
      if ((comp_ctl & 1) != 0 && tightIStreams[stream_id] != null) {
    	  System.out.println("Cleaning tightIStreams " + stream_id);
    	  tightIStreams[stream_id].free();
    	  tightIStreams[stream_id] = null;
      }
      comp_ctl >>= 1;
    }

    // Check correctness of subencoding value.
    if (comp_ctl > rfb.TightMaxSubencoding) {
      throw new Exception("Incorrect tight subencoding: " + comp_ctl);
    }

    // Handle solid-color rectangles.
    if (comp_ctl == rfb.TightFill) {

    if (bytesPixel == 1) {
    	int idx = rfb.is.readUnsignedByte();
		memGraphics.setColor(colors[idx]);
		if (rfb.rec != null) {
		  rfb.rec.writeByte(idx);
		}
    }else{
      	byte[] buf = new byte[3];
      	rfb.readFully(buf);
      	if (rfb.rec != null) {
      		rfb.rec.write(buf);
      	}
      	Color bg = new Color(0xFF000000 | (buf[0] & 0xFF) << 16 |
		     (buf[1] & 0xFF) << 8 | (buf[2] & 0xFF));
      	memGraphics.setColor(bg);
      }
      memGraphics.fillRect(x, y, w, h);
      scheduleRepaint(x, y, w, h);
      return;
    }

    if (comp_ctl == rfb.TightJpeg) {
      // Read JPEG data.
      byte[] jpegData = new byte[rfb.readCompactLen()];
      rfb.readFully(jpegData);
      if (rfb.rec != null) {
    	  if (!rfb.recordFromBeginning) {
    		  rfb.recordCompactLen(jpegData.length);
    	  }
    	  rfb.rec.write(jpegData);
      }

      // Create an Image object from the JPEG data.
      Image jpegImage = Toolkit.getDefaultToolkit().createImage(jpegData);

      // Remember the rectangle where the image should be drawn.
      jpegRect = new Rectangle(x, y, w, h);

      // Let the imageUpdate() method do the actual drawing, here just
      // wait until the image is fully loaded and drawn.
      synchronized(jpegRect) {
    	  Toolkit.getDefaultToolkit().prepareImage(jpegImage, -1, -1, this);
    	  try {
    		  // Wait no longer than three seconds.
    		  jpegRect.wait(3000);
    	  } catch (InterruptedException e) {
    		  throw new Exception("Interrupted while decoding JPEG image");
    	  }
      }

      // Done, jpegRect is not needed any more.
      jpegRect = null;
      return;
    }

    // Read filter id and parameters.
    int numColors = 0, rowSize = w;
    byte[] palette8 = new byte[2];
    int[] palette24 = new int[256];
    boolean useGradient = false;
    if ((comp_ctl & rfb.TightExplicitFilter) != 0) {
      int filter_id = rfb.is.readUnsignedByte();
      if (rfb.rec != null) {
    	  rfb.rec.writeByte(filter_id);
      }
      if (filter_id == rfb.TightFilterPalette) {
    	  numColors = rfb.is.readUnsignedByte() + 1;
    	  if (rfb.rec != null) {
    		  rfb.rec.writeByte(numColors - 1);
    	  }
    	  if (bytesPixel == 1) {
    		  if (numColors != 2) {
    			  throw new Exception("Incorrect tight palette size: " + numColors);
    		  }
    		  rfb.readFully(palette8);
    		  if (rfb.rec != null) {
    			  rfb.rec.write(palette8);
    		  }
    	  } else {
    		  byte[] buf = new byte[numColors * 3];
    		  rfb.readFully(buf);
    		  if (rfb.rec != null) {
    			  rfb.rec.write(buf);
    		  }
    		  for (int i = 0; i < numColors; i++) {
    			  palette24[i] = ((buf[i * 3] & 0xFF) << 16 |
    					  (buf[i * 3 + 1] & 0xFF) << 8 |
    					  (buf[i * 3 + 2] & 0xFF));
    		  }
    	  }
    	  if (numColors == 2)
    		  rowSize = (w + 7) / 8;
    	  } else if (filter_id == rfb.TightFilterGradient) {
    		  useGradient = true;
    	  } else if (filter_id != rfb.TightFilterCopy) {
    		  throw new Exception("Incorrect tight filter id: " + filter_id);
    	  }
    }
    if (numColors == 0 && bytesPixel == 4)
      rowSize *= 3;

    // Read, optionally uncompress and decode data.
    int dataSize = h * rowSize;
    if (dataSize < rfb.TightMinToCompress) {
      // Data size is small - not compressed with zlib.
      if (numColors != 0) {
	// Indexed colors.
	byte[] indexedData = new byte[dataSize];
	rfb.readFully(indexedData);
	if (rfb.rec != null) {
	  rfb.rec.write(indexedData);
	}
	if (numColors == 2) {
	  // Two colors.
	  if (bytesPixel == 1) {
	    decodeMonoData(x, y, w, h, indexedData, palette8);
	  } else {
	    decodeMonoData(x, y, w, h, indexedData, palette24);
	  }
	} else {
	  // 3..255 colors (assuming bytesPixel == 4).
	  int i = 0;
	  for (int dy = y; dy < y + h; dy++) {
	    for (int dx = x; dx < x + w; dx++) {
	      pixels24[dy * rfb.framebufferWidth + dx] =
		palette24[indexedData[i++] & 0xFF];
	    }
	  }
	}
      } else if (useGradient) {
	// "Gradient"-processed data
	byte[] buf = new byte[w * h * 3];
	rfb.readFully(buf);
	if (rfb.rec != null) {
	  rfb.rec.write(buf);
	}
	decodeGradientData(x, y, w, h, buf);
      } else {
	// Raw truecolor data.
	if (bytesPixel == 1) {
	  for (int dy = y; dy < y + h; dy++) {
	    rfb.readFully(pixels8, dy * rfb.framebufferWidth + x, w);
	    if (rfb.rec != null) {
	      rfb.rec.write(pixels8, dy * rfb.framebufferWidth + x, w);
	    }
	  }
	} else {
	  byte[] buf = new byte[w * 3];
	  int i, offset;
	  for (int dy = y; dy < y + h; dy++) {
	    rfb.readFully(buf);
	    if (rfb.rec != null) {
	      rfb.rec.write(buf);
	    }
	    offset = dy * rfb.framebufferWidth + x;
	    for (i = 0; i < w; i++) {
	      pixels24[offset + i] =
		(buf[i * 3] & 0xFF) << 16 |
		(buf[i * 3 + 1] & 0xFF) << 8 |
		(buf[i * 3 + 2] & 0xFF);
	    }
	  }
	}
      }
    } else {
      // Data was compressed with zlib.
      int zlibDataLen = rfb.readCompactLen();
      byte[] zlibData = new byte[zlibDataLen];
      rfb.readFully(zlibData);
      if (rfb.rec != null && rfb.recordFromBeginning) {
    	  rfb.rec.write(zlibData);
      }
      int stream_id = comp_ctl & 0x03;
      if(tightIStreams[stream_id] == null){
    	  System.out.println("Creating new tightIStreams " + stream_id);
    	  tightIStreams[stream_id] = new ZStream();
    	  err = tightIStreams[stream_id].inflateInit();
    	  CHECK_ERR(tightIStreams[stream_id], err, "inflateInit");
      }
      ZStream myInflater = tightIStreams[stream_id];
      if(myInflater == null){
    	  System.out.println("null ZStream!");
    	  System.exit(0);
      }
      myInflater.next_in = zlibData;
      myInflater.next_in_index = 0;
      myInflater.avail_in = zlibData.length;
      
      byte[] buf = new byte[dataSize];

      myInflater.next_out = buf;
      myInflater.next_out_index = 0;
      myInflater.avail_out = buf.length;
      err = myInflater.inflate(JZlib.Z_PARTIAL_FLUSH);
      CHECK_ERR(myInflater, err, "tight inflate partial flush");
    	  
      if (rfb.rec != null && !rfb.recordFromBeginning) {
    	  rfb.recordCompressedData(buf);
      }

      if (numColors != 0) {
    	  // Indexed colors.
    	  if (numColors == 2) {
    		  // Two colors.
    		  if (bytesPixel == 1) {
    			  decodeMonoData(x, y, w, h, buf, palette8);
    		  } else {
    			  decodeMonoData(x, y, w, h, buf, palette24);
    		  }
    	  } else {
    		  // More than two colors (assuming bytesPixel == 4).
    		  int i = 0;
    		  for (int dy = y; dy < y + h; dy++) {
    			  for (int dx = x; dx < x + w; dx++) {
    				  pixels24[dy * rfb.framebufferWidth + dx] =
    					  palette24[buf[i++] & 0xFF];
    			  }
    		  }
    	  }
      } else if (useGradient) {
    	  // Compressed "Gradient"-filtered data (assuming bytesPixel == 4).
    	  decodeGradientData(x, y, w, h, buf);
      } else {
    	  // Compressed truecolor data.
    	  if (bytesPixel == 1) {
    		  int destOffset = y * rfb.framebufferWidth + x;
    		  for (int dy = 0; dy < h; dy++) {
    			  System.arraycopy(buf, dy * w, pixels8, destOffset, w);
    			  destOffset += rfb.framebufferWidth;
    		  }
    	  } else {
    		  int srcOffset = 0;
    		  int destOffset, i;
    		  for (int dy = 0; dy < h; dy++) {
    			  destOffset = (y + dy) * rfb.framebufferWidth + x;
    			  for (i = 0; i < w; i++) {
    				  pixels24[destOffset + i] =
    					  (buf[srcOffset] & 0xFF) << 16 |
    					  (buf[srcOffset + 1] & 0xFF) << 8 |
    					  (buf[srcOffset + 2] & 0xFF);
    				  srcOffset += 3;
    			  }
    		  }
    	  }
      }
    }

    handleUpdatedPixels(x, y, w, h);
    scheduleRepaint(x, y, w, h);
  }

  //
  // Decode 1bpp-encoded bi-color rectangle (8-bit and 24-bit versions).
  //

  void decodeMonoData(int x, int y, int w, int h, byte[] src, byte[] palette) {

    int dx, dy, n;
    int i = y * rfb.framebufferWidth + x;
    int rowBytes = (w + 7) / 8;
    byte b;

    for (dy = 0; dy < h; dy++) {
      for (dx = 0; dx < w / 8; dx++) {
	b = src[dy*rowBytes+dx];
	for (n = 7; n >= 0; n--)
	  pixels8[i++] = palette[b >> n & 1];
      }
      for (n = 7; n >= 8 - w % 8; n--) {
	pixels8[i++] = palette[src[dy*rowBytes+dx] >> n & 1];
      }
      i += (rfb.framebufferWidth - w);
    }
  }

  void decodeMonoData(int x, int y, int w, int h, byte[] src, int[] palette) {

    int dx, dy, n;
    int i = y * rfb.framebufferWidth + x;
    int rowBytes = (w + 7) / 8;
    byte b;

    for (dy = 0; dy < h; dy++) {
      for (dx = 0; dx < w / 8; dx++) {
	b = src[dy*rowBytes+dx];
	for (n = 7; n >= 0; n--)
	  pixels24[i++] = palette[b >> n & 1];
      }
      for (n = 7; n >= 8 - w % 8; n--) {
	pixels24[i++] = palette[src[dy*rowBytes+dx] >> n & 1];
      }
      i += (rfb.framebufferWidth - w);
    }
  }

  //
  // Decode data processed with the "Gradient" filter.
  //

  void decodeGradientData (int x, int y, int w, int h, byte[] buf) {

    int dx, dy, c;
    byte[] prevRow = new byte[w * 3];
    byte[] thisRow = new byte[w * 3];
    byte[] pix = new byte[3];
    int[] est = new int[3];

    int offset = y * rfb.framebufferWidth + x;

    for (dy = 0; dy < h; dy++) {

      /* First pixel in a row */
      for (c = 0; c < 3; c++) {
	pix[c] = (byte)(prevRow[c] + buf[dy * w * 3 + c]);
	thisRow[c] = pix[c];
      }
      pixels24[offset++] =
	(pix[0] & 0xFF) << 16 | (pix[1] & 0xFF) << 8 | (pix[2] & 0xFF);

      /* Remaining pixels of a row */
      for (dx = 1; dx < w; dx++) {
	for (c = 0; c < 3; c++) {
	  est[c] = ((prevRow[dx * 3 + c] & 0xFF) + (pix[c] & 0xFF) -
		    (prevRow[(dx-1) * 3 + c] & 0xFF));
	  if (est[c] > 0xFF) {
	    est[c] = 0xFF;
	  } else if (est[c] < 0x00) {
	    est[c] = 0x00;
	  }
	  pix[c] = (byte)(est[c] + buf[(dy * w + dx) * 3 + c]);
	  thisRow[dx * 3 + c] = pix[c];
	}
	pixels24[offset++] =
	  (pix[0] & 0xFF) << 16 | (pix[1] & 0xFF) << 8 | (pix[2] & 0xFF);
      }

      System.arraycopy(thisRow, 0, prevRow, 0, w * 3);
      offset += (rfb.framebufferWidth - w);
    }
  }

  //
  // Display newly updated area of pixels.
  //

  void handleUpdatedPixels(int x, int y, int w, int h) {

    // Draw updated pixels of the off-screen image.
    pixelsSource.newPixels(x, y, w, h);
    memGraphics.setClip(x, y, w, h);
    memGraphics.drawImage(rawPixelsImage, 0, 0, null);
    memGraphics.setClip(0, 0, rfb.framebufferWidth, rfb.framebufferHeight);
  }

  //
  // Tell JVM to repaint specified desktop area.
  //

  void scheduleRepaint(int x, int y, int w, int h) {
    // Request repaint, deferred if necessary.
    if (rfb.framebufferWidth == scaledWidth) {
      repaint(viewer.deferScreenUpdates, x, y, w, h);
    } else {
      int sx = x * scalingFactor / 100;
      int sy = y * scalingFactor / 100;
      int sw = ((x + w) * scalingFactor + 49) / 100 - sx + 1;
      int sh = ((y + h) * scalingFactor + 49) / 100 - sy + 1;
      repaint(viewer.deferScreenUpdates, sx, sy, sw, sh);
    }
  }
  
  //
  // Check zlib error message
  //
  
  void CHECK_ERR(ZStream z, int err, String msg){
	if(err != JZlib.Z_OK){
		if(z.msg != null) System.out.println(z.msg + " ");
		System.out.println(msg + " error: " + err);
	}
  }
  
  //
  // Handle encode state
  //
  byte[] handleEncodeState(){
	  switch(rfb.updateRectEncoding){
	  	case rfbProto.EncodingRaw:  	case rfbProto.EncodingCopyRect:
	  	case rfbProto.EncodingRRE:	  	case rfbProto.EncodingCoRRE:	    
	  	case rfbProto.EncodingHextile:
	  		return null;
	  	default:
	  }
	  
	//Get zlib state
	byte decoderState[] = encodeZlibState();
	if(decoderState == null){
		System.out.println("DecoderState == null");
		return null;
	}

	//Form packet - |Msg type (1 byte)|Padding (1 byte)|Enc type (4 bytes)|State len (4 bytes)|State data|
	//-->|State data| zlib = |# decoders (2 bytes)||Dec mode (2 bytes)|Adler32 (8 bytes)|dict len (4 bytes)|dictionary||...|| 
	final int DECODER_STATE_HEADER_LEN = 10;
	byte packet[] = new byte[decoderState.length + DECODER_STATE_HEADER_LEN];
	packet[0] = rfbProto.DecoderState;
	int len = decoderState.length;
	System.out.println("Len state is " + len);
	int encType = rfb.updateRectEncoding;
	packet[2] = (byte) (encType >> 24);
	packet[3] = (byte) (encType >> 16);
	packet[4] = (byte) (encType >> 8);
	packet[5] = (byte) encType;
	packet[6] = (byte) (len >> 24);
	System.out.println((byte) (len >> 24) + " " + (byte) (len >> 16) + " " + (byte) (len >> 8) + " " + (byte) len );
	packet[7] = (byte) (len >> 16);
	packet[8] = (byte) (len >> 8);
	packet[9] = (byte) len;
	System.arraycopy(decoderState, 0, packet, 10, decoderState.length);
	
	return packet;
  }
	
  //
  // Get decoder state
  // zlib state data = |# decoders (2 byte)||Dec mode (2 byte)|Adler32 (8 bytes)|dict len (4 bytes)|dictionary||...||
  //
  
  public byte[] encodeZlibState(){
	  ZStream[] decoders = new ZStream[4];
	  byte[] decoderState = null;
	  
	  short decoderCount = 0;
	  short decoderMode = 0;
	  long adlerVal = 0;
	  byte[] dict = null;
	  
	  for(int i = 0; i < 4; i++)
		  decoders[i] = null;
	  
	  System.out.println("updateRectEncoding " + rfb.updateRectEncoding);
	  
	  switch(rfb.updateRectEncoding){
	      case rfbProto.EncodingZRLE:
	    	  if(zrleInStream == null) break;
	    	  
	    	  decoders[0] = zrleInStream.getDecoder();
	    	  
	    	  decoderCount = 1;
	    	  decoderMode = (short) decoders[0].inflateGetMode();
	    	  adlerVal = decoders[0].adler;
	    	  dict = decoders[0].inflateGetDictionary();
	    	  
	    	  int decLen = 16 + dict.length + 4 + zrleTilePixels24.length * 4;
	    	  decoderState = new byte[decLen];
	    	  
	    	  break;
		  case rfbProto.EncodingZlib:
			  if(zlibIStream == null) break;
			  
			  decoders[0] = zlibIStream;
			  
	    	  decoderCount = 1;
	    	  decoderMode = (short) decoders[0].inflateGetMode();
	    	  adlerVal = decoders[0].adler;
	    	  dict = decoders[0].inflateGetDictionary();
	    	  
	    	  decoderState = new byte[16 + dict.length];
	    	  
			  break;
		  case rfbProto.EncodingTight: //Need to pack 4 dictionaries
			  int stateLen = 0;
			  decoderCount = 4;
			  stateLen = 2;
			  
			  for(int i=0; i < decoderCount; i++){
				  decoders[i] = tightIStreams[i];
				  stateLen += 14;
				  stateLen += tightIStreams[i].inflateGetDictionary().length;
			  }
			  
			  decoderState = new byte[stateLen];	    	  
	    	  
			  break;
		  default:
			  return null;
	  }
	  
	  System.out.println("decoderCount " + decoderCount);
	  
	  int currpos = 2;
	  decoderState[0] = (byte) (decoderCount >> 8);
      decoderState[1] = (byte) decoderCount;
      
	  for(int i=0; i < decoderCount; i++){
		  if(decoders[i] == null) continue;
		  
		  //Get required information
		  decoderMode = (short) decoders[i].inflateGetMode();
    	  adlerVal = decoders[i].adler;
    	  dict = decoders[i].inflateGetDictionary();
    	  
    	  //Packet formation       	  
    	  decoderState[currpos++] = (byte) (decoderMode >> 8);
    	  decoderState[currpos++] = (byte) decoderMode;   	  
    	  decoderState[currpos++] = (byte) (adlerVal >> 56);
    	  decoderState[currpos++] = (byte) (adlerVal >> 48);
    	  decoderState[currpos++] = (byte) (adlerVal >> 40);
    	  decoderState[currpos++] = (byte) (adlerVal >> 32);
    	  decoderState[currpos++] = (byte) (adlerVal >> 24);
    	  decoderState[currpos++] = (byte) (adlerVal >> 16);
    	  decoderState[currpos++] = (byte) (adlerVal >> 8);
    	  decoderState[currpos++] = (byte) (adlerVal);
    	  decoderState[currpos++] = (byte) (dict.length >> 24);
    	  decoderState[currpos++] = (byte) (dict.length >> 16);
    	  decoderState[currpos++] = (byte) (dict.length >> 8);
    	  decoderState[currpos++] = (byte) (dict.length);
    	  System.arraycopy(dict, 0, decoderState, currpos, dict.length);
    	  currpos += dict.length;
    	  
    	  System.out.println("decoderMode " + decoderMode);
    	  System.out.println("adlerVal " + adlerVal);
    	  System.out.println("dictLen " + dict.length + " dict[0] " + dict[0] + " dict[end] " + dict[dict.length - 1]);
	  }
	  
	  return decoderState;
  }
 
  //
  // Handle decoder state message
  //
  void handleDecoderState(){
	  try{
		  rfb.is.readByte();
		  int encType = rfb.is.readInt();
		  int statelen = rfb.is.readInt();
		  storeDecoderState(encType);
	  }catch(IOException ex){
		  ex.printStackTrace();
	  }
  }
  
  //
  // Set decoder state
  // zlib state data = |# decoders (2 byte)||Dec mode (2 byte)|Adler32 (8 bytes)|dict len (4 bytes)|dictionary||...||
  //
  
  void storeDecoderState(int encType){
	  ZStream[] decoders = new ZStream[4];
	  byte[] decoderState = null;
	  System.out.println("sjiosfuidfdihf");
	  short decoderCount = 0;
	  short decoderMode = 0;
	  long adlerVal = 0;
	  byte[] dict = null;
	  
	  for(int i = 0; i < 4; i++)
		  decoders[i] = null;
	  
	  //get target decoders
	  switch(encType){
		case rfbProto.EncodingZRLE:
			System.out.println("EncodingZRLE");
			decoders[0] = zrleInStream.getDecoder();
		    break;
		case rfbProto.EncodingZlib:
			System.out.println("EncodingZlib");
			decoders[0] = zlibIStream;
		    break;
		case rfbProto.EncodingTight:
			System.out.println("EncodingTight");
			for(int i = 0; i < 4; i++)
				decoders[i] = tightIStreams[i];
		    break;
		default:
		    System.out.println("current decoder not suitable to store state.");
	  }
	  
	  //read source from socket and store to target
	  try{
		  decoderCount = (short) rfb.is.readUnsignedShort();
		  System.out.println("Num of dec is " + decoderCount);
		  
		  for(int i = 0; i < decoderCount; i++){
			  decoderMode = (short) rfb.is.readUnsignedShort();
			  adlerVal = rfb.is.readLong();
			  int dictlen = rfb.is.readInt();
			  dictlen = (dictlen << 32) >>> 32;
			  dict = new byte[dictlen];
			  rfb.is.readFully(dict);
			  System.out.println("adler " + adlerVal + " dictLen " + dictlen + " dict[0] " + dict[0] + " dict[end] " + dict[dictlen -1]);
			  decoders[i].inflateSetStateTo(decoderMode, adlerVal, dict, dictlen);
		  }
	  }catch(IOException ex){
		  System.out.println("Cannot read decoder state from socket.");
		  ex.printStackTrace();
		  return;
	  }
	  

  }
  
  //
  // Handle events.
  //

  public void keyPressed(KeyEvent evt) {
    processLocalKeyEvent(evt);
  }
  public void keyReleased(KeyEvent evt) {
    processLocalKeyEvent(evt);
  }
  public void keyTyped(KeyEvent evt) {
    evt.consume();
  }

  public void mousePressed(MouseEvent evt) {
    processLocalMouseEvent(evt, false);
  }
  public void mouseReleased(MouseEvent evt) {
    processLocalMouseEvent(evt, false);
  }
  public void mouseMoved(MouseEvent evt) {
    processLocalMouseEvent(evt, true);
  }
  public void mouseDragged(MouseEvent evt) {
    processLocalMouseEvent(evt, true);
  }

  public void processLocalKeyEvent(KeyEvent evt) {
    if (viewer.rfb != null && rfb.inNormalProtocol) {
		synchronized(rfb) {
		  try {
		    rfb.writeKeyEvent(evt);
		  } catch (Exception e) {
		    e.printStackTrace();
		  }
		  rfb.notify();
		}
    }
    // Don't ever pass keyboard events to AWT for default processing. 
    // Otherwise, pressing Tab would switch focus to ButtonPanel etc.
    evt.consume();
  }

  public void processLocalMouseEvent(MouseEvent evt, boolean moved) {
	if (viewer.rfb != null && rfb.inNormalProtocol) {
      if (rfb.framebufferWidth != scaledWidth) {
        int sx = (evt.getX() * 100 + scalingFactor/2) / scalingFactor;
        int sy = (evt.getY() * 100 + scalingFactor/2) / scalingFactor;
        evt.translatePoint(sx - evt.getX(), sy - evt.getY());
      }
      synchronized(rfb) {
	try {
	  rfb.writePointerEvent(evt);
	} catch (Exception e) {
	  e.printStackTrace();
	}
	rfb.notify();
      }
    }
  }

  //
  // Ignored events.
  //

  public void mouseClicked(MouseEvent evt) {}
  public void mouseEntered(MouseEvent evt) {}
  public void mouseExited(MouseEvent evt) {}
}