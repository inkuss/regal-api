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

import static archive.fedora.FedoraVocabulary.IS_MEMBER_OF;
import static archive.fedora.FedoraVocabulary.ITEM_ID;
import helper.DataciteClient;
import helper.HttpArchiveException;
import helper.OaiSet;
import helper.OaiSetBuilder;
import helper.URN;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.DublinCoreData;
import models.Globals;
import models.Link;
import models.Node;
import models.Pair;
import models.RegalObject;
import models.Transformer;

import org.openrdf.model.Statement;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;

import archive.fedora.CopyUtils;
import archive.fedora.RdfException;
import archive.fedora.RdfUtils;
import controllers.MyController;

/**
 * @author Jan Schnasse
 *
 */
public class Modify extends RegalAction {

    /**
     * @param pid
     *            the pid that must be updated
     * @param content
     *            the file content as byte array
     * @param mimeType
     *            the mimetype of the file
     * @param name
     *            the name of the file
     * @param md5Hash
     *            a hash for the content. Can be null.
     * @return A short message
     * @throws IOException
     *             if data can not be written to a tmp file
     */
    public String updateData(String pid, InputStream content, String mimeType,
	    String name, String md5Hash) throws IOException {
	if (content == null) {
	    throw new HttpArchiveException(406, pid
		    + " you've tried to upload an empty stream."
		    + " This action is not supported. Use HTTP DELETE instead.");
	}
	File tmp = File.createTempFile(name, "tmp");
	tmp.deleteOnExit();
	CopyUtils.copy(content, tmp);
	Node node = new Read().readNode(pid);
	if (node != null) {
	    node.setUploadData(tmp.getAbsolutePath(), mimeType);
	    node.setFileLabel(name);
	    node.setMimeType(mimeType);
	    Globals.fedora.updateNode(node);
	} else {
	    throw new HttpArchiveException(500, "Lost Node!");
	}
	node = updateIndex(pid);
	if (md5Hash != null && !md5Hash.isEmpty()) {

	    String fedoraHash = node.getChecksum();
	    if (!md5Hash.equals(fedoraHash)) {
		throw new HttpArchiveException(417, pid + " expected a MD5 of "
			+ fedoraHash + " but you provided a MD5 value of "
			+ md5Hash);
	    }
	}
	return pid + " data successfully updated!";
    }

    /**
     * @param pid
     *            The pid that must be updated
     * @param dc
     *            A dublin core object
     * @return a short message
     */
    public String updateDC(String pid, DublinCoreData dc) {
	Node node = new Read().readNode(pid);
	node.setDublinCoreData(dc);
	Globals.fedora.updateNode(node);
	updateIndex(node.getPid());
	return pid + " dc successfully updated!";
    }

    /**
     * @param pid
     *            the node's pid
     * @param content
     *            a json array to provide ordering information about the
     *            object's children
     * @return a message
     */
    public String updateSeq(String pid, String content) {
	try {
	    if (content == null) {
		throw new HttpArchiveException(406, pid
			+ " You've tried to upload an empty string."
			+ " This action is not supported."
			+ " Use HTTP DELETE instead.\n");
	    }
	    play.Logger.info(content);
	    File file = CopyUtils.copyStringToFile(content);
	    Node node = new Read().readNode(pid);
	    if (node != null) {
		node.setSeqFile(file.getAbsolutePath());
		Globals.fedora.updateNode(node);
	    }
	    updateIndex(node.getPid());
	    return pid + " sequence of child objects updated!";
	} catch (RdfException e) {
	    throw new HttpArchiveException(400, e);
	} catch (IOException e) {
	    throw new UpdateNodeException(e);
	}
    }

    /**
     * @param pid
     *            The pid that must be updated
     * @param content
     *            The metadata as rdf string
     * @return a short message
     */
    public String updateMetadata(String pid, String content) {
	try {
	    Node node = new Read().readNode(pid);
	    return updateMetadata(node, content);
	} catch (Exception e) {
	    throw new UpdateNodeException(e);
	}
    }

    /**
     * @param node
     *            The node that must be updated
     * @param content
     *            The metadata as rdf string
     * @return a short message
     */
    String updateMetadata(Node node, String content) {
	try {
	    String pid = node.getPid();
	    if (content == null) {
		throw new HttpArchiveException(406, pid
			+ " You've tried to upload an empty string."
			+ " This action is not supported."
			+ " Use HTTP DELETE instead.\n");
	    }
	    RdfUtils.validate(content);

	    File file = CopyUtils.copyStringToFile(content);
	    node.setMetadataFile(file.getAbsolutePath());

	    if (content.contains(archive.fedora.Vocabulary.REL_LOBID_DOI)) {
		List<String> dois = RdfUtils.findRdfObjects(node.getPid(),
			archive.fedora.Vocabulary.REL_LOBID_DOI, content,
			RDFFormat.NTRIPLES);
		if (!dois.isEmpty()) {
		    node.setDoi(dois.get(0));
		}
	    }
	    Globals.fedora.updateNode(node);
	    reindexNodeAndParent(node);
	    return pid + " metadata successfully updated!";
	} catch (RdfException e) {
	    throw new HttpArchiveException(400, e);
	} catch (IOException e) {
	    throw new UpdateNodeException(e);
	}
    }

    private void reindexNodeAndParent(Node node) {
	node = updateIndex(node.getPid());
	String parentPid = node.getParentPid();
	if (parentPid != null && !parentPid.isEmpty()) {
	    updateIndex(parentPid);
	}
    }

    /**
     * Generates a urn
     * 
     * @param id
     *            usually the id of an object without namespace
     * @param namespace
     *            usually the namespace
     * @param snid
     *            the urn subnamespace id e.g."hbz:929:02"
     * @param userId
     * @return the urn
     */
    public String addUrn(String id, String namespace, String snid, String userId) {
	String pid = namespace + ":" + id;
	return addUrn(pid, snid, userId);
    }

    /**
     * Generates a urn
     * 
     * @param pid
     *            usually the id of an object with namespace
     * @param snid
     *            the urn subnamespace id e.g."hbz:929:02"
     * @return the urn
     */
    String addUrn(String pid, String snid, String userId) {
	Node node = new Read().readNode(pid);
	node.setLastModifiedBy(userId);
	return addUrn(node, snid);
    }

    /**
     * Generates a urn
     * 
     * @param node
     *            the node to add a urn to
     * @param snid
     *            the urn subnamespace id e.g."hbz:929:02"
     * @return the urn
     */
    String addUrn(Node node, String snid) {
	String subject = node.getPid();
	if (node.hasUrn())
	    throw new HttpArchiveException(409, subject
		    + " already has a urn. Leave unmodified!");
	String hasUrn = "http://purl.org/lobid/lv#urn";
	String urn = generateUrn(subject, snid);
	node.addTransformer(new Transformer("epicur"));
	return addMetadataField(node, hasUrn, urn);
    }

    /**
     * Generates a urn
     * 
     * @param node
     *            the object
     * @param snid
     *            the urn subnamespace id
     * @param userId
     * @return the urn
     */
    public String replaceUrn(Node node, String snid, String userId) {
	String subject = node.getPid();
	String hasUrn = "http://purl.org/lobid/lv#urn";
	String metadata = node.getMetadata();
	String urn = generateUrn(subject, snid);
	metadata = RdfUtils.replaceTriple(subject, hasUrn, urn, true, metadata);
	node.addTransformer(new Transformer("epicur"));
	node.setLastModifiedBy(userId);
	updateMetadata(node, metadata);
	return "Update " + subject + " metadata " + metadata;
    }

    /**
     * @param nodes
     *            a list of nodes
     * @param snid
     *            a urn snid e.g."hbz:929:02"
     * @param fromBefore
     *            only objects created before "fromBefore" will get a urn
     * @return a message
     */
    public String addUrnToAll(List<Node> nodes, String snid, Date fromBefore) {
	return apply(nodes, n -> addUrn(n, snid, fromBefore));
    }

    private String addUrn(Node n, String snid, Date fromBefore) {
	String contentType = n.getContentType();
	if (n.getCreationDate().before(fromBefore)) {
	    if ("journal".equals(contentType)) {
		return addUrn(n, snid);
	    } else if ("monograph".equals(contentType)) {
		return addUrn(n, snid);
	    } else if ("file".equals(contentType)) {
		return addUrn(n, snid);
	    }
	}
	return "Not Updated " + n.getPid() + " " + n.getCreationDate()
		+ " is not before " + fromBefore + " or contentType "
		+ contentType + " is not allowed to carry urn.";
    }

    /**
     * @param nodes
     *            a list of nodes
     * @param fromBefore
     *            only nodes from before the given Date will be modified
     * @return a message for client
     */
    public String addDoiToAll(List<Node> nodes, Date fromBefore) {
	return apply(nodes, n -> addDoi(n, fromBefore));
    }

    private String addDoi(Node n, Date fromBefore) {
	try {
	    String contentType = n.getContentType();
	    if (n.getCreationDate().before(fromBefore)) {
		if ("monograph".equals(contentType)) {
		    return MyController.mapper.writeValueAsString(addDoi(n));
		}
	    }
	    return "Not Updated " + n.getPid() + " " + n.getCreationDate()
		    + " is not before " + fromBefore + " or contentType "
		    + contentType + " is not allowed to carry urn.";
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    /**
     * @param node
     *            the node to be published on the oai interface
     * @return A short message.
     */
    public String makeOAISet(Node node) {
	try {
	    String pid = node.getPid();
	    OaiSetBuilder oaiSetBuilder = new OaiSetBuilder();
	    RepositoryResult<Statement> statements = RdfUtils.getStatements(
		    node.getMetadata(), "fedora:info/");
	    while (statements.hasNext()) {
		Statement st = statements.next();
		String subject = st.getSubject().stringValue();
		String predicate = st.getPredicate().stringValue();
		String object = st.getObject().stringValue();

		OaiSet set = oaiSetBuilder.getSet(subject, predicate, object);
		if (set == null) {
		    continue;
		}
		if (!Globals.fedora.nodeExists(set.getPid())) {
		    createOAISet(set.getName(), set.getSpec(), set.getPid());
		}
		linkObjectToOaiSet(node, set.getSpec(), set.getPid());
	    }

	    if ("public".equals(node.getAccessScheme())) {
		addSet(node, "open_access");
	    }
	    if (node.hasUrn()) {
		addSet(node, "epicur");
		String urn = node.getUrnFromMetadata();
		if (urn.startsWith("urn:nbn:de:hbz:929:01")) {
		    addSet(node, "urn-set-1");
		} else if (urn.startsWith("urn:nbn:de:hbz:929:02")) {
		    addSet(node, "urn-set-2");
		}
	    }
	    if (node.hasLinkToCatalogId()) {
		play.Logger.info(node.getPid() + " add aleph set!");
		addSet(node, "aleph");
		addSet(node, Globals.alephSetName);
	    }
	    addSet(node, node.getContentType());
	    updateIndex(node.getPid());
	    return pid + " successfully created oai sets!";
	} catch (Exception e) {
	    throw new MetadataNotFoundException(e);
	}

    }

    private void addSet(Node node, String name) {
	String spec = name;
	String namespace = "oai";
	String oaipid = namespace + ":" + name;
	if (!Globals.fedora.nodeExists(oaipid)) {
	    createOAISet(name, spec, oaipid);
	}
	linkObjectToOaiSet(node, spec, oaipid);
    }

    private void linkObjectToOaiSet(Node node, String spec, String pid) {
	node.removeRelations(ITEM_ID);
	node.removeRelation(IS_MEMBER_OF, "info:fedora/" + pid);
	Link link = new Link();
	link.setPredicate(IS_MEMBER_OF);
	link.setObject("info:fedora/" + pid, false);
	node.addRelation(link);
	link = new Link();
	link.setPredicate(ITEM_ID);
	link.setObject("info:fedora/" + node.getPid(), false);
	node.addRelation(link);
	Globals.fedora.updateNode(node);
    }

    private void createOAISet(String name, String spec, String pid) {
	String setSpecPred = "http://www.openarchives.org/OAI/2.0/setSpec";
	String setNamePred = "http://www.openarchives.org/OAI/2.0/setName";
	Link setSpecLink = new Link();
	setSpecLink.setPredicate(setSpecPred);
	Link setNameLink = new Link();
	setNameLink.setPredicate(setNamePred);
	String namespace = "oai";
	{
	    Node oaiset = new Node();
	    oaiset.setNamespace(namespace);
	    oaiset.setPID(pid);
	    setSpecLink.setObject(spec, true);
	    oaiset.addRelation(setSpecLink);
	    setNameLink.setObject(name, true);
	    oaiset.addRelation(setNameLink);
	    DublinCoreData dc = oaiset.getDublinCoreData();
	    dc.addTitle(name);
	    oaiset.setDublinCoreData(dc);
	    Globals.fedora.createNode(oaiset);
	}
    }

    /**
     * Generates a urn
     * 
     * @param niss
     *            usually the pid of an object
     * @param snid
     *            usually the namespace
     * @return the urn
     */
    String generateUrn(String niss, String snid) {
	URN urn = new URN(snid, niss);
	return urn.toString();
    }

    /**
     * @param node
     *            generate metadatafile with lobid data for this node
     * @return a short message
     */
    public String lobidify(Node node) {
	String pid = node.getPid();
	List<Pair<String, String>> identifier = node.getDublinCoreData()
		.getIdentifier();
	String alephid = "";
	for (Pair<String, String> id : identifier) {
	    if (id.getLeft().startsWith("TT") || id.getLeft().startsWith("HT")) {
		alephid = id.getLeft();
		break;
	    }
	}
	if (alephid.isEmpty()) {
	    throw new HttpArchiveException(500, pid + " no Catalog-Id found");
	}
	String lobidUri = "http://lobid.org/resource/" + alephid;
	try {
	    URL lobidUrl = new URL("http://lobid.org/resource/" + alephid
		    + "/about");
	    RDFFormat inFormat = RDFFormat.TURTLE;
	    String accept = "text/turtle";
	    String str = RdfUtils.readRdfToString(lobidUrl, inFormat,
		    RDFFormat.NTRIPLES, accept);
	    if (str.contains("http://www.w3.org/2002/07/owl#sameAs")) {
		str = RdfUtils.followSameAsAndInclude(lobidUrl, pid, inFormat,
			accept);
	    }
	    str = Pattern.compile(lobidUri).matcher(str)
		    .replaceAll(Matcher.quoteReplacement(pid))
		    + "<"
		    + pid
		    + "> <"
		    + archive.fedora.Vocabulary.REL_MAB_527
		    + "> <" + lobidUri + "> .";

	    return updateMetadata(node, str);

	} catch (MalformedURLException e) {
	    throw new HttpArchiveException(500, e);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}

    }

    /**
     * reinits oai sets on every node
     * 
     * @param nodes
     *            a list of nodes
     * @return a message
     */
    public String reinitOaiSets(List<Node> nodes) {
	return apply(nodes, n -> makeOAISet(n));
    }

    /**
     * Imports lobid metadata for each node in the list
     * 
     * @param nodes
     *            list of nodes
     * @return a message
     */
    public String lobidify(List<Node> nodes) {
	return apply(nodes, n -> lobidify(n));
    }

    /**
     * @param node
     *            links the node to it's parents parent.
     * @return the updated node
     */
    public Node moveUp(Node node) {
	String recentParent = node.getParentPid();
	Node parent = new Read().readNode(recentParent);
	String destinyPid = parent.getParentPid();
	if (destinyPid == null || destinyPid.isEmpty())
	    throw new HttpArchiveException(406,
		    "Can't find valid destiny for move operation. "
			    + node.getParentPid() + " parent of "
			    + node.getPid() + " has no further parent.");
	RegalObject object = new RegalObject();
	object.setParentPid(destinyPid);
	node = new Create().patchResource(node, object);

	play.Logger.info("Move " + node.getPid() + " to new parent "
		+ node.getParentPid() + ". Recent Parent was " + recentParent
		+ ". Calculated destiny was " + destinyPid);
	return node;
    }

    /**
     * @param node
     *            the node is the target of the copy operation
     * @param field
     *            defines which metadata field to copy
     * @param copySource
     *            the pid of the source of the copy operation
     * @return the updated node
     */
    public Node copyMetadata(Node node, String field, String copySource) {
	if (copySource.isEmpty()) {
	    copySource = node.getParentPid();
	}
	Node parent = new Read().readNode(copySource);
	String subject = node.getPid();
	play.Logger.debug("Try to enrich " + node.getPid() + " with "
		+ parent.getPid() + " . Looking for field " + field);
	String pred = Globals.profile.nMap.get(field).uri;
	List<String> value = RdfUtils.findRdfObjects(subject, pred,
		parent.getMetadata(), RDFFormat.NTRIPLES);
	String metadata = node.getMetadata();
	if (metadata == null)
	    metadata = "";
	if (value != null && !value.isEmpty()) {
	    metadata = RdfUtils.replaceTriple(subject, pred, value.get(0),
		    true, metadata);
	} else {
	    throw new HttpArchiveException(406, "Source object " + copySource
		    + " has no field: " + field);
	}
	updateMetadata(node, metadata);
	return node;
    }

    /**
     * @param nodes
     *            a list of nodes to hammer on
     * @return a message
     */
    public String flattenAll(List<Node> nodes) {
	return apply(nodes, n -> flatten(n).getPid());
    }

    /**
     * Flatten a node means to take the title of the parent and to move up the
     * node by one level in the object tree
     * 
     * @param n
     *            the node to hammer on
     * @return the updated node
     */
    public Node flatten(Node n) {
	return moveUp(copyMetadata(n, "title", ""));
    }

    @SuppressWarnings({ "serial" })
    class MetadataNotFoundException extends RuntimeException {
	MetadataNotFoundException(Throwable e) {
	    super(e);
	}
    }

    @SuppressWarnings({ "serial" })
    private class UpdateNodeException extends RuntimeException {
	UpdateNodeException(Throwable cause) {
	    super(cause);
	}
    }

    /**
     * Creates a new doi identifier and registers to datacite
     * 
     * @param node
     * @return a key value structure as feedback to the client
     */
    public Map<String, Object> addDoi(Node node) {
	String contentType = node.getContentType();
	if ("file".equals(contentType) || "issue".equals(contentType)
		|| "volume".equals(contentType)) {
	    throw new HttpArchiveException(
		    412,
		    node.getPid()
			    + " resource is of type "
			    + contentType
			    + ". It is not allowed to mint Dois for this type. Leave unmodified!");
	}
	Map<String, Object> result = new HashMap<String, Object>();
	String doi = node.getDoi();
	result.put("Doi", doi);
	if (doi == null || doi.isEmpty()) {
	    doi = createDoiIdentifier(node);
	    node.setDoi(doi);
	    RegalObject o = new RegalObject();
	    o.getIsDescribedBy().setDoi(node.getDoi());
	    new Create().patchResource(node, o);
	    String objectUrl = Globals.urnbase + node.getPid();
	    String xml = new Transform().datacite(node);
	    play.Logger.debug(xml);
	    DataciteClient client = new DataciteClient();
	    String registerMetadataResponse = client
		    .registerMetadataAtDatacite(node, xml);
	    String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
	    String makeOaiSetResponse = makeOAISet(node);

	    result.put("Metadata", xml);
	    result.put("registerMetadataResponse", registerMetadataResponse);
	    result.put("mintDoiResponse", mintDoiResponse);
	    result.put("makeOaiSetResponse", makeOaiSetResponse);
	    return result;
	} else {
	    throw new HttpArchiveException(409, node.getPid()
		    + " already has a doi. Leave unmodified!");
	}

    }

    /**
     * Updates an existing doi's metadata and url
     * 
     * @param node
     * @return a key value structure as feedback to the client
     */
    public Map<String, Object> updateDoi(Node node) {
	Map<String, Object> result = new HashMap<String, Object>();
	String doi = node.getDoi();
	result.put("Doi", doi);
	if (doi == null || doi.isEmpty()) {
	    throw new HttpArchiveException(
		    412,
		    node.getPid()
			    + " resource is not associated to doi. Please create a doi first (POST /doi).  Leave unmodified!");
	} else {
	    String objectUrl = Globals.urnbase + node.getPid();
	    String xml = new Transform().datacite(node);
	    play.Logger.debug(xml);
	    DataciteClient client = new DataciteClient();
	    String registerMetadataResponse = client
		    .registerMetadataAtDatacite(node, xml);
	    String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
	    String makeOaiSetResponse = makeOAISet(node);
	    result.put("Metadata", xml);
	    result.put("registerMetadataResponse", registerMetadataResponse);
	    result.put("mintDoiResponse", mintDoiResponse);
	    result.put("makeOaiSetResponse", makeOaiSetResponse);
	    return result;
	}
    }

    private String createDoiIdentifier(Node node) {
	String pid = node.getPid();
	String id = pid.replace(node.getNamespace() + ":", "");
	String doi = Globals.doiPrefix + "00" + id;
	return doi;
    }

    public String addMetadataField(Node node, String pred, String obj) {
	String metadata = node.getMetadata();
	metadata = RdfUtils.addTriple(node.getPid(), pred, obj, true, metadata,
		RDFFormat.NTRIPLES);
	updateMetadata(node, metadata);
	node = new Read().readNode(node.getPid());
	makeOAISet(node);
	return "Update " + node.getPid() + "! " + pred + " has been added.";

    }

    public Map<String, Object> setObjectTimestamp(Node node, Date date,
	    String userId) {
	Map<String, Object> result = new HashMap<String, Object>();
	try {
	    String content = Globals.dateFormat.format(date);
	    File file = CopyUtils.copyStringToFile(content);
	    node.setObjectTimestampFile(file.getAbsolutePath());
	    node.setLastModifiedBy(userId);
	    result.put("pid", node.getPid());
	    result.put("timestamp", content);
	    Globals.fedora.updateNode(node);
	    String pp = node.getParentPid();
	    if (pp != null) {
		Node parent = new Read().readNode(pp);
		result.put("parent", setObjectTimestamp(parent, date, userId));
	    }
	    updateIndex(node.getPid());
	    return result;
	} catch (IOException e) {
	    throw new HttpArchiveException(500, e);
	}
    }
}
