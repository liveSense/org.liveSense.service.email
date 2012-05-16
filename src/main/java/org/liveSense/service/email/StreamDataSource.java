package org.liveSense.service.email;

import javax.activation.DataSource;
import javax.activation.MimetypesFileTypeMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * StreamDataSource represents implementation of DataSource interfaces, and as
 * result of that, objects of this class could be used within MimeBodyPart
 * objects to help them to read data from objects which they conveys. This class
 * is designed to help in getting data from objects of type InputStream and all
 * its subclasses.
 */
public class StreamDataSource implements DataSource {

	/**
	 * Storage for obtained content-type.
	 */
	private String contentType = null;

	/**
	 * Storage for obtained file name.
	 */
	private String fileName = "";

	/**
	 * Storage for data derived from InputStream.
	 */
	private byte[] att = null;

	/**
	 * Constructs StreamDataSource with given input stream and coresponding string
	 * which contains virtual or real file name. Extension of file name is used to
	 * get appropriate mime-type. All mime-types which are predefined according to
	 * particular file extensions can be found in mime.types file in META-INF directory
	 * from smime.jar file. This file can be changed or extended so that it could adjust
	 * mime-types to aditional requests. For more information see Java documentation
	 * related to class MimetypesFileTypeMap.
	 * @throws IOException 
	 */
	public StreamDataSource(InputStream in0, String fileName0, String contentType) throws IOException {

		String sAtach = new String();

		byte[] b = new byte[100000];
		int a = in0.read(b);
		while (a == 100000) {
			sAtach = sAtach.concat(new String(b, "ISO-8859-1"));
			a = in0.read(b);
		}
		in0.close();
		sAtach = sAtach.concat(new String(b, "ISO-8859-1").substring(0, a));
		att = sAtach.getBytes("ISO-8859-1");

		this.contentType = contentType;
		if (fileName0 != null) {
			if (this.contentType == null)
				this.contentType = new MimetypesFileTypeMap().getContentType(fileName0);
			fileName = new String(fileName0);
		}
		
		if (this.contentType == null || this.contentType.equalsIgnoreCase(""))
			this.contentType = "application/octet-stream";

		this.contentType = this.contentType + "; name=\"" + fileName0 + "\"";
		
	}

	/**
	 * Sets content type. Using of this metod should be avoided because Content-Type
	 * is set in the process of construction of StreamDataSource object, by using
	 * information got from "fileName0" parameter. This method will override
	 * value set after construction. This method will be used when construction is
	 * performed with null value of "fileName0" parameter, or in case when automatic
	 * obtaining of value for content-type did not satisfy what is expected.
	 * @param contType0 Content-Type for MIME message header field
	 */
	public void setContentType(String contType0) {
		contentType = contType0;
	}

	/**
	 * Implements getContentType method from DataSource interface
	 * @return Content-Type for MIME message header field
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Implements getInputStream method from DataSource interface
	 * @return CMS enveloped object
	 * @exception IOException
	 */
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream(att);
	}

	/**
	 * Implements getName method from DataSource interface
	 * @return Name: EnvelopedDataContentInfo
	 */
	public String getName() {
		return fileName;
	}

	/**
	 * Implements getOutputStream method from DataSource interface. This method is
	 * not in use.
	 * @return nothing
	 * @exception IOException is always thrown when this method is used.
	 */
	public OutputStream getOutputStream() throws IOException {
		throw new IOException("StreamDataSource does not support getOutputStream()");
	}

}