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

import static archive.fedora.FedoraVocabulary.HAS_PART;
import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.Vocabulary.REL_CONTENT_TYPE;
import static archive.fedora.Vocabulary.REL_IS_NODE_TYPE;
import static archive.fedora.Vocabulary.TYPE_OBJECT;
import helper.HttpArchiveException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

import models.DublinCoreData;
import models.Globals;
import models.Link;
import models.Node;
import models.Urn;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.openrdf.rio.RDFFormat;
import org.w3c.dom.Element;

import play.Logger;
import archive.fedora.FedoraVocabulary;
import archive.fedora.RdfUtils;
import archive.fedora.UrlConnectionException;
import archive.fedora.XmlUtils;

/**
 * 
 * @author Jan Schnasse
 *
 */
public class Read extends RegalAction {

    /**
     * @param pid
     *            the will be read to the node
     * @return a Node containing the data from the repository
     */
    public Node readNode(String pid) {
	Node n = internalReadNode(pid);
	if ("D".equals(n.getState())) {
	    throw new HttpArchiveException(410, "The requested resource " + pid
		    + " has been marked as deleted.");
	}
	addLabelsForParts(n);
	writeNodeToCache(n);
	return n;
    }

    /**
     * returns the lastModified child of the whole tree
     * 
     * @param node
     * @param contentType
     *            if set, only parts of specific type will be analysed
     * @return node
     */
    public Node getLastModifiedChild(Node node, String contentType) {
	if (contentType == null || contentType.isEmpty()) {
	    return getLastModifiedChild(node);
	} else {
	    Node last = null;
	    for (Node n : getParts(node)) {
		Date cur = n.getLastModified();
		if (contentType.equals(n.getContentType())) {
		    if (last != null) {
			if (cur.after(last.getLastModified())) {
			    last = n;
			}
		    } else {
			last = n;
		    }
		}
	    }
	    if (last == null)
		return node;
	    return last;
	}
    }

    /**
     * @param node
     * @return child recently modified
     */
    public Node getLastModifiedChild(Node node) {
	Node last = node;
	for (Node n : getParts(node)) {
	    Date cur = n.getLastModified();
	    if (cur.after(last.getLastModified())) {
		last = n;
	    }
	}
	return last;
    }

    /**
     * @param pid
     *            the will be read to the node
     * @return a Node containing the data from the repository
     */
    public Node internalReadNode(String pid) {
	Node n = readNodeFromCache(pid);
	if (n != null) {
	    return n;
	}
	n = Globals.fedora.readNode(pid);
	n.setAggregationUri(createAggregationUri(n.getPid()));
	n.setRemUri(n.getAggregationUri() + ".rdf");
	n.setDataUri(n.getAggregationUri() + "/data");
	n.setContextDocumentUri("http://" + Globals.server
		+ "/public/edoweb-resources.json");
	writeNodeToCache(n);
	return n;
    }

    void addLabelsForParts(Node n) {
	List<Link> rels = n.getRelsExt();
	for (Link l : rels) {
	    if (HAS_PART.equals(l.getPredicate())
		    || IS_PART_OF.equals(l.getPredicate())) {
		addLabel(n, l);
	    }
	}
    }

    private void addLabel(Node n, Link l) {
	try {
	    String label = readMetadata(l.getObject(), "title");
	    l.setObjectLabel(label);
	    n.removeRelation(l.getPredicate(), l.getObject());
	    n.addRelation(l);
	} catch (Exception e) {

	}
    }

    /**
     * @param pid
     *            the pid of the node
     * @return a Map that represents the node
     */
    public Map<String, Object> readNodeFromIndex(String pid) {
	return Globals.search.get(pid);
    }

    /**
     * @param node
     * @return all parts and their parts recursively
     */
    public List<Node> getParts(Node node) {
	List<Node> result = new ArrayList<Node>();
	result.add(node);
	List<Node> parts = getNodes(node.getPartsSorted().stream()
		.map((Link l) -> l.getObject()).collect(Collectors.toList()));
	for (Node p : parts) {
	    result.addAll(getParts(p));
	}
	return result;
    }

    /**
     * @param node
     *            a regal node
     * @return a tree of regal objects starting with the passed node as root
     */
    public Map<String, Object> getPartsAsTree(Node node, String style) {
	Map<String, Object> nm = null;
	if ("short".equals(style)) {
	    nm = node.getLdWithoutContextShortStyle();
	} else {
	    nm = node.getLdWithoutContext();
	}
	@SuppressWarnings("unchecked")
	List<Map<String, Object>> parts = (List<Map<String, Object>>) nm
		.get("hasPart");
	List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
	if (parts != null) {
	    for (Map<String, Object> part : parts) {
		String id = (String) part.get("@id");
		Map<String, Object> child = new HashMap<String, Object>();
		child.put(id, getPartsAsTree(internalReadNode(id), style));
		children.add(child);
	    }
	    nm.put("hasPart", children);
	}
	return nm;
    }

    /**
     * @param list
     *            a list of nodes to create a json like map for
     * @return a map with objects
     */
    public List<Map<String, Object>> hitlistToMap(List<SearchHit> list) {
	List<Map<String, Object>> map = new ArrayList<Map<String, Object>>();
	for (SearchHit hit : list) {
	    Map<String, Object> m = hit.getSource();
	    m.put("primaryTopic", hit.getId());
	    map.add(m);
	}
	return map;
    }

    /**
     * @param type
     *            The objectTyp
     * @param namespace
     *            list only objects in this namespace
     * @param from
     *            show only hits starting at this index
     * @param until
     *            show only hits ending at this index
     * @return A list of pids with type {@type}
     */
    public List<SearchHit> listSearch(String type, String namespace, int from,
	    int until) {
	return Arrays.asList(Globals.search.list(namespace, type, from, until)
		.getHits());
    }

    /**
     * @param type
     *            The objectTyp
     * @param namespace
     *            list only objects in this namespace
     * @param from
     *            show only hits starting at this index
     * @param until
     *            show only hits ending at this index
     * @return a list of nodes
     */
    public List<Node> listRepo(String type, String namespace, int from,
	    int until) {
	List<String> list = null;
	if (from < 0 || until <= from) {
	    throw new HttpArchiveException(316,
		    "until and from not sensible. choose a valid range, please.");
	} else if (type == null || type.isEmpty() && namespace != null
		&& !namespace.isEmpty()) {
	    return getNodes(listRepoNamespace(namespace, from, until));
	} else if (namespace == null || namespace.isEmpty() && type != null
		&& !type.isEmpty()) {
	    return getNodes(listRepoType(type, from, until));
	} else if ((namespace == null || namespace.isEmpty())
		&& (type == null || type.isEmpty())) {
	    list = listRepoAll();
	} else {
	    list = listRepo(type, namespace);
	}
	return getNodes(sublist(list, from, until));
    }

    /**
     * @param ids
     *            a list of ids to get objects for
     * @return a list of nodes
     */
    public List<Node> getNodes(List<String> ids) {
	return ids.stream().map((String id) -> {
	    try {
		return internalReadNode(id);
	    } catch (Exception e) {
		Logger.error("" + id, e);
		return new Node(id);
	    }
	}).filter(n -> n != null).collect(Collectors.toList());
    }

    /**
     * @param ids
     *            a list of ids to get objects for
     * @return a list of Maps each represents a node
     */
    public List<Map<String, Object>> getNodesFromIndex(List<String> ids) {
	return ids.stream().map((String id) -> readNodeFromIndex(id))
		.collect(Collectors.toList());
    }

    private List<String> listRepo(String type, String namespace) {
	List<String> result = new ArrayList<String>();
	List<String> typedList = listRepoType(type);
	if (namespace != null && !namespace.isEmpty()) {
	    for (String item : typedList) {
		if (item.startsWith(namespace + ":")) {
		    result.add(item);
		}
	    }
	    return result;
	} else {
	    return typedList;
	}
    }

    private List<String> listRepoType(String type) {
	List<String> typedList;
	String query = "* <" + REL_CONTENT_TYPE + "> \"" + type + "\"";
	try (InputStream in = Globals.fedora.findTriples(query,
		FedoraVocabulary.SPO, FedoraVocabulary.N3)) {
	    typedList = RdfUtils.getFedoraSubject(in);
	    return typedList;
	} catch (IOException e) {
	    play.Logger.warn("", e);
	}
	return new ArrayList<String>();
    }

    private List<String> listRepoAll() {
	List<String> typedList;
	String query = "* <" + REL_IS_NODE_TYPE + "> <" + TYPE_OBJECT + ">";
	try (InputStream in = Globals.fedora.findTriples(query,
		FedoraVocabulary.SPO, FedoraVocabulary.N3)) {
	    typedList = RdfUtils.getFedoraSubject(in);
	    return typedList;
	} catch (IOException e) {
	    play.Logger.warn("", e);
	}
	return new ArrayList<String>();
    }

    private List<String> listRepoType(String type, int from, int until) {
	List<String> list = listRepoType(type);
	return sublist(list, from, until);
    }

    /**
     * List all pids within a namespace
     * 
     * @param namespace
     *            a valid namespace
     * @return a list of pids
     */
    public List<String> listRepoNamespace(String namespace) {
	return listByQuery(namespace + ":*");
    }

    /**
     * @param namespace
     *            list only objects in this namespace
     * @param from
     *            show only hits starting at this index
     * @param until
     *            show only hits ending at this index
     * @return a list of nodes
     */
    public List<String> listRepoNamespace(String namespace, int from, int until) {
	List<String> list = listRepoNamespace(namespace);
	return sublist(list, from, until);
    }

    private List<String> listByQuery(String query) {
	List<String> objects = null;
	objects = Globals.fedora.findNodes(query);
	return objects;
    }

    private List<String> sublist(List<String> list, int from, int until) {
	if (from >= list.size()) {
	    return new Vector<String>();
	}
	if (until < list.size()) {
	    return list.subList(from, until);
	} else {
	    return list.subList(from, list.size());
	}
    }

    /**
     * @param pid
     *            The pid to read the dublin core stream from.
     * @return A DCBeanAnnotated java object.
     */
    public DublinCoreData readDC(String pid) {
	Node node = readNode(pid);
	String uri = getHttpUriOfResource(node);
	if (node != null)
	    return node.getDublinCoreData().addIdentifier(uri);
	return null;
    }

    /**
     * @param pid
     *            the pid of the object
     * @param field
     *            if field is specified, only the value of a certain field will
     *            be returned
     * @return n-triple metadata
     */
    public String readMetadata(String pid, String field) {
	Node node = internalReadNode(pid);
	String result = readMetadata(node, field);
	return result == null ? "No " + field : result;
    }

    /**
     * @param node
     * @param field
     *            the shortname of metadata field
     * @return the ntriples or just one field
     */
    public String readMetadata(Node node, String field) {
	try {
	    String metadata = node.getMetadata();
	    if (field == null || field.isEmpty()) {
		return metadata;
	    } else {
		String pred = Globals.profile.nMap.get(field).uri;
		List<String> value = RdfUtils.findRdfObjects(node.getPid(),
			pred, metadata, RDFFormat.NTRIPLES);

		return value.isEmpty() ? null : value.get(0);
	    }
	} catch (UrlConnectionException e) {
	    throw new HttpArchiveException(404, e);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    /**
     * @param pid
     *            the pid of the object
     * @param field
     *            if field is specified, only a certain field of the node's
     *            metadata will be returned
     * @return n-triple metadata
     */
    public String readMetadataFromCache(String pid, String field) {
	try {
	    Node node = readNode(pid);
	    String metadata = node.getMetadata();
	    if (metadata == null || metadata.isEmpty())
		throw new HttpArchiveException(404, "No Metadata on " + pid
			+ " available!");
	    if (field == null || field.isEmpty()) {
		return metadata;
	    } else {
		String pred = Globals.profile.nMap.get(field).uri;
		List<String> value = RdfUtils.findRdfObjects(pid, pred,
			metadata, RDFFormat.NTRIPLES);

		return value.isEmpty() ? "No " + field : value.get(0);
	    }
	} catch (UrlConnectionException e) {
	    throw new HttpArchiveException(404, e);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    /**
     * @param node
     *            the pid of the object
     * @return ordered json array of parts
     */
    public String readSeq(Node node) {
	try {
	    return node.getSeq();
	} catch (UrlConnectionException e) {
	    throw new HttpArchiveException(404, e);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    /**
     * @param pid
     *            the pid
     * @return the last modified date
     */
    public Date getLastModified(String pid) {
	Node node = readNode(pid);
	return node.getLastModified();
    }

    /**
     * @param node
     *            the node to read a urn from
     * @return a urn object that describes the status of the urn
     */
    public Urn getUrnStatus(Node node) {
	String urn = getUrn(node);
	Urn result = new Urn(urn);
	result.init(Globals.urnbase + node.getPid());
	return result;
    }

    String getUrn(Node node) {
	List<String> urns = getNodeLdProperty(node,
		"http://purl.org/lobid/lv#urn");
	if (urns == null || urns.isEmpty()) {
	    throw new HttpArchiveException(500, "No urn fount at: "
		    + node.getPid());
	}
	if (urns.size() > 1) {
	    throw new HttpArchiveException(500, "Found multiple urns at: "
		    + node.getPid());
	}
	return urns.get(0);
    }

    /**
     * @param node
     *            the node to fetch a certain property from
     * @param predicate
     *            the property in its long form
     * @return all objects that are referenced using the predicate
     */
    public List<String> getNodeLdProperty(Node node, String predicate) {
	List<String> linkedObjects = RdfUtils.findRdfObjects(node.getPid(),
		predicate, node.getMetadata(), RDFFormat.NTRIPLES);
	return linkedObjects;
    }

    /**
     * @param node
     * @return 200 if object can be found at oaiprovider, 404 if not, 500 on
     *         error
     */
    public int getOaiStatus(Node node) {
	try {
	    URL url = new URL(Globals.oaiMabXmlAddress + node.getPid());
	    HttpURLConnection con = (HttpURLConnection) url.openConnection();
	    HttpURLConnection.setFollowRedirects(true);
	    con.connect();
	    Element root = XmlUtils.getDocument(con.getInputStream());
	    List<Element> elements = XmlUtils.getElements("//setSpec", root,
		    null);
	    if (elements.isEmpty())
		return 404;
	    return con.getResponseCode();
	} catch (Exception e) {
	    play.Logger.warn("", e);
	    return 500;
	}
    }

    Map<String, String> getLinks(Node node) {
	String oai = Globals.oaiMabXmlAddress + node.getPid();
	String aleph = Globals.alephAddress + node.getLegacyId();
	String lobid = Globals.lobidAddress + node.getLegacyId();
	String api = this.getHttpUriOfResource(node);
	String urn = Globals.urnResolverAddress + node.getUrnFromMetadata();
	String doi = "https://dx.doi.org/" + node.getDoi();
	String frontend = Globals.urnbase + node.getPid();
	String digitool = Globals.digitoolAddress
		+ node.getPid().substring(node.getNamespace().length() + 1);

	Map<String, String> result = new HashMap<String, String>();
	result.put("oai", oai);
	result.put("aleph", aleph);
	result.put("lobid", lobid);
	result.put("api", api);
	result.put("urn", urn);
	result.put("doi", doi);
	result.put("frontend", frontend);
	result.put("digitool", digitool);

	return result;
    }

    /**
     * The status contains information about the object with regard to
     * thirdparty system
     * 
     * @param node
     * @return a Map with status information
     */
    public Map<String, Object> getStatus(Node node) {
	Map<String, Object> result = new HashMap<String, Object>();
	result.put("urnStatus", urnStatus(node));
	result.put("doiStatus", doiStatus(node));
	result.put("oaiStatus", getOaiStatus(node));
	result.put("links", getLinks(node));
	result.put("title", readMetadata(node, "title"));
	result.put("metadataAccess", node.getPublishScheme());
	result.put("dataAccess", node.getAccessScheme());
	result.put("type", node.getContentType());
	result.put("pid",
		node.getPid().substring(node.getNamespace().length() + 1));
	result.put("catalogId", node.getLegacyId());
	result.put("urn", node.getUrnFromMetadata());
	return result;
    }

    private int urnStatus(Node node) {
	try {
	    Urn urn = getUrnStatus(node);
	    int urnStatus = urn == null ? 500 : urn.getResolverStatus();
	    return urnStatus;
	} catch (Exception e) {
	    play.Logger.warn("", e);
	    return 500;
	}
    }

    private int doiStatus(Node node) {
	try {
	    return getFinalResponseCode("https://dx.doi.org/" + node.getDoi());
	} catch (Exception e) {
	    play.Logger.warn("", e);
	    return 500;
	}
    }

    /**
     * @param nodes
     * @return status information for many nodes
     */
    public List<Map<String, Object>> getStatus(List<Node> nodes) {
	return nodes.stream().map((Node n) -> getStatus(n))
		.collect(Collectors.toList());
    }

    /**
     * @param namespace
     * @param from
     * @param until
     * @return a list of elasticsearch hits of objects within the given range
     */
    public List<SearchHit> list(String namespace, Date from, Date until) {
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	String f = dateFormat.format(from);
	String u = dateFormat.format(until);
	String query = "isDescribedBy.created:[" + f + " TO " + u + "]";
	play.Logger.info("List all from " + f + " to " + u);
	List<SearchHit> result = new ArrayList<SearchHit>();
	int step = 100;
	int start = 0;
	SearchHits hits = Globals.search.query(new String[] { namespace },
		query, start, step);
	long size = hits.getTotalHits();

	result.addAll(Arrays.asList((hits.getHits())));
	for (int i = 0; i < (size - (size % step)); i += step) {
	    hits = Globals.search.query(new String[] { namespace }, query, i,
		    step);
	    result.addAll(Arrays.asList((hits.getHits())));
	}
	return result;

    }

    private int getFinalResponseCode(String url) throws IOException {
	HttpURLConnection con = (HttpURLConnection) new URL(url)
		.openConnection();
	con.setInstanceFollowRedirects(false);
	con.connect();
	con.getInputStream();
	if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
		|| con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
		|| con.getResponseCode() == 307 || con.getResponseCode() == 303) {
	    String redirectUrl = con.getHeaderField("Location");

	    return getFinalResponseCode(redirectUrl);
	}
	return con.getResponseCode();
    }
}
