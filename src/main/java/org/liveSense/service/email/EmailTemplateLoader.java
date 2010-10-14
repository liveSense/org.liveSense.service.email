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

import freemarker.cache.TemplateLoader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 15, 2010
 */
public class EmailTemplateLoader extends AdministrativeService implements TemplateLoader {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(TemplateLoader.class);
    HashMap<String, TemplateSource> objs = new HashMap<String, TemplateSource>();
    private Session session;
    SlingRepository repository;
   

    public EmailTemplateLoader(SlingRepository repository) {
        this.repository = repository;
    }


    private Node getNodeByName(String name) {
        try {
            if (session == null || !session.isLive()) {
                session = getAdministrativeSession(repository);
            }
            Node templateNode = session.getRootNode().getNode(name);
            if (templateNode != null) {
                return templateNode;
            } else {
                log.error("getNodeByName: Template not found: "+name);
                return null;
            }
        } catch (PathNotFoundException ex) {
            log.error("getNodeByName: Path not found: "+name, ex);
            return null;

        } catch (RepositoryException ex) {
            log.error("getNodeByName: Repository error: "+name, ex);
            return null;
        }
    }

    public Object findTemplateSource(String name) {
        if (name == null) {
            return null;
        }
        Node node = getNodeByName(name);
        if (node == null) return null;
        try {
            return new TemplateSource(name, node.getNode("jcr:content").getProperty("jcr:lastModified").getDate().getTimeInMillis());
        } catch (ValueFormatException ex) {
            log.error("findTemplateSource: Value format error: "+name, ex);
            return null;
        } catch (RepositoryException ex) {
            log.error("findTemplateSource: Repository error: "+name, ex);
            return null;
        }
     }

    public long getLastModified(Object templateSource) {
        if (templateSource == null) {
            return -1;
        }
        if (!(templateSource instanceof TemplateSource)) {
            return -1;
        }

        TemplateSource src = (TemplateSource)templateSource;
        Node node = getNodeByName(src.getName());
        if (node == null) return -1;
        try {
            return node.getNode("jcr:content").getProperty("jcr:lastModified").getDate().getTimeInMillis();
        } catch (ValueFormatException ex) {
            log.error("getLastModified: Value format error: "+src.getName(), ex);
            return -1;
        } catch (RepositoryException ex) {
            log.error("getLastModified: Repository error: "+src.getName(), ex);
            return -1;
        }
    }

    public Reader getReader(Object templateSource, String encoding) throws IOException {
        if (templateSource == null) {
            throw new IOException("getReader: TemplateSource object is null");
        }
        if (!(templateSource instanceof TemplateSource)) {
            throw new IOException("getReader: Object is not a TemplateSource instance");
        }

        TemplateSource src = (TemplateSource)templateSource;
        Node node = getNodeByName(src.getName());
        if (node == null) throw new IOException("getReader: Node is null");;
        try {
            return new InputStreamReader(node.getNode("jcr:content").getProperty("jcr:data").getStream(),encoding);
        } catch (ValueFormatException ex) {
            log.error("getReader: Value format error: "+src.getName(), ex);
            throw new IOException("getReader: Value format error",ex);
        } catch (RepositoryException ex) {
            log.error("getReader: Repository error: "+src.getName(), ex);
            throw new IOException("getReader: Repository error", ex);
        }
        
    }

    public void closeTemplateSource(Object templateSource) throws IOException {
    }

    private static class TemplateSource {

        private final String name;
        private final long lastModified;

        TemplateSource(String name, long lastModified) {
            if (name == null) {
                throw new IllegalArgumentException("name == null");
            }
            if (lastModified < -1L) {
                throw new IllegalArgumentException("lastModified < -1L");
            }
            this.name = name;
            this.lastModified = lastModified;
        }

        public long getLastModified() {
            return lastModified;
        }

        public String getName() {
            return name;
        }

        public boolean equals(Object obj) {
            if (obj instanceof TemplateSource) {
                return name.equals(((TemplateSource) obj).name);
            }
            return false;
        }

        public int hashCode() {
            return name.hashCode();
        }
    }
}

