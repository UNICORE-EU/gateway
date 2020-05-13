/*
 * Copyright (c) 2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package eu.unicore.gateway;

import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeaderValueParser;

import eu.unicore.gateway.base.RawMessageExchange;

public class FakeServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
			throws ServletException, IOException {
		write("OKOKOK\n", resp);
	}
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
			throws ServletException, IOException {
		// reply with consignor header
		String consignor = req.getHeader(RawMessageExchange.CONSIGNOR_HEADER);
		NameValuePair[] parsed = BasicHeaderValueParser.parseParameters(consignor, new BasicHeaderValueParser());
		for(NameValuePair p: parsed){
			write(p.getName()+"="+p.getValue()+"\n",resp);
		}
	}
	
	private void write(String what, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		resp.setContentLength(what.length());
		PrintStream out = new PrintStream(resp.getOutputStream());
		out.print(what);
		out.flush();
		resp.flushBuffer();
	}

}
