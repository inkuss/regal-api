/*
 * Copyright 2014 hbz NRW (http://www.hbz-nrw.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package actions;

import static archive.fedora.Vocabulary.TYPE_OBJECT;
import helper.HttpArchiveException;

import java.util.List;

import models.Globals;
import models.Node;
import models.RegalObject;
import models.Transformer;

/**
 * @author Jan Schnasse
 *
 */
public class Create extends RegalAction {

    /**
     * @param node
     * @param object
     * @return the updated node
     */
    public Node updateResource(Node node, RegalObject object) {
	new Index().remove(node);
	overrideNodeMembers(node, object);
	Globals.fedora.updateNode(node);
	updateIndex(node.getPid());
	return node;
    }

    /**
     * @param node
     * @param object
     * @return the updated node
     */
    public Node patchResource(Node node, RegalObject object) {
	new Index().remove(node);
	setNodeMembers(node, object);
	Globals.fedora.updateNode(node);
	updateIndex(node.getPid());
	return node;
    }

    /**
     * @param nodes
     *            nodes to set new properties for
     * @param object
     *            the RegalObject contains props that will be applied to all
     *            nodes in the list
     * @return a message
     */
    public String patchResources(List<Node> nodes, RegalObject object) {
	return apply(nodes, n -> patchResource(n, object).getPid());
    }

    /**
     * @param namespace
     * @param object
     * @return the updated node
     */
    public Node createResource(String namespace, RegalObject object) {
	String pid = pid(namespace);
	return createResource(pid.split(":")[1], namespace, object);
    }

    /**
     * @param id
     * @param namespace
     * @param object
     * @return the updated node
     */
    public Node createResource(String id, String namespace, RegalObject object) {
	Node node = initNode(id, namespace, object);
	updateResource(node, object);
	updateIndex(node.getPid());
	return node;
    }

    private Node initNode(String id, String namespace, RegalObject object) {
	Node node = new Node();
	node.setNamespace(namespace).setPID(namespace + ":" + id);
	node.setContentType(object.getContentType());
	node.setAccessScheme(object.getAccessScheme());
	node.setPublishScheme(object.getPublishScheme());
	node.setAggregationUri(createAggregationUri(node.getPid()));
	node.setRemUri(node.getAggregationUri() + ".rdf");
	node.setDataUri(node.getAggregationUri() + "/data");
	node.setContextDocumentUri("http://" + Globals.server
		+ "/public/edoweb-resources.json");
	Globals.fedora.createNode(node);
	return node;
    }

    private void setNodeMembers(Node node, RegalObject object) {
	if (object.getContentType() != null)
	    setNodeType(object.getContentType(), node);
	if (object.getAccessScheme() != null)
	    node.setAccessScheme(object.getAccessScheme());
	if (object.getPublishScheme() != null)
	    node.setPublishScheme(object.getPublishScheme());
	if (object.getParentPid() != null)
	    linkWithParent(object.getParentPid(), node);
	if (object.getIsDescribedBy().getCreatedBy() != null)
	    node.setCreatedBy(object.getIsDescribedBy().getCreatedBy());
	if (object.getIsDescribedBy().getImportedFrom() != null)
	    node.setImportedFrom(object.getIsDescribedBy().getImportedFrom());
	if (object.getIsDescribedBy().getLegacyId() != null)
	    node.setLegacyId(object.getIsDescribedBy().getLegacyId());
	if (object.getIsDescribedBy().getName() != null)
	    node.setName(object.getIsDescribedBy().getName());
	if (object.getTransformer() != null)
	    updateTransformer(object.getTransformer(), node);
	if (object.getIsDescribedBy().getDoi() != null)
	    node.setDoi(object.getIsDescribedBy().getDoi());
	if (object.getIsDescribedBy().getUrn() != null)
	    node.setUrn(object.getIsDescribedBy().getUrn());
    }

    private void overrideNodeMembers(Node node, RegalObject object) {
	setNodeType(object.getContentType(), node);
	node.setAccessScheme(object.getAccessScheme());
	node.setPublishScheme(object.getPublishScheme());
	linkWithParent(object.getParentPid(), node);
	node.setCreatedBy(object.getIsDescribedBy().getCreatedBy());
	node.setImportedFrom(object.getIsDescribedBy().getImportedFrom());
	node.setLegacyId(object.getIsDescribedBy().getLegacyId());
	node.setName(object.getIsDescribedBy().getName());
	node.setDoi(object.getIsDescribedBy().getDoi());
	node.setUrn(object.getIsDescribedBy().getUrn());
	updateTransformer(object.getTransformer(), node);
    }

    private void setNodeType(String type, Node node) {
	node.setType(TYPE_OBJECT);
	node.setContentType(type);
    }

    private void linkWithParent(String parentPid, Node node) {
	try {
	    Node parent = new Read().readNode(parentPid);
	    unlinkOldParent(node);
	    linkToNewParent(parent, node);
	    inheritTitle(parent, node);
	    inheritRights(parent, node);
	    updateIndex(parentPid);
	} catch (Exception e) {
	    play.Logger.debug("", e);
	}
    }

    private void inheritTitle(Node from, Node to) {
	String title = new Read().readMetadata(to, "title");
	String parentTitle = new Read().readMetadata(from, "title");
	if (title == null && parentTitle != null) {
	    new Modify().addMetadataField(to,
		    Globals.profile.nMap.get("title").uri, parentTitle);
	}
    }

    private void linkToNewParent(Node parent, Node child) {
	Globals.fedora.linkToParent(child, parent.getPid());
	Globals.fedora.linkParentToNode(parent.getPid(), child.getPid());
    }

    private void unlinkOldParent(Node node) {
	String pp = node.getParentPid();
	if (pp != null && !pp.isEmpty()) {
	    try {
		Globals.fedora.unlinkParent(node);
		updateIndex(pp);
	    } catch (HttpArchiveException e) {
		play.Logger.debug("", e);
	    }
	}
    }

    private void inheritRights(Node from, Node to) {
	to.setAccessScheme(from.getAccessScheme());
	to.setPublishScheme(from.getPublishScheme());
    }

    private void updateTransformer(List<String> transformers, Node node) {
	node.removeAllContentModels();
	String type = node.getContentType();
	if ("public".equals(node.getPublishScheme())) {
	    node.addTransformer(new Transformer("oaidc"));
	    if ("monograph".equals(type) || "journal".equals(type)
		    || "webpage".equals(type)) {
		node.addTransformer(new Transformer("mets"));
	    }
	}
	if (node.hasUrn()) {
	    node.addTransformer(new Transformer("epicur"));
	    if ("monograph".equals(type) || "journal".equals(type)
		    || "webpage".equals(type))
		if (node.hasLinkToCatalogId()) {
		    node.addTransformer(new Transformer("aleph"));
		}
	}
	if (transformers != null) {
	    for (String t : transformers) {
		if ("oaidc".equals(t))
		    continue; // implicitly added - or not allowed to set
		if ("epicur".equals(t))
		    continue; // implicitly added - or not allowed to set
		if ("aleph".equals(t))
		    continue; // implicitly added - or not allowed to set
		if ("mets".equals(t))
		    continue; // implicitly added - or not allowed to set
		node.addTransformer(new Transformer(t));
	    }
	}
    }

    /**
     * 
     * @param cms
     *            a List of Transformers
     * @param userId
     * @return a message
     */
    public String contentModelsInit(List<Transformer> cms) {
	Globals.fedora.updateContentModels(cms);
	return "Success!";
    }

    /**
     * @param namespace
     *            a namespace in fedora , corresponds to an index in
     *            elasticsearch
     * @return a new pid in the namespace
     */
    public String pid(String namespace) {
	return Globals.fedora.getPid(namespace);
    }

}