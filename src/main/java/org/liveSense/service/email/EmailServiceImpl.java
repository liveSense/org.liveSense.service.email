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
import java.util.UUID;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.liveSense.core.Configurator;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @scr.component label="%emailservice.service.name"
 *                description="%emailservice.service.description"
 *                immediate="true"
 * @scr.service @
 */
@Component(label = "%email.service.name", description = "%email.service.description", immediate = true, metatype = true)
@Service
public class EmailServiceImpl extends AdministrativeService implements
	EmailService {

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

    @Property(name = PARAM_NODE_TYPE, label = "%nodeType.label", description = "%nodeType.description", value = { DEFAULT_NODE_TYPE })
    private String nodeType = DEFAULT_NODE_TYPE;

    @Property(name = PARAM_PROPERTY_NAME, label = "%propertyName.label", description = "%propertyName.description", value = DEFAULT_PROPERTY_NAME)
    private String propertyName = DEFAULT_PROPERTY_NAME;

    @Property(name= PARAM_SPOOL_FOLDER, label = "%spoolFolder.name", description = "%spoolFolder.description")
    private String spoolFolder = DEFAULT_SPOOL_FOLDER;

    @Reference
    private Configurator configurator;

    /**
     * The JCR Repository we access to resolve resources
     * 
     */
    @Reference
    private SlingRepository repository;

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    /** Returns the JCR repository used by this service. */
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
    }

    @Override
    public void deactivate(ComponentContext componentContext)
	    throws RepositoryException {
	super.deactivate(componentContext);
    }

    public void sendEmail(String resourceUrl) throws Exception {
	sendEmail(null, resourceUrl);
    }
    
    public void sendEmail(Session session, String resourceUrl) throws Exception {

	boolean haveSession = false;
	if (session != null && session.isLive()) {
	    haveSession = true;
	} else {
	    session = getAdministrativeSession(repository, true);
	}
	ResourceResolver resourceResolver = null;

	try {

	    // Store mail to Spool folder
	    Node mailNode = session.getRootNode().getNode(spoolFolder)
		    .addNode(UUID.randomUUID().toString(), nodeType);
	    mailNode.setProperty("resourceUrl", resourceUrl);
	    mailNode = mailNode.addNode(propertyName, "nt:resource");

	    final Resource res = resourceResolver.getResource(resourceUrl);

	    mailNode.setProperty("jcr:data", new Binary() {
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
	    if (!haveSession)
		releaseAdministrativeSession(session);
	}
    }

}
