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
/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 13, 2010
 */
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.event.jobs.JobUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Observe the liveSense:email nodes for changes, and generate
 * send job when nodes are added/changed/deleted in spool.
 */
@Component(label="%emailResourceChangeListener.name",
        description="%emailResourceChangeListener.description",
        immediate=true,
        metatype=true,
        configurationFactory=true,
        policy=ConfigurationPolicy.OPTIONAL)
@Properties(value={
	    @Property(name=EmailResourceChangeListener.PARAM_EMAIL_SPOOL_PATH, 
	    		label="%contentPathes.name", 
	    		description="%contentPathes.description", 
	    		value={EmailResourceChangeListener.DEFAULT_EMAIL_SPOOL_PATH}),
	    @Property(name = EmailResourceChangeListener.PARAM_NODE_TYPE, 
	    		label = "%nodeType.label", 
	    		description = "%nodeType.description", 
	    		value = {EmailResourceChangeListener.NODE_TYPE_EMAIL }),
	    @Property(name = EmailResourceChangeListener.PARAM_PROPERTY_NAME, 
	    		label = "%propertyName.label", 
	    		description = "%propertyName.description", 
	    		value =	EmailResourceChangeListener.DEFAULT_PROPERTY_NAME )
})

public class EmailResourceChangeListener {
    private static final Logger log = LoggerFactory.getLogger(EmailResourceChangeListener.class);
			
    public static final String PARAM_EMAIL_SPOOL_PATH = EmailSendJobEventHandler.PARAM_SPOOL_FOLDER;
    public static final String EMAIL_SPOOL_PATH = EmailSendJobEventHandler.DEFAULT_SPOOL_FOLDER;
    public static final String DEFAULT_EMAIL_SPOOL_PATH = EMAIL_SPOOL_PATH;
		
    public static final String PARAM_NODE_TYPE = "email.nodeType";
    public static final String NODE_TYPE_EMAIL = "email:email";	        
    public static final String DEFAULT_NODE_TYPE = NODE_TYPE_EMAIL;

    public static final String PARAM_PROPERTY_NAME = "email.propertyName";
    public static final String PROPERTY_NAME = "jcr:content";
    public static final String DEFAULT_PROPERTY_NAME = PROPERTY_NAME;

    public static final String EMAIL_SEND_TOPIC = "org/liveSense/email/send";
    public static final String EMAIL_REMOVE_TOPIC = "org/liveSense/email/remove";
 
	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
    private SlingRepository repository;
	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
    private EventAdmin eventAdmin;
	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
    ResourceResolverFactory resourceResolverFactory;

    private ArrayList<PathEventListener> eventListeners = new ArrayList<PathEventListener>();
    
    private ObservationManager observationManager;
    private Session session;
    
    private String contentPathes = DEFAULT_EMAIL_SPOOL_PATH;
    private String nodeType = DEFAULT_NODE_TYPE;
    private String propertyName = DEFAULT_PROPERTY_NAME;
    
    class PathEventListener implements EventListener {

    	private void generateJobEvent(String eventType, String filePath, String fileName) {
            if (!fileName.startsWith(".")) {
            	try {
               		log.info(">Generate email send event "+JobUtil.PROPERTY_JOB_TOPIC+" "+EMAIL_SEND_TOPIC+" for " +eventType+" "+filePath+" "+fileName);
       	    		final Dictionary<String, Object> props = new Hashtable<String, Object>();
                        props.put(JobUtil.PROPERTY_JOB_TOPIC, EMAIL_SEND_TOPIC);
           		props.put("resourcePath", "/"+filePath+"/"+fileName);
           		props.put(PARAM_NODE_TYPE, nodeType);
           		props.put(PARAM_PROPERTY_NAME, propertyName);
	    		org.osgi.service.event.Event emailSendJob = new org.osgi.service.event.Event(JobUtil.TOPIC_JOB, props);
	    		eventAdmin.sendEvent(emailSendJob);
            	} catch (Exception e) {
            		log.error("Error on JobEvent: "+filePath+"/"+fileName);
				}
            }
    		
    	}

    	private void removeJobEvent(String eventType, String filePath, String fileName) {
            if (!fileName.startsWith(".")) {
        		log.info(">Remove email send event "+org.apache.sling.event.jobs.JobUtil.PROPERTY_JOB_TOPIC+" "+EMAIL_REMOVE_TOPIC+" for " +eventType+" "+filePath+" "+fileName);
            	final Dictionary<String, Object> props = new Hashtable<String, Object>();
	            props.put(JobUtil.PROPERTY_JOB_TOPIC, EMAIL_REMOVE_TOPIC);
	    		props.put("resourcePath", "/"+filePath+"/"+fileName);
	    		org.osgi.service.event.Event emailSendJob = new org.osgi.service.event.Event(JobUtil.TOPIC_JOB, props);
	    		eventAdmin.sendEvent(emailSendJob);
            }   		
    	}
    	
        public void onEvent(EventIterator it) {
            while (it.hasNext()) {
                Event event = it.nextEvent();
                try {

                    if (event.getType() == Event.NODE_REMOVED || event.getType() == Event.NODE_ADDED) {
                        String[] pathElements = event.getPath().split("/");

                        StringBuffer sb = new StringBuffer();
                        for (int i = 1; i < pathElements.length-2; i++) {
                            if (i!=0) sb.append("/");
                            sb.append(pathElements[i]);
                        }
                        String filePath = sb.toString().substring(1);
                        String fileName = pathElements[pathElements.length-2];
                        String parentFolder = pathElements[pathElements.length-3];
                        String eventType = (event.getType()==Event.NODE_ADDED ? "NODE_ADDED" : (event.getType()==Event.NODE_REMOVED ? "NODE_REMOVED" : "UNHANDLED_EVENT"));
                        if (event.getType() == Event.NODE_ADDED) {
                        	generateJobEvent(eventType, filePath, fileName);
                        } else if (event.getType() == Event.NODE_REMOVED) {
                        	removeJobEvent(eventType, filePath, fileName);
                        }
                    } else {
                        String[] pathElements = event.getPath().split("/");

                        StringBuffer sb = new StringBuffer();
                        for (int i = 1; i < pathElements.length-3; i++) {
                            if (i!=0) sb.append("/");
                            sb.append(pathElements[i]);
                        }
                        String filePath = sb.toString().substring(1);
                        String fileName = pathElements[pathElements.length-3];
                        String parentFolder = pathElements[pathElements.length-4];

                        String propertyName = pathElements[pathElements.length-1];

                        String eventType = (event.getType()==Event.PROPERTY_ADDED ? "PROPERTY_ADDED" : (event.getType()==Event.PROPERTY_CHANGED ? "PROPERTY_CHANGED" : (event.getType()==Event.PROPERTY_REMOVED ? "PROPERTY_REMOVED" : "UNHANDLED_EVENT")));
                        if ("jcr:data".equals(propertyName)) generateJobEvent(eventType, filePath, fileName);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
    

    /**
     * Activates this component.
     *
     * @param componentContext The OSGi <code>ComponentContext</code> of this
     *            component.
     */
    @Activate
    protected void activate(ComponentContext componentContext) throws RepositoryException {
         // Setting up content path
    	contentPathes = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_EMAIL_SPOOL_PATH), DEFAULT_EMAIL_SPOOL_PATH);
	
    	// Cut leading and trailing /
    	if (!contentPathes.startsWith("/")) contentPathes = "/"+contentPathes;
    	if (contentPathes.endsWith("/")) contentPathes = contentPathes.substring(0, contentPathes.length()-1);
    	
        // Setting up supported node type
        nodeType = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_NODE_TYPE), DEFAULT_NODE_TYPE);

        // Setting up supported property name
        propertyName = PropertiesUtil.toString(componentContext.getProperties().get(PARAM_PROPERTY_NAME), DEFAULT_PROPERTY_NAME);

        try {
        	session = repository.loginAdministrative(null);
	        if (repository.getDescriptor(Repository.OPTION_OBSERVATION_SUPPORTED).equals("true")) {
	        	log.info("Adding mail spool listener: "+contentPathes);
	            observationManager = session.getWorkspace().getObservationManager();
	            PathEventListener listener = new PathEventListener();
	            observationManager.addEventListener(listener, Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED, contentPathes, true, null, new String[]{"nt:resource"}, true);
	            eventListeners.add(listener);
	
	            listener = new PathEventListener();
	            observationManager.addEventListener(listener, Event.NODE_REMOVED, contentPathes, true, null, new String[]{nodeType}, true);
	            eventListeners.add(listener);
	        }
        } catch (RepositoryException e) {
        	log.error("Activate failed", e);
        	throw e;
		} finally {
		}
    }
  
    public void deactivate(ComponentContext componentContext) throws RepositoryException {
        if (observationManager != null) {
            for (PathEventListener listener : eventListeners) {
                observationManager.removeEventListener(listener);
            }
        }
        
        if (session != null) {
        	session.logout();
        	session = null;
        }
    }    
}

