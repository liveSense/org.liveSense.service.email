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

import java.util.Date;
import java.util.HashMap;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.mail.internet.MimeMessage;

/**
 * Email service.
 */
public interface EmailService {

	/**
	 * Extract plain text from HTML with boilerpipe
	 * @param html The HTML source
	 * @return  The text representation of HTML
	 * @throws Exception
	 */
	public String extractTextFromHtml(String html) throws Exception;

	/**
	 * Send a mime message as administrator user. 
	 * @param message - The mimeMessage
	 * @throws Exception
	 */
	public void sendEmail(MimeMessage message) throws Exception;

	/**
	 * Send a mime message.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param message - The mimeMessage
	 * @throws Exception
	 */
	public void sendEmail(Session session, MimeMessage message) throws Exception;

	
	/**
	 * Send a mime message as administrator user.
	 * @param content - The HTML content
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmail(String content,  String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param content - The HTML content
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmail(Session session, String content, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc) throws Exception;


	/**
	 * Send RFC822 Compliant email  as Administrator user.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: test@test
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: Sun, 22 Apr 2012 00:03:58 +0200 (CEST)
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear Adam! =
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param content- The RFC822 compliant message as string
	 * @throws Exception
	 */
	public void sendEmailFromRFC822String(String content) throws Exception;

	/**
	 * Send RFC822 Compliant email as Administrator user.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: test@test
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: Sun, 22 Apr 2012 00:03:58 +0200 (CEST)
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear Adam! =
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param content- The RFC822 compliant message as string
	 * @throws Exception
	 */
	public void sendEmailFromRFC822String(Session session, String content) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template as Administrator user. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(String template, Node resource) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template as Administrator user. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(String template, String resource) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template as Administrator user. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template- The FreeMarker template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(String template, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template as Administrator user. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(String template, Node resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template as Administrator user. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(String template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * 	@param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, Node resource) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
 	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, String resource) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template- The FreeMarker template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, Node resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send RFC822 Compliant email with FreeMarker template. 
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template- The FreeMarker template
	 * @param resource - The Node is used as resource for template
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateString(Session session, String template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template as Administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(String template, String resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template as Administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, String resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template as Administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(String template, Node resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template as Administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template as administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template as administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Node template, Node resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template as administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(String template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template as administrator user. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(String template, Node resource, HashMap<String, Object> variables) throws Exception;

	
	/**
	 * Send email from RFC822 Compliant FreeMarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, String resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, Node resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant FreeMarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${node.@emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${node.@emailDate}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${node.@fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, Node template, Node resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, String resource, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send email from RFC822 Compliant freemarker template. The resource node proerties are accessible via template.
	 * <code>
	 * From: noreply <invitation-noresponse@examples.com>
	 * To: ${emailAddress}
	 * Content-Type: text/plain; charset=utf-8
	 * Content-Transfer-Encoding: quoted-printable
	 * Subject: =?iso-8859-1?Q?Test mail?=
	 * Date: ${date}
	 * Message-Id: <8369F0FB-657C-46F6-BCF7-193A36C199C2@test.com>
	 * Mime-Version: 1.0
	 * X-Mailer: liveSense
	 * Dear ${fullName}
	 * This is a test message =
	 * Please do not reply =
	 * </code>
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The template node
	 * @param resource - The node is used for template as resource
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromRFC822TemplateNode(Session session, String template, Node resource, HashMap<String, Object> variables) throws Exception;

	
	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;


	/**
	 * Send a mime message with a FreeMarker template as administrator user. 
	 * @param template - The FreeMarker template
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message with a FreeMarker template as administrator user.  The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(Session session, String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(Session session, String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(Session session, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(Session session, String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	
	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateString(Session session, String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception;


	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, String resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;


	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template's path
	 * @param resource - The resource node.
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user.
	 * @param template - The FreeMarker template's path
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template  as administrator user. 
	 * @param template - The FreeMarker template node
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template's path
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template.
	 * @param template - The FreeMarker template's path
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template as administrator user. The resource node properties are accessible via template. 
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Node template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;


	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String resource, String[] to, String[] cc, String[] bcc) throws Exception;


	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String[] to, String[] cc, String[] bcc) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The node is used for template as resource
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;


	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param to - List of TO. If it's null tries the resource node's "to" property
	 * @param cc - List of CC. If it's null tries the resource node's "cc" property
	 * @param bcc - List of BCC. If it's null tries the resource node's "bcc" property
	 * @param variables - Additional template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. 
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, String template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, String resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template. The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, String template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

	/**
	 * Send a mime message from a resource node with a FreeMarker template.  The resource node properties are accessible via template.
	 * @param session - JCR Session - If the session is not presented it uses Administrator session
	 * @param template - The FreeMarker template node
	 * @param resource - The resource node.
	 * @param subject - The subject of email
	 * @param replyTo - The reply to address of email
	 * @param from - The from field of email
	 * @param date - The email's date
	 * @param to - The to recepients
	 * @param cc - The cc recepients
	 * @param bcc - The bcc recepients
	 * @param variables - Template variables
	 * @throws Exception
	 */
	public void sendEmailFromTemplateNode(Session session, Node template, Node resource, String subject, String replyTo, String from, Date date, String[] to, String[] cc, String[] bcc, HashMap<String, Object> variables) throws Exception;

}
