# [liveSense :: Service :: Email compositing and sending - org.liveSense.service.email](http://github.com/liveSense/org.liveSense.service.email)

## Description
liveSense Email service. It's storing the mails in /var/spool and a Job sends it periodically

## OSGi Exported packages
* org.liveSense.service.email(1.0.1.SNAPSHOT)
* org.liveSense.template.freemarker.wrapper(1.0.1.SNAPSHOT)

## OSGi Dependencies
* __System Bundle - org.apache.felix.framework (4.0.3)__
	* javax.activation
	* javax.activation
	* org.w3c.dom
	* org.w3c.dom
	* org.xml.sax
	* org.xml.sax
* __Content Repository for JavaTM Technology API - javax.jcr (2.0)__
	* javax.jcr
	* javax.jcr.nodetype
	* javax.jcr.observation
	* javax.jcr.query
* __Commons Lang - org.apache.commons.lang (2.6)__
	* org.apache.commons.lang
* __Apache Sling Repository API Bundle - org.apache.sling.jcr.api (2.1.0)__
	* org.apache.sling.jcr.api
* __Apache Sling API - org.apache.sling.api (2.4.2)__
	* org.apache.sling.api.resource
* __Apache Commons IO Bundle - org.apache.commons.io (1.4)__
	* org.apache.commons.io
* __Apache Sling Commons OSGi support - org.apache.sling.commons.osgi (2.2.0)__
	* org.apache.sling.commons.osgi
* __JavaMail API (compat) - javax.mail (1.4.4)__
	* javax.mail
	* javax.mail.internet
* __Apache Sling Event Support - org.apache.sling.event (3.2.0)__
	* org.apache.sling.event
	* org.apache.sling.event.jobs
* __Apache ServiceMix :: Bundles :: freemarker - org.apache.servicemix.bundles.freemarker (2.3.18.1)__
	* freemarker.template
* __Apache Felix EventAdmin - org.apache.felix.eventadmin (1.3.2)__
	* org.osgi.service.event
* __[liveSense :: Core - org.liveSense.core (2-SNAPSHOT)](http://github.com/liveSense/org.liveSense.core)__
	* org.liveSense.core
* __Apache ServiceMix :: Bundles :: xercesImpl - org.apache.servicemix.bundles.xerces (2.9.1.5)__
	* org.apache.xerces.impl
	* org.apache.xerces.parsers
	* org.apache.xerces.util
	* org.apache.xerces.xni
	* org.apache.xerces.xni.parser
* __OPS4J Pax Logging - API - org.ops4j.pax.logging.pax-logging-api (1.7.0)__
	* org.slf4j
	* org.slf4j
	* org.slf4j
	* org.slf4j
* __Jackrabbit JCR Commons - org.apache.jackrabbit.jackrabbit-jcr-commons (2.4.2)__
	* org.apache.jackrabbit.value
* __Apache Felix Declarative Services - org.apache.felix.scr (1.6.2)__
	* org.osgi.service.component

## OSGi Embedded JARs
* boilerpipe-1.1.0.jar
* nekohtml-1.9.15.jar

## Dependency Graph
![alt text](http://raw.github.com.everydayimmirror.in/liveSense/org.liveSense.service.email/master/osgidependencies.svg "")