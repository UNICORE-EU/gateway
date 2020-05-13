/** 
 * 
 * Copyright 2004 Protique Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 * 
 **/
package eu.unicore.gateway.soap;


import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * used to identify the SOAP version used in the current message exchange
 */
public interface SoapVersion {

    public double getVersion();

    public String getNamespace();

    public String getPrefix();

    public QName getEnvelope();

    public QName getHeader();

    public QName getBody();

    public QName getFault();

    public String getSoapEncodingStyle();

    // Role related properties
    //-------------------------------------------------------------------------
    public String getNoneRole();

    public String getUltimateReceiverRole();

    public String getNextRole();


    // Helper methods for outputting SOAP related data
    //-------------------------------------------------------------------------
    public void writeStartElement(XMLStreamWriter out, String localName) throws XMLStreamException;

    public void writeFault(XMLStreamWriter out, SoapFault fault, String hostUri) throws XMLStreamException;


}
