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

import java.util.logging.Level;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.utils.AdministrativeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 13, 2010
 */
public class EmailSendJob extends AdministrativeService implements Job {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(EmailSendJob.class);

    private SlingRepository repository;

    String spoolPath;

    public EmailSendJob(SlingRepository repository, String spoolPath) {
        this.spoolPath = spoolPath;
        this.repository = repository;
    }

    public void execute(JobContext context) {
        log.info("Executing EmailSendJob");
        try {
            Session session = getAdministrativeSession(repository);

            NodeIterator iter = session.getRootNode().getNode(spoolPath).getNodes();
            while (iter.hasNext()) {
                Node node = iter.nextNode();
                try {
                    log.info("Sending email: "+node.getName());
                    MimeMessage msg = new MimeMessage(null, node.getNode("jcr:content").getProperty("jcr:data").getStream());
                    log.info("  --> Transporting to: "+msg.getAllRecipients()[0].toString());
                    Transport.send(msg);
                    try {
                        node.remove();
                    } catch (RepositoryException ex) {
                        log.error("Could not remove mail from spool folder: "+node.getName());
                        log.debug("Exception: ",ex);
                    }
                } catch (MessagingException ex) {
                    log.error("Message could not be send: "+node.getName());
                    log.debug("Exception: ",ex);
                } catch (PathNotFoundException ex) {
                    log.error("Path not found - maybe not a nt:file node?: "+node.getName());
                    log.debug("Exception: ",ex);
                } catch (RepositoryException ex) {
                    log.error("Repository error: "+node.getName());
                    log.debug("Exception: ",ex);
                }
            }
            if (session.hasPendingChanges()) {
                session.save();
            }
            releaseAdministrativeSession(session);
        } catch (RepositoryException ex) {
            log.error("Could not execute EmailSendJob, repository error",ex);
        }

    }

}
