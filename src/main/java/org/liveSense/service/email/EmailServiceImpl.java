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

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.value.BinaryValue;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.liveSense.core.Configurator;
import org.liveSense.core.Md5Encrypter;
import org.liveSense.template.freemarker.wrapper.NodeModel;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.l3s.boilerpipe.extractors.ArticleExtractor;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Default implementation of EmailService. The generated mails created under the spoolFolder with nodeType type.
 * When a node is created under the spool folder the EmailSendJobEventHandler catch it and tries to deliver.
 */
@Component(label = "%email.service.name", description = "%email.service.description", immediate = true, metatype = true)
@Service(value = EmailService.class)
@Properties(value = { 
		@Property(name = EmailServiceImpl.PARAM_NODE_TYPE, label = "%nodeType.label", description = "%nodeType.description", value = { EmailServiceImpl.DEFAULT_NODE_TYPE }),
		@Property(name = EmailServiceImpl.PARAM_PROPERTY_NAME, label = "%propertyName.label", description = "%propertyName.description", value = EmailServiceImpl.DEFAULT_PROPERTY_NAME),
		@Property(name = EmailServiceImpl.PARAM_SPOOL_FOLDER, label = "%spoolFolder.name", description = "%spoolFolder.description", value = EmailServiceImpl.DEFAULT_SPOOL_FOLDER) })
public class EmailServiceImpl implements EmailService {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

	public final static String PARAM_SPOOL_FOLDER = EmailSendJobEventHandler.PARAM_SPOOL_FOLDER;
	public final static String DEFAULT_SPOOL_FOLDER = EmailSendJobEventHandler.DEFAULT_SPOOL_FOLDER;

	public static final String PARAM_NODE_TYPE = EmailResourceChangeListener.PARAM_NODE_TYPE;
	public static final String DEFAULT_NODE_TYPE = EmailResourceChangeListener.NODE_TYPE_EMAIL;

	public static final String PARAM_PROPERTY_NAME = EmailResourceChangeListener.PARAM_PROPERTY_NAME;
	public static final String DEFAULT_PROPERTY_NAME = EmailResourceChangeListener.PROPERTY_NAME;

	private String nodeType = DEFAULT_NODE_TYPE;
	private String propertyName = DEFAULT_PROPERTY_NAME;
	private String spoolFolder = DEFAULT_SPOOL_FOLDER;

	@Reference
	private Configurator configurator;

	@Reference
	private SlingRepository repository;

	@Reference
	ResourceResolverFactory resourceResolverFactory;

	protected SlingRepository getRepository() {
		return repository;
	}

	private Configuration templateConfig;

	/**
	 * Activates this component.
	 * 
	 * @param componentContext
	 *            The OSGi <code>ComponentContext</code> of this component.
	 */
	@Activate
	protected void activate(ComponentContext componentContext) throws RepositoryException {
		Dictionary<?, ?> props = componentContext.getProperties();
		nodeType = PropertiesUtil.toString(props.get(PARAM_NODE_TYPE), DEFAULT_NODE_TYPE);
		propertyName = PropertiesUtil.toString(props.get(DEFAULT_PROPERTY_NAME), DEFAULT_PROPERTY_NAME);
		spoolFolder = PropertiesUtil.toString(props.get(PARAM_SPOOL_FOLDER), DEFAULT_SPOOL_FOLDER);

		if (spoolFolder.startsWith("/"))
			spoolFolder = spoolFolder.substring(1);
		if (spoolFolder.endsWith("/"))
			spoolFolder = spoolFolder.substring(0, spoolFolder.length() - 1);

		Session admin = repository.loginAdministrative(null);
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

		templateConfig = new Configuration();
	}

	@Deactivate
	public void deactivate(ComponentContext componentContext) throws RepositoryException {
	}

	private String getNodeContentAsString(Node node) throws ValueFormatException, PathNotFoundException, IOException, RepositoryException {
		if (node == null)
			return null;
		return IOUtils.toString(node.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream(), configurator.getEncoding());
	}

	private Node getNodeFromPath(Session session, String path) throws RepositoryException {
		if (session == null || path == null)
			return null;
		ResourceResolver resourceResolver = null;
		Map<String, Object> authInfo = new HashMap<String, Object>();
		authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
		try {
			resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
		} catch (LoginException e) {
			log.error("Authentication error");
			throw new RepositoryException();
		}

		Node node = null;
		try {
			final Resource res = resourceResolver.resolve(path);
			if (res != null) {
				node = res.adaptTo(Node.class);
			}
		} finally {
			if (resourceResolver != null) {
				resourceResolver.close();
			}
		}

		return node;
	}

	private String templateNode(String name, Node resource, String template, HashMap<String, Object> bindings) throws ValueFormatException, PathNotFoundException, IOException, RepositoryException, TemplateException {
		if (template == null)
			return null;
		if (bindings == null && resource == null) {
			return template;
		} else {
			if (bindings == null)
				bindings = new HashMap<String, Object>();

			Template tmpl = new Template(name, new StringReader(template), templateConfig);
			if (resource != null)
				bindings.put("node", new NodeModel(resource));

			StringWriter tmplWriter = new StringWriter(32768);
			tmpl.process(bindings, tmplWriter);
			return tmplWriter.toString();
		}
	}

	private InternetAddress[] convertToInternetAddress(Object address) throws AddressException, UnsupportedEncodingException {
		if (address == null) return new InternetAddress[]{};
		if (address instanceof InternetAddress)
			return new InternetAddress[] {(InternetAddress)address};
		else if (address instanceof String) {
			return new InternetAddress[] {new InternetAddress(MimeUtility.encodeText((String)address, configurator.getEncoding(), "Q"))};
		} else if (address instanceof InternetAddress[]) {
			return (InternetAddress[]) address;
		} else if (address instanceof List<?>) {
			return convertToInternetAddress(((List) address).toArray());
		} else if (address instanceof Object[]) {
			List<InternetAddress> list = new ArrayList<InternetAddress>();
			for (Object o : (Object[])address) {
				for (InternetAddress addr : convertToInternetAddress(o)) {
					list.add(addr);
				}
				return list.toArray(new InternetAddress[list.size()]);
			}
		}
		return new InternetAddress[] {new InternetAddress(MimeUtility.encodeText(address.toString(), configurator.getEncoding(), "Q"))};

	}
	
	private MimeMessage prepareMimeMessage(MimeMessage mimeMessage, Node node, String template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws AddressException, MessagingException, ValueFormatException, PathNotFoundException,
			RepositoryException, UnsupportedEncodingException {

		
		if (replyTo != null) {
			mimeMessage.setReplyTo(convertToInternetAddress(replyTo));
		} else {
			if (node != null && node.hasProperty("replyTo")) {
				mimeMessage.setReplyTo(convertToInternetAddress(node.getProperty("replyTo").getString()));
			} else if (variables != null && variables.containsKey("replyTo")) {
				mimeMessage.setReplyTo(convertToInternetAddress(variables.get("replyTo")));
			}
		}
		if (date == null) {
			if (node != null && node.hasProperty("mailDate")) {
				mimeMessage.setSentDate(node.getProperty("mailDate").getDate().getTime());
			} else if (variables != null && variables.containsKey("mailDate")) {
				mimeMessage.setSentDate((Date)variables.get("mailDate"));
			} else {
				mimeMessage.setSentDate(new Date());
			}
		} else {
			mimeMessage.setSentDate(date);
		}

		if (subject != null) {
			mimeMessage.setSubject(MimeUtility.encodeText(subject, configurator.getEncoding(), "Q"));
		} else {
			if (node != null && node.hasProperty("subject")) {
				mimeMessage.setSubject(MimeUtility.encodeText(node.getProperty("subject").getString(), configurator.getEncoding(), "Q"));
			} else if (variables != null && variables.containsKey("subject")) {
				mimeMessage.setSubject(MimeUtility.encodeText((String)variables.get("subject"), configurator.getEncoding(), "Q"));
			}
		}

		if (from != null) {
			mimeMessage.setFrom(convertToInternetAddress(from)[0]);
		} else {
			if (node != null && node.hasProperty("from")) {
				mimeMessage.setFrom(convertToInternetAddress(node.getProperty("from").getString())[0]);
			} else if (variables != null && variables.containsKey("from")) {
				mimeMessage.setFrom(convertToInternetAddress(variables.get("from"))[0]);
			}
		}

		if (to != null) {
			mimeMessage.addRecipients(Message.RecipientType.TO, convertToInternetAddress(to));
		} else {
			if (node != null && node.hasProperty("to")) {
				if (node.getProperty("to").isMultiple()) {
					Value[] values = node.getProperty("to").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipients(Message.RecipientType.TO, convertToInternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipients(Message.RecipientType.TO, convertToInternetAddress(node.getProperty("to").getString()));
				}
			}	else if (variables != null && variables.containsKey("to")) {
				mimeMessage.addRecipients(Message.RecipientType.TO, convertToInternetAddress(variables.get("to")));
			}

		}

		if (cc != null) {
			mimeMessage.addRecipients(Message.RecipientType.CC, convertToInternetAddress(cc));
		} else {
			if (node != null && node.hasProperty("cc")) {
				if (node.getProperty("cc").isMultiple()) {
					Value[] values = node.getProperty("cc").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipients(Message.RecipientType.CC, convertToInternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipients(Message.RecipientType.CC, convertToInternetAddress(node.getProperty("cc").getString()));
				}
			} else if (variables != null && variables.containsKey("cc")) {
				mimeMessage.addRecipients(Message.RecipientType.CC, convertToInternetAddress(variables.get("cc")));
			}
		}

		if (bcc != null) {
			mimeMessage.addRecipients(Message.RecipientType.BCC, convertToInternetAddress(bcc));
		} else {
			if (node != null && node.hasProperty("bcc")) {
				if (node.getProperty("bcc").isMultiple()) {
					Value[] values = node.getProperty("bcc").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipients(Message.RecipientType.BCC, convertToInternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipients(Message.RecipientType.BCC, convertToInternetAddress(node.getProperty("bcc").getString()));
				}
			}  else if (variables != null && variables.containsKey("bcc")) {
				mimeMessage.addRecipients(Message.RecipientType.BCC, convertToInternetAddress(variables.get("bcc")));
			}
		}
		return mimeMessage;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String extractTextFromHtml(String html) throws Exception {
		return ArticleExtractor.getInstance().getText(html);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmail(MimeMessage message) throws Exception {
		sendEmail(null, message);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmail(Session session, final MimeMessage message) throws Exception {
		boolean haveSession = false;

		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}

			// Store mail to Spool folder
			Node mailNode = session.getRootNode().getNode(spoolFolder).addNode(UUID.randomUUID().toString(), nodeType);
			mailNode = mailNode.addNode(propertyName, "nt:resource");

			PipedInputStream in = new PipedInputStream();
			final PipedOutputStream out = new PipedOutputStream(in);
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						message.writeTo(out);
						out.close();
					} catch (IOException e) {
						log.error("Could not write mail message stream", e);
					} catch (MessagingException e) {
						log.error("Could not write mail message stream", e);
					}
				}
			}).start();
			BinaryValue bv = null;
			try {
				bv = new BinaryValue(in);
			} catch (IllegalArgumentException e) {
				// The jackrabbit closes the PipedInputStream, thats incorrect
			}
			if (bv != null) {
				mailNode.setProperty("jcr:data", bv);
			}
			mailNode.setProperty("jcr:lastModified", Calendar.getInstance());
			mailNode.setProperty("jcr:mimeType", "message/rfc822");

		} catch (Exception ex) {
			log.error("Cannot create mail: ", ex);
			throw ex;
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges())
					try {
						session.save();
					} catch (Throwable th) {
					}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmail(String content, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateString(null, content, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmail(Session session, String content, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateString(session, content, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822String(String content) throws Exception {
		sendEmailFromRFC822TemplateString(null, null, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822String(Session session, String content) throws Exception {
		sendEmailFromRFC822TemplateString(session, null, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(String template, String resource) throws Exception {
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			sendEmailFromRFC822TemplateString(session, template, getNodeFromPath(session, resource), null);
		} finally {
			if (session != null)
				if (session.hasPendingChanges())
					try {
						session.save();
					} catch (Throwable th) {
					}
			session.logout();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(String template, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(String template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(String template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(Session session, String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateString(session, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(Session session, String template, String resource) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromRFC822TemplateString(session, template, getNodeFromPath(session, resource), (HashMap<String, Object>) null);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(Session session, String template, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, template, (Node) null, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(Session session, String template, Node resource, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			String html = templateNode(Md5Encrypter.encrypt(template), resource, template, variables);
			if (html == null)
				throw new RepositoryException("Template is empty");

			// Store mail to Spool folder
			Node mailNode = session.getRootNode().getNode(spoolFolder).addNode(UUID.randomUUID().toString(), nodeType);

			mailNode = mailNode.addNode(propertyName, "nt:resource");
			mailNode.setProperty("jcr:data", new BinaryValue(html.getBytes(configurator.getEncoding())));
			mailNode.setProperty("jcr:lastModified", Calendar.getInstance());
			mailNode.setProperty("jcr:mimeType", "message/rfc822");
		} catch (Exception ex) {
			log.error("Cannot create mail: ", ex);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateString(Session session, String template, String resource, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromRFC822TemplateNode(session, template, getNodeFromPath(session, resource), variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(String template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Node template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Node template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(String template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(String template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, String template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, getNodeContentAsString(template), resource, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, getNodeContentAsString(template), resource, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, String template, String resource, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromRFC822TemplateNode(session, template, getNodeFromPath(session, resource), variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromRFC822TemplateNode(Session session, String template, Node resource, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromRFC822TemplateString(session, getNodeContentAsString(getNodeFromPath(session, template)), resource, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(String template, String resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(String template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(String template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(Session session, String template, String resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(Session session, String template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(Session session, String template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(String template, String resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(String template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, String resource, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, Node resource, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(String template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, String template, String resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, Object[] to, Object[] cc, Object[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, String template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromTemplateString(session, getNodeContentAsString(getNodeFromPath(session, template)), (Node)null, subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), (Node)null, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, String template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromTemplateString(session, getNodeContentAsString(getNodeFromPath(session, template)), getNodeFromPath(session, resource), subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromTemplateString(session, getNodeContentAsString(template), getNodeFromPath(session, resource), subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromTemplateString(session, getNodeContentAsString(getNodeFromPath(session, template)), resource, subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(String template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		Session session = null;
		try {
			session = repository.loginAdministrative(null);
			sendEmailFromTemplateString(session, getNodeContentAsString(getNodeFromPath(session, template)), resource, subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (session != null) {
				if (session.hasPendingChanges())
					try {
						session.save();
					} catch (Throwable th) {
					}
				session.logout();
			}
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(String template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateNode(Node template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(getNodeContentAsString(template), resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(String template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(String template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(Session session, String template, String resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;
		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}
			sendEmailFromTemplateString(template, getNodeFromPath(session, resource), subject, replyTo, from, date, to, cc, bcc, variables);
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendEmailFromTemplateString(Session session, String template, Node resource, String subject, Object replyTo, Object from, Date date, Object[] to, Object[] cc, Object[] bcc, HashMap<String, Object> variables) throws Exception {
		boolean haveSession = false;

		try {
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}

			if (template == null) {
				throw new RepositoryException("Template is null");
			}
			String html = templateNode(Md5Encrypter.encrypt(template), resource, template, variables);
			if (html == null)
				throw new RepositoryException("Template is empty");

			// create the messge.
			MimeMessage mimeMessage = new MimeMessage((javax.mail.Session) null);

			MimeMultipart rootMixedMultipart = new MimeMultipart("mixed");
			mimeMessage.setContent(rootMixedMultipart);

			MimeMultipart nestedRelatedMultipart = new MimeMultipart("related");
			MimeBodyPart relatedBodyPart = new MimeBodyPart();
			relatedBodyPart.setContent(nestedRelatedMultipart);
			rootMixedMultipart.addBodyPart(relatedBodyPart);

			MimeMultipart messageBody = new MimeMultipart("alternative");
			MimeBodyPart bodyPart = null;
			for (int i = 0; i < nestedRelatedMultipart.getCount(); i++) {
				BodyPart bp = nestedRelatedMultipart.getBodyPart(i);
				if (bp.getFileName() == null) {
					bodyPart = (MimeBodyPart) bp;
				}
			}
			if (bodyPart == null) {
				MimeBodyPart mimeBodyPart = new MimeBodyPart();
				nestedRelatedMultipart.addBodyPart(mimeBodyPart);
				bodyPart = mimeBodyPart;
			}
			bodyPart.setContent(messageBody, "text/alternative");

			// Create the plain text part of the message.
			MimeBodyPart plainTextPart = new MimeBodyPart();
			plainTextPart.setText(extractTextFromHtml(html), configurator.getEncoding());
			messageBody.addBodyPart(plainTextPart);

			// Create the HTML text part of the message.
			MimeBodyPart htmlTextPart = new MimeBodyPart();
			htmlTextPart.setContent(html, "text/html;charset=" + configurator.getEncoding()); // ;charset=UTF-8
			messageBody.addBodyPart(htmlTextPart);

			// Check if resource have nt:file childs adds as attachment
			if (resource != null && resource.hasNodes()) {
				NodeIterator iter = resource.getNodes();
				while (iter.hasNext()) {
					Node n = iter.nextNode();
					if (n.getPrimaryNodeType().isNodeType("nt:file")) {
						// Part two is attachment
						MimeBodyPart attachmentBodyPart = new MimeBodyPart();
						InputStream fileData = n.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream();
						String mimeType = n.getNode("jcr:content").getProperty("jcr:mimeType").getString();
						String fileName = n.getName();
						
						DataSource source = new StreamDataSource(fileData, fileName, mimeType);
						attachmentBodyPart.setDataHandler(new DataHandler(source));
						attachmentBodyPart.setFileName(fileName);
						attachmentBodyPart.setDisposition(MimeBodyPart.ATTACHMENT);
						attachmentBodyPart.setContentID(fileName);
						rootMixedMultipart.addBodyPart(attachmentBodyPart);
					}
				}
			}

			prepareMimeMessage(mimeMessage, resource, template, subject, replyTo, from, date, to, cc, bcc, variables);
			sendEmail(session, mimeMessage);
			
			//
		} finally {
			if (!haveSession && session != null) {
				if (session.hasPendingChanges()) {
					try {
						session.save();
					} catch (Throwable th) {
					}
				}
				session.logout();
			}
		}
	}
}
