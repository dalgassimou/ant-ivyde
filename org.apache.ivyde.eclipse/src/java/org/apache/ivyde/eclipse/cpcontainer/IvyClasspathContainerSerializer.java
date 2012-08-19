/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivyde.eclipse.cpcontainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ivyde.eclipse.IvyPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class IvyClasspathContainerSerializer {

    private static final String ROOT = "ivydecontainers";

    private static final String IVYCP = "ivycp";

    private static final String PATH = "path";

    private static final String PROJECT = "project";

    private static final String CPENTRIES = "cpentries";

    private static final String CPATTRS = "cpattrs";

    private static final String ATTR = "attr";

    private static final String NAME = "name";

    private static final String VALUE = "value";

    private static final String CPENTRY = "cpentry";

    private static final String KIND = "kind";

    private static final String SOURCE = "kind";

    private static final String ACCESS_RULES = "accessRules";

    private static final String RULE = "rule";

    private static final String PATTERN = "pattern";

    private File containersStateDir;

    public IvyClasspathContainerSerializer(File containersStateDir) {
        this.containersStateDir = containersStateDir;
    }

    public void save(IJavaProject project) {
        List/* <IvyClasspathContainer> */ivycps = IvyClasspathUtil
                .getIvyClasspathContainers(project);
        try {
            FileOutputStream out = new FileOutputStream(new File(containersStateDir, project
                    .getProject().getName() + ".xml"));
            try {
                write(out, ivycps);
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    // don't care
                }
            }
        } catch (IOException ioe) {
            IvyPlugin.log(IStatus.WARNING, "IvyDE container states of the project "
                    + project.getProject().getName() + " cound not be saved", ioe);
        }
    }

    public Map/* <IPath, IvyClasspathContainer> */read(IJavaProject project) {
        try {
            FileInputStream in = new FileInputStream(new File(containersStateDir, project
                    .getProject().getName() + ".xml"));
            try {
                return read(in);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // don't care
                }
            }
        } catch (IOException ioe) {
            IvyPlugin.log(IStatus.WARNING, "IvyDE container states of the project "
                    + project.getProject().getName() + " cound not be read", ioe);
            return null;
        }
    }

    private void write(OutputStream out, List/* <IvyClasspathContainer> */containers)
            throws IOException {
        try {
            StreamResult result = new StreamResult(out);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Node root = document.createElement(ROOT);
            document.appendChild(root);

            Iterator it = containers.iterator();
            while (it.hasNext()) {
                IvyClasspathContainer ivycp = (IvyClasspathContainer) it.next();

                Node node = document.createElement(IVYCP);
                root.appendChild(node);
                NamedNodeMap attributes = node.getAttributes();
                Attr attr = document.createAttribute(PATH);
                attr.setValue(ivycp.getPath().toString());
                attributes.setNamedItem(attr);

                attr = document.createAttribute(PROJECT);
                attr.setValue(ivycp.getConf().getProject().getName());
                attributes.setNamedItem(attr);

                writeCpEntries(ivycp, document, node, ivycp.getClasspathEntries());

                writeCpAttr(ivycp, document, node, ivycp.getConf().getAttributes());
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            DOMSource source = new DOMSource(document);

            transformer.transform(source, result);

        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (TransformerException e) {
            if (e.getException() instanceof IOException) {
                throw (IOException) e.getException();
            }
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    private void writeCpEntries(IvyClasspathContainer ivycp, Document document, Node node,
            IClasspathEntry[] classpathEntries) {
        if (classpathEntries == null) {
            return;
        }

        Node cpEntriesNode = document.createElement(CPENTRIES);
        node.appendChild(cpEntriesNode);

        for (int i = 0; i < classpathEntries.length; i++) {
            Node cpEntryNode = document.createElement(CPENTRY);
            cpEntriesNode.appendChild(cpEntryNode);

            int kind = classpathEntries[i].getEntryKind();
            NamedNodeMap attributes = cpEntryNode.getAttributes();
            Attr attr = document.createAttribute(KIND);
            attr.setValue(Integer.toString(kind));
            attributes.setNamedItem(attr);

            attr = document.createAttribute(PATH);
            attr.setValue(classpathEntries[i].getPath().toString());
            attributes.setNamedItem(attr);

            IPath source = classpathEntries[i].getSourceAttachmentPath();
            if (source != null) {
                attr = document.createAttribute(SOURCE);
                attr.setValue(source.toString());
                attributes.setNamedItem(attr);
            }

            writeAccessRules(ivycp, document, cpEntryNode, classpathEntries[i].getAccessRules());

            writeCpAttr(ivycp, document, cpEntryNode, classpathEntries[i].getExtraAttributes());
        }
    }

    private void writeAccessRules(IvyClasspathContainer ivycp, Document document, Node cpEntryNode,
            IAccessRule[] accessRules) {
        if (accessRules == null) {
            return;
        }
        Node accessRulesNode = document.createElement(ACCESS_RULES);
        cpEntryNode.appendChild(accessRulesNode);

        for (int i = 0; i < accessRules.length; i++) {
            Node accessRuleNode = document.createElement(RULE);
            accessRulesNode.appendChild(accessRuleNode);

            NamedNodeMap attributes = accessRuleNode.getAttributes();
            Attr attr = document.createAttribute(PATTERN);
            attr.setValue(accessRules[i].getPattern().toString());
            attributes.setNamedItem(attr);

            attr = document.createAttribute(KIND);
            attr.setValue(Integer.toString(accessRules[i].getKind()));
            attributes.setNamedItem(attr);

        }
    }

    private void writeCpAttr(IvyClasspathContainer ivycp, Document document, Node node,
            IClasspathAttribute[] attrs) {
        if (attrs == null) {
            return;
        }
        Node cpAttrsNode = document.createElement(CPATTRS);
        node.appendChild(cpAttrsNode);

        for (int i = 0; i < attrs.length; i++) {
            Node attrNode = document.createElement(ATTR);
            cpAttrsNode.appendChild(attrNode);

            NamedNodeMap attributes = attrNode.getAttributes();
            Attr attr = document.createAttribute(NAME);
            attr.setValue(attrs[i].getName());
            attributes.setNamedItem(attr);

            attr = document.createAttribute(VALUE);
            attr.setValue(attrs[i].getValue());
            attributes.setNamedItem(attr);
        }
    }

    public Map/* <IPath, IvyClasspathContainer> */read(InputStream in) throws IOException {
        try {
            InputSource source = new InputSource(in);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(source);

            NodeList elements = document.getElementsByTagName(IVYCP);

            Map/* <IPath, IvyClasspathContainer> */ivycps = new HashMap();

            int count = elements.getLength();
            for (int i = 0; i != count; i++) {
                Node node = elements.item(i);

                NamedNodeMap attributes = node.getAttributes();
                IPath path = new Path(getAttribute(attributes, PATH));

                IProject p = ResourcesPlugin.getWorkspace().getRoot()
                        .getProject(getAttribute(attributes, PROJECT));
                IJavaProject project = JavaCore.create(p);

                IClasspathEntry[] cpEntries = new IClasspathEntry[0];
                IClasspathAttribute[] cpAttributes = null;

                NodeList children = node.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node item = children.item(j);
                    if (item.getNodeName().equals(CPENTRIES)) {
                        cpEntries = readCpEntries(item);
                    } else if (item.getNodeName().equals(CPATTRS)) {
                        cpAttributes = readCpAttr(item);
                    }
                }

                IvyClasspathContainer ivycp = new IvyClasspathContainer(project, path, cpEntries,
                        cpAttributes);
                ivycps.put(path, ivycp);
            }
            return ivycps;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (SAXException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t == null) {
                t = e;
            }
            IOException ioe = new IOException(t.getMessage());
            ioe.initCause(t);
            throw ioe;
        }

    }

    private IClasspathEntry[] readCpEntries(Node cpEntries) throws SAXException {
        List/* <IClasspathEntry> */entries = new ArrayList();
        NodeList children = cpEntries.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item.getNodeName().equals(CPENTRY)) {
                IClasspathEntry cpEntry = readCpEntry(item);
                if (cpEntry != null) {
                    entries.add(cpEntry);
                }
            }
        }
        return (IClasspathEntry[]) entries.toArray(new IClasspathEntry[entries.size()]);
    }

    private IClasspathEntry readCpEntry(Node cpEntryNode) throws SAXException {
        NamedNodeMap attributes = cpEntryNode.getAttributes();
        int kind = Integer.parseInt(getAttribute(attributes, KIND));
        IPath path = new Path(getAttribute(attributes, PATH));
        String source = getAttribute(attributes, SOURCE);
        IPath sourcePath = null;
        if (source != null) {
            sourcePath = new Path(source);
        }

        IClasspathAttribute[] cpAttrs = null;
        IAccessRule[] accessRules = null;
        NodeList children = cpEntryNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item.getNodeName().equals(CPATTRS)) {
                cpAttrs = readCpAttr(item);
            } else if (item.getNodeName().equals(ACCESS_RULES)) {
                accessRules = readAccessRules(item);
            }
        }

        IClasspathEntry entry;
        switch (kind) {
            case IClasspathEntry.CPE_PROJECT:
                entry = JavaCore.newProjectEntry(path, accessRules, true, cpAttrs, true);
                break;
            case IClasspathEntry.CPE_LIBRARY:
                sourcePath = IvyClasspathContainerMapper.getSourceAttachment(path, sourcePath);
                IPath sourceRootPath = IvyClasspathContainerMapper.getSourceAttachmentRoot(path,
                    sourcePath);
                entry = JavaCore.newLibraryEntry(path, sourcePath, sourceRootPath, accessRules,
                    cpAttrs, false);
                break;
            default:
                return null;
        }

        return entry;
    }

    private IAccessRule[] readAccessRules(Node accessRulesNode) throws SAXException {
        List/* <IAccessRule> */rules = new ArrayList();
        NodeList children = accessRulesNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item.getNodeName().equals(RULE)) {
                IAccessRule rule = readAccessRule(item);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return (IAccessRule[]) rules.toArray(new IAccessRule[rules.size()]);
    }

    private IAccessRule readAccessRule(Node ruleNode) throws SAXException {
        NamedNodeMap attributes = ruleNode.getAttributes();
        int kind = Integer.parseInt(getAttribute(attributes, KIND));
        IPath pattern = new Path(getAttribute(attributes, PATTERN));
        return JavaCore.newAccessRule(pattern, kind);
    }

    private IClasspathAttribute[] readCpAttr(Node cpAttrsNode) throws SAXException {
        List/* <IClasspathAttribute> */attrs = new ArrayList();
        NodeList children = cpAttrsNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item.getNodeName().equals(ATTR)) {
                IClasspathAttribute attr = readAttr(item);
                if (attr != null) {
                    attrs.add(attr);
                }
            }
        }
        return (IClasspathAttribute[]) attrs.toArray(new IClasspathAttribute[attrs.size()]);
    }

    private IClasspathAttribute readAttr(Node attrNode) throws SAXException {
        NamedNodeMap attributes = attrNode.getAttributes();
        String name = getAttribute(attributes, NAME);
        String value = getAttribute(attributes, VALUE);
        return JavaCore.newClasspathAttribute(name, value);
    }

    private String getAttribute(NamedNodeMap attributes, String name) throws SAXException {
        Node node = attributes.getNamedItem(name);
        if (node == null) {
            throw new SAXException("Attribute '" + name + "' not found");
        }
        return node.getNodeValue();
    }

}
