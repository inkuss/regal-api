/*
 * Copyright 2019 hbz NRW (http://www.hbz-nrw.de/)
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
package helper;

import java.io.File;
import java.nio.file.Files;

import actions.Modify;
import actions.Modify.UpdateNodeException;
import archive.fedora.CopyUtils;
import models.Gatherconf;
import models.Globals;
import models.Gatherconf.CrawlerSelection;
import models.Node;
import models.ToScienceObject;
import play.Logger;
import play.Play;
import com.yourmediashelf.fedora.client.request.GetDatastream;
import com.yourmediashelf.fedora.client.request.GetDatastreamDissemination;
import com.yourmediashelf.fedora.client.response.FedoraResponse;
import com.yourmediashelf.fedora.client.response.GetDatastreamResponse;

/**
 * Methoden zum Veröffentlichen von Websites (openWayback AccessPoint
 * "weltweit"). Methoden zum Zurückziehen veröffentlichter Websites (openWayback
 * AccessPoint "lesesaal" oder "wayback").
 * 
 * @author I. Kuss, hbz
 */
public class WebsiteVersionPublisher {

	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");
	// Das Unterverzeichnis in public-data/ (volle Pfadangabe) als Java-Klasse
	// "File"
	private static File publicCrawlDir = null;
	private static String jobDir = null;
	private static String msg = null;

	/**
	 * Veröffentlicht und De-Publiziert Webschnitte
	 * 
	 * @param node Der Knoten (Klasse models.Node) einer zu patchenden Ressource
	 * @param object Das Regal-Objekt einer zu patchenden Ressource
	 * @return eine Textnachricht über die hier ausgeführten Aktionen
	 */
	public String handleWebpagePublishing(Node node, ToScienceObject object) {
		try {
			if ((object == null) || (object.getAccessScheme() == null)) {
				play.Logger.warn("Zugriffsrecht für ID " + node.getPid()
						+ " kann nicht ermittelt werden!");
				return "";
			}
			if (object.getAccessScheme().equals("public")) {
				if (node.getContentType().equals("version")) {
					publishWebpageVersion(node);
					return "Webschnitt ist veröffentlicht. Das Indexieren des Webschnitts in der OpenWayback-Maschine kann mehrere Minuten dauern.";
				} else if (node.getContentType().equals("webpage")) {
					return "Webpage ist veröffentlicht.";
				}
			}

			if (object.getAccessScheme().equals("restricted")
					&& node.getContentType().equals("version")) {
				retreatWebpageVersion(node);
				return "Webschnitt ist auf zugriffsbeschränkt (Lesesaal) gesetzt.";
			} else if (node.getContentType().equals("webpage")) {
				return "Webpage ist nur im Lesesaal zugänglich.";
			}
			return "";
		} catch (Exception e) {
			play.Logger.error("", e);
			WebgatherLogger.error(e.toString());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode realisiert sowohl das Veröffentlichen als auch das
	 * De-Publizieren eines Webschnitss anhand des Zugriffsrechts "accessScheme".
	 * Diese Methode wird über den Endpoint /resource/:pid/publishVersion
	 * aufgerufen.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param accessScheme das Zugriffsrecht als String: "public", "restricted"
	 * @throws RuntimeException eine Ausnahem, wenn es nicht geklappt hat
	 */
	public void publishWebpageVersion(Node node, String accessScheme)
			throws RuntimeException {
		WebgatherLogger
				.info("Realisierung eines Zugriffsrechts für einen Webschnitt.");
		try {
			if (accessScheme.equals("public")) {
				publishWebpageVersion(node);
			} else if (accessScheme.equals("restricted")) {
				retreatWebpageVersion(node);
			} else {
				msg = "Nicht unterstützes Zugriffsrecht :" + accessScheme;
				WebgatherLogger.warn(msg);
				throw new RuntimeException(msg);
			}
		} catch (Exception e) {
			WebgatherLogger.error("Webpage Version " + node.getPid()
					+ " kann nicht veröffentlicht werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Veröffentlicht eine Webpage-Version (=Webschnitt), indem es sie in der
	 * Openwayback-Kollektion "weltweit" anlegt (WARC-Objekte) oder indem es sie
	 * in den Apache-Pfad webharvests/ schiebt (ausgpackte Sites).
	 * 
	 * @param node der Knoten des Webschnittes
	 * @throws RuntimeException Ausnahme aufgetreten
	 */
	public void publishWebpageVersion(Node node) throws RuntimeException {
		WebgatherLogger.info("Jetzt wird ein Webschnitt veröffentlicht.");
		try {
			getConfFromFedora(node.getPid(), node);
			Gatherconf conf = Gatherconf.create(node.getConf());
			WebgatherLogger.debug("conf=" + conf.toString());
			PublishWebArchive(node, conf);
		} catch (Exception e) {
			WebgatherLogger.error("Webpage Version " + node.getPid()
					+ " kann nicht veröffentlicht werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Zieht eine Webpage-Version (=Webschnitt) von der Veröffentlichung zurück,
	 * indem es sie in der Openwayback-Kollektion "weltweit" löscht (WARC-Objekte)
	 * oder indem es sie in den Apache-Pfad restrictedweb/ schiebt (ausgepackte
	 * Sites).
	 * 
	 * @param node der Knoten des Webschnittes
	 * @throws RuntimeException Ausnahme aufgetreten
	 */
	public void retreatWebpageVersion(Node node) throws RuntimeException {
		WebgatherLogger
				.info("Ein Webschnitt wird von der Veröffentlichung zurückgezogen.");
		try {
			getConfFromFedora(node.getPid(), node);
			Gatherconf conf = Gatherconf.create(node.getConf());
			WebgatherLogger.debug("conf=" + conf.toString());
			dePublishWebArchive(node, conf);
		} catch (Exception e) {
			WebgatherLogger.error("Webpage Version " + node.getPid()
					+ " kann nicht von der Veröffentlichung zurück gezogen werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Publiziert ein Webarchiv (gepackte Seite oder ausgepackte Seite)
	 * 
	 * @param node Der Knoten des Webschnitts
	 * @param conf Die Gatherconf des Webschnitts
	 */
	private static void PublishWebArchive(Node node, Gatherconf conf) {
		String localDir = null;
		try {
			localDir = conf.getLocalDir();
			if (localDir.matches("(.*)/webharvests/(.*)")
					|| localDir.matches("(.*)/restrictedweb/(.*)")) {
				// Auf der Platte ausgepackte Website (direkter Aufruf im Browser)
				WebgatherLogger.debug("Publiziere localDir " + localDir);
				String newLocalDir = chkMvSiteToPublicWebharvests(node, conf, localDir);
				// localDir UND OpenwaybackLink in der Conf updaten
				setLocalDirAndOpenwaybackLinkToPublicWebharvests(node, conf,
						newLocalDir);
			} else {
				// als WARC-Archiv gepackte Website (indexiert in Wayback)
				createSoftlinkInPublicData(node, conf);
				setOpenwaybackLinkToPublicAccessPoint(node, conf);
			}
		} catch (Exception e) {
			WebgatherLogger.error(
					"Webschnitt " + node.getPid() + " konnte nicht publiziert werden.");
			throw new RuntimeException(e);
		}
	}

	/**
	 * De-Publiziert ein Webarchiv (gepackte Seite oder ausgepackte Seite)
	 * 
	 * @param node Der Knoten des Webschnitts
	 * @param conf Die Gatherconf des Webschnitts
	 */
	private static void dePublishWebArchive(Node node, Gatherconf conf) {
		String localDir = null;
		try {
			localDir = conf.getLocalDir();
			if (localDir.matches("(.*)/webharvests/(.*)")
					|| localDir.matches("(.*)/restrictedweb/(.*)")) {
				// Auf der Platte ausgepackte Website (direkter Aufruf im Browser)
				WebgatherLogger.debug("De-publiziere localDir " + localDir);
				String newLocalDir = chkMvSiteToRestrictedWeb(node, conf, localDir);
				setLocalDirAndOpenwaybackLinkToRestrictedWeb(node, conf, newLocalDir);
			} else {
				// als WARC-Archiv gepackte Website (indexiert in Wayback)
				chkRemoveSoftlinkInPublicData(node, conf);
				setOpenwaybackLinkToRestrictedAccessPoint(node, conf);
			}
		} catch (Exception e) {
			WebgatherLogger.error("Webschnitt " + node.getPid()
					+ " konnte nicht de-publiziert werden.");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ermittelt jobDir und ergänzt ggfs. localDir. jobDir ist das
	 * Basisverzeichnis, unter dem das WARC-Archiv dieses Webschnitts liegt. Das
	 * jobDir wird aus dem gewählten Crawler ermittelt. Bei heritrix- und
	 * wget-Crawls muss das localDir um das Unterverzeichnis warcs/ ergänzt
	 * werdem, um die WARC-Datei zu finden.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param conf die Gatherconf des Webschnitts
	 * @return localDir
	 */
	private static String findJobDirLocalDir(Node node, Gatherconf conf) {
		String localDir = conf.getLocalDir();
		WebgatherLogger.debug("localDir=" + localDir);
		CrawlerSelection crawlerSelection = conf.getCrawlerSelection();
		jobDir =
				Play.application().configuration().getString("regal-api.wget.dataDir");
		if (localDir.startsWith(jobDir)) {
			WebgatherLogger.debug("jobDir=" + jobDir);
			localDir = localDir.concat("/warcs");
			return localDir;
		}
		jobDir = Play.application().configuration()
				.getString("regal-api.heritrix.jobDir");
		if (localDir.startsWith(jobDir)) {
			WebgatherLogger.debug("jobDir=" + jobDir);
			localDir = localDir.concat("/warcs");
			return localDir;
		}
		jobDir =
				Play.application().configuration().getString("regal-api.wpull.jobDir");
		if (localDir.startsWith(jobDir)) {
			WebgatherLogger.debug("jobDir=" + jobDir);
			/* Achtung, jobDir ist temporär <=> wpull-data-crawldir */
			return localDir;
		}
		jobDir =
				Play.application().configuration().getString("regal-api.wpull.outDir");
		if (localDir.startsWith(jobDir)) {
			WebgatherLogger.debug("jobDir=" + jobDir);
			/*
			 * outDir ist der permanente Speicherort des Webschnitts, <=> wpull-data
			 */
			return localDir;
		}
		jobDir = null;
		throw new RuntimeException("Crawl-Verzeichnis " + localDir
				+ " beginnt nicht mit einem bekannten Crawler-Verzeichnis! Crawler-Auswahl ist "
				+ crawlerSelection + ". PID=" + node.getPid());
	}

	/**
	 * Ermittelt Speicherort der Webarchiv-Datei (WARC) und legt einen Softlink
	 * unterhalb von public-data/ an, der auf diese Webarchiv-Datei zeigt. Dadurch
	 * wird das Webarchiv zur Openwayback-Kollektion "weltweit" hinzugefügt und
	 * subsequent im Openwayback-Zugriffspunkt "weltweit" indexiert, also
	 * veröffentlicht.
	 *
	 * @param node Der Knoten des Webschnittes
	 * @param conf Die Konfigurationsdatei für das Webcrawling (Gatherconf), die
	 *          diesem Webschnitt zugrunde gelegt wurde.
	 */
	public static void createSoftlinkInPublicData(Node node, Gatherconf conf) {
		try {
			String localDir = findJobDirLocalDir(node, conf);
			WebgatherLogger.debug("localDir: " + localDir);
			String subDir = localDir.substring(jobDir.length() + 1);
			WebgatherLogger.debug("Unterverzeichnis für Webcrawl: " + subDir);
			File publicCrawlDirL = createPublicDataSubDir(subDir);
			getDataFromFedora(node, localDir);
			String DSLocation = node.getUploadFile();
			WebgatherLogger.debug("uploadFile=" + DSLocation);
			File uploadFile = new File(DSLocation);
			chkCreateSoftlink(publicCrawlDirL.getPath(), localDir,
					uploadFile.getName());
		} catch (Exception e) {
			WebgatherLogger.error("Softlink für Webschnitt " + node.getPid()
					+ " konnte unterhalb des Verzeichnisses public-data/ nicht angelegt werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Löscht einen Softlink unterhalb von public-data/, der auf eine
	 * Webarchiv-Datei zeigt. Dadurch wird das Webarchiv in der
	 * Openwayback-Kollektion "weltweit" entfernt.
	 * 
	 * @param node Der Knoten des Webschnittes
	 * @param conf Die Konfigurationsdatei für das Webcrawling (Gatherconf), die
	 *          diesem Webschnitt zugrunde gelegt wurde.
	 */
	private static void chkRemoveSoftlinkInPublicData(Node node,
			Gatherconf conf) {
		String localDir = findJobDirLocalDir(node, conf);
		try {
			String subDir = localDir.substring(jobDir.length() + 1);
			WebgatherLogger.debug("Unterverzeichnis für Webcrawl: " + subDir);
			if (!chkExistsPublicDataSubDir(subDir)) {
				WebgatherLogger.debug("Nichts zu tun.");
				return;
			}
			getDataFromFedora(node, localDir);
			String DSLocation = node.getUploadFile();
			WebgatherLogger.debug("uploadFile=" + DSLocation);
			File uploadFile = new File(DSLocation);
			chkRemoveSoftlink(publicCrawlDir.getPath(), uploadFile.getName());
		} catch (Exception e) {
			WebgatherLogger.error("Softlink für Webschnitt " + node.getPid()
					+ " auf Verzeichnis " + localDir
					+ " konnte unterhalb von public-data/ nicht gelöscht werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Verschiebt eine ausgepackte Website in den veröffentlchten Bereich. Macht
	 * nichts, falls die Site sich schon dort befindet.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param conf die Gatherconf am Webschnitt
	 * @param localDir localDir aus der Gatherconf
	 * @return Der volle Pfadname der Website im veröffentlichten Bereich
	 */
	private static String chkMvSiteToPublicWebharvests(Node node, Gatherconf conf,
			String localDir) {
		File localFile = new File(localDir);
		if (!localFile.exists()) {
			throw new RuntimeException("Ausgepackte Website " + localDir
					+ " existiert nicht! PID=" + node.getPid());
		}
		if (localDir.startsWith(Globals.webharvestsDataDir)) {
			WebgatherLogger.info("Nichts zu tun. " + node.getPid()
					+ " liegt bereits veröffentlicht unter " + localDir);
			return localDir;
		}
		if (!localDir.startsWith(Globals.restrictedwebDataDir)) {
			throw new RuntimeException(
					"Ausgepackte Website befindet sich in keinem bekannten Verzeichnis! localDir="
							+ localDir + "; PID" + node.getPid());
		}
		WebgatherLogger.debug("dataDir=" + Globals.restrictedwebDataDir);
		try {
			// ermittle Pfad unterhalb von restrictedweb/
			String versionDir =
					localDir.substring(Globals.restrictedwebDataDir.length() + 1);
			WebgatherLogger.debug("Unterverzeichnis für Webcrawl: " + versionDir);
			File versionFile =
					new File(Globals.webharvestsDataDir + "/" + versionDir);
			if (versionFile.exists()) {
				WebgatherLogger.info(
						"Ausgepackte Website befindet sich schon im veröffentlichten Bereich:"
								+ versionFile.getPath());
				return versionFile.getPath();
			}
			// gehe ein Verzeichnis höher, das sollte das Verz. der Webpage sein
			String webpageDir = versionDir.replaceAll("/\\w+:\\d+$", "");
			WebgatherLogger.debug("Verzeichnis für Webpage: " + webpageDir);
			// lege das Verzeichnis der Webpage ggfs. an
			File webpageFile =
					new File(Globals.webharvestsDataDir + "/" + webpageDir);
			if (!webpageFile.exists()) {
				webpageFile.mkdirs();
				WebgatherLogger
						.info("Verzeichnis " + webpageFile.getPath() + " wurde angelegt.");
			}
			localFile.renameTo(versionFile);
			WebgatherLogger.info("Verzeichnis " + localDir + " wurde umbenannt zu "
					+ versionFile.toPath());
			return versionFile.getPath();
		} catch (Exception e) {
			WebgatherLogger.error("Ausgepackter Webschnitt " + node.getPid()
					+ " in Verzeichnis " + localDir
					+ " konnte nicht in den veröffentlichten Bereich verschoben werden.");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Verschiebt eine ausgepackte Website in den zugriffsbeschränkten Bereich.
	 * Macht nichts, falls die Site sich schon dort befindet.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param conf die Gatherconf am Webschnitt
	 * @param localDir localDir aus der Gatherconf
	 * @return Der volle Pfadname der Website im zugriffsbeschränkten bereich
	 */
	private static String chkMvSiteToRestrictedWeb(Node node, Gatherconf conf,
			String localDir) {
		File localFile = new File(localDir);
		if (!localFile.exists()) {
			throw new RuntimeException("Ausgepackte Website " + localDir
					+ " existiert nicht! PID=" + node.getPid());
		}
		if (localDir.startsWith(Globals.restrictedwebDataDir)) {
			WebgatherLogger.info("Nichts zu tun. " + node.getPid()
					+ " liegt bereits zugriffsbeschränkt unter " + localDir);
			return localDir;
		}
		if (!localDir.startsWith(Globals.webharvestsDataDir)) {
			throw new RuntimeException(
					"Ausgepackte Website befindet sich in keinem bekannten Verzeichnis! localDir="
							+ localDir + "; PID" + node.getPid());
		}
		WebgatherLogger.debug("dataDir=" + Globals.webharvestsDataDir);
		try {
			// ermittle Pfad unterhalb von webharvests/
			String versionDir =
					localDir.substring(Globals.webharvestsDataDir.length() + 1);
			WebgatherLogger.debug("Unterverzeichnis für Webcrawl: " + versionDir);
			File versionFile =
					new File(Globals.restrictedwebDataDir + "/" + versionDir);
			if (versionFile.exists()) {
				WebgatherLogger.info(
						"Ausgepackte Website befindet sich schon im zugriffsbeschränkten Bereich:"
								+ versionFile.getPath());
				return versionFile.getPath();
			}
			// gehe ein Verzeichnis höher, das sollte das Verz. der Webpage sein
			String webpageDir = versionDir.replaceAll("/\\w+:\\d+$", "");
			WebgatherLogger.debug("Verzeichnis für Webpage: " + webpageDir);
			// lege das Verzeichnis der Webpage ggfs. an
			File webpageFile =
					new File(Globals.restrictedwebDataDir + "/" + webpageDir);
			if (!webpageFile.exists()) {
				webpageFile.mkdirs();
				WebgatherLogger
						.info("Verzeichnis " + webpageFile.getPath() + " wurde angelegt.");
			}
			localFile.renameTo(versionFile);
			WebgatherLogger.info("Verzeichnis " + localDir + " wurde umbenannt zu "
					+ versionFile.toPath());
			return versionFile.getPath();
		} catch (Exception e) {
			WebgatherLogger.error("Ausgepackter Webschnitt " + node.getPid()
					+ " in Verzeichnis " + localDir
					+ " konnte nicht in den zugriffsberschränkten Bereich verschoben werden.");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ändert den Openwayback-Link in der Gatherconf des Webschnittes, so dass er
	 * auf den öffentlichen Zugriffspunkt zeigt. Tut er dies schon, wird nichts
	 * gemacht.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param conf die Gatherconf, Konfigurationsdatei für den Crawler, die für
	 *          diesen Webschnitt maßgeblich war
	 */
	public static void setOpenwaybackLinkToPublicAccessPoint(Node node,
			Gatherconf conf) {
		try {
			String localDir = conf.getLocalDir();
			WebgatherLogger.debug("localDir=" + localDir);
			String openWaybackLink = conf.getOpenWaybackLink();
			if (openWaybackLink == null) {
				// Baue Openwayback-Link selber neu zusammen
				// Datumsstempel, 8stellig, aus localDir ermitteln
				String dateStamp = localDir.replaceAll("^.*/([0-9]{8})[0-9]*$", "$1");
				WebgatherLogger.debug("dateStamp=" + dateStamp);
				String openwaybackBaseLink = Play.application().configuration()
						.getString("regal-api.heritrix.openwaybackLink");
				String url = conf.getUrl();
				openWaybackLink =
						new String(openwaybackBaseLink + dateStamp + "/" + url);
			}
			WebgatherLogger.debug("openWaybackLink=" + openWaybackLink);
			play.Logger.debug("openWaybackLink=" + openWaybackLink);
			String publicOpenWaybackLink =
					openWaybackLink.replace("/wayback/", "/weltweit/");
			publicOpenWaybackLink =
					publicOpenWaybackLink.replace("/lesesaal/", "/weltweit/");
			conf.setOpenWaybackLink(publicOpenWaybackLink);
			play.Logger.debug("publicOpenWaybackLink=" + publicOpenWaybackLink);
			String msg = new Modify().updateConf(node, conf.toString());
			WebgatherLogger.info(
					"Openwayback-Link wurde auf \"weltweit\" gesetzt für Webschnitt "
							+ node.getPid() + ". Modify-Message: " + msg);
		} catch (Exception e) {
			WebgatherLogger.error("Openwayback-Link für Webschnitt " + node.getPid()
					+ " kann nicht auf \"öffentlich\" gesetzt werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ändert den Openwayback-Link in der Gatherconf des Webschnittes, so dass er
	 * auf den zugriffsbeschränkten Zugriffspunkt zeigt. Tut er dies schon, wird
	 * nichts gemacht.
	 * 
	 * @param node der Knoten des Webschnitts
	 * @param conf die Gatherconf, Konfigurationsdatei für den Crawler, die für
	 *          diesen Webschnitt maßgeblich war
	 */
	private static void setOpenwaybackLinkToRestrictedAccessPoint(Node node,
			Gatherconf conf) {
		try {
			String localDir = conf.getLocalDir();
			WebgatherLogger.debug("localDir=" + localDir);
			String restrictedAccessPoint = Play.application().configuration()
					.getString("regal-api.heritrix.openwaybackLink");
			String openWaybackLink = conf.getOpenWaybackLink();
			String restrictedOpenWaybackLink = null;
			if (openWaybackLink == null) {
				// Baue Openwayback-Link selber neu zusammen
				// Datumsstempel, 8stellig, aus localDir ermitteln
				String dateStamp = localDir.replaceAll("^.*/([0-9]{8})[0-9]*$", "$1");
				WebgatherLogger.debug("dateStamp=" + dateStamp);
				String url = conf.getUrl();
				restrictedOpenWaybackLink =
						new String(restrictedAccessPoint + dateStamp + "/" + url);
				WebgatherLogger.debug("openWaybackLink=" + restrictedOpenWaybackLink);
			} else {
				WebgatherLogger.debug("openWaybackLink=" + openWaybackLink);
				play.Logger.debug("openWaybackLink=" + openWaybackLink);
				if (openWaybackLink.startsWith(restrictedAccessPoint)) {
					WebgatherLogger
							.info("openWaybackLink steht schon auf beschränktem Zugriff.");
					return;
				}
				// vorhandener OpenWaybackLink wird auf "zugriffsbeschränkt"
				// umgeschrieben
				int startIndex = openWaybackLink.indexOf("weltweit/");
				if (startIndex < 0) {
					throw new RuntimeException(
							"Unbekannter Zugriffspunkt in OpenwaybackLink " + openWaybackLink
									+ " !");
				}
				restrictedOpenWaybackLink =
						restrictedAccessPoint + openWaybackLink.substring(startIndex + 9);
				WebgatherLogger
						.info("Neuer Openwayback-Link: " + restrictedOpenWaybackLink);
			}
			conf.setOpenWaybackLink(restrictedOpenWaybackLink);
			play.Logger
					.debug("restrictedOpenWaybackLink=" + restrictedOpenWaybackLink);
			msg = new Modify().updateConf(node, conf.toString());
			WebgatherLogger.info(
					"Openwayback-Link wurde auf \"lesesaal|wayback\" gesetzt für Webschnitt "
							+ node.getPid() + ". Modify-Message: " + msg);
		} catch (UpdateNodeException e) {
			e.printStackTrace();
		} catch (Exception e) {
			WebgatherLogger.error("Openwayback-Link für Webschnitt " + node.getPid()
					+ " kann nicht auf \"lesesaal|wayback\" gesetzt werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ändert localDir (den Speicherort des Webarchivs) und den Openwayback-Link
	 * in der Gatherconf des Webschnittes auf den veröffentlichten Bereich
	 * (webharvests). Ist dies schon der Fall, wird nichts gemacht.
	 * 
	 * @param node der Knoten der WebpageVersion
	 * @param conf Gatherconf des Webschnitts
	 * @param newLocalDir neuer lokaler Speicherpfad des Webschnitts
	 */
	private static void setLocalDirAndOpenwaybackLinkToPublicWebharvests(
			Node node, Gatherconf conf, String newLocalDir) {
		try {
			conf.setLocalDir(newLocalDir);
			String versionDir =
					newLocalDir.substring(Globals.webharvestsDataDir.length() + 1);
			String publicOpenWaybackLink =
					Globals.webharvestsDataUrl + "/" + versionDir + "/webschnitt.xml";
			WebgatherLogger.info("Neuer Openwayback-Link: " + publicOpenWaybackLink);
			conf.setOpenWaybackLink(publicOpenWaybackLink);
			String msg = new Modify().updateConf(node, conf.toString());
			WebgatherLogger.info(
					"localDir und Openwayback-Link wurde auf öffentlich gesetzt für Webschnitt "
							+ node.getPid() + ". Modify-Message: " + msg);
		} catch (UpdateNodeException e) {
			e.printStackTrace();
		} catch (Exception e) {
			WebgatherLogger.error("localDir und Openwayback-Link für Webschnitt "
					+ node.getPid() + " konnten nicht auf öffentlich gesetzt werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ändert localDir (den Speicherort des Webarchivs) und den Openwayback-Link
	 * in der Gatherconf des Webschnittes auf den zugriffsbeschränkten Bereich
	 * (restrictedweb). Ist dies schon der Fall, wird nichts gemacht.
	 * 
	 * @param node der Knoten der WebpageVersion
	 * @param conf Gatherconf des Webschnitts
	 * @param newLocalDir neuer lokaler Speicherpfad des Webschnitts
	 */
	private static void setLocalDirAndOpenwaybackLinkToRestrictedWeb(Node node,
			Gatherconf conf, String newLocalDir) {
		try {
			conf.setLocalDir(newLocalDir);
			String versionDir =
					newLocalDir.substring(Globals.restrictedwebDataDir.length() + 1);
			String restrictedOpenWaybackLink =
					Globals.restrictedwebDataUrl + "/" + versionDir + "/webschnitt.xml";
			WebgatherLogger
					.info("Neuer Openwayback-Link: " + restrictedOpenWaybackLink);
			conf.setOpenWaybackLink(restrictedOpenWaybackLink);
			String msg = new Modify().updateConf(node, conf.toString());
			WebgatherLogger.info(
					"localDir und Openwayback-Link wurde auf zugriffbeschränkt gesetzt für Webschnitt "
							+ node.getPid() + ". Modify-Message: " + msg);
		} catch (UpdateNodeException e) {
			e.printStackTrace();
		} catch (Exception e) {
			WebgatherLogger
					.error("localDir und Openwayback-Link für Webschnitt " + node.getPid()
							+ " konnten nicht auf zugriffsbeschränkt gesetzt werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Erzeugt eine weiche Verknüpfung (Softlink) in einem Unterverzeichnis von
	 * public-data/ auf eine eingesammelte WARC-Datei in einem der
	 * Crawler-Verzeichnisse. Macht nichts, falls diese weiche Verknüpfung schon
	 * existiert.
	 * 
	 * @param publicCrawlDir Das Verzeichnis public-data/ mit Unterverzeichnissen,
	 *          in denen der Softlink angelegt werden soll.
	 * @param localDir Das Verzeichnis, in dem die WARC-Datei wirklich liegt.
	 * @param uploadFile Der Dateiname der WARC-Datei (ohne Pfadangaben).
	 */
	private static void chkCreateSoftlink(String publicCrawlDirP, String localDir,
			String uploadFile) {
		try {
			File softLink = new File(publicCrawlDirP + "/" + uploadFile);
			if (softLink.exists()) {
				WebgatherLogger
						.info("Softlink " + softLink.getPath() + " gibt es schon.");
				return;
			}
			File localFile = new File(localDir + "/" + uploadFile);
			if (!localFile.exists()) {
				throw new RuntimeException("Lokale Webarchiv-Datei "
						+ localFile.getPath() + " existiert nicht!");
			}
			Files.createSymbolicLink(softLink.toPath(), localFile.toPath());
			WebgatherLogger.info("Symbolischer Link " + softLink.getPath()
					+ " wurde erfolgreich angelegt.");
		} catch (Exception e) {
			WebgatherLogger.error("Kann Softlink " + uploadFile + " in Verzeichnis "
					+ publicCrawlDirP + " nicht anlegen!");
			// throw new RuntimeException(e);
			return;
		}
	}

	/**
	 * Löscht einen Softlink auf eine eingesammelte WARC-Datei in einem
	 * Unterverzeichnis von public-data/. Macht nichts, falls der Softlink schon
	 * gelöscht ist.
	 * 
	 * @param publicCrawlDir Das Verzeichnis public-data/ mit Unterverzeichnissen,
	 *          in denen der Softlink gelöscht werden soll.
	 * @param uploadFile Der Dateiname der WARC-Datei (ohne Pfadangaben).
	 */
	private static void chkRemoveSoftlink(String publicCrawlDirP,
			String uploadFile) {
		try {
			File softLink = new File(publicCrawlDirP + "/" + uploadFile);
			if (!softLink.exists()) {
				WebgatherLogger.info(
						"Softlink " + softLink + " gibt es nicht bzw. ist schon gelöscht.");
				return;
			}
			Files.delete(softLink.toPath());
			WebgatherLogger
					.info("Softlink " + softLink.getPath() + " wurde entfernt.");
		} catch (Exception e) {
			WebgatherLogger.error("Kann Softlink " + uploadFile + " in Verzeichnis "
					+ publicCrawlDirP + " nicht löschen !");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Legt eine Verzeichnisstruktur unterhalb von public-data/ an, falls diese
	 * noch nicht vorhanden ist.
	 * 
	 * @param subDir die Verzeichnisstruktur unterhalb von public-data/.
	 *          Unterverzeichnisse sind durch "/" getrennt.
	 */
	private static File createPublicDataSubDir(String subDir) {
		try {
			String publicJobDir = Play.application().configuration()
					.getString("regal-api.public.jobDir");
			File publicCrawlDirL = new File(publicJobDir + "/" + subDir);
			if (!publicCrawlDirL.exists()) {
				publicCrawlDirL.mkdirs();
			}
			return publicCrawlDirL;
		} catch (Exception e) {
			WebgatherLogger.error("Kann Verzeichnisstruktur " + subDir
					+ " unterhalb von public-data/ nicht anlegen!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Prüft die Existenz einer Verzeichnisstruktur unterhalb von public-data/
	 * 
	 * @param subDir die Verzeichnisstruktur unterhalb von public-data/.
	 *          Unterverzeichnisse sind durch "/" getrennt.
	 * @return Ja oder Nein : Verzeichnis existiert
	 */
	private static boolean chkExistsPublicDataSubDir(String subDir) {
		publicCrawlDir = null;
		try {
			String publicJobDir = Play.application().configuration()
					.getString("regal-api.public.jobDir");
			publicCrawlDir = new File(publicJobDir + "/" + subDir);
			if (!publicCrawlDir.exists()) {
				WebgatherLogger.debug("Das Datenverzeichnis " + publicCrawlDir.getPath()
						+ " gibt es nicht.");
				return false;
			}
			return true;
		} catch (Exception e) {
			WebgatherLogger.error("Kann Verzeichnisstruktur " + subDir
					+ " unterhalb von public-data/ nicht überprüfen !");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Lies einen Datenstrom "conf" (Konfigurationsdatei für das Webgathering) aus
	 * der Fedora
	 * 
	 * @param pid
	 * @param node
	 * @throws Exception
	 */
	private static void getConfFromFedora(String pid, Node node)
			throws RuntimeException {
		try {
			FedoraResponse response =
					new GetDatastreamDissemination(pid, "conf").execute();
			node.setConf(
					CopyUtils.copyToString(response.getEntityInputStream(), "utf-8"));
		} catch (Exception e) {
			WebgatherLogger.warn("Datenstrom \"conf\" konnte nicht gelesen werden!");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Lies einen Datenstrom "data" (die WARC-Datei beschreibend) aus der Fedora
	 * 
	 * @param pid
	 * @param node
	 * @throws Exception
	 */
	private static void getDataFromFedora(Node node, String localDir)
			throws RuntimeException {
		try {
			GetDatastreamResponse response =
					new GetDatastream(node.getPid(), "data").execute();
			String dsLocation = response.getDatastreamProfile().getDsLocation();
			WebgatherLogger.debug("datastream location: " + dsLocation);
			// Bastle upload file zusammen
			File fileLocation = new File(dsLocation);
			String uploadFile = localDir + "/" + fileLocation.getName();
			WebgatherLogger.info("Upload File wird gesetzt auf: " + uploadFile);
			node.setUploadFile(uploadFile);
			// Hier könnte man noch weitere Felder in den Node übernehmen
		} catch (Exception e) {
			WebgatherLogger.warn(
					"Datenstrom \"data\" konnte nicht gelesen werden oder enthält ungültige Werte !");
			throw new RuntimeException(e);
		}
	}

}
