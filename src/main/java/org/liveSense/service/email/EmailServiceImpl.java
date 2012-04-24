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
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
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
@Properties(value = { @Property(name = EmailServiceImpl.PARAM_NODE_TYPE, label = "%nodeType.label", description = "%nodeType.description", value = { EmailServiceImpl.DEFAULT_NODE_TYPE }),
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

	//	private String templateNode(String name, Node resource, Node templateNode, HashMap<String, Object> bindings) throws ValueFormatException, PathNotFoundException, IOException, RepositoryException, TemplateException {
	//		return templateNode(name, resource, getNodeContentAsString(resource), bindings);
	//	}

	private MimeMessage prepareMimeMessage(MimeMessage mimeMessage, Node node, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws AddressException, MessagingException, ValueFormatException, PathNotFoundException,
			RepositoryException {

		if (replyTo != null) {
			mimeMessage.setReplyTo(new InternetAddress[] { new InternetAddress(replyTo) });
		} else {
			if (node != null && node.hasProperty("replyTo")) {
				mimeMessage.setReplyTo(new InternetAddress[] { new InternetAddress(node.getProperty("replyTo").getString()) });
			} else if (variables != null && variables.containsKey("replyTo")) {
				mimeMessage.setReplyTo(new InternetAddress[] { new InternetAddress((String)variables.get("replyTo")) });
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
			mimeMessage.setSubject(subject);
		} else {
			if (node != null && node.hasProperty("subject")) {
				mimeMessage.setSubject(node.getProperty("subject").getString());
			} else if (variables != null && variables.containsKey("subject")) {
				mimeMessage.setSubject((String)variables.get("subject"));
			}
		}

		if (from != null) {
			mimeMessage.setFrom(new InternetAddress(from));
		} else {
			if (node != null && node.hasProperty("from")) {
				mimeMessage.setFrom(new InternetAddress(node.getProperty("from").getString()));
			} else if (variables != null && variables.containsKey("from")) {
				mimeMessage.setFrom(new InternetAddress((String)variables.get("from")));
			}
		}

		if (to != null) {
			for (int i = 0; i < to.length; i++) {
				mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(to[i]));
			}
		} else {
			if (node != null && node.hasProperty("to")) {
				if (node.getProperty("to").isMultiple()) {
					Value[] values = node.getProperty("to").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(node.getProperty("to").getString()));
				}
			}  else if (variables != null && variables.containsKey("to") && (variables.get("to") instanceof String[])) {
				String[] values = (String[])variables.get("to");
				for (int i = 0; i < values.length; i++) {
					mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(values[i]));
				}
			}
		}

		if (cc != null) {
			for (int i = 0; i < cc.length; i++) {
				mimeMessage.addRecipient(Message.RecipientType.CC, new InternetAddress(cc[i]));
			}
		} else {
			if (node != null && node.hasProperty("cc")) {
				if (node.getProperty("cc").isMultiple()) {
					Value[] values = node.getProperty("cc").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipient(Message.RecipientType.CC, new InternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipient(Message.RecipientType.CC, new InternetAddress(node.getProperty("cc").getString()));
				}
			}  else if (variables != null && variables.containsKey("cc") && (variables.get("cc") instanceof String[])) {
				String[] values = (String[])variables.get("cc");
				for (int i = 0; i < values.length; i++) {
					mimeMessage.addRecipient(Message.RecipientType.CC, new InternetAddress(values[i]));
				}
			}
		}

		if (bcc != null) {
			for (int i = 0; i < bcc.length; i++) {
				mimeMessage.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc[i]));
			}
		} else {
			if (node != null && node.hasProperty("bcc")) {
				if (node.getProperty("bcc").isMultiple()) {
					Value[] values = node.getProperty("bcc").getValues();
					for (int i = 0; i < values.length; i++) {
						mimeMessage.addRecipient(Message.RecipientType.BCC, new InternetAddress(values[i].getString()));
					}
				} else {
					mimeMessage.addRecipient(Message.RecipientType.BCC, new InternetAddress(node.getProperty("bcc").getString()));
				}
			}  else if (variables != null && variables.containsKey("bcc") && (variables.get("bcc") instanceof String[])) {
				String[] values = (String[])variables.get("bcc");
				for (int i = 0; i < values.length; i++) {
					mimeMessage.addRecipient(Message.RecipientType.BCC, new InternetAddress(values[i]));
				}
			}
		}
		return mimeMessage;
	}

	/**
	 * {@inheritDoc}
	 */
	/*
		public void sendEmailFromRFC822TemplateString(Session session, Node resource, String template, HashMap<String, Object> variables) throws Exception {
			ResourceResolver resourceResolver = null;
			boolean haveSession = false;

			try {
				if (session != null && session.isLive()) {
					haveSession = true;
				} else {
					session = repository.loginAdministrative(null);
				}

				// Store mail to Spool folder
				Node mailNode = session.getRootNode().getNode(spoolFolder).addNode(UUID.randomUUID().toString(), nodeType);
				//mailNode.setProperty("resourceUrl", resourceUrl);
				mailNode = mailNode.addNode(propertyName, "nt:resource");

				Map<String, Object> authInfo = new HashMap<String, Object>();
				authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
				try {
					resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
				} catch (LoginException e) {
					log.error("Authentication error");
					throw new RepositoryException();
				}

	//			final Resource res = resourceResolver.resolve(resourceUrl);
	//			Node node = res.adaptTo(Node.class);

				//final Resource tmplt = resourceResolver.resolve(templateUrl);
				//Node templateNode = res.adaptTo(Node.class);

				/*
				HashMap<String, Object> bindings = variables;
				if (bindings == null)
					bindings = new HashMap<String, Object>();

				Template tmpl = new Template(templateUrl, new StringReader(IOUtils.toString(is, configurator.getEncoding())), templateConfig);
				bindings.put("node", new NodeModel(templateNode));

				StringWriter tmplWriter = new StringWriter(32768);
				tmpl.process(bindings, tmplWriter);
				* /
				mailNode.setProperty("jcr:data", new BinaryValue(templateNode(template, resource, template, variables).getBytes(configurator.getEncoding())));
				mailNode.setProperty("jcr:lastModified", Calendar.getInstance());
				mailNode.setProperty("jcr:mimeType", "message/rfc822");

			} catch (Exception ex) {
				log.error("Cannot create mail: ", ex);
			} finally {
				if (resourceResolver != null)
					resourceResolver.close();
				if (!haveSession && session != null) {
					session.logout();
			}
		}

		public void sendEmail(final MimeMessage message) throws Exception {
			sendEmail(null, message);
		}

		public void sendEmail(Session session, final MimeMessage message) throws Exception {
			ResourceResolver resourceResolver = null;
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

				Map<String, Object> authInfo = new HashMap<String, Object>();
				authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
				try {
					resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
				} catch (LoginException e) {
					log.error("Authentication error");
					throw new RepositoryException();
				}

				PipedInputStream in = new PipedInputStream();
				final PipedOutputStream out = new PipedOutputStream(in);
				new Thread(new Runnable() {
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
				if (resourceResolver != null)
					resourceResolver.close();
				if (!haveSession && session != null) {
					session.logout();
			}
		}

		@SuppressWarnings("unchecked")
		public void sendEmailFromNode(Session session, String path, String template, String[] to, String[] cc, String[] bcc) throws Exception {
			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
			ResourceResolver resourceResolver = null;
			try {
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
					sendEmailFromNode(session, node, template, to, cc, bcc);
				} else {
				
				}
			} finally {
				if (resourceResolver != null) resourceResolver.close();
			}

		}
		
		public void sendEmailFromNode(Session session, Node node, String template, String[] to, String[] cc, String[] bcc) throws Exception {
			sendEmailFromNode(session, node, template, null, null, null, null, to, cc, bcc, null);
		}

		@SuppressWarnings("unchecked")
		public void sendEmailFromNode(Session session, Node node, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
			Map<String, Object> authInfo = new HashMap<String, Object>();
			authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
			ResourceResolver resourceResolver = null;
			boolean haveSession = false;
			if (session != null && session.isLive()) {
				haveSession = true;
			} else {
				session = repository.loginAdministrative(null);
			}

			try {			
				try {
					resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
				} catch (LoginException e) {
					log.error("Authentication error");
					throw new RepositoryException();
				}
		
		
				if (node != null) {
					Configuration templateConfig = new Configuration();
		
					final Resource tmplt = resourceResolver.resolve(template);
					InputStream is = null;
					if (tmplt != null) {
						is = tmplt.adaptTo(InputStream.class);
					}

					String html = template;
					if (is != null) {
						@SuppressWarnings("rawtypes")
						Map bindings = new HashMap();
						Template tmpl = new Template(template, new StringReader(IOUtils.toString(is, configurator.getEncoding())), templateConfig);
						bindings.put("node", new NodeModel(node));
						if (variables != null) {
							bindings.putAll(variables);
						}
			
						StringWriter tmplWriter = new StringWriter(32768);
						tmpl.process(bindings, tmplWriter);
			
						//        byte[] html = tmplWriter.toString().getBytes(configurator.getEncoding());
						html = tmplWriter.toString();
					}
		
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
		
					prepareMimeMessage(mimeMessage, node, template, subject, replyTo, from, date, to, cc, bcc, variables);
					sendEmail(session, mimeMessage);
				}
			} finally {
				if (resourceResolver != null) resourceResolver.close();
				if (!haveSession && session != null) { session.logout();
			}
		}

	/**
	 * {@inheritDoc}
	 */
	public String extractTextFromHtml(String html) throws Exception {
		return ArticleExtractor.getInstance().getText(html);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmail(MimeMessage message) throws Exception {
		sendEmail(null, message);
	}

	/**
	 * {@inheritDoc}
	 */
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
	public void sendEmail(String content, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateString(null, content, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmail(Session session, String content, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateString(session, content, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822String(String content) throws Exception {
		sendEmailFromRFC822TemplateString(null, null, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822String(Session session, String content) throws Exception {
		sendEmailFromRFC822TemplateString(session, null, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateString(String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
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
	public void sendEmailFromRFC822TemplateString(String template, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, (Node) null, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateString(String template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateString(String template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(null, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateString(session, template, resource, (HashMap<String, Object>) null);
	}

	/**
	 * {@inheritDoc}
	 */
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
	public void sendEmailFromRFC822TemplateString(Session session, String template, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, template, (Node) null, variables);
	}

	/**
	 * {@inheritDoc}
	 */
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
	public void sendEmailFromRFC822TemplateNode(String template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(String template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(String template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateNode(null, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource) throws Exception {
		sendEmailFromRFC822TemplateNode(session, template, resource, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, getNodeContentAsString(template), resource, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource, HashMap<String, Object> variables) throws Exception {
		sendEmailFromRFC822TemplateString(session, getNodeContentAsString(template), resource, variables);
	}

	/**
	 * {@inheritDoc}
	 */
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
	public void sendEmailFromTemplateString(String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(Session session, String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(Session session, String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(Session session, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Node template, String resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Node template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(session, template, resource, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), (Node)null, null, null, null, null, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateNode(Session session, Node template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), (Node)null, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(session, getNodeContentAsString(template), resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateNode(Node template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateNode(null, template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(getNodeContentAsString(template), resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
		sendEmailFromTemplateString(template, resource, subject, replyTo, from, date, to, cc, bcc, variables);
	}

	/**
	 * {@inheritDoc}
	 */
	public void sendEmailFromTemplateString(Session session, String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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
	public void sendEmailFromTemplateString(Session session, String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception {
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

			prepareMimeMessage(mimeMessage, resource, template, subject, replyTo, from, date, to, cc, bcc, variables);
			sendEmail(session, mimeMessage);
			// TODO : Attachments
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
