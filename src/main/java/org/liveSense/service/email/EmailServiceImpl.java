/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.liveSense.service.email;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.Scheduler;
import org.liveSense.core.AdministrativeService;
import org.liveSense.core.Configurator;

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
/**
 * @scr.component label="%emailservice.service.name"
 *                description="%emailservice.service.description"
 *                immediate="true"
 * @scr.service 
 * @
 */
public class EmailServiceImpl extends AdministrativeService implements EmailService {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    /**
     * @scr.property    label="%smtpHost.name"
     *                  description="%smtpHost.description"
     *                  valueRef="DEFAULT_SMTP_HOST"
     */
    public static final String PARAM_SMTP_HOST = "smtpHost";
    public static final String DEFAULT_SMTP_HOST = "localhost";
    private String smtpHost = DEFAULT_SMTP_HOST;
    /**
     * @scr.property    label="%smtpPort.name"
     *                  description="%smtpPort.description"
     *                  valueRef="DEFAULT_SMTP_PORT"
     */
    public static final String PARAM_SMTP_PORT = "smtpPort";
    public static final Long DEFAULT_SMTP_PORT = new Long(25);
    private Long smtpPort = DEFAULT_SMTP_PORT;
    /**
     * @scr.property    label="%smtpConnectionTimeout.name"
     *                  description="%smtpConnectionTimeout.description"
     *                  valueRef="DEFAULT_SMTP_CONNECTION_TIMEOUT"
     */
    public static final String PARAM_SMTP_CONNECTION_TIMEOUT = "smtpConnectionTimeout";
    public static final Long DEFAULT_SMTP_CONNECTION_TIMEOUT = new Long(10000);
    private Long smtpConnectionTimeout = DEFAULT_SMTP_CONNECTION_TIMEOUT;
    /**
     * @scr.property    label="%smtpSslEnable.name"
     *                  description="%smtpSslEnable.description"
     *                  valueRef="DEFAULT_SMTP_SSL_ENABLE"
     */
    public static final String PARAM_SMTP_SSL_ENABLE = "smtpSslEnable";
    public static final Boolean DEFAULT_SMTP_SSL_ENABLE = new Boolean(false);
    private Boolean smtpSslEnable = DEFAULT_SMTP_SSL_ENABLE;
    /**
     * @scr.property    label="%smtpUserName.name"
     *                  description="%smtpUserName.description"
     *                  valueRef="DEFAULT_SMTP_USER_NAME"
     */
    public static final String PARAM_SMTP_USER_NAME = "smtpUserName";
    public static final String DEFAULT_SMTP_USER_NAME = "";
    private String smtpUserName = DEFAULT_SMTP_USER_NAME;
    /**
     * @scr.property    label="%smtpPassword.name"
     *                  description="%smtpPassword.description"
     *                  valueRef="DEFAULT_SMTP_PASSWORD"
     */
    public static final String PARAM_SMTP_PASSWORD = "smtpPassword";
    public static final String DEFAULT_SMTP_PASSWORD = "";
    private String smtpPassword = DEFAULT_SMTP_PASSWORD;
    /**
     * @scr.property    label="%templateRefreshPeriod.name"
     *                  description="%templateRefreshPeriod.description"
     *                  valueRef="DEFAULT_TEMPLATE_REFRESH_PERIOD"
     */
    public static final String PARAM_TEMPLATE_REFRESH_PERIOD = "templateRefreshPeriod";
    public static final Long DEFAULT_TEMPLATE_REFRESH_PERIOD = new Long(60 * 10);
    private Long templateRefreshPeriod = DEFAULT_TEMPLATE_REFRESH_PERIOD;
    /**
     * @scr.property    label="%spoolFolder.name"
     *                  description="%spoolFolder.description"
     *                  valueRef="DEFAULT_SPOOL_FOLDER"
     */
    public static final String PARAM_SPOOL_FOLDER = "spoolFolder";
    public static final String DEFAULT_SPOOL_FOLDER = "/var/spool/queue/mail/";
    private String spoolFolder = DEFAULT_SPOOL_FOLDER;

    /**
     * @scr.property    label="%emailSendJobPeriod.name"
     *                  description="%emailSendJobPeriod.description"
     *                  valueRef="DEFAULT_EMAIL_SEND_JOB_PERIOD"
     */
    public static final String PARAM_EMAIL_SEND_JOB_PERIOD = "emailSendJobPeriod";
    public static final Long DEFAULT_EMAIL_SEND_JOB_PERIOD = new Long(60);
    private Long emailSendJobPeriod = DEFAULT_EMAIL_SEND_JOB_PERIOD;

    /**
     * @scr.reference
     */
    private Configurator configurator;
    /**
     * The JCR Repository we access to resolve resources
     *
     * @scr.reference
     */
    private SlingRepository repository;

    /** Returns the JCR repository used by this service. */
    protected SlingRepository getRepository() {
        return repository;
    }
    Configuration templateConfig;


    /**
    *   @scr.reference policy="static"
    *      interface="org.apache.sling.commons.scheduler.Scheduler"
    *      bind="bindScheduler"
    **/
    protected Scheduler scheduler;

    protected void bindScheduler(Scheduler scheduler) throws Exception {
        this.scheduler = scheduler;
    }


    public void startSendEmailSchedulerJob() {
        log.info("Starting emailSendJob");

        Map<String, Serializable> config = new HashMap<String, Serializable>();
        //set any configuration options in the config map here
        Job job = new EmailSendJob(repository, spoolFolder);
        try {
            scheduler.addPeriodicJob("emailSendJob", job, config, emailSendJobPeriod, false);
        } catch(Throwable th) {
            log.error("Cannot start emailSendJob", th);
        }
    }

    public void stopSendEmailSchedulerJob() {
        log.info("Stopping emailSendJob");
        try {
            scheduler.removeJob("emailSendJob");
        } catch(Throwable th) {
            log.error("Cannot stop emailSendJob", th);
        }
    }

    /**
     * Activates this component.
     *
     * @param componentContext The OSGi <code>ComponentContext</code> of this
     *            component.
     */
    protected void activate(ComponentContext componentContext) throws RepositoryException {
        Dictionary<?, ?> props = componentContext.getProperties();

        String smtpHostNew = (String) componentContext.getProperties().get(PARAM_SMTP_HOST);
        if (smtpHostNew == null || smtpHostNew.length() == 0) {
            smtpHostNew = DEFAULT_SMTP_HOST;
        }
        if (!smtpHostNew.equals(this.smtpHost)) {
            log.info("Setting new smtpHost {} (was {})", smtpHostNew, this.smtpHost);
            this.smtpHost = smtpHostNew;
        }

        Long smtpPortNew = (Long) componentContext.getProperties().get(PARAM_SMTP_PORT);
        if (smtpPortNew == null || smtpPortNew == 0) {
            smtpPortNew = DEFAULT_SMTP_PORT;
        }
        if (!smtpPortNew.equals(this.smtpPort)) {
            log.info("Setting new smtpPort {} (was {})", smtpPortNew, this.smtpPort);
            this.smtpPort = smtpPortNew;
        }

        Long smtpConnectionTimeoutNew = (Long) componentContext.getProperties().get(PARAM_SMTP_CONNECTION_TIMEOUT);
        if (smtpConnectionTimeoutNew == null || smtpConnectionTimeoutNew == 0) {
            smtpConnectionTimeoutNew = DEFAULT_SMTP_CONNECTION_TIMEOUT;
        }
        if (!smtpConnectionTimeoutNew.equals(this.smtpConnectionTimeout)) {
            log.info("Setting new smtpConnectionTimeout {} (was {})", smtpConnectionTimeoutNew, this.smtpConnectionTimeout);
            this.smtpConnectionTimeout = smtpConnectionTimeoutNew;
        }

        Boolean smtpSslEnableNew = (Boolean) componentContext.getProperties().get(PARAM_SMTP_SSL_ENABLE);
        if (smtpSslEnableNew == null) {
            smtpSslEnableNew = DEFAULT_SMTP_SSL_ENABLE;
        }
        if (!smtpSslEnableNew.equals(this.smtpSslEnable)) {
            log.info("Setting new smtpSslEnable {} (was {})", smtpSslEnableNew, this.smtpSslEnable);
            this.smtpSslEnable = smtpSslEnableNew;
        }

        String smtpUserNameNew = (String) componentContext.getProperties().get(PARAM_SMTP_USER_NAME);
        if (smtpUserNameNew == null || smtpUserNameNew.length() == 0) {
            smtpUserNameNew = DEFAULT_SMTP_USER_NAME;
        }
        if (!smtpUserNameNew.equals(this.smtpUserName)) {
            log.info("Setting new smtpUserName {} (was {})", smtpUserNameNew, this.smtpUserName);
            this.smtpUserName = smtpUserNameNew;
        }

        String smtpPasswordNew = (String) componentContext.getProperties().get(PARAM_SMTP_PASSWORD);
        if (smtpPasswordNew == null || smtpPasswordNew.length() == 0) {
            smtpPasswordNew = DEFAULT_SMTP_PASSWORD;
        }
        if (!smtpPasswordNew.equals(this.smtpPassword)) {
            log.info("Setting new smtpPassword {} (was {})", smtpPasswordNew, this.smtpPassword);
            this.smtpPassword = smtpPasswordNew;
        }

        Long templateRefreshPeriodNew = (Long) componentContext.getProperties().get(PARAM_TEMPLATE_REFRESH_PERIOD);
        if (templateRefreshPeriodNew == null || templateRefreshPeriodNew == 0) {
            templateRefreshPeriodNew = DEFAULT_TEMPLATE_REFRESH_PERIOD;
        }
        if (!templateRefreshPeriodNew.equals(this.templateRefreshPeriod)) {
            log.info("Setting new templateRefreshPeriod {} (was {})", templateRefreshPeriodNew, this.templateRefreshPeriod);
            this.templateRefreshPeriod = templateRefreshPeriodNew;
        }

        String spoolFolderNew = (String) componentContext.getProperties().get(PARAM_SPOOL_FOLDER);
        if (spoolFolderNew == null || spoolFolderNew.length() == 0) {
            spoolFolderNew = DEFAULT_SPOOL_FOLDER;
        }
        if (!spoolFolderNew.equals(this.spoolFolder)) {
            log.info("Setting new spoolFolder {} (was {})", spoolFolderNew, this.spoolFolder);
            this.spoolFolder = spoolFolderNew;
        }


        templateConfig = new Configuration();
        templateConfig.setDefaultEncoding(configurator.getEncoding());
        templateConfig.setTemplateLoader(new EmailTemplateLoader(repository));
        templateConfig.setTemplateUpdateDelay(templateRefreshPeriod.intValue());

        Properties sysprops = System.getProperties();

        sysprops.put("mail.smtp.host", smtpHost);
        sysprops.put("mail.smtp.port", Long.toString(smtpPort));
        sysprops.put("mail.smtp.connectiontimeout", Long.toString(smtpConnectionTimeout));
        sysprops.put("mail.smtp.timeout", Long.toString(smtpConnectionTimeout));
        sysprops.put("mail.smtp.ssl.enable", Boolean.toString(smtpSslEnable));

        Session session = getAdministrativeSession(repository);

        if (spoolFolder.startsWith("/")) spoolFolder = spoolFolder.substring(1);
        if (spoolFolder.endsWith("/")) spoolFolder = spoolFolder.substring(0, spoolFolder.length()-1);
        
        String[] spool = spoolFolder.split("/");
        Node node = session.getRootNode();
        for (int i = 0; i < spool.length; i++) {
            String name = spool[i];
            if (!"".equals(name) && !node.hasNode(name)) {
                node = node.addNode(name, "nt:unstructured");
                node.setProperty("sling:resourceType", "liveSense/mailFolder");
                log.info("Creating: {}",node.getPath());
            } else {
                if (!"".equals(name)) node = node.getNode(name);
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
		session.logout();


        Long emailSendJobPeriodNew = (Long) componentContext.getProperties().get(PARAM_EMAIL_SEND_JOB_PERIOD);
        if (emailSendJobPeriodNew == null || emailSendJobPeriodNew == 0) {
            emailSendJobPeriodNew = DEFAULT_EMAIL_SEND_JOB_PERIOD;
        }
        if (!emailSendJobPeriodNew.equals(this.emailSendJobPeriod)) {
            log.info("Setting new emailSendJobPeriod {} (was {})", emailSendJobPeriodNew, this.emailSendJobPeriod);
            this.emailSendJobPeriod = emailSendJobPeriodNew;
            stopSendEmailSchedulerJob();
            startSendEmailSchedulerJob();
        } else {
            startSendEmailSchedulerJob();
        }
        

		/*
        ArrayList part = new ArrayList();
        part.add(new MimePart() {

            public String getName() {
                return "test.txt";
            }

            public String getMimeType() {
                return "plain/text";
            }

            public String getTemplate() {
                return null;
            }

            public Object getData() {
                return "ajsa sajsd lajsd lajsd lakjsd lkajsd lkajsd ajsdlajs qwklqwr772���d�sf�sdfl�sdkf ???asdpwqowjka�sfk";
            }
        });

        String to[] = {"test@semmi.se"};
        try {
            sendEmail(to, null, null, "kuldo@semmi.se", "Heylofasz", "testbody", part);
        } catch (Exception ex) {
            log.error("Sendmail error: ",ex);
        }
         */

    }

    @Override
    public void deactivate(ComponentContext componentContext) throws RepositoryException {
//        if (session.hasPendingChanges()) {
//            session.save();
//        }
        super.deactivate(componentContext);
    }




    private class Authenticator extends javax.mail.Authenticator {

        private PasswordAuthentication authentication;

        public Authenticator() {
            String username = smtpUserName;
            String password = smtpPassword;
            authentication = new PasswordAuthentication(username, password);
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }

    private class InputStreamDataSource implements DataSource {

        private String name;
        private String contentType;
        private ByteArrayOutputStream baos;

        InputStreamDataSource(String name, String contentType, InputStream inputStream) throws IOException {
            this.name = name;
            this.contentType = contentType;

            baos = new ByteArrayOutputStream();

            int read;
            byte[] buff = new byte[256];
            while ((read = inputStream.read(buff)) != -1) {
                baos.write(buff, 0, read);
            }
        }

        public String getContentType() {
            return contentType;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(baos.toByteArray());
        }

        public String getName() {
            return name;
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Cannot write to this read-only resource");
        }
    }

    private Multipart createMultipart(ArrayList<MimePart> message) throws RepositoryException, IOException, MessagingException, TemplateException {
        Multipart mp = new MimeMultipart();

        for (int i = 0; i < message.size(); i++) {
            MimePart part = message.get(i);


            BodyPart bp = new MimeBodyPart();

            Object data = null;

            if (part.getTemplate() != null) {
                Template tmpl = templateConfig.getTemplate(part.getTemplate());
                StringWriter output = new StringWriter();
                tmpl.process(part.getData(), output);
				bp.setContent(output.toString(), part.getMimeType());
            } else
			if (part.getText() != null) {
				bp.setText(part.getText());
			} else {
				if (part.getData() instanceof InputStream) {
					bp.setDataHandler(new DataHandler(new InputStreamDataSource(part.getName(), part.getMimeType(), (InputStream) part.getData())));
				} else if (part.getData() instanceof DataHandler) {
					bp.setDataHandler((DataHandler) part.getData());
				} else if (part.getData() instanceof String) {
					bp.setDataHandler(new DataHandler(new InputStreamDataSource(part.getName(), part.getMimeType(), new ByteArrayInputStream(((String) part.getData()).getBytes(configurator.getEncoding())))));
				}
			}
            mp.addBodyPart(bp);
        }

        return mp;
    }

    
    public void sendEmail(Session session, String[] to, String[] cc, String[] bcc, String from, String subject, ArrayList<MimePart> message) throws Exception {
        try {
            /*
            javax.mail.Session mailsession = null;
            if (smtpUsername == null || "".equals(smtpUsername)) {
            javax.mail.Session.getInstance(sysprops);
            }
            else {
            sysprops.put("mail.smtp.auth", "true");
            javax.mail.Session.getInstance(sysprops, new Authenticator());
            }
            Message msg = new MimeMessage(mailsession);
            msg.setFrom(new InternetAddress(activationFrom));
            for (int i=0; i<to.length; i++) {
            msg.setRecipients(Message.RecipientType.TO,
            InternetAddress.parse(to[i], false));
            }
            for (int i=0; i<cc.length; i++) {
            msg.setRecipients(Message.RecipientType.CC,
            InternetAddress.parse(cc[i], false));
            }
            for (int i=0; i<bcc.length; i++) {
            msg.setRecipients(Message.RecipientType.BCC,
            InternetAddress.parse(bcc[i], false));
            }
            msg.setSubject(activationFrom);
            msg.setText(message);
            msg.setHeader("X-Mailer", "EsayfasiBouncer");
            msg.setSentDate(new Date());
            // -- Send the message --
            Transport.send(msg);
             *
             */
            javax.mail.Session mailSession = null;
            Message msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(from));
            if (to!=null) for (int i = 0; i < to.length; i++) {
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to[i], false));
            }
            if (cc!=null) for (int i = 0; i < cc.length; i++) {
                msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc[i], false));
            }
            if (bcc!=null) for (int i = 0; i < bcc.length; i++) {
                msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc[i], false));
            }

            msg.setSubject(MimeUtility.encodeText(subject, configurator.getEncoding(), "Q"));
            /*
			if (body != null) {
                msg.setText(body);
            }*/
            msg.setContent(createMultipart(message));
            msg.setHeader("X-Mailer", "liveSense bouncer");
            msg.setSentDate(new Date());

           
            // Store mail to Spool folder
            Node mailNode = session.getRootNode().getNode(spoolFolder).addNode(UUID.randomUUID().toString(), "nt:file");
            mailNode = mailNode.addNode("jcr:content","nt:resource");
            
            PipedInputStream in = new PipedInputStream();
            final PipedOutputStream out = new PipedOutputStream(in);
  
            final Message msgPipe = msg;
            new Thread(
                new Runnable(){
                    public void run(){
                        try {
                            msgPipe.writeTo(out);
                            out.flush();
                            out.close();
                        } catch (IOException ex) {
                            log.error("sendEmail: Broken pipe: ",ex);
                        } catch (MessagingException ex) {
                            log.error("sendEmail: Message error: ",ex);
                        }
                    }
                }
            ).start();
            
            mailNode.setProperty("jcr:data", in);
            mailNode.setProperty("jcr:lastModified", Calendar.getInstance());
            mailNode.setProperty("jcr:mimeType","plain/text");
        } catch (TemplateException ex) {
            log.error("Template error ",ex);
        } finally {
		}
    }

}

