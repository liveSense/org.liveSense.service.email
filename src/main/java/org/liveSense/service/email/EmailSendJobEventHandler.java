/*
 *  Copyright 2010 Robert Csakany <robson@semmi.se>.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package org.liveSense.service.email;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.event.EventUtil;
import org.apache.sling.event.jobs.JobProcessor;
import org.apache.sling.event.jobs.JobUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 14, 2010
 */

@Component(label="%emailSendJobEventHandler.name",
        description="%emailSendJobEventHandler.description",
        immediate=true,
        metatype=true,
        policy=ConfigurationPolicy.OPTIONAL)
@Service(value = org.osgi.service.event.EventHandler.class)
@Properties(value={
		@Property(name = "event.topics", value = {
				EmailResourceChangeListener.EMAIL_SEND_TOPIC,
				EmailResourceChangeListener.EMAIL_REMOVE_TOPIC }),
	    @Property(name=EmailSendJobEventHandler.PARAM_SMTP_HOST, 
	    	label="%smtpHost.name", 
	    	description="%smtpHost.description", 
	    	value=EmailSendJobEventHandler.DEFAULT_SMTP_HOST),
	    @Property(name=EmailSendJobEventHandler.PARAM_SMTP_PORT, 
	    	label="%smtpPort.name", 
	    	description="%smtpPort.description", 
	    	longValue=EmailSendJobEventHandler.DEFAULT_SMTP_PORT),
	    @Property(name=EmailSendJobEventHandler.PARAM_SMTP_CONNECTION_TIMEOUT, 
	    	label="%smtpConnectionTimeout.name", 
	    	description="%smtpConnectionTimeout.description", 
	    	longValue=EmailSendJobEventHandler.DEFAULT_SMTP_CONNECTION_TIMEOUT),
		@Property(name=EmailSendJobEventHandler.PARAM_SMTP_SSL_ENABLE, 
			label="%smtpSslEnable.name", 
			description="%smtpSslEnable.description", 
			boolValue=EmailSendJobEventHandler.DEFAULT_SMTP_SSL_ENABLE),	    
	    @Property(name=EmailSendJobEventHandler.PARAM_SMTP_USER_NAME, 
	    	label="%smtpUserName.name", 
	    	description="%smtpUserName.description", 
	    	value=EmailSendJobEventHandler.DEFAULT_SMTP_USER_NAME),	    
	    @Property(name=EmailSendJobEventHandler.PARAM_SMTP_PASSWORD, 
	    	label="%smtpPassword.name", 
	    	description="%smtpPassword.description", 
	    	value=EmailSendJobEventHandler.DEFAULT_SMTP_PASSWORD),	    
	    @Property(name=EmailSendJobEventHandler.PARAM_SPOOL_FOLDER, 
	    	label="%spoolFolder.name", 
	    	description="%spoolFolder.description", 
	    	value=EmailSendJobEventHandler.DEFAULT_SPOOL_FOLDER)	    
})

public class EmailSendJobEventHandler
		implements JobProcessor, EventHandler {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory
			.getLogger(EmailSendJobEventHandler.class);

	public static final String PARAM_SMTP_HOST = "smtpHost";
    public static final String DEFAULT_SMTP_HOST = "localhost";

    public static final String PARAM_SMTP_PORT = "smtpPort";
    public static final long DEFAULT_SMTP_PORT = 25;

    public static final String PARAM_SMTP_CONNECTION_TIMEOUT = "smtpConnectionTimeout";
    public static final long DEFAULT_SMTP_CONNECTION_TIMEOUT = 10000;
    
    public static final String PARAM_SMTP_SSL_ENABLE = "smtpSslEnable";
    public static final boolean DEFAULT_SMTP_SSL_ENABLE = false;

    public static final String PARAM_SMTP_USER_NAME = "smtpUserName";
    public static final String DEFAULT_SMTP_USER_NAME = "";

    public static final String PARAM_SMTP_PASSWORD = "smtpPassword";
    public static final String DEFAULT_SMTP_PASSWORD = "";


    public static final String PARAM_SPOOL_FOLDER = "spoolFolder";
    public static final String DEFAULT_SPOOL_FOLDER = "/var/spool/queue/mail/";

    private String smtpHost = DEFAULT_SMTP_HOST;
    private long smtpPort = DEFAULT_SMTP_PORT;
    private long smtpConnectionTimeout = DEFAULT_SMTP_CONNECTION_TIMEOUT;
    private boolean smtpSslEnable = DEFAULT_SMTP_SSL_ENABLE;
    private String smtpUserName = DEFAULT_SMTP_USER_NAME;
    private String smtpPassword = DEFAULT_SMTP_PASSWORD;
    private String spoolFolder = DEFAULT_SPOOL_FOLDER;


	@Reference
	SlingRepository repository;

	@Reference
	ResourceResolverFactory resourceResolverFactory;

	/**
	 * Activates this component.
	 * 
	 * @param componentContext
	 *            The OSGi <code>ComponentContext</code> of this component.
	 */
	@Activate
	protected void activate(ComponentContext componentContext)
			throws RepositoryException {

		smtpHost = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SMTP_HOST), DEFAULT_SMTP_HOST);
		smtpPort = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_SMTP_PORT), DEFAULT_SMTP_PORT);
		smtpConnectionTimeout = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_SMTP_CONNECTION_TIMEOUT), DEFAULT_SMTP_CONNECTION_TIMEOUT);
		smtpSslEnable = PropertiesUtil.toBoolean(componentContext.getProperties().get(PARAM_SMTP_SSL_ENABLE), DEFAULT_SMTP_SSL_ENABLE);
		smtpUserName = PropertiesUtil.toString(componentContext.getProperties().get(DEFAULT_SMTP_USER_NAME), DEFAULT_SMTP_USER_NAME);
		smtpPassword = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SMTP_PASSWORD), DEFAULT_SMTP_PASSWORD);
		spoolFolder = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SPOOL_FOLDER), DEFAULT_SPOOL_FOLDER);

    	Session admin = repository.loginAdministrative(null);
    	if (spoolFolder.startsWith("/")) spoolFolder = spoolFolder.substring(1);
    	
    	String spoolFolders[] = spoolFolder.split("/");
    	Node n = admin.getRootNode();
    	for (int i = 0; i < spoolFolders.length; i++) {
			if (!n.hasNode(spoolFolders[i])) {
				n = n.addNode(spoolFolders[i]);
			} else {
				n = n.getNode(spoolFolders[i]);
			}
		}
    	admin.save();
    	admin.logout();

        /*
        Properties sysprops = System.getProperties();
        sysprops.put("mail.smtp.host", smtpHost);
        sysprops.put("mail.smtp.port", Long.toString(smtpPort));
        sysprops.put("mail.smtp.connectiontimeout", Long.toString(smtpConnectionTimeout));
        sysprops.put("mail.smtp.timeout", Long.toString(smtpConnectionTimeout));
        sysprops.put("mail.smtp.ssl.enable", Boolean.toString(smtpSslEnable));
         */
	}

	public void handleEvent(Event event) {
		if (EventUtil.isLocal(event)) {
			JobUtil.processJob(event, this);
		}
	}

	public boolean process(Event event) {
		Session session = null;
		ResourceResolver resourceResolver = null;

		try {
			String resourcePath = (String) event.getProperty("resourcePath");
			String nodeType = (String) event.getProperty(EmailResourceChangeListener.PARAM_NODE_TYPE);
			String propertyName = (String) event.getProperty(EmailResourceChangeListener.PARAM_PROPERTY_NAME);

			session = repository.loginAdministrative(null);

			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
					session);
			try {
				resourceResolver = resourceResolverFactory
						.getResourceResolver(authInfo);
			} catch (LoginException e) {
				log.error("Authentication error");
				return false;
			}

				if (event
						.getTopic()
						.equals(EmailResourceChangeListener.EMAIL_REMOVE_TOPIC)) {
					// remove
					return deleteMail(session, resourcePath);
				} else if (event
						.getTopic()
						.equals(EmailResourceChangeListener.EMAIL_SEND_TOPIC)) {
					// insert
					Resource res = resourceResolver.getResource(resourcePath);
					if (ResourceUtil.isA(res, nodeType)) {
						return sendMail(session, resourcePath);
					} else {
					    log.error("NodeTypeConflict - expected: "+nodeType+" actual: "+res.getResourceType());
					    return false;
					}
				}
			return true;
		} catch (RepositoryException e) {
			log.error("process - "+ e, e);
			return false;
		} catch (Exception e) {
			log.error("process - " + e, e);
			return false;
		} finally {
		    if (resourceResolver != null) resourceResolver.close();
			if (session != null) {
				session.logout();
				session = null;
			}
		}
	}

    public boolean sendMail(Session session, String path) throws RepositoryException, Exception {
	    ResourceResolver resourceResolver = null;

    		try {
		    log.info("Sending email: " + path);
	
		    Map<String, Object> authInfo = new HashMap<String, Object>();
		    authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
		    try {
				resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
		    } catch (LoginException e) {
				log.error("Authentication error");
				throw new RepositoryException();
		    }
	
		    Resource res = resourceResolver.getResource(path);
		    Node node = res.adaptTo(Node.class);
	
		    if (node != null) {
				// Sending mail to SMTP
				try {
				    log.info("Sending email: " + node.getName());
				    MimeMessage msg = new MimeMessage(getMailSession(), 
				    	node.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream());
				    if (msg.getAllRecipients() != null) {
					    log.info("  --> Transporting to: "+ msg.getAllRecipients()[0].toString());
					    Transport.send(msg);
					    try {
							node.remove();
					    } catch (RepositoryException ex) {
							log.error("Could not remove mail from spool folder: " + node.getName(), ex);
							return false;
					    }
				    } else {
					    log.warn("  --> No recepients, removing "+ node.getName());
					    try {
							node.remove();
					    } catch (RepositoryException ex) {
							log.error("Could not remove mail from spool folder: " + node.getName(), ex);
							return false;
					    }
				    	
				    }
				} catch (MessagingException ex) {
				    log.error("Message could not be send: " + node.getName(), ex);
				    return false;
				} catch (PathNotFoundException ex) {
				    log.error("Path not found - maybe not a nt:file node?: "
					    + node.getName(), ex);
				    return false;
				} catch (RepositoryException ex) {
				    log.error("Repository error: " + node.getName(), ex);
				    return false;
				}
				return true;
		    }
		    return false;
		} finally {
		    if (resourceResolver != null) resourceResolver.close();
		}
    }

	public boolean deleteMail(Session session, String path)
			throws RepositoryException, Exception {
	    ResourceResolver resourceResolver = null;
		try {
			log.info("Removing email: " + path);

			Map<String, Object> authInfo = new HashMap<String, Object>();
		    authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
		    try {
				resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
		    } catch (LoginException e) {
				log.error("Authentication error");
				throw new RepositoryException();
		    }
	
		    Resource res = resourceResolver.getResource(path);
		    Node node = null;
		    	if (res != null) {
		    		node = res.adaptTo(Node.class);
		    	}
		    if (node != null) {
				// Removing node
		    		node.remove();
		    }
			return true;

	    } catch (RepositoryException e) {
	    		log.error("Email remove", e);
	    		throw e;
		} finally {
		    if (resourceResolver != null) resourceResolver.close();
		}
	}


	
	/*
	 * mail.smtp.user	String	Default user name for SMTP.
	mail.smtp.host	String	The SMTP server to connect to.
	mail.smtp.port	int	The SMTP server port to connect to, if the connect() method doesn't explicitly specify one. Defaults to 25.
	mail.smtp.connectiontimeout	int	Socket connection timeout value in milliseconds. Default is infinite timeout.
	mail.smtp.timeout	int	Socket I/O timeout value in milliseconds. Default is infinite timeout.
	mail.smtp.from	String	 Email address to use for SMTP MAIL command. This sets the envelope return address. Defaults to msg.getFrom() or InternetAddress.getLocalAddress(). NOTE: mail.smtp.user was previously used for this.
	mail.smtp.localhost	String	 Local host name used in the SMTP HELO or EHLO command. Defaults to InetAddress.getLocalHost().getHostName(). Should not normally need to be set if your JDK and your name service are configured properly.
	mail.smtp.localaddress	String	 Local address (host name) to bind to when creating the SMTP socket. Defaults to the address picked by the Socket class. Should not normally need to be set, but useful with multi-homed hosts where it's important to pick a particular local address to bind to.
	mail.smtp.localport	int	 Local port number to bind to when creating the SMTP socket. Defaults to the port number picked by the Socket class.
	mail.smtp.ehlo	boolean	 If false, do not attempt to sign on with the EHLO command. Defaults to true. Normally failure of the EHLO command will fallback to the HELO command; this property exists only for servers that don't fail EHLO properly or don't implement EHLO properly.
	mail.smtp.auth	boolean	If true, attempt to authenticate the user using the AUTH command. Defaults to false.
	mail.smtp.auth.mechanisms	String	 If set, lists the authentication mechanisms to consider, and the order in which to consider them. Only mechanisms supported by the server and supported by the current implementation will be used. The default is "LOGIN PLAIN DIGEST-MD5 NTLM", which includes all the authentication mechanisms supported by the current implementation.
	mail.smtp.auth.ntlm.domain	String	 The NTLM authentication domain.
	mail.smtp.auth.ntlm.flags	int	 NTLM protocol-specific flags. See http://curl.haxx.se/rfc/ntlm.html#theNtlmFlags for details.
	mail.smtp.submitter	String	The submitter to use in the AUTH tag in the MAIL FROM command. Typically used by a mail relay to pass along information about the original submitter of the message. See also the setSubmitter method of SMTPMessage. Mail clients typically do not use this.
	mail.smtp.dsn.notify	String	The NOTIFY option to the RCPT command. Either NEVER, or some combination of SUCCESS, FAILURE, and DELAY (separated by commas).
	mail.smtp.dsn.ret	String	The RET option to the MAIL command. Either FULL or HDRS.
	mail.smtp.allow8bitmime	boolean	 If set to true, and the server supports the 8BITMIME extension, text parts of messages that use the "quoted-printable" or "base64" encodings are converted to use "8bit" encoding if they follow the RFC2045 rules for 8bit text.
	mail.smtp.sendpartial	boolean	 If set to true, and a message has some valid and some invalid addresses, send the message anyway, reporting the partial failure with a SendFailedException. If set to false (the default), the message is not sent to any of the recipients if there is an invalid recipient address.
	mail.smtp.sasl.realm	String	The realm to use with DIGEST-MD5 authentication.
	mail.smtp.quitwait	boolean	 If set to false, the QUIT command is sent and the connection is immediately closed. If set to true (the default), causes the transport to wait for the response to the QUIT command.
	mail.smtp.reportsuccess	boolean	 If set to true, causes the transport to include an SMTPAddressSucceededException for each address that is successful. Note also that this will cause a SendFailedException to be thrown from the sendMessage method of SMTPTransport even if all addresses were correct and the message was sent successfully.
	mail.smtp.socketFactory	SocketFactory	 If set to a class that implements the javax.net.SocketFactory interface, this class will be used to create SMTP sockets. Note that this is an instance of a class, not a name, and must be set using the put method, not the setProperty method.
	mail.smtp.socketFactory.class	String	 If set, specifies the name of a class that implements the javax.net.SocketFactory interface. This class will be used to create SMTP sockets.
	mail.smtp.socketFactory.fallback	boolean	 If set to true, failure to create a socket using the specified socket factory class will cause the socket to be created using the java.net.Socket class. Defaults to true.
	mail.smtp.socketFactory.port	int	 Specifies the port to connect to when using the specified socket factory. If not set, the default port will be used.
	mail.smtp.ssl.enable	boolean	 If set to true, use SSL to connect and use the SSL port by default. Defaults to false for the "smtp" protocol and true for the "smtps" protocol.
	mail.smtp.ssl.checkserveridentity	boolean	 If set to true, check the server identity as specified by RFC 2595. These additional checks based on the content of the server's certificate are intended to prevent man-in-the-middle attacks. Defaults to false.
	mail.smtp.ssl.trust	String	 If set, and a socket factory hasn't been specified, enables use of a MailSSLSocketFactory. If set to "*", all hosts are trusted. If set to a whitespace separated list of hosts, those hosts are trusted. Otherwise, trust depends on the certificate the server presents.
	mail.smtp.ssl.socketFactory	SSLSocketFactory	 If set to a class that extends the javax.net.ssl.SSLSocketFactory class, this class will be used to create SMTP SSL sockets. Note that this is an instance of a class, not a name, and must be set using the put method, not the setProperty method.
	mail.smtp.ssl.socketFactory.class	String	 If set, specifies the name of a class that extends the javax.net.ssl.SSLSocketFactory class. This class will be used to create SMTP SSL sockets.
	mail.smtp.ssl.socketFactory.port	int	 Specifies the port to connect to when using the specified socket factory. If not set, the default port will be used.
	mail.smtp.ssl.protocols	string	 Specifies the SSL protocols that will be enabled for SSL connections. The property value is a whitespace separated list of tokens acceptable to the javax.net.ssl.SSLSocket.setEnabledProtocols method.
	mail.smtp.ssl.ciphersuites	string	 Specifies the SSL cipher suites that will be enabled for SSL connections. The property value is a whitespace separated list of tokens acceptable to the javax.net.ssl.SSLSocket.setEnabledCipherSuites method.
	mail.smtp.mailextension	String	 Extension string to append to the MAIL command. The extension string can be used to specify standard SMTP service extensions as well as vendor-specific extensions. Typically the application should use the SMTPTransport method supportsExtension to verify that the server supports the desired service extension. See RFC 1869 and other RFCs that define specific extensions.
	mail.smtp.starttls.enable	boolean	 If true, enables the use of the STARTTLS command (if supported by the server) to switch the connection to a TLS-protected connection before issuing any login commands. Note that an appropriate trust store must configured so that the client will trust the server's certificate. Defaults to false.
	mail.smtp.starttls.required	boolean	 If true, requires the use of the STARTTLS command. If the server doesn't support the STARTTLS command, or the command fails, the connect method will fail. Defaults to false.
	mail.smtp.userset	boolean	 If set to true, use the RSET command instead of the NOOP command in the isConnected method. In some cases sendmail will respond slowly after many NOOP commands; use of RSET avoids this sendmail issue. Defaults to false.
	mail.smtp.noop.strict	boolean	 If set to true (the default), insist on a 250 response code from the NOOP command to indicate success. The NOOP command is used by the isConnected method to determine if the connection is still alive. Some older servers return the wrong response code on success, some servers don't implement the NOOP command at all and so always return a failure code. Set this property to false to handle servers that are broken in this way. Normally, when a server times out a connection, it will send a 421 response code, which the client will see as the response to the next command it issues. Some servers send the wrong failure response code when timing out a connection. Do not set this property to false when dealing with servers that are broken in this way.
	 */

	private javax.mail.Session getMailSession() {

		java.util.Properties properties = new java.util.Properties();
		Authenticator authenticator = null;
		if (smtpUserName != null && !"".equals(smtpUserName)) {
        		authenticator = new Authenticator(smtpUserName, smtpPassword);
        		//properties.setProperty("mail.smtp.submitter", authenticator.getPasswordAuthentication().getUserName());
        		properties.setProperty("mail.smtp.auth", "true");
		}
		properties.setProperty("mail.smtp.host", smtpHost);
		properties.setProperty("mail.smtp.port", Long.toString(smtpPort));
		properties.setProperty("mail.smtp.connectiontimeout", Long.toString(smtpConnectionTimeout));
		properties.setProperty("mail.smtp.timeout", Long.toString(smtpConnectionTimeout));
		properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(smtpSslEnable));

		return javax.mail.Session.getInstance(properties, authenticator);
	}

	private class Authenticator extends javax.mail.Authenticator {
		private PasswordAuthentication authentication;

		public Authenticator(final String username, final String password) {
		    super();
		    authentication = new PasswordAuthentication(username, password);
		}

		protected PasswordAuthentication getPasswordAuthentication() {
			return authentication;
		}
	}
}
