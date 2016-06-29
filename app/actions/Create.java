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
import helper.Mail;
import helper.oai.OaiDispatcher;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.mail.*;

import models.Gatherconf;
import models.Globals;
import models.Node;
import models.RegalObject;
import models.RegalObject.Provenience;

import play.Logger;
import play.Play;

/**
 * @author Jan Schnasse
 *
 */
public class Create extends RegalAction {

	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");

	@SuppressWarnings({ "javadoc", "serial" })
	public class WebgathererTooBusyException extends HttpArchiveException {

		public WebgathererTooBusyException(int status, String msg) {
			super(status, msg);
			WebgatherLogger.error(msg);
		}

	}

	// @Inject
	// MailerClient mailerClient;

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
			play.Logger.debug("Fail link " + node.getPid() + " to " + parentPid + "",
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
				play.Logger.debug("", e);
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
		try {
			if (Globals.heritrix.isBusy()) {
				throw new WebgathererTooBusyException(403,
						"Webgatherer is too busy! Please try again later.");
			}
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			WebgatherLogger.debug("Create webpageVersion " + n.getPid());
			conf = Gatherconf.create(n.getConf());
			WebgatherLogger.debug("Create webpageVersi " + conf.toString());
			// execute heritrix job
			conf.setName(n.getPid());
			if (!Globals.heritrix.jobExists(conf.getName())) {
				Globals.heritrix.createJob(conf);
			}
			boolean success = Globals.heritrix.teardown(conf.getName());
			WebgatherLogger.debug("Teardown " + conf.getName() + " " + success);
			Globals.heritrix.launch(conf.getName());

			Thread.currentThread().sleep(10000);

			Globals.heritrix.unpause(conf.getName());

			Thread.currentThread().sleep(10000);

			File crawlDir = Globals.heritrix.getCurrentCrawlDir(conf.getName());
			String warcPath = Globals.heritrix.findLatestWarc(crawlDir);
			String uriPath = Globals.heritrix.getUriPath(warcPath);

			String localpath =
					Globals.heritrixData + "/heritrix-data" + "/" + uriPath;
			WebgatherLogger.debug("Path to WARC " + localpath);

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
			webpageVersion.setLocalData(localpath);
			webpageVersion.setMimeType("application/warc");
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);

			conf.setLocalDir(crawlDir.getAbsolutePath());
			String msg = new Modify().updateConf(webpageVersion, conf.toString());

			WebgatherLogger.info(msg);

			return webpageVersion;
		} catch (Exception e) {
			WebgatherLogger.error("Website {} kann nicht gecrawlt werden !",
					n.getPid());
			// E-Mail erzeugen, dass Website nicht gecrawlt werden kann
			Mail mail = new Mail();
			mail.setTo(Play.application().configuration().getString("javax.mail.to"));
			mail.setFrom(
					Play.application().configuration().getString("javax.mail.from"));
			String mailMsg =
					"Das Crawlen der Website " + n.getPid() + " ist fehlgeschlagen !";
			if (conf != null) {
				mailMsg += "\nURL: " + conf.getUrl();
			}
			mail.setMessage(mailMsg);
			mail.setSubject("ERROR crawling Webpage " + n.getPid());

			String to = mail.getTo();
			int result;
			result = mail.sendMail();
			if (result == 0) {
				WebgatherLogger.info(" E-Mail erfolgreich verschickt an " + to);
			} else {
				WebgatherLogger
						.error("E-Mail an " + to + " konnte nicht zugestellt werden !");
			}
			throw new RuntimeException(e);
		}
	}

} /* END of Class Create */