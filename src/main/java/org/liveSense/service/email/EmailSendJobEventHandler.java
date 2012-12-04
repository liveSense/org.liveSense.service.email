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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
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
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 14, 2010
 */

@Component(label = "%emailSendJobEventHandler.name", description = "%emailSendJobEventHandler.description", immediate = true, metatype = true, policy = ConfigurationPolicy.OPTIONAL)
@Service(value = {org.osgi.service.event.EventHandler.class, java.lang.Runnable.class})
@Properties(value = { @Property(name = "event.topics", value = { EmailResourceChangeListener.EMAIL_SEND_TOPIC, EmailResourceChangeListener.EMAIL_REMOVE_TOPIC }),
		@Property(
				name="scheduler.name", 
				value="EmailSendJobEventHandler"),
		@Property(
				name="scheduler.expression", 
				value="0 * * ? * * "),
		@Property(name = EmailSendJobEventHandler.PARAM_NODE_TYPE, label = "%nodeType.name", description = "%nodeType.description", value = EmailSendJobEventHandler.DEFAULT_NODE_TYPE),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_HOST, label = "%smtpHost.name", description = "%smtpHost.description", value = EmailSendJobEventHandler.DEFAULT_SMTP_HOST),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_PORT, label = "%smtpPort.name", description = "%smtpPort.description", longValue = EmailSendJobEventHandler.DEFAULT_SMTP_PORT),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_CONNECTION_TIMEOUT, label = "%smtpConnectionTimeout.name", description = "%smtpConnectionTimeout.description", longValue = EmailSendJobEventHandler.DEFAULT_SMTP_CONNECTION_TIMEOUT),
		@Property(name = EmailSendJobEventHandler.PARAM_INITIAL_RETRY_DELAY, label = "%initialRetryDelay.name", description = "%initialRetryDelay.description", longValue = EmailSendJobEventHandler.DEFAULT_INITIAL_RETRY_DELAY),
		@Property(name = EmailSendJobEventHandler.PARAM_ADDITIONAL_RANDOM_RETRY_DELAY, label = "%additionalRandomRetryDelay.name", description = "%additionalRandomRetryDelay.description", longValue = EmailSendJobEventHandler.DEFAULT_ADDITIONAL_RANDOM_RETRY_DELAY),
		@Property(name = EmailSendJobEventHandler.PARAM_MAXIMUM_RETRY, label = "%maximumRetry.name", description = "%maximumRetry.description", longValue = EmailSendJobEventHandler.DEFAULT_MAXIMUM_RETRY),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_SSL_ENABLE, label = "%smtpSslEnable.name", description = "%smtpSslEnable.description", boolValue = EmailSendJobEventHandler.DEFAULT_SMTP_SSL_ENABLE),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_STARTTLS_ENABLE, label = "%smtpStartTlsEnable.name", description = "%smtpStartTlsEnable.description", boolValue = EmailSendJobEventHandler.DEFAULT_SMTP_STARTLS_ENABLE),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_USER_NAME, label = "%smtpUserName.name", description = "%smtpUserName.description", value = EmailSendJobEventHandler.DEFAULT_SMTP_USER_NAME),
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_PASSWORD, label = "%smtpPassword.name", description = "%smtpPassword.description", value = EmailSendJobEventHandler.DEFAULT_SMTP_PASSWORD),
		@Property(name = EmailSendJobEventHandler.PARAM_SPOOL_FOLDER, label = "%spoolFolder.name", description = "%spoolFolder.description", value = EmailSendJobEventHandler.DEFAULT_SPOOL_FOLDER),
		@Property(name = EmailSendJobEventHandler.PARAM_TEST_MAIL_ADDRESS, label = "%testMailAddress.name", description = "%testMailAddress.description", value = EmailSendJobEventHandler.DEFAULT_TEST_MAIL_ADDRESS), 
		@Property(name = EmailSendJobEventHandler.PARAM_SMTP_DEBUG, label = "%smtpDebug.name", description = "%smtpDebug.description", boolValue = EmailSendJobEventHandler.DEFAULT_SMTP_DEBUG) 
})
public class EmailSendJobEventHandler implements JobProcessor, EventHandler, Runnable {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(EmailSendJobEventHandler.class);

	public static final String PARAM_NODE_TYPE = EmailResourceChangeListener.PARAM_NODE_TYPE;
	public static final String DEFAULT_NODE_TYPE = EmailResourceChangeListener.NODE_TYPE_EMAIL;

	public static final String PARAM_SMTP_HOST = "smtpHost";
	public static final String DEFAULT_SMTP_HOST = "localhost";

	public static final String PARAM_SMTP_PORT = "smtpPort";
	public static final long DEFAULT_SMTP_PORT = 25;

	public static final String PARAM_SMTP_CONNECTION_TIMEOUT = "smtpConnectionTimeout";
	public static final long DEFAULT_SMTP_CONNECTION_TIMEOUT = 10000;

	public static final String PARAM_INITIAL_RETRY_DELAY = "initialRetryDelay";
	public static final long DEFAULT_INITIAL_RETRY_DELAY = 5 * 1000 * 60;

	public static final String PARAM_ADDITIONAL_RANDOM_RETRY_DELAY = "additionalRandomRetryDelay";
	public static final long DEFAULT_ADDITIONAL_RANDOM_RETRY_DELAY = 5 * 1000 * 60;

	public static final String PARAM_MAXIMUM_RETRY = "maximumRetry";
	public static final long DEFAULT_MAXIMUM_RETRY = 12;

	public static final String PARAM_SMTP_SSL_ENABLE = "smtpSslEnable";
	public static final boolean DEFAULT_SMTP_SSL_ENABLE = false;

	public static final String PARAM_SMTP_STARTTLS_ENABLE = "smtpStartTlsEnable";
	public static final boolean DEFAULT_SMTP_STARTLS_ENABLE = false;

	public static final String PARAM_SMTP_USER_NAME = "smtpUserName";
	public static final String DEFAULT_SMTP_USER_NAME = "";

	public static final String PARAM_SMTP_PASSWORD = "smtpPassword";
	public static final String DEFAULT_SMTP_PASSWORD = "";

	public static final String PARAM_SPOOL_FOLDER = "spoolFolder";
	public static final String DEFAULT_SPOOL_FOLDER = "/var/spool/queue/mail/";

	public static final String PARAM_TEST_MAIL_ADDRESS = "testMailAddress";
	public static final String DEFAULT_TEST_MAIL_ADDRESS = "test@example.com";

	public static final String PARAM_SMTP_DEBUG = "smtpDebug";
	public static final boolean DEFAULT_SMTP_DEBUG = false;

	private String smtpHost = DEFAULT_SMTP_HOST;
	private long smtpPort = DEFAULT_SMTP_PORT;
	private long smtpConnectionTimeout = DEFAULT_SMTP_CONNECTION_TIMEOUT;

	private long initialRetryDelay = DEFAULT_INITIAL_RETRY_DELAY;
	private long additionalRandomRetryDelay = DEFAULT_ADDITIONAL_RANDOM_RETRY_DELAY;
	private long maximumRetry = DEFAULT_MAXIMUM_RETRY;

	private boolean smtpSslEnable = DEFAULT_SMTP_SSL_ENABLE;
	private boolean smtpStartTlsEnable = DEFAULT_SMTP_STARTLS_ENABLE;
	private String smtpUserName = DEFAULT_SMTP_USER_NAME;
	private String smtpPassword = DEFAULT_SMTP_PASSWORD;
	private String spoolFolder = DEFAULT_SPOOL_FOLDER;
	private String testMailAddress = DEFAULT_TEST_MAIL_ADDRESS;
	private boolean smtpDebug = DEFAULT_SMTP_DEBUG;
	private String nodeType = DEFAULT_NODE_TYPE;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	SlingRepository repository;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	ResourceResolverFactory resourceResolverFactory;
	
	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	EventAdmin eventAdmin;

	/**
	 * Activates this component.
	 * 
	 * @param componentContext
	 *            The OSGi <code>ComponentContext</code> of this component.
	 */
	@Activate
	protected void activate(ComponentContext componentContext) throws RepositoryException {

		smtpHost = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SMTP_HOST), DEFAULT_SMTP_HOST);
		smtpPort = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_SMTP_PORT), DEFAULT_SMTP_PORT);
		smtpConnectionTimeout = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_SMTP_CONNECTION_TIMEOUT), DEFAULT_SMTP_CONNECTION_TIMEOUT);

		initialRetryDelay = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_INITIAL_RETRY_DELAY), DEFAULT_INITIAL_RETRY_DELAY);
		additionalRandomRetryDelay = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_ADDITIONAL_RANDOM_RETRY_DELAY), DEFAULT_ADDITIONAL_RANDOM_RETRY_DELAY);
		maximumRetry = PropertiesUtil.toLong(componentContext.getProperties().get(PARAM_MAXIMUM_RETRY), DEFAULT_MAXIMUM_RETRY);

		smtpSslEnable = PropertiesUtil.toBoolean(componentContext.getProperties().get(PARAM_SMTP_SSL_ENABLE), DEFAULT_SMTP_SSL_ENABLE);
		smtpStartTlsEnable = PropertiesUtil.toBoolean(componentContext.getProperties().get(PARAM_SMTP_STARTTLS_ENABLE), DEFAULT_SMTP_STARTLS_ENABLE);
		smtpUserName = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SMTP_USER_NAME), DEFAULT_SMTP_USER_NAME);
		smtpPassword = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SMTP_PASSWORD), DEFAULT_SMTP_PASSWORD);

		spoolFolder = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_SPOOL_FOLDER), DEFAULT_SPOOL_FOLDER);
		testMailAddress = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_TEST_MAIL_ADDRESS), DEFAULT_TEST_MAIL_ADDRESS);
		smtpDebug = PropertiesUtil.toBoolean(componentContext.getProperties().get(PARAM_SMTP_DEBUG), DEFAULT_SMTP_DEBUG);
		nodeType = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_NODE_TYPE), DEFAULT_NODE_TYPE);

		Session admin = repository.loginAdministrative(null);
		if (spoolFolder.startsWith("/"))
			spoolFolder = spoolFolder.substring(1);

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
//			String propertyName = (String) event.getProperty(EmailResourceChangeListener.PARAM_PROPERTY_NAME);

			session = repository.loginAdministrative(null);

			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
			try {
				resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
			} catch (LoginException e) {
				log.error("Authentication error");
				return false;
			}

			if (event.getTopic().equals(EmailResourceChangeListener.EMAIL_REMOVE_TOPIC)) {
				// remove
				return deleteMail(session, resourcePath);
			} else if (event.getTopic().equals(EmailResourceChangeListener.EMAIL_SEND_TOPIC)) {
				// insert
				Resource res = resourceResolver.getResource(resourcePath);
				if (ResourceUtil.isA(res, nodeType)) {
					return sendMail(session, resourcePath);
				} else {
					log.error("NodeTypeConflict - expected: " + nodeType + " actual: " + res.getResourceType());
					return false;
				}
			}
			return true;
		} catch (RepositoryException e) {
			log.error("process - " + e, e);
			return false;
		} catch (Exception e) {
			log.error("process - " + e, e);
			return false;
		} finally {
			if (resourceResolver != null)
				resourceResolver.close();
			if (session != null && session.isLive()) {
				try {
					if (session.hasPendingChanges()) {
						session.save();
					}
				} catch (Exception e) {
					log.error("Could not save session", e);
				}
				session.logout();
				session = null;
			}
		}
	}

	private void updateFailedMailJob(Node mailNode) {
		if (mailNode != null) {
			// This was the first unsuccessfull try
			try {
				if (!mailNode.hasProperty("retryCount")) {
					mailNode.setProperty("retryCount", new Long(1));
					// Calculating the time of next try.
					long nextTry = (new Double(
							new Long(initialRetryDelay).doubleValue() +
							new Long(System.currentTimeMillis()).doubleValue() + 
							Math.random()*new Long(additionalRandomRetryDelay).doubleValue()
							).longValue());
					
					mailNode.setProperty("nextTry", nextTry);

				} else {
					// If the number of tries exceeds maximumRetry, remove the spool
					if (mailNode.getProperty("retryCount").getLong() >= maximumRetry) {
						mailNode.remove();
						return;
					}
					// Increase retry counter
					mailNode.setProperty("retryCount", mailNode.getProperty("retryCount").getLong()+1);
					// Calculating the time of next try. Exponential intervals are in the retries
					long nextTry = (new Double(
							Math.exp(new Double( new Long(mailNode.getProperty("retryCount").getLong()).doubleValue() )) * new Long(initialRetryDelay).doubleValue() +
							new Long(System.currentTimeMillis()).doubleValue() + 
							Math.random()*new Long(additionalRandomRetryDelay).doubleValue()
							).longValue());
					
					mailNode.setProperty("nextTry", nextTry);
				}
			} catch (Throwable e) {
				log.error("Could not update failed mail job", e);
			}
		}
	}
	
	@SuppressWarnings("static-access")
	public boolean sendMail(Session session, String path) throws RepositoryException, Exception {
		ResourceResolver resourceResolver = null;
		ByteArrayOutputStream debugPrintOut = null;

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
					javax.mail.Session mailSession = getMailSession();

					PrintStream debugPrintStream = null;

					if (smtpDebug) {
						debugPrintOut = new ByteArrayOutputStream();
						debugPrintStream =  new PrintStream(debugPrintOut);
						mailSession.setDebug(true);
						mailSession.setDebugOut(debugPrintStream);
					}
					
					MimeMessage msg = new MimeMessage(mailSession, node.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream());
					if (StringUtils.isNotEmpty(testMailAddress)) {
						msg.setRecipient(RecipientType.TO,  new InternetAddress(testMailAddress));
//						msg.setRecipient(RecipientType.BCC, (InternetAddress)null);
//						msg.setRecipient(RecipientType.CC, (InternetAddress)null);
					}
					if (msg.getAllRecipients() != null) {
						log.info("  --> Transporting to: " + msg.getAllRecipients()[0].toString());
						if (smtpSslEnable) mailSession.getTransport("smtps").send(msg);
						else  mailSession.getTransport("smtp").send(msg);
						try {
							node.remove();
						} catch (RepositoryException ex) {
							log.error("Could not remove mail from spool folder: " + node.getName(), ex);
							return true;
						}
					} else {
						log.warn("  --> No recepients, removing " + node.getName());
						try {
							node.remove();
						} catch (RepositoryException ex) {
							log.error("Could not remove mail from spool folder: " + node.getName(), ex);
							return true;
						}

					}
				} catch (MessagingException ex) {
					log.error("Message could not be send: " + node.getName(), ex);
					updateFailedMailJob(node);
					return true;
				} catch (PathNotFoundException ex) {
					log.error("Path not found - maybe not a nt:file node?: " + node.getName(), ex);
					try {
						node.remove();
					} catch (Throwable e) {
					}
					return true;
				} catch (RepositoryException ex) {
					log.error("Repository error: " + node.getName(), ex);
					updateFailedMailJob(node);
					return true;
				}
				return true;
			}
			updateFailedMailJob(node);
			return true;
		} finally {
			if (debugPrintOut != null) {
				log.info(debugPrintOut.toString());
			}

			if (resourceResolver != null)
				resourceResolver.close();
		}
	}

	public boolean deleteMail(Session session, String path) throws RepositoryException, Exception {
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
			if (resourceResolver != null)
				resourceResolver.close();
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
		if (StringUtils.isNotEmpty(smtpUserName)) {
			authenticator = new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(smtpUserName, smtpPassword);
				}
			};
			properties.setProperty("mail.smtp.auth", "true");
		}
		
		properties.setProperty("mail.smtp.host", smtpHost);
		properties.setProperty("mail.smtp.port", Long.toString(smtpPort));
		properties.setProperty("mail.smtp.connectiontimeout", Long.toString(smtpConnectionTimeout));
		properties.setProperty("mail.smtp.timeout", Long.toString(smtpConnectionTimeout));
		properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(smtpSslEnable));
		properties.setProperty("mail.smtp.startls.enable", Boolean.toString(smtpStartTlsEnable));
		if (smtpSslEnable) {
			properties.setProperty("mail.smtp.socketFactory.port", Long.toString(smtpPort));
			properties.setProperty("mail.smtp.socketFactory.fallback", "false");
			properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		}
		if (smtpDebug) {
			properties.setProperty("mail.debug", "true");
			StringWriter writer = new StringWriter();
			try {
				properties.store(writer, "");
			} catch (IOException e) {
			}
			log.info(writer.toString());
		}

		return javax.mail.Session.getInstance(properties, authenticator);
	}

	/*
	 * Checking the spool folder for mails have not been sent yet,
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		Session session = null;
		try {
			
			// Searcing in spool for mails
			session = repository.loginAdministrative(null);
			QueryManager queryManager = session.getWorkspace().getQueryManager();
			Query query = queryManager.createQuery("/jcr:root/"+spoolFolder+"*[@jcr:primaryType='email:email' and @nextTry<"+System.currentTimeMillis()+"]", Query.XPATH);
			QueryResult res = query.execute();
			RowIterator iter = res.getRows();
			while (iter.hasNext()) {
				Row row = iter.nextRow();
				String path =row.getPath();
				try {
					log.info("Retry send email: "+path);
	
					final Dictionary<String, Object> props = new Hashtable<String, Object>();
			        props.put(JobUtil.PROPERTY_JOB_TOPIC, EmailResourceChangeListener.EMAIL_SEND_TOPIC);
			        props.put("resourcePath", path);
			        props.put(EmailResourceChangeListener.PARAM_NODE_TYPE, nodeType);
			        //props.put(EmailResourceChangeListener.PARAM_PROPERTY_NAME, propertyName);
			        org.osgi.service.event.Event emailSendJob = new org.osgi.service.event.Event(JobUtil.TOPIC_JOB, props);
			        eventAdmin.sendEvent(emailSendJob);
				} catch (Exception e) {
					log.error("Could not send email: "+path,e);
				}
			}
		} catch (Exception e2) {
			log.error("Error on periodical run: ", e2);
		} finally {
			if (session != null && session.isLive()) {
				session.logout();
			}
		}
	}	
}
