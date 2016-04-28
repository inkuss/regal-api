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

import helper.DataciteClient;
import helper.HttpArchiveException;
import helper.MyEtikettMaker;
import helper.URN;
import helper.oai.OaiDispatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.DublinCoreData;
import models.Globals;
import models.Node;
import models.RegalObject;

import org.openrdf.model.BNode;
import org.openrdf.model.Graph;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.rio.RDFFormat;
import org.w3c.dom.Element;

import archive.fedora.CopyUtils;
import archive.fedora.RdfException;
import archive.fedora.RdfUtils;
import archive.fedora.XmlUtils;
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
    public String updateData(String pid, InputStream content, String mimeType, String name, String md5Hash)
	    throws IOException {
	if (content == null) {
	    throw new HttpArchiveException(406, pid + " you've tried to upload an empty stream."
		    + " This action is not supported. Use HTTP DELETE instead.");
	}
	File tmp = File.createTempFile(name, "tmp");
	tmp.deleteOnExit();
	CopyUtils.copy(content, tmp);
	Node node = new Read().readNode(pid);
	if (node != null) {
	    node.setUploadFile(tmp.getAbsolutePath());
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
		throw new HttpArchiveException(417,
			pid + " expected a MD5 of " + fedoraHash + " but you provided a MD5 value of " + md5Hash);
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
		throw new HttpArchiveException(406, pid + " You've tried to upload an empty string."
			+ " This action is not supported." + " Use HTTP DELETE instead.\n");
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
    public String updateLobidifyAndEnrichMetadata(String pid, String content) {
	try {
	    Node node = new Read().readNode(pid);
	    return updateLobidifyAndEnrichMetadata(node, content);
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
    public String updateLobidifyAndEnrichMetadata(Node node, String content) {

	String pid = node.getPid();
	if (content == null) {
	    throw new HttpArchiveException(406, pid + " You've tried to upload an empty string."
		    + " This action is not supported." + " Use HTTP DELETE instead.\n");
	}
	if (content.contains(archive.fedora.Vocabulary.REL_MAB_527)) {
	    String lobidUri = RdfUtils
		    .findRdfObjects(node.getPid(), archive.fedora.Vocabulary.REL_MAB_527, content, RDFFormat.NTRIPLES)
		    .get(0);
	    String alephid = lobidUri.replaceFirst("http://lobid.org/resource/", "");
	    content = getLobidDataAsNtripleString(node, alephid);
	    updateMetadata(node, content);
	    String enrichMessage = enrichMetadata(node);
	    return pid + " metadata successfully updated, lobidified and enriched! " + enrichMessage;
	} else {
	    play.Logger.info("Enrich " + node.getPid());
	
	    if (content == null || content.isEmpty()) {
		play.Logger.info("Not metadata to enrich " + node.getPid());
		return "Not metadata to enrich " + node.getPid();
	    }
	    List<Statement> enrichStatements = addDataFromGnd(node, content);
	    content = RdfUtils.replaceTriples(enrichStatements, content);
	    updateMetadata(node, content);	    
	    
	    return pid + " metadata successfully updated!";
	}
    }

    private String updateMetadata(Node node, String content) {
	try {
	    String pid = node.getPid();
	    if (content == null) {
		throw new HttpArchiveException(406, pid + " You've tried to upload an empty string."
			+ " This action is not supported." + " Use HTTP DELETE instead.\n");
	    }
	    RdfUtils.validate(content);
	    File file = CopyUtils.copyStringToFile(content);
	    node.setMetadataFile(file.getAbsolutePath());
	    if (content.contains(archive.fedora.Vocabulary.REL_LOBID_DOI)) {
		List<String> dois = RdfUtils.findRdfObjects(node.getPid(), archive.fedora.Vocabulary.REL_LOBID_DOI,
			content, RDFFormat.NTRIPLES);
		if (!dois.isEmpty()) {
		    node.setDoi(dois.get(0));
		}
	    }
	    node.setMetadata(content);
	    OaiDispatcher.makeOAISet(node);
	    reindexNodeAndParent(node);
	    return pid + " metadata successfully updated!";
	} catch (RdfException e) {
	    throw new HttpArchiveException(400, e);
	} catch (IOException e) {
	    throw new UpdateNodeException(e);
	}
    }

    private String getLobidDataAsNtripleString(Node node, String alephid) {
	String pid = node.getPid();
	String lobidUri = "http://lobid.org/resource/" + alephid;
	play.Logger.info("GET " + lobidUri);
	try {
	    URL lobidUrl = new URL("http://lobid.org/resource/" + alephid + "/about");
	    RDFFormat inFormat = RDFFormat.TURTLE;
	    String accept = "text/turtle";
	    Graph graph = RdfUtils.readRdfToGraphAndFollowSameAs(lobidUrl, inFormat, accept);
	    ValueFactory f = RdfUtils.valueFactory;
	    Statement parallelEditionStatement = f.createStatement(f.createURI(pid),
		    f.createURI(archive.fedora.Vocabulary.REL_MAB_527), f.createURI(lobidUri));
	    graph.add(parallelEditionStatement);
	    tryToImportOrderingFromLobidData2(alephid, pid, graph, f);
	    return RdfUtils.graphToString(RdfUtils.rewriteSubject(lobidUri, pid, graph), RDFFormat.NTRIPLES);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}

    }

    private void tryToImportOrderingFromLobidData2(String alephid, String pid, Graph graph, ValueFactory f) {
	try {
	    String ordering = getAuthorOrdering(alephid);
	    if (ordering != null) {
		Statement contributorOrderStatement = f.createStatement(f.createURI(pid),
			f.createURI("http://purl.org/lobid/lv#contributorOrder"), f.createLiteral(ordering));
		graph.add(contributorOrderStatement);
	    }
	} catch (Exception e) {
	    play.Logger.error("Ordering info not available!", e);
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
	if (node.hasUrnInMetadata() || node.hasUrn())
	    throw new HttpArchiveException(409, subject + " already has a urn. Leave unmodified!");
	String urn = generateUrn(subject, snid);
	node.setUrn(urn);
	return OaiDispatcher.makeOAISet(node);
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
	String urn = generateUrn(node.getPid(), snid);
	node.setLastModifiedBy(userId);
	node.setUrn(urn);
	return OaiDispatcher.makeOAISet(node);
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
	return "Not Updated " + n.getPid() + " " + n.getCreationDate() + " is not before " + fromBefore
		+ " or contentType " + contentType + " is not allowed to carry urn.";
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
	    return "Not Updated " + n.getPid() + " " + n.getCreationDate() + " is not before " + fromBefore
		    + " or contentType " + contentType + " is not allowed to carry urn.";
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
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
	return updateLobidifyAndEnrichMetadata(node, node.getMetadata());
    }

    /**
     * reinits oai sets on every node
     * 
     * @param nodes
     *            a list of nodes
     * @return a message
     */
    public String reinitOaiSets(List<Node> nodes) {
	return apply(nodes, n -> OaiDispatcher.makeOAISet(n));
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
	    throw new HttpArchiveException(406, "Can't find valid destiny for move operation. " + node.getParentPid()
		    + " parent of " + node.getPid() + " has no further parent.");
	RegalObject object = new RegalObject();
	object.setParentPid(destinyPid);
	node = new Create().patchResource(node, object);
	play.Logger.info("Move " + node.getPid() + " to new parent " + node.getParentPid() + ". Recent Parent was "
		+ recentParent + ". Calculated destiny was " + destinyPid);
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
	play.Logger
		.debug("Try to enrich " + node.getPid() + " with " + parent.getPid() + " . Looking for field " + field);
	String pred = getUriFromJsonName(field);
	List<String> value = RdfUtils.findRdfObjects(subject, pred, parent.getMetadata(), RDFFormat.NTRIPLES);
	String metadata = node.getMetadata();
	if (metadata == null)
	    metadata = "";
	if (value != null && !value.isEmpty()) {
	    metadata = RdfUtils.replaceTriple(subject, pred, value.get(0), true, metadata);
	} else {
	    throw new HttpArchiveException(406, "Source object " + copySource + " has no field: " + field);
	}
	updateLobidifyAndEnrichMetadata(node, metadata);
	return node;
    }

    public String enrichMetadata(Node node) {
	try {
	    play.Logger.info("Enrich " + node.getPid());
	    String metadata = node.getMetadata();
	    if (metadata == null || metadata.isEmpty()) {
		play.Logger.info("Not metadata to enrich " + node.getPid());
		return "Not metadata to enrich " + node.getPid();
	    }
	    List<Statement> enrichStatements = addDataFromGnd(node, metadata);

	    play.Logger.info("Enrich " + node.getPid() + " with institution.");
	    List<Statement> institutions = findInstitution(node);
	    enrichStatements.addAll(institutions);

	    play.Logger.info("Enrich " + node.getPid() + " with parent.");
	    List<Statement> catalogParents = findParents(node, metadata);
	    enrichStatements.addAll(catalogParents);
	    metadata = RdfUtils.replaceTriples(enrichStatements, metadata);
	    updateMetadata(node, metadata);
	    
	} catch (Exception e) {
	    play.Logger.debug("", e);
	    return "Enrichment of " + node.getPid() + " partially failed !\n" + e.getMessage();
	}
	return "Enrichment of " + node.getPid() + " succeeded!";
    }

    private List<Statement> addDataFromGnd(Node node, String metadata) {
	play.Logger.info("Enrich " + node.getPid() + " with gnd.");
	List<String> gndIds = findAllGndIds(metadata);
	List<Statement> enrichStatements = new ArrayList<Statement>();
	for (String uri : gndIds) {
	enrichStatements.addAll(getStatements(uri));
	}
	return enrichStatements;
    }

    private List<Statement> findParents(Node node, String metadata) {
	List<Statement> catalogParents = new ArrayList<Statement>();
	// getIsPartOf
	List<String> parents = RdfUtils.findRdfObjects(node.getPid(), "http://purl.org/dc/terms/isPartOf", metadata,
		RDFFormat.NTRIPLES);
	for (String p : parents) {
	    ValueFactory v = new ValueFactoryImpl();
	    String label = getEtikett(p);
	    Statement st = v.createStatement(v.createURI(p),
		    v.createURI("http://www.w3.org/2004/02/skos/core#prefLabel"),
		    v.createLiteral(Normalizer.normalize(label, Normalizer.Form.NFKC)));
	    catalogParents.add(st);
	}
	return catalogParents;
    }

    private String getEtikett(String p) {
	String prefLabel = MyEtikettMaker.getLabelFromEtikettWs(p);
	return prefLabel;
    }

    private List<Statement> findInstitution(Node node) {
	try {
	    String alephid = new Read().getIdOfParallelEdition(node);
	    String uri = Globals.lobidAddress + alephid + "/about?format=source";
	    play.Logger.info("GET " + uri);
	    try (InputStream in = RdfUtils.urlToInputStream(new URL(uri), "application/xml")) {
		String gndEndpoint = "http://d-nb.info/gnd/";
		List<Element> institutionHack = XmlUtils
			.getElements("//datafield[@tag='078' and @ind1='r' and @ind2='1']/subfield", in, null);
		List<Statement> result = new ArrayList<Statement>();
		for (Element el : institutionHack) {
		    String marker = el.getTextContent();
		    if (!marker.contains("ellinet"))
			continue;
		    if (!marker.contains("GND"))
			continue;
		    String gndId = gndEndpoint + marker.replaceFirst(".*ellinet.*GND:.*\\([^)]*\\)", "");
		    if (gndId.endsWith("16269969-4")) {
			gndId = gndEndpoint + "2006655-7";
		    }
		    play.Logger.debug("Add data from " + gndId);
		    ValueFactory v = new ValueFactoryImpl();
		    Statement link = v.createStatement(v.createURI(node.getPid()),
			    v.createURI("http://dbpedia.org/ontology/institution"), v.createURI(gndId));
		    result.add(link);
		    result.addAll(getStatements(gndId));
		}
		return result;
	    }
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);

	}
    }

    private List<Statement> getStatements(String uri) {
	play.Logger.info("GET " + uri);
	List<Statement> filteredStatements = new ArrayList<Statement>();
	try {
	    for (Statement s : RdfUtils.readRdfToGraph(new URL(uri + "/about/lds"), RDFFormat.RDFXML,
		    "application/rdf+xml")) {
		boolean isLiteral = s.getObject() instanceof Literal;
		if (!(s.getSubject() instanceof BNode)) {
		    if (isLiteral) {
			ValueFactory v = new ValueFactoryImpl();
			Statement newS = v.createStatement(v.createURI(uri), s.getPredicate(), v.createLiteral(
				Normalizer.normalize(s.getObject().stringValue(), Normalizer.Form.NFKC)));
			filteredStatements.add(newS);
		    }
		}
	    }
	} catch (Exception e) {
	    play.Logger.warn("Not able to get data from" + uri);
	}
	return filteredStatements;
    }

    private List<String> findAllGndIds(String metadata) {
	HashMap<String, String> result = new HashMap<String, String>();
	Matcher m = Pattern.compile("http://d-nb.info/gnd/[1234567890-]*").matcher(metadata);
	while (m.find()) {
	    String id = m.group();
	    result.put(id, id);
	}
	return new Vector<String>(result.keySet());
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
	if ("file".equals(contentType) || "issue".equals(contentType) || "volume".equals(contentType)) {
	    throw new HttpArchiveException(412, node.getPid() + " resource is of type " + contentType
		    + ". It is not allowed to mint Dois for this type. Leave unmodified!");
	}
	Map<String, Object> result = new HashMap<String, Object>();
	String doi = node.getDoi();

	if (doi == null || doi.isEmpty()) {
	    doi = createDoiIdentifier(node);
	    result.put("Doi", doi);
	    // node.setDoi(doi);
	    String objectUrl = Globals.urnbase + node.getPid();
	    String xml = new Transform().datacite(node, doi);
	    MyController.validate(xml, "public/schemas/datacite.xsd");
	    try {
		DataciteClient client = new DataciteClient();
		result.put("Metadata", xml);
		String registerMetadataResponse = client.registerMetadataAtDatacite(node, xml);
		result.put("registerMetadataResponse", registerMetadataResponse);
		if (client.getStatus() != 200)
		    throw new RuntimeException("Registering Doi failed!");
		String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
		result.put("mintDoiResponse", mintDoiResponse);
		if (client.getStatus() != 200)
		    throw new RuntimeException("Minting Doi failed!");

	    } catch (Exception e) {
		throw new HttpArchiveException(502,
			node.getPid() + " Add Doi failed!\n" + result + "\n Datacite replies: " + e.getMessage());
	    }
	    RegalObject o = new RegalObject();
	    o.getIsDescribedBy().setDoi(doi);
	    new Create().patchResource(node, o);
	    return result;
	} else {
	    throw new HttpArchiveException(409, node.getPid() + " already has a doi. Leave unmodified! ");
	}

    }

    /**
     * Creates a new doi identifier and registers to datacite
     * 
     * @param node
     * @return a key value structure as feedback to the client
     */
    public Map<String, Object> replaceDoi(Node node) {
	node.setDoi(null);
	return addDoi(node);
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
	    throw new HttpArchiveException(412, node.getPid()
		    + " resource is not associated to doi. Please create a doi first (POST /doi).  Leave unmodified!");
	} else {
	    String objectUrl = Globals.urnbase + node.getPid();
	    String xml = new Transform().datacite(node, doi);
	    MyController.validate(xml, "public/schemas/datacite.xsd");
	    DataciteClient client = new DataciteClient();
	    String registerMetadataResponse = client.registerMetadataAtDatacite(node, xml);
	    String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
	    String makeOaiSetResponse = OaiDispatcher.makeOAISet(node);
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

    /**
     * @param node
     *            the node to add a conf to
     * @param content
     *            json representation of conf
     * @return a message
     */
    public String updateConf(Node node, String content) {
	try {
	    if (content == null) {
		throw new HttpArchiveException(406, node.getPid() + " You've tried to upload an empty string."
			+ " This action is not supported." + " Use HTTP DELETE instead.\n");
	    }
	    play.Logger.info("Write to conf: " + content);
	    File file = CopyUtils.copyStringToFile(content);
	    if (node != null) {
		node.setConfFile(file.getAbsolutePath());
		play.Logger.info("Update node" + file.getAbsolutePath());
		Globals.fedora.updateNode(node);
	    }
	    updateIndex(node.getPid());
	    return node.getPid() + " webgatherer conf updated!";
	} catch (RdfException e) {
	    throw new HttpArchiveException(400, e);
	} catch (IOException e) {
	    throw new UpdateNodeException(e);
	}
    }

    /**
     * the node
     * 
     * @param pred
     *            Rdf-Predicate will be added to /metadata of node
     * @param obj
     *            Rdf-Object will be added to /metadata of node
     * @return a user message as string
     */
    public String addMetadataField(Node node, String pred, String obj) {
	String metadata = node.getMetadata();
	metadata = RdfUtils.addTriple(node.getPid(), pred, obj, true, metadata, RDFFormat.NTRIPLES);
	updateLobidifyAndEnrichMetadata(node, metadata);
	node = new Read().readNode(node.getPid());
	OaiDispatcher.makeOAISet(node);
	return "Update " + node.getPid() + "! " + pred + " has been added.";

    }

    /**
     * @param node
     *            what was modified?
     * @param date
     *            when was modified?
     * @param userId
     *            who has modified?
     * @return a user message in form of a map
     */
    public Map<String, Object> setObjectTimestamp(Node node, Date date, String userId) {
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

    public String lobidify(Node node, String alephid) {
	return updateMetadata(node, getLobidDataAsNtripleString(node, alephid));
    }

    public String getAuthorOrdering(String alephid) {
	try {
	    URL lobidUrl = new URL("http://gaia.hbz-nrw.de:9200/resources/_all/" + alephid + "/_source");
	    RDFFormat inFormat = RDFFormat.JSONLD;
	    String accept = "application/json";
	    Graph myGraph = RdfUtils.readRdfToGraph(lobidUrl, inFormat, accept);
	    Iterator<Statement> statements = myGraph.iterator();
	    while (statements.hasNext()) {
		Statement curStatement = statements.next();
		String pred = curStatement.getPredicate().stringValue();
		String obj = curStatement.getObject().stringValue();
		if ("http://purl.org/lobid/lv#contributorOrder".equals(pred)) {
		    return obj;
		}
	    }
	    return null;
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }
}
