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
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.transform.*; 
import javax.xml.transform.dom.DOMSource; 
import javax.xml.transform.stream.StreamResult;

public class JChannelProfileDOM {
	Document doc;
	File file;
	
	JChannelProfileDOM(File file){
	    try {
	    	this.file = file;
            // Create a builder factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Create the builder and parse the file
            doc = factory.newDocumentBuilder().parse(file);
        } catch (SAXException e) {
            // A parsing error occurred; the xml input is not valid
        } catch (ParserConfigurationException e) {
        } catch (IOException e) {
        }
	}
	
	void setMcastAddr(String addr){
		NodeList list = doc.getElementsByTagName("UDP");
		
		Element ele = (Element) list.item(0);
		
		ele.setAttribute("mcast_addr", "${jgroups.udp.mcast_addr:" + addr + "}");
	}
	
	void setMcastPort(long port){
		NodeList list = doc.getElementsByTagName("UDP");
		
		Element ele = (Element) list.item(0);
		
		ele.setAttribute("mcast_port", "${jgroups.udp.mcast_port:" + port + "}");
	}
	
	void setTTL(long ttl){
		NodeList list = doc.getElementsByTagName("UDP");
		
		Element ele = (Element) list.item(0);
		
		ele.setAttribute("ip_ttl", "${jgroups.udp.ip_ttl:" + ttl + "}");
	}
	
	String getMcastAddr(){
		NodeList list = doc.getElementsByTagName("UDP");
		
		Element ele = (Element) list.item(0);
		String str = ele.getAttribute("mcast_addr");
		int indexcol = str.indexOf(':');
		int indexbrace = str.indexOf('}');
		
		return str.substring(indexcol + 1, indexbrace);
	}
	
	long getMcastPort(){
		NodeList list = doc.getElementsByTagName("UDP");

		Element ele = (Element) list.item(0);
		String str = ele.getAttribute("mcast_port");
		int indexcol = str.indexOf(':');
		int indexbrace = str.indexOf('}');
		
		String portStr = str.substring(indexcol + 1, indexbrace);
		
		return Long.valueOf(portStr);
	}
	
	long getTTL(){
		NodeList list = doc.getElementsByTagName("UDP");

		Element ele = (Element) list.item(0);
		String str = ele.getAttribute("ip_ttl");
		int indexcol = str.indexOf(':');
		int indexbrace = str.indexOf('}');
		
		String portStr = str.substring(indexcol + 1, indexbrace);
		
		return Long.valueOf(portStr);
	}
	
	void saveFile(){
	    try{
	        if (file.exists()){
	          Transformer tFormer = TransformerFactory.newInstance().newTransformer();
	          tFormer.setOutputProperty(OutputKeys.METHOD, "xml");
	          Source source = new DOMSource(doc);
	          Result result = new StreamResult(file);
	          tFormer.transform(source, result);
	        }
	      }
	      catch (Exception e){
	        System.err.println(e);
	        System.exit(0);
	      }  
	}
}
