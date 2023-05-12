package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;

public abstract class AbstractStreamReaderRequestEntity extends AbstractHttpEntity
{

	public AbstractStreamReaderRequestEntity(boolean chunked) {
		this(HttpHeaders.CONTENT_TYPE, "text/xml; charset=ISO-8859-1", chunked);
	}

	public AbstractStreamReaderRequestEntity(String contentType, String contentEncoding, boolean chunked) {
		super(contentType, contentEncoding, chunked);
	}

	@Override
	public abstract void writeTo(OutputStream os) throws IOException;

	@Override
	public long getContentLength()
	{
		return -1;
	}

	@Override
	public boolean isRepeatable()
	{
		return false;
	}
	
	@Override
	public boolean isStreaming()
	{
		return true;
	}

}
