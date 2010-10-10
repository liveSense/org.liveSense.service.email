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

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 16, 2010
 */
public class NodeAttachmentMimePart implements MimePart {

    private Object data;
    private String name;
    private String mimeType;
    private String templatePath;
 
    public NodeAttachmentMimePart(Node node) throws RepositoryException {
        if (node.hasNode("jcr:content")) {
            node = node.getNode("jcr:content");
            setName(node.getParent().getName());
            setMimeType(node.getProperty("jcr:mimeType").getString());
        }
        if (node.hasProperty("jcr:data")) {
            setData(node.getProperty("jcr:data").getStream());
        }
    }

    public Object getData() {
        return data;
    }

    public String getTemplate() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

	public String getText() {
		return null;
	}
}
 
