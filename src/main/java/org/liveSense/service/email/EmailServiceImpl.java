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
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.liveSense.core.AdministrativeService;
import org.liveSense.core.Configurator;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }

    @Deactivate
    public void deactivate(ComponentContext componentContext) throws RepositoryException {
    }

    public void sendEmail(String resourceUrl) throws Exception {
    	sendEmail(null, resourceUrl);
    }
    
    public void sendEmail(Session session, String resourceUrl) throws Exception {
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
		    mailNode.setProperty("resourceUrl", resourceUrl);
		    mailNode = mailNode.addNode(propertyName, "nt:resource");
	
			Map<String, Object> authInfo = new HashMap<String, Object>();
		    authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
		    try {
				resourceResolver = resourceResolverFactory.getResourceResolver(authInfo);
		    } catch (LoginException e) {
				log.error("Authentication error");
				throw new RepositoryException();
		    }

		    final Resource res = resourceResolver.getResource(resourceUrl);
	
		    mailNode.setProperty("jcr:data", 
		    		new Binary() {
							InputStream is;
					
							public InputStream getStream() throws RepositoryException {
							    is = res.adaptTo(InputStream.class);
							    return is;
							}
					
							public int read(byte[] b, long position) throws IOException,
								RepositoryException {
							    return is.read(b, (int) position, 4096);
							}
					
							public long getSize() throws RepositoryException {
							    try {
							    	return is.available();
							    } catch (IOException e) {
							    	throw new RepositoryException(e);
							    }
							}
					
							public void dispose() {
							    try {
							    	is.close();
							    } catch (Exception e) {
							    	log.error("Dispose error!");
							    }
							}
					    });
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
