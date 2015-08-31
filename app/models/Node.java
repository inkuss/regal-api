/*
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
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
package models;

import static archive.fedora.FedoraVocabulary.HAS_PART;
import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.Vocabulary.REL_HBZ_ID;
import static archive.fedora.Vocabulary.REL_IS_NODE_TYPE;
import static archive.fedora.Vocabulary.REL_MAB_527;
import helper.HttpArchiveException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;

import org.openrdf.rio.RDFFormat;

import archive.fedora.RdfUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * A Node of object graph. Nodes are used to model complex objects
 * 
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
@XmlRootElement(name = "object")
public class Node {

    /**
     * Dublin Core Metadata
     */
    public DublinCoreData dublinCoreData = new DublinCoreData();

    private String metadataFile = null;
    private String seqFile = null;
    private String uploadFile = null;
    private String objectTimestampFile = null;
    private List<Link> links = new Vector<Link>();
    private List<Transformer> transformer = new Vector<Transformer>();

    private String metadata = null;
    private String seq = null;
    private String objectTimestamp = null;

    private String pid = null;

    private Date lastModified = null;
    private Date creationDate = null;
    private String aggregationUri = null;
    private String remUri = null;
    private String dataUri = null;
    private String contentType = null;
    private String accessScheme = "private";
    private String parentPid = null;
    private String publishScheme = "private";

    private String fileLabel = null;
    private String fileMimeType = null;
    private BigInteger fileSize = null;
    private String fileChecksum = null;

    private String label = null;
    private String type = null;
    private String state = null;
    private String namespace = null;

    private String contextDocumentUri = null;

    private String createdBy = null;
    private String lastModifiedBy = null;
    private String importedFrom = null;
    private String legacyId = null;
    private String catalogId = null;
    private String name = null;
    private String doi = null;
    private String urn = null;

    private String fulltext;

    /**
     * Creates a new Node.
     * 
     */
    public Node() {
    }

    /**
     * Creates a new node with a certain pid.
     * 
     * @param pid
     *            the ID of the node.
     */
    public Node(String pid) {
	setPID(pid);
    }

    /**
     * Creates a CatalogId based on the objects Pid
     * 
     */
    public void createCatalogId() {
	if (pid != null && !pid.isEmpty()) {
	    String id = pid.split(":")[1];
	    setCatalogId("ED" + id);
	}
    }

    /**
     * Adds a relation. If the relation is a IS_PART_OF relation, the node is
     * assumed to have a new parent. All relations are stored in the rels_ext
     * collection.
     * 
     * @param link
     *            the link: predicats, object.
     * @return this
     */
    public Node addRelation(Link link) {
	links.add(link);
	return this;
    }

    /**
     * Nodes can be associated with content models. A ContentModel defines web
     * services which can operate on the persisted node.
     * 
     * @param cm
     *            the ContentModel
     * @return this
     */
    public Node addTransformer(Transformer cm) {
	if (!transformer.contains(cm))
	    transformer.add(cm);
	return this;
    }

    /**
     * @param id
     *            the Transformer-Id
     * @return this
     */
    public Node removeTransformer(String id) {
	Iterator<Transformer> it = transformer.iterator();
	while (it.hasNext()) {
	    Transformer t = it.next();
	    if (t.getId().compareTo(id) == 0) {
		play.Logger.info("REMOVE: " + id);
		it.remove();
	    }
	}
	return this;
    }

    /**
     * Removes all relations pointing to a certain object using a certain
     * predicate.
     * 
     * @param pred
     *            the link or predicate
     * @param obj
     *            the object or namespaced node-pid
     */
    public void removeRelation(String pred, String obj) {
	Vector<Link> newRels = new Vector<Link>();
	for (Link link : links) {
	    if (link.getPredicate().compareTo(pred) == 0
		    && link.getObject().compareTo(obj) == 0) {
		// enter here and you will not be part of the new triple vector

	    } else {
		newRels.add(link);
	    }
	}
	setLinks(newRels);
    }

    /**
     * Set the nodes PID
     * 
     * @param pid
     *            the ID
     * @return this
     */
    public Node setPID(String pid) {
	this.pid = pid;
	createCatalogId();
	return this;
    }

    /**
     * The relsExt defines all relations of the node.
     * 
     * @param newLinks
     *            all relations of the node
     * @return this
     */
    private Node setLinks(List<Link> newLinks) {
	links = new Vector<Link>();
	for (Link link : newLinks) {
	    addRelation(link);
	}
	return this;
    }

    /**
     * The nodes namespace
     * 
     * @param namespace
     *            a namespace
     * 
     * @return this
     */
    public Node setNamespace(String namespace) {
	this.namespace = namespace;
	return this;
    }

    /**
     * The type of the Node. Valid types are dependend to the context.
     * 
     * @param str
     *            the type
     * @return this
     */
    public Node setType(String str) {
	this.type = str;
	return this;
    }

    /**
     * A state
     * 
     * @param str
     *            the state
     * @return this
     */
    public Node setState(String str) {
	this.state = str;
	return this;
    }

    /**
     * A Node can be associated to a file and it's containing data.
     * 
     * @param localLocation
     *            file location
     * @param mimeType
     *            mime type of the data
     * @return this
     */
    public Node setUploadData(String localLocation, String mimeType) {
	uploadFile = localLocation;
	setMimeType(mimeType);
	return this;
    }

    /**
     * @param mimeType
     *            the mimeType of the data
     * @return this
     */
    public Node setMimeType(String mimeType) {
	this.fileMimeType = mimeType;
	return this;
    }

    /**
     * The metadata file
     * 
     * @return the absolute path to file
     */
    public String getMetadataFile() {
	return metadataFile;
    }

    /**
     * @param metadataFile
     *            The absolutepath to the metadatafile
     */
    public void setMetadataFile(String metadataFile) {
	this.metadataFile = metadataFile;
    }

    /**
     * The metadata file
     * 
     * @return the absolute path to file
     */
    public String getSeqFile() {
	return seqFile;
    }

    /**
     * @param seqFile
     *            The absolutepath to a file that provides ordering information
     *            for the object's children
     */
    public void setSeqFile(String seqFile) {
	this.seqFile = seqFile;
    }

    /**
     * @return The mime type of the data
     */
    public String getMimeType() {
	return fileMimeType;
    }

    /**
     * @return the data file
     */
    public String getUploadFile() {
	return uploadFile;
    }

    /**
     * @return the state
     */
    public String getState() {
	return state;
    }

    /**
     * @return the node's pid with namespace
     */
    public String getPid() {
	return pid;
    }

    /**
     * @return all relations
     */
    @JsonIgnore()
    public List<Link> getRelsExt() {
	return links;
    }

    /**
     * @return the namespace
     */
    public String getNamespace() {
	return namespace;
    }

    /**
     * @return the node's type
     */
    public String getNodeType() {
	return type;
    }

    /**
     * @return the label
     */
    public String getLabel() {
	return label;
    }

    /**
     * @param str
     *            a label for the node
     * @return this
     */
    public Node setLabel(String str) {
	label = str;
	return this;
    }

    /**
     * The content type can be used to specify certain content related
     * characteristics. e.g. type is more about the abstract role within the
     * graph.
     * 
     * @param type
     *            the actual content type
     */
    public void setContentType(String type) {
	contentType = type;
    }

    /**
     * @return the content type
     */
    public String getContentType() {
	return contentType;
    }

    /**
     * @return the last date the object has been modified
     */
    public Date getLastModified() {
	return lastModified;
    }

    /**
     * @param lastModified
     *            the last date the object has been modified
     */
    public void setLastModified(Date lastModified) {
	this.lastModified = lastModified;
    }

    /**
     * @return a dublin core java object representation
     */
    @JsonIgnore()
    public DublinCoreData getDublinCoreData() {
	return dublinCoreData;
    }

    /**
     * @param predicate
     *            all links with this predicate will be removed
     * @return the removed statements
     */
    public List<Link> removeRelations(String predicate) {
	Vector<Link> newRels = new Vector<Link>();
	Vector<Link> removed = new Vector<Link>();
	for (Link rel : links) {
	    if (rel.getPredicate().compareTo(predicate) == 0) {
		removed.add(rel);
	    } else {
		newRels.add(rel);
	    }
	}
	this.setLinks(newRels);
	return removed;
    }

    /**
     * @return a label for the upload data
     */
    public String getFileLabel() {
	return fileLabel;
    }

    /**
     * @param label
     *            a label for the upload data
     * @return this
     */
    public Node setFileLabel(String label) {
	fileLabel = label;
	return this;
    }

    /**
     * @param dc
     *            dublin core data in one bag
     * @return this
     */
    public Node setDublinCoreData(DublinCoreData dc) {
	this.dublinCoreData = dc;
	return this;
    }

    /**
     * @return returns the fileSize
     */
    public BigInteger getFileSize() {
	return fileSize;
    }

    /**
     * @param sizeInByte
     *            sets the filesize
     */
    public void setFileSize(BigInteger sizeInByte) {
	fileSize = sizeInByte;
    }

    /**
     * @return the checksum of the data
     */
    public String getChecksum() {
	return fileChecksum;
    }

    /**
     * @param checksum
     *            sets a checksum for the data
     */
    public void setChecksum(String checksum) {
	this.fileChecksum = checksum;
    }

    /**
     * Reinitialise the transformer list with an empty list
     * 
     */
    public void removeAllContentModels() {
	transformer = new Vector<Transformer>();
    }

    /**
     * @return the createdate of the storage object
     */
    public Date getCreationDate() {
	return creationDate;
    }

    /**
     * @param createDate
     *            the date when this node was created
     */
    public void setCreationDate(Date createDate) {
	creationDate = createDate;
    }

    /**
     * @return a string that signals who is allowed to access this node's data
     */
    public String getAccessScheme() {
	if (accessScheme == null)
	    return "private";
	return accessScheme;
    }

    /**
     * @param accessScheme
     *            a string that signals who is allowed to access this node's
     *            data
     */
    public void setAccessScheme(String accessScheme) {
	this.accessScheme = accessScheme;
    }

    /**
     * @return a string that signals who is allowed to access this node's
     *         metadata
     */
    public String getPublishScheme() {
	if (accessScheme == null)
	    return "private";
	return publishScheme;
    }

    /**
     * @param publishScheme
     *            a string that signals who is allowed to access this node's
     *            metadata
     */
    public void setPublishScheme(String publishScheme) {
	this.publishScheme = publishScheme;
    }

    /**
     * @return n-triple metadata as string
     */
    @JsonIgnore()
    public String getMetadata() {
	return metadata;
    }

    /**
     * @param metadata
     *            n-triple metadata as string
     * @return this
     */
    public Node setMetadata(String metadata) {
	this.metadata = metadata;
	return this;
    }

    /**
     * @return the content of seq datastream in a string
     */
    @JsonIgnore()
    public String getSeq() {
	return seq;
    }

    /**
     * @param seq
     *            datastream as string
     * @return this
     */
    public Node setSeq(String seq) {
	this.seq = seq;
	return this;
    }

    /**
     * @param pid
     * @return this
     */
    public Node setPid(String pid) {
	this.pid = pid;
	return this;
    }

    /**
     * @return catalogId
     */
    public String getCatalogId() {
	return catalogId;
    }

    /**
     * @param catalogId
     * @return this
     */
    public Node setCatalogId(String catalogId) {
	this.catalogId = catalogId;
	return this;
    }

    /**
     * @return parentPid
     */
    public String getParentPid() {
	return parentPid;
    }

    /**
     * @param parentPid
     * @return this
     */
    public Node setParentPid(String parentPid) {
	this.parentPid = parentPid;
	return this;
    }

    /**
     * @return fileMimeType
     */
    public String getFileMimeType() {
	return fileMimeType;
    }

    /**
     * @param fileMimeType
     * @return this
     */
    public Node setFileMimeType(String fileMimeType) {
	this.fileMimeType = fileMimeType;
	return this;
    }

    /**
     * @return fileChecksum
     */
    public String getFileChecksum() {
	return fileChecksum;
    }

    /**
     * @param fileChecksum
     * @return this
     */
    public Node setFileChecksum(String fileChecksum) {
	this.fileChecksum = fileChecksum;
	return this;
    }

    /**
     * @return type
     */
    public String getType() {
	return type;
    }

    /**
     * @param uploadFile
     * @return this
     */
    public Node setUploadFile(String uploadFile) {
	this.uploadFile = uploadFile;
	return this;
    }

    /**
     * @return aggregationUri
     */
    public String getAggregationUri() {
	return aggregationUri;
    }

    /**
     * @param aggregation
     * @return this
     */
    public Node setAggregationUri(String aggregation) {
	this.aggregationUri = aggregation;
	return this;
    }

    /**
     * @return remUri
     */
    public String getRemUri() {
	return remUri;
    }

    /**
     * @param rem
     * @return this
     */
    public Node setRemUri(String rem) {
	this.remUri = rem;
	return this;
    }

    /**
     * @return dataUri
     */
    public String getDataUri() {
	return dataUri;
    }

    /**
     * @param data
     * @return this
     */
    public Node setDataUri(String data) {
	this.dataUri = data;
	return this;
    }

    /**
     * @return transformer
     */
    public List<Transformer> getTransformer() {
	return transformer;
    }

    /**
     * @param transformer
     * @return this
     */
    public Node setTransformer(List<Transformer> transformer) {
	this.transformer = transformer;
	return this;
    }

    /**
     * @param contextDocumentUri
     * @return this
     */
    public Node setContextDocumentUri(String contextDocumentUri) {
	this.contextDocumentUri = contextDocumentUri;
	return this;
    }

    /**
     * @return getFileSizeAsString
     */
    public String getFileSizeAsString() {
	if (fileSize != null)
	    return fileSize.toString();
	return null;
    }

    /**
     * @return contextDocumentUri
     */
    public String getContextDocumentUri() {
	return contextDocumentUri;
    }

    /**
     * @return createdBy
     */
    public String getCreatedBy() {
	return createdBy;
    }

    /**
     * @param createdBy
     * @return this
     */
    public Node setCreatedBy(String createdBy) {
	this.createdBy = createdBy;
	return this;
    }

    /**
     * @return importedFrom
     */
    public String getImportedFrom() {
	return importedFrom;
    }

    /**
     * @param importedFrom
     * @return this
     */
    public Node setImportedFrom(String importedFrom) {
	this.importedFrom = importedFrom;
	return this;
    }

    /**
     * @return Rels-Ext AND metadata as List of Link
     */
    @JsonIgnore()
    public List<Link> getLinks() {
	try {
	    InputStream stream = new ByteArrayInputStream(
		    metadata.getBytes(StandardCharsets.UTF_8));
	    RdfResource rdf = RdfUtils.createRdfResource(stream,
		    RDFFormat.NTRIPLES, pid);
	    rdf = rdf.resolve();
	    rdf.addLinks(getRelsExt());
	    return Globals.profile.addLabels(rdf).getLinks();
	} catch (NullPointerException e) {
	    return new ArrayList<Link>();
	}
    }

    /**
     * @return a map representing the rdf data on this object
     */
    @JsonValue
    public Map<String, Object> getLd() {
	List<Link> ls = getLinks();
	Map<String, Object> rdf = new HashMap<String, Object>();
	rdf.put("@id", getPid());
	rdf.put("primaryTopic", getPid());
	for (Link l : ls) {
	    if (HAS_PART.equals(l.getPredicate()))
		continue;
	    if (REL_HBZ_ID.equals(l.getPredicate()))
		continue;
	    if (IS_PART_OF.equals(l.getPredicate()))
		continue;
	    addLinkToJsonMap(rdf, l);
	}
	addPartsToJsonMap(rdf);
	rdf.remove("isNodeType");

	rdf.put("contentType", getContentType());
	rdf.put("accessScheme", getAccessScheme());
	rdf.put("publishScheme", getPublishScheme());
	rdf.put("transformer", getTransformer().stream().map(t -> t.getId())
		.collect(Collectors.toList()));
	rdf.put("catalogId", getCatalogId());

	if (fulltext != null)
	    rdf.put("fulltext-ocr", fulltext);

	HashMap<String, Object> aboutMap = new HashMap<String, Object>();
	aboutMap.put("@id", this.getAggregationUri() + ".rdf");
	if (createdBy != null)
	    aboutMap.put("createdBy", getCreatedBy());
	if (legacyId != null)
	    aboutMap.put("legacyId", getLegacyId());
	if (importedFrom != null)
	    aboutMap.put("importedFrom", getImportedFrom());
	if (name != null)
	    aboutMap.put("name", getName());
	if (urn != null)
	    aboutMap.put("urn", getUrn());
	if (lastModifiedBy != null)
	    aboutMap.put("lastModifiedBy", getLastModifiedBy());
	aboutMap.put("modified", getLastModified());
	if (objectTimestamp != null) {
	    aboutMap.put("objectTimestamp", objectTimestamp);
	} else {
	    aboutMap.put("objectTimestamp", getLastModified());
	}
	aboutMap.put("created", getCreationDate());
	aboutMap.put("describes", this.getAggregationUri());
	rdf.put("isDescribedBy", aboutMap);
	if (doi != null)
	    rdf.put("doi", getDoi());
	if (parentPid != null)
	    rdf.put("parentPid", parentPid);

	if (getMimeType() != null && !getMimeType().isEmpty()) {
	    Map<String, Object> hasDataMap = new HashMap<String, Object>();
	    hasDataMap.put("@id", getDataUri());
	    hasDataMap.put("format", getMimeType());
	    hasDataMap.put("size", getFileSize());
	    if (getChecksum() != null) {
		Map<String, Object> checksum = new HashMap<String, Object>();
		checksum.put("checksumValue", getChecksum());
		checksum.put("generator", "http://en.wikipedia.org/wiki/MD5");
		checksum.put("type",
			"http://downlode.org/Code/RDF/File_Properties/schema#Checksum");
		hasDataMap.put("checksum", checksum);
	    }
	    rdf.put("hasData", hasDataMap);
	}

	rdf.put("@context", getContext());
	return rdf;
    }

    public Map<String, Object> getLdShortStyle() {
	List<Link> ls = getLinks();
	Map<String, Object> rdf = new HashMap<String, Object>();
	rdf.put("@id", getPid());
	for (Link l : ls) {
	    if (Globals.profile.nMap.get("title").uri.equals(l.getPredicate())) {
		addLinkToJsonMap(rdf, l);
		break;
	    }
	}
	addPartsToJsonMap(rdf);
	rdf.remove("isNodeType");
	rdf.put("contentType", getContentType());
	if (parentPid != null)
	    rdf.put("parentPid", parentPid);
	if (getMimeType() != null && !getMimeType().isEmpty()) {
	    Map<String, Object> hasDataMap = new HashMap<String, Object>();
	    hasDataMap.put("@id", getDataUri());
	    hasDataMap.put("format", getMimeType());
	    hasDataMap.put("size", getFileSize());
	    if (getChecksum() != null) {
		Map<String, Object> checksum = new HashMap<String, Object>();
		checksum.put("checksumValue", getChecksum());
		checksum.put("generator", "http://en.wikipedia.org/wiki/MD5");
		checksum.put("type",
			"http://downlode.org/Code/RDF/File_Properties/schema#Checksum");
		hasDataMap.put("checksum", checksum);
	    }
	    rdf.put("hasData", hasDataMap);
	}
	return rdf;
    }

    private void addLinkToJsonMap(Map<String, Object> rdf, Link l) {

	Map<String, Object> resolvedObject = null;
	if (l.getObjectLabel() != null) {
	    String id = l.getObject();
	    String value = l.getObjectLabel();
	    resolvedObject = new HashMap<String, Object>();
	    resolvedObject.put("@id", id);
	    resolvedObject.put("prefLabel", value);
	}
	if (rdf.containsKey(l.getShortName())) {
	    @SuppressWarnings("unchecked")
	    List<Object> list = (List<Object>) rdf.get(l.getShortName());
	    if (resolvedObject == null) {
		list.add(l.getObject());
	    } else {
		list.add(resolvedObject);
	    }
	} else {
	    List<Object> list = new ArrayList<Object>();
	    if (resolvedObject == null) {
		list.add(l.getObject());
	    } else {
		list.add(resolvedObject);
	    }
	    rdf.put(l.getShortName(), list);
	}
    }

    private void addPartsToJsonMap(Map<String, Object> rdf) {
	for (Link l : getPartsSorted()) {
	    if (l.getObjectLabel() == null || l.getObjectLabel().isEmpty())
		l.setObjectLabel(l.getObject());
	    addLinkToJsonMap(rdf, l);
	}
    }

    /**
     * @return a Map representing additional information about the shortnames
     *         used in getLd
     */
    public Map<String, Object> getContext() {
	List<Link> ls = getLinks();
	Map<String, Object> pmap;
	Map<String, Object> cmap = new HashMap<String, Object>();
	for (Link l : ls) {
	    pmap = new HashMap<String, Object>();
	    pmap.put("@id", l.getPredicate());
	    pmap.put("label", l.getPredicateLabel());
	    if (!l.isLiteral) {
		pmap.put("@type", "@id");
	    }
	    cmap.put(l.getShortName(), pmap);
	}

	cmap.put("label", "http://www.w3.org/2000/01/rdf-schema#label");
	cmap.put("nodeType", REL_IS_NODE_TYPE);
	cmap.put("modified", "http://purl.org/dc/terms/modified");
	cmap.put("objectTimestamp", "http://hbz-nrw.de/regal#objectTimestamp");
	cmap.put("created", "http://purl.org/dc/terms/created");

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#contentType");
	pmap.put("label", "Regaltyp");
	cmap.put("contentType", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/lobid/lv#hbzID");
	pmap.put("label", "Katalog Nr.");
	cmap.put("catalogId", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#lastModifiedBy");
	pmap.put("label", "Bearbeitet durch");
	cmap.put("lastModifiedBy", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#importedFrom");
	pmap.put("label", "Original Quelle");
	cmap.put("importedFrom", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#createdBy");
	pmap.put("label", "Angelegt durch");
	cmap.put("createdBy", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#legacyId");
	pmap.put("label", "Alternative ID");
	cmap.put("legacyId", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#name");
	pmap.put("label", "Interner Name");
	cmap.put("name", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#accessScheme");
	pmap.put("label", "Sichtbarkeit Daten");
	cmap.put("accessScheme", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#publishScheme");
	pmap.put("label", "Sichtbarkeit Metadaten");
	cmap.put("publishScheme", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/lobid/lv#urn");
	pmap.put("label", "Urn");
	cmap.put("urn", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#doi");
	pmap.put("label", "Regal-Doi");
	cmap.put("doi", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://hbz-nrw.de/regal#hasData");
	pmap.put("label", "Daten");
	cmap.put("hasData", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://xmlns.com/foaf/0.1/primaryTopic");
	pmap.put("label", "Vgl.");
	pmap.put("@type", "@id");
	cmap.put("primaryTopic", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/dc/terms/hasPart");
	pmap.put("label", "Kindobjekt");
	pmap.put("@type", "@id");
	pmap.put("@container", "@list");
	cmap.put("hasPart", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/dc/terms/isPartOf");
	pmap.put("label", "Überordnung");
	pmap.put("@type", "@id");
	cmap.put("isPartOf", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/dc/terms/isPartOf");
	pmap.put("label", "Überordnung");
	pmap.put("@type", "@id");
	cmap.put("parentPid", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://purl.org/dc/terms/format");
	pmap.put("label", "Mime Type");
	cmap.put("format", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id",
		"http://downlode.org/Code/RDF/File_Properties/schema#size");
	pmap.put("label", "Bytes");
	cmap.put("size", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id",
		"http://downlode.org/Code/RDF/File_Properties/schema#checksum");
	pmap.put("label", "MD5");
	cmap.put("checksumValue", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id",
		"http://downlode.org/Code/RDF/File_Properties/schema#generator");
	pmap.put("label", "Generator");
	pmap.put("@type", "@id");
	cmap.put("generator", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id",
		"http://downlode.org/Code/RDF/File_Properties/schema#checksum");
	pmap.put("label", "Checksum");
	pmap.put("@type", "@id");
	cmap.put("checksum", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://www.openarchives.org/ore/terms/describes");
	pmap.put("label", "Beschreibt");
	pmap.put("@type", "@id");
	cmap.put("describes", pmap);

	pmap = new HashMap<String, Object>();
	pmap.put("@id", "http://www.openarchives.org/ore/terms/isDescribedBy");
	pmap.put("label", "Beschrieben durch");
	pmap.put("@type", "@id");
	cmap.put("isDescribedBy", pmap);

	// pmap = new HashMap<String, Object>();
	// pmap.put("@id", "http://hbz-nrw.de/regal#fulltext-ocr");
	// pmap.put("label", "Volltext-OCR");
	// cmap.put("fulltext-ocr", pmap);

	cmap.put("prefLabel", "http://www.w3.org/2004/02/skos/core#prefLabel");

	return cmap;
    }

    /**
     * Returns a list of pids of related objects. Looks for other objects those
     * are connected to the pid by a certain relation
     * 
     * @param relation
     *            a relation that describes what kind of relatives you are
     *            looking for
     * @return list of pids of related objects
     */
    public List<Link> getRelatives(String relation) {
	List<Link> result = new Vector<Link>();
	for (Link l : links) {
	    if (l.getPredicate().equals(relation))
		result.add(l);
	}
	return result;
    }

    /**
     * @param node
     * @return an ordered list of the nodes Children taking the information
     *         provided by seq datastream into account
     */
    public List<Link> getPartsSorted() {
	return sort(getRelatives(HAS_PART), getSeqArray());
    }

    private List<Link> sort(List<Link> nodeIds, String[] seq) {
	List<Link> sorted = new ArrayList<Link>();
	if (nodeIds == null || nodeIds.isEmpty())
	    return sorted;
	for (String i : seq) {
	    int j = -1;
	    if ((j = nodeIds.stream().map((Link l) -> l.getObject())
		    .collect(Collectors.toList()).indexOf(i)) != -1) {
		sorted.add(nodeIds.get(j));
		nodeIds.remove(j);
	    }
	}
	sorted.addAll(nodeIds);
	return sorted;
    }

    private String[] getSeqArray() {
	try {
	    if (this.seq == null || this.seq.isEmpty())
		return new String[] {};
	    ObjectMapper mapper = new ObjectMapper();
	    return mapper.readValue(getSeq(), String[].class);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    @Override
    public int hashCode() {
	int result = 17;
	result = 31 * result + (pid != null ? pid.hashCode() : 0);
	result = 31 * result + (catalogId != null ? catalogId.hashCode() : 0);
	result = 31 * result
		+ (lastModified != null ? lastModified.hashCode() : 0);
	result = 31 * result
		+ (creationDate != null ? creationDate.hashCode() : 0);
	result = 31 * result
		+ (aggregationUri != null ? aggregationUri.hashCode() : 0);
	result = 31 * result + (remUri != null ? remUri.hashCode() : 0);
	result = 31 * result + (dataUri != null ? dataUri.hashCode() : 0);
	result = 31 * result
		+ (contentType != null ? contentType.hashCode() : 0);
	result = 31 * result
		+ (accessScheme != null ? accessScheme.hashCode() : 0);
	result = 31 * result + (parentPid != null ? parentPid.hashCode() : 0);
	result = 31 * result
		+ (fileMimeType != null ? fileMimeType.hashCode() : 0);
	result = 31 * result
		+ (fileChecksum != null ? fileChecksum.hashCode() : 0);
	result = 31 * result + (fileSize != null ? fileSize.hashCode() : 0);
	result = 31 * result + (fileLabel != null ? fileLabel.hashCode() : 0);
	return result;
    }

    @Override
    public boolean equals(Object other) {
	if (this == other)
	    return true;
	if (!(other instanceof Node))
	    return false;
	Node mt = (Node) other;
	if (!(pid == null ? mt.pid == null : pid.equals(mt.pid)))
	    return false;
	if (!(catalogId == null ? mt.catalogId == null : catalogId
		.equals(mt.catalogId)))
	    return false;
	if (!(lastModified == null ? mt.lastModified == null : lastModified
		.equals(lastModified)))
	    return false;
	if (!(creationDate == null ? mt.creationDate == null : creationDate
		.equals(mt.creationDate)))
	    return false;
	if (!(aggregationUri == null ? mt.aggregationUri == null
		: aggregationUri.equals(mt.aggregationUri)))
	    return false;
	if (!(remUri == null ? mt.remUri == null : remUri.equals(mt.remUri)))
	    return false;
	if (!(dataUri == null ? mt.dataUri == null : dataUri.equals(mt.dataUri)))
	    return false;
	if (!(contentType == null ? mt.contentType == null : contentType
		.equals(mt.contentType)))
	    return false;
	if (!(accessScheme == null ? mt.accessScheme == null : accessScheme
		.equals(mt.accessScheme)))
	    return false;
	if (!(parentPid == null ? mt.parentPid == null : parentPid
		.equals(mt.parentPid)))
	    return false;
	if (!(fileMimeType == null ? mt.fileMimeType == null : fileMimeType
		.equals(mt.fileMimeType)))
	    return false;
	if (!(fileChecksum == null ? mt.fileChecksum == null : fileChecksum
		.equals(mt.fileChecksum)))
	    return false;
	if (!(fileSize == null ? mt.fileSize == null : fileSize
		.equals(mt.fileSize)))
	    return false;
	if (!(fileLabel == null ? mt.fileLabel == null : fileLabel
		.equals(mt.fileLabel)))
	    return false;

	return true;
    }

    @Override
    public String toString() {
	ObjectMapper mapper = JsonUtil.mapper();
	StringWriter w = new StringWriter();
	try {
	    mapper.writeValue(w, this);
	} catch (Exception e) {
	    e.printStackTrace();
	    return super.toString();
	}
	return w.toString();
    }

    /**
     * @return true if the metadata contains urn
     */
    public boolean hasUrn() {
	String hasUrn = "http://purl.org/lobid/lv#urn";
	try {
	    return RdfUtils.hasTriple(pid, hasUrn, metadata);
	} catch (Exception e) {
	    return false;
	}
    }

    /**
     * @return true if metadata contains catalog id
     */
    public boolean hasLinkToCatalogId() {
	boolean result = RdfUtils.hasTriple(pid, REL_MAB_527, metadata);
	return result;
    }

    /**
     * @return a urn or null
     */
    public String getUrnFromMetadata() {
	try {
	    String hasUrn = "http://purl.org/lobid/lv#urn";
	    return RdfUtils.findRdfObjects(pid, hasUrn, metadata,
		    RDFFormat.NTRIPLES).get(0);
	} catch (Exception e) {
	    return null;
	}
    }

    /**
     * Part of provenance data
     * 
     * @return the old id of the object
     */
    public String getLegacyId() {
	return legacyId;
    }

    /**
     * @param legacyId
     *            an id that once has been used for this object
     * @return this object
     */
    public Node setLegacyId(String legacyId) {
	this.legacyId = legacyId;
	return this;
    }

    /**
     * Part of provenance data
     * 
     * @return a system internal name for the object
     */
    public String getName() {
	return name;
    }

    /**
     * @param name
     * @return this
     */
    public Node setName(String name) {
	this.name = name;
	return this;
    }

    /**
     * Add a text extraction of the nodes data
     * 
     * @param string
     */
    public void addFulltext(String string) {
	fulltext = string;
    }

    /**
     * @return a text extraction of the nodes data
     */
    public String getFulltext() {
	return fulltext;
    }

    /**
     * @return a doi for this particular object
     */
    public String getDoi() {
	return doi;
    }

    /**
     * @param doi
     * @return this
     */
    public Node setDoi(String doi) {
	this.doi = doi;
	return this;
    }

    /**
     * @return an urn for this particular object
     */
    public String getUrn() {
	return urn;
    }

    /**
     * @param urn
     * @return this
     */
    public Node setUrn(String urn) {
	this.urn = urn;
	return this;
    }

    /**
     * @return a map without the context document
     */
    public Map<String, Object> getLdWithoutContext() {
	Map<String, Object> map = getLd();
	map.remove("@context");
	return map;

    }

    /**
     * @return a map without the context document
     */
    public Map<String, Object> getLdWithoutContextShortStyle() {
	Map<String, Object> map = getLdShortStyle();
	map.remove("@context");
	return map;
    }

    /**
     * @param userId
     * @return this
     */
    public Node setLastModifiedBy(String userId) {
	lastModifiedBy = userId;
	return this;
    }

    /**
     * @return user id of last modifying user
     */
    public String getLastModifiedBy() {
	return lastModifiedBy;
    }

    /**
     * @return returns true if doi is not null and not empty
     */
    public boolean hasDoi() {
	String doi = this.getDoi();
	return doi != null && !doi.isEmpty();
    }

    public String getObjectTimestampFile() {
	return objectTimestampFile;
    }

    public void setObjectTimestampFile(String path) {
	this.objectTimestampFile = path;
    }

    public void setObjectTimestamp(String timestamp) {
	objectTimestamp = timestamp;
    }

    public String getObjectTimestamp() {
	return objectTimestamp;
    }

}
