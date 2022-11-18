/*
 * Copyright (c) 2009 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on 2009-08-21
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.gateway;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

public class Utils
{
	public static String readFile(File f) throws IOException
	{
		return FileUtils.readFileToString(f, "UTF-8");
	}

	public static Header getBasicAuth(String user, String passwd) {
		try{
			String val = "Basic "+Base64.getEncoder().encodeToString((user+":"+passwd).getBytes("US-ASCII"));
			return new BasicHeader("Authorization", val);
		}catch(Exception e){throw new RuntimeException(e);}
	}
	
}
