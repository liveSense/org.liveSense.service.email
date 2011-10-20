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

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.qom.Length;

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
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.liveSense.core.AdministrativeService;
import org.liveSense.core.Configurator;
import org.liveSense.template.freemarker.wrapper.NodeModel;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.Template;

@Component(label = "%email.service.name", 
			description = "%email.service.description", 
			immediate = true, 
			metatype = true)
@Service(value=EmailService.class)
@Properties(value={
	    @Property(name = EmailServiceImpl.PARAM_NODE_TYPE, 
	    		label = "%nodeType.label", 
	    		description = "%nodeType.description", 
	    		value = { EmailServiceImpl.DEFAULT_NODE_TYPE }),
	    @Property(name = EmailServiceImpl.PARAM_PROPERTY_NAME, 
	    		label = "%propertyName.label", 
	    		description = "%propertyName.description", 
	    		value = EmailServiceImpl.DEFAULT_PROPERTY_NAME),
	    @Property(name= EmailServiceImpl.PARAM_SPOOL_FOLDER, 
	    		label = "%spoolFolder.name", 
	    		description = "%spoolFolder.description",
	    		value = EmailServiceImpl.DEFAULT_SPOOL_FOLDER)
})
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
    protected void activate(ComponentContext componentContext)
	    throws RepositoryException {
    	Dictionary<?, ?> props = componentContext.getProperties();
    	nodeType = OsgiUtil.toString(props.get(PARAM_NODE_TYPE), DEFAULT_NODE_TYPE);
    	propertyName = OsgiUtil.toString(props.get(DEFAULT_PROPERTY_NAME), DEFAULT_PROPERTY_NAME);
    	spoolFolder = OsgiUtil.toString(props.get(PARAM_SPOOL_FOLDER), DEFAULT_SPOOL_FOLDER);
    	
    	if (spoolFolder.startsWith("/")) spoolFolder = spoolFolder.substring(1);
    	if (spoolFolder.endsWith("/")) spoolFolder = spoolFolder.substring(0, spoolFolder.length()-2);
    	
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

    public void sendEmail(String resourceUrl, String templateUrl) throws Exception {
    	sendEmail(null, resourceUrl, (HashMap)null);
    }
    
    public void sendEmail(Session session, String resourceUrl, String templateUrl) throws Exception {
    	sendEmail(null, resourceUrl, null, null);
    }
    
    public void sendEmail(String resourceUrl, String templateUrl, HashMap<String, Object> variables) throws Exception {
    	sendEmail(null, resourceUrl);
    }
    
    public void sendEmail(Session session, String resourceUrl, String templateUrl, HashMap<String, Object> variables) throws Exception {
		ResourceResolver resourceResolver = null;
		boolean haveSession = false;

		try {
			if (session != null && session.isLive()) {
			    haveSession = true;
			} else {
			    session = repository.loginAdministrative(null);
			}
		
		    // Store mail to Spool folder
		    Node mailNode = session.getRootNode().getNode(spoolFolder)
			    .addNode(UUID.randomUUID().toString(), nodeType);
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


		    final Resource tmplt = resourceResolver.resolve(templateUrl);
		    final Resource res = resourceResolver.resolve(resourceUrl);
		    Node templateNode = res.adaptTo(Node.class);
		    InputStream is = tmplt.adaptTo(InputStream.class);
		    
		    HashMap<String, Object> bindings = variables;
		    if (bindings == null) bindings = new HashMap<String, Object>();
		    
		    Template tmpl = new Template(templateUrl, new StringReader(IOUtils.toString(is, "UTF-8")), templateConfig);
	        bindings.put("node", new NodeModel(templateNode));
	        
	        StringWriter tmplWriter = new StringWriter(32768);
	        tmpl.process(bindings, tmplWriter);
	        
		    
		    mailNode.setProperty("jcr:data", new BinaryValue(tmplWriter.toString().getBytes("utf-8")));
		    mailNode.setProperty("jcr:lastModified", Calendar.getInstance());
		    mailNode.setProperty("jcr:mimeType", "message/rfc822");

		} catch (Exception ex) {
		    log.error("Cannot create mail: ", ex);
		} finally {
		    if (resourceResolver != null)
		    	resourceResolver.close();
		    if (!haveSession && session != null)
		    	session.logout();
		}
    }

}
