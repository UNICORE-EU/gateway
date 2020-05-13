/*
 * Copyright (c) 2009 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on 2009-10-23
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.gateway;

import java.io.IOException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.soap.Soap11;
import eu.unicore.gateway.soap.Soap12;
import eu.unicore.gateway.soap.SoapFault;
import eu.unicore.gateway.soap.SoapFault.FaultCode;
import eu.unicore.gateway.soap.SoapVersion;

public class HeadersParser
{
	public static final String WSA_200508_NAMESPACE="http://www.w3.org/2005/08/addressing";
	private String serverUri;
	
	public HeadersParser(String serverUri)
	{
		this.serverUri = serverUri;
	}

	/**
	 * Read, cache and parse the headers.
	 */
	public void parseHeaders(RawMessageExchange exchange) throws Exception
	{
		XMLEventReader reader = exchange.getEventReader();
		int baseOffset = exchange.getMultipartOffset();
		
		try
		{
			boolean markAtNextElement = false;
			SoapVersion soap = null;
			int state = 0;
			while (reader.hasNext())
			{
				XMLEvent event = reader.peek();
				
				if ((event.isStartElement() || event.isEndElement()) && markAtNextElement)
				{
					markAtNextElement = false;
					exchange.getReader().setMarkedPos(
							baseOffset + event.getLocation().getCharacterOffset());
				}
				
				// BufferingProxyReader mark is set after SOAP header start or after
				// SOAP envelope start iff header is not present.
				
				if (event.isStartElement())
				{
					QName q = event.isStartElement() ? event.asStartElement().getName() :
						event.asEndElement().getName();
					if (state == 0) //need Envelope
					{
						// check soap version
						if (Soap11.envelope.equals(q))
						{
							soap = Soap11.getInstance();
							exchange.setSoapVersion(soap);
							markAtNextElement = true;
						} else if (Soap12.envelope.equals(q))
						{
							soap = Soap12.getInstance();
							exchange.setSoapVersion(soap);
							markAtNextElement = true;
						} else
							throw new SoapFault(serverUri, FaultCode.VERSION_MISMATCH,
								"(Supported) SOAP Envelope element is not the first element");
						state = 1;
					} else if (state == 1) //need header or body
					{
						// check if the header element is reached
						if (soap.getHeader().equals(q))
						{
							markAtNextElement = true;
							exchange.setHeaderPresent(true);
							state = 2;
						} else if (q.equals(soap.getBody()))
						{
							break;
						} else
							throw new SoapFault(serverUri, FaultCode.VERSION_MISMATCH,
							"SOAP Envelope must possess Header or Body as its first child element");
					} else if (state == 2) //(in header) need body or check WSA 
					{
						// check for WS-Addressing header element
						if (q.getNamespaceURI().equals(WSA_200508_NAMESPACE))
						{
							processWSA(exchange);
							continue;
						}
						// stop if we have reached the SOAP body
						if (q.equals(soap.getBody()))
						{
							break;
						}
					}
				}
				
				event = reader.nextEvent();
			}
		} catch (XMLStreamException ise)
		{
			throw new SoapFault(serverUri, FaultCode.SENDER, "Failed to parse headers!", ise);
		} catch (IOException ioe)
		{
			throw new SoapFault(serverUri, FaultCode.SENDER, "Failed to parse headers!", ioe);
		}

	}
	
	private void processWSA(RawMessageExchange exchange) throws Exception
	{
		XMLEventReader reader = exchange.getEventReader();

		XMLEvent e = reader.nextEvent();
		if (e == null || !e.isStartElement())
			return;
		String element = e.asStartElement().getName().getLocalPart();

		// read till the next character event (or end element)
		while (reader.hasNext())
		{
			XMLEvent event = reader.nextEvent();
			if (event.isEndElement())
				break;
			if (event.isCharacters())
			{
				String data = event.asCharacters().getData();
				if (element.equals("To"))
					exchange.setWsaToAddress(data);
				else if (element.equals("Action"))
					exchange.setWsaAction(data);
			}
		}
	}	
}
