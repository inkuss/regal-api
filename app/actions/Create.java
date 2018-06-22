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

import java.io.File;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import helper.HttpArchiveException;
import helper.mail.WebgatherExceptionMail;
import helper.oai.OaiDispatcher;
import helper.WpullCrawl;
import models.Gatherconf;
import models.Globals;
import models.Node;
import models.RegalObject;
import models.RegalObject.Provenience;
import play.Logger;

/**
 * @author Jan Schnasse
 *
 */
public class Create extends RegalAction {

	private static final Logger.ALogger ApplicationLogger =
			Logger.of("application");
	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");
	private static final BigInteger bigInt1024 = new BigInteger("1024");

	@SuppressWarnings({ "javadoc", "serial" })
	public class WebgathererTooBusyException extends HttpArchiveException {

		public WebgathererTooBusyException(int status, String msg) {
			super(status, msg);
			WebgatherLogger.error(msg);
		}

	}

	/**
	 * @param node
	 * @param object
	 * @return the updated node
	 */
	public Node updateResource(Node node, RegalObject object) {
		new Index().remove(node);
		overrideNodeMembers(node, object);
		return updateResource(node);
	}

	private Node updateResource(Node node) {
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
		return updateResource(node);
	}

	/**
	 * @param nodes nodes to set new properties for
	 * @param object the RegalObject contains props that will be applied to all
	 *          nodes in the list
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
		node.setContextDocumentUri(
				"http://" + Globals.server + "/public/edoweb-resources.json");
		// node.setLabel("Hannes");
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
			OaiDispatcher.updateTransformer(object.getTransformer(), node);
		if (object.getIsDescribedBy().getDoi() != null)
			node.setDoi(object.getIsDescribedBy().getDoi());
		if (object.getIsDescribedBy().getUrn() != null)
			node.setUrn(object.getIsDescribedBy().getUrn());
		OaiDispatcher.makeOAISet(node);
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
		OaiDispatcher.makeOAISet(node);
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
			play.Logger.warn("Fail link " + node.getPid() + " to " + parentPid + "",
					e);
		}
	}

	private void inheritTitle(Node from, Node to) {
		String title = new Read().readMetadata(to, "title");
		String parentTitle = new Read().readMetadata(from, "title");
		if (title == null && parentTitle != null) {
			new Modify().addMetadataField(to, getUriFromJsonName("title"),
					parentTitle);
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
				play.Logger.warn("", e);
			}
		}
	}

	private void inheritRights(Node from, Node to) {
		to.setAccessScheme(from.getAccessScheme());
		to.setPublishScheme(from.getPublishScheme());
	}

	/**
	 * @param namespace a namespace in fedora , corresponds to an index in
	 *          elasticsearch
	 * @return a new pid in the namespace
	 */
	public String pid(String namespace) {
		return Globals.fedora.getPid(namespace);
	}

	/**
	 * @param n must be of type webpage
	 * @return a new version pointing to a heritrix crawl
	 */
	public Node createWebpageVersion(Node n) {
		Gatherconf conf = null;
		File crawlDir = null;
		String localpath = null;
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			WebgatherLogger.debug("Create webpageVersion for name " + n.getPid());
			conf = Gatherconf.create(n.getConf());
			WebgatherLogger
					.debug("Create webpageVersion with conf " + conf.toString());
			conf.setName(n.getPid());
			if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.heritrix)) {
				if (Globals.heritrix.isBusy()) {
					throw new WebgathererTooBusyException(403,
							"Webgatherer is too busy! Please try again later.");
				}
				if (!Globals.heritrix.jobExists(conf.getName())) {
					Globals.heritrix.createJob(conf);
				}
				boolean success = Globals.heritrix.teardown(conf.getName());
				WebgatherLogger.debug("Teardown " + conf.getName() + " " + success);

				Globals.heritrix.launch(conf.getName());
				WebgatherLogger.debug("Launched " + conf.getName());
				Thread.currentThread().sleep(10000);

				Globals.heritrix.unpause(conf.getName());
				WebgatherLogger.debug("Unpaused " + conf.getName());
				Thread.currentThread().sleep(10000);

				crawlDir = Globals.heritrix.getCurrentCrawlDir(conf.getName());
				String warcPath = Globals.heritrix.findLatestWarc(crawlDir);
				String uriPath = Globals.heritrix.getUriPath(warcPath);

				localpath = Globals.heritrixData + "/heritrix-data" + "/" + uriPath;
				WebgatherLogger.debug("Path to WARC " + localpath);
			} else if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.wpull)) {
				WpullCrawl wpullCrawl = new WpullCrawl(conf);
				wpullCrawl.createJob();
				wpullCrawl.startJob();
				crawlDir = wpullCrawl.getCrawlDir();
				localpath = wpullCrawl.getLocalpath();
				if (wpullCrawl.getExitState() != 0) {
					throw new RuntimeException("Crawl job returns with exit state "
							+ wpullCrawl.getExitState() + "!");
				}
				WebgatherLogger.debug("Path to WARC " + crawlDir.getAbsolutePath());
			} else {
				throw new RuntimeException(
						"Unknown crawler selection " + conf.getCrawlerSelection() + "!");
			}

			// create fedora object with unmanaged content pointing to
			// the respective warc container
			RegalObject regalObject = new RegalObject();
			regalObject.setContentType("version");
			Provenience prov = regalObject.getIsDescribedBy();
			prov.setCreatedBy("webgatherer");
			prov.setName(conf.getName());
			prov.setImportedFrom(conf.getUrl());
			regalObject.setIsDescribedBy(prov);
			regalObject.setParentPid(n.getPid());
			Node webpageVersion = createResource(n.getNamespace(), regalObject);
			String label = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			new Modify().updateLobidifyAndEnrichMetadata(webpageVersion,
					"<" + webpageVersion.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + label + "\" .");
			if (localpath != null) {
				webpageVersion.setLocalData(localpath);
			}
			webpageVersion.setMimeType("application/warc");
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);

			conf.setLocalDir(crawlDir.getAbsolutePath());
			String owDatestamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
			conf.setOpenWaybackLink(
					Globals.heritrix.openwaybackLink + owDatestamp + "/" + conf.getUrl());
			String msg = new Modify().updateConf(webpageVersion, conf.toString());

			WebgatherLogger.info(msg);

			return webpageVersion;
		} catch (Exception e) {
			// WebgatherExceptionMail.sendMail(n.getPid(), conf.getUrl());
			WebgatherLogger.warn("Crawl of Webpage " + n.getPid() + ","
					+ conf.getUrl() + " has failed !\n\tReason: " + e.getMessage());
			WebgatherLogger.debug("", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param n must be of type webpage
	 * @param versionPid gewünschte Pid für die Version (7-stellig numerisch)
	 * @param label Label für die Version im Format YYYY-MM-DD
	 * @return a new version pointing to an imported crawl. The imported crawl is
	 *         in wget-data/ and indexed in openwayback
	 */
	public Node importWebpageVersion(Node n, String versionPid, String label) {
		Gatherconf conf = null;
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger.debug("Import webpageVersion PID" + n.getPid());
			conf = Gatherconf.create(n.getConf());
			ApplicationLogger.debug("Import webpageVersion Conf" + conf.toString());
			conf.setName(n.getPid());
			conf.setId(versionPid);
			Date startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
					.parse(label + " 12:00:00");
			conf.setStartDate(startDate);

			// hier auf ein bestehendes WARC in wget-data/ verweisen
			String crawlDateTimestamp = label.substring(0, 4) + label.substring(5, 7)
					+ label.substring(8, 10); // crawl-Datum im Format yyyymmdd
			File crawlDir = new File(Globals.wget.dataDir + "/" + conf.getName() + "/"
					+ crawlDateTimestamp);
			ApplicationLogger.debug("crawlDir=" + crawlDir.toString());
			File warcDir = new File(crawlDir.getAbsolutePath() + "/warcs");
			String warcPath = warcDir.listFiles()[0].getAbsolutePath();
			ApplicationLogger.debug("Path to WARC " + warcPath);
			String uriPath = Globals.wget.getUriPath(warcPath);
			String localpath = Globals.wgetData + "/wget-data" + uriPath;
			ApplicationLogger.debug("URI-Path to WARC " + localpath);

			// create fedora object with unmanaged content pointing to
			// the respective warc container
			RegalObject regalObject = new RegalObject();
			regalObject.setContentType("version");
			Provenience prov = regalObject.getIsDescribedBy();
			prov.setCreatedBy("webgatherer");
			prov.setName(conf.getName()); // name=parentPid
			prov.setImportedFrom(conf.getUrl());
			regalObject.setIsDescribedBy(prov);
			regalObject.setParentPid(n.getPid());
			Node webpageVersion =
					createResource(versionPid, n.getNamespace(), regalObject);
			new Modify().updateLobidifyAndEnrichMetadata(webpageVersion,
					"<" + webpageVersion.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + label + "\" .");
			webpageVersion.setLocalData(localpath);
			webpageVersion.setMimeType("application/warc");
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);

			conf.setLocalDir(crawlDir.getAbsolutePath());
			conf.setOpenWaybackLink(Globals.heritrix.openwaybackLink
					+ crawlDateTimestamp + "/" + conf.getUrl());
			String msg = new Modify().updateConf(webpageVersion, conf.toString());

			ApplicationLogger.info(msg);

			return webpageVersion;
		} catch (Exception e) {
			ApplicationLogger.error(
					"Import der WebsiteVersion {} zu Webpage {} ist fehlgeschlagen !",
					versionPid, n.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param n must be of type web page
	 * @param jsn Json node
	 * @return a new version pointing to a linked, unpacked crawl
	 */
	public Node linkWebpageVersion(Node n, JsonNode jsn) {
		Gatherconf conf = null;
		String versionPid = "";
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger.debug("Link webpageVersion to PID" + n.getPid());
			conf = Gatherconf.create(n.getConf());
			ApplicationLogger.debug("Link webpageVersion Conf" + conf.toString());
			conf.setName(n.getPid());
			versionPid =
					jsn.findValue("versionPid").toString().replaceAll("^\"|\"$", "");
			ApplicationLogger.debug("versionPid: " + versionPid);
			conf.setId(versionPid);
			String dateTimestamp =
					jsn.findValue("dateTimestamp").toString().replaceAll("^\"|\"$", "");
			ApplicationLogger.debug("dateTimestamp: " + dateTimestamp);
			Date startDate = new SimpleDateFormat("yyyyMMdd HH:mm:ss")
					.parse(dateTimestamp + " 12:00:00");
			conf.setStartDate(startDate);
			String label = dateTimestamp.substring(0, 4) + "-"
					+ dateTimestamp.substring(4, 6) + "-" + dateTimestamp.substring(6, 8);
			ApplicationLogger.debug("label: " + label);
			String zipSizeStr =
					jsn.findValue("zipSize").toString().replaceAll("^\"|\"$", "");
			BigInteger zipSize = new BigInteger(zipSizeStr).multiply(bigInt1024);
			ApplicationLogger.debug("zipSize (bytes): " + zipSize.toString());
			String relUri = n.getPid() + "/" + n.getNamespace() + ":" + versionPid;
			File crawlDir = new File(Globals.webharvestsDataDir + "/" + relUri);
			conf.setLocalDir(crawlDir.getAbsolutePath());

			String localpath =
					Globals.webharvestsDataUrl + "/" + relUri + "/webschnitt.xml";
			ApplicationLogger.debug("URI-Path to archive: " + localpath);
			conf.setOpenWaybackLink(localpath);

			// create Regal object
			RegalObject regalObject = new RegalObject();
			regalObject.setContentType("version");
			Provenience prov = regalObject.getIsDescribedBy();
			prov.setCreatedBy("webgatherer");
			prov.setName(conf.getName());
			prov.setImportedFrom(conf.getUrl());
			regalObject.setIsDescribedBy(prov);
			regalObject.setParentPid(n.getPid());
			Node webpageVersion =
					createResource(versionPid, n.getNamespace(), regalObject);
			new Modify().updateLobidifyAndEnrichMetadata(webpageVersion,
					"<" + webpageVersion.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + label + "\" .");
			webpageVersion.setLocalData(localpath);
			webpageVersion.setMimeType("application/xml");
			webpageVersion.setFileSize(zipSize);
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);
			String msg = new Modify().updateConf(webpageVersion, conf.toString());
			ApplicationLogger.info(msg);

			return webpageVersion;
		} catch (Exception e) {
			ApplicationLogger.error(
					"Link unpacked website version {} to webpage {} failed !", versionPid,
					n.getPid());
			throw new RuntimeException(e);
		}
	}

} /* END of Class Create */