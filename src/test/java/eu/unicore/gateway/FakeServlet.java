package eu.unicore.gateway;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.ParserCursor;

import eu.unicore.gateway.base.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FakeServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
			throws ServletException, IOException {
		write("OKOKOK\n", resp);
	}
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
			throws ServletException, IOException {
		// reply with consignor header
		String consignor = req.getHeader(Servlet.CONSIGNOR_HEADER);
		NameValuePair[] parsed = new BasicHeaderValueParser().parseParameters(consignor, new ParserCursor(0, consignor.length()));
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
