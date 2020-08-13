/*
 * Copyright 2017 hbz NRW (http://www.hbz-nrw.de/)
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
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import actions.Create;
import helper.mail.Mail;
import models.Gatherconf;
import models.Globals;
import models.Message;
import models.Node;
import play.Logger;

/**
 * Eine Klasse mit nützlichen Methoden im Umfeld des Webgatherings
 * 
 * @author I. Kuss, hbz
 */
public class WebgatherUtils {

	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");
	/** Datumsformat für String-Repräsentation von Datümern */
	public static final DateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz");

	/**
	 * Eine Methode zum Validieren und Umwandeln einer URL. Die URL wird nach
	 * ASCII konvertiert, falls sie noch nicht in dieser Kodierung ist. Wahlweise
	 * wird vorher nach Punycode konvertiert. Das Schema kann wahlweise erhalten
	 * (falls vorhanden) oder entfernt werden. Bei ungültigen URL wird eine
	 * URISyntaxException geschmissen.
	 * 
	 * von hier kopiert:
	 * https://nealvs.wordpress.com/2016/01/18/how-to-convert-unicode-url-to-ascii
	 * -in-java/
	 * 
	 * @param url ein Uniform Resource Locator als Zeichenkette
	 * @return eine URL als Zeichenkette
	 */
	public static String convertUnicodeURLToAscii(String url) {
		try {
			URL u = new URL(url);
			URI uri =
					new URI(u.getProtocol(), u.getUserInfo(), IDN.toASCII(u.getHost()),
							u.getPort(), u.getPath(), u.getQuery(), u.getRef());
			String correctEncodedURL = uri.toASCIIString();
			return correctEncodedURL;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Erzeugt eine Nachricht für den Fall, dass eine URL umgezogen ist.
	 * 
	 * @param conf die Crawler-Settings (Gatherconf)
	 * @return die Nachricht
	 */
	public static Message createInvalidUrlMessage(Gatherconf conf) {
		Message msg = null;
		if (conf.getUrlNew() == null) {
			msg = new Message("Die Website ist unbekannt verzogen.\n"
					+ "Bitte geben Sie auf dem Tab \"Crawler settings\" eine neue, gültige URL ein. Solange wird die Website nicht erneut eingesammelt.");
		} else {
			msg = new Message("Die Website ist umgezogen nach " + conf.getUrlNew()
					+ ".\n"
					+ "Bitte bestätigen Sie den Umzug auf dem Tab \"Crawler settings\" (URL kann dort vorher editiert werden).");
		}
		return msg;
	}

	/**
	 * Schickt E-Mail mit einer Umzugsnotiz und Aufforderung, die neue URL zu
	 * bestätigen.
	 * 
	 * @param node der Knoten der Website
	 * @param conf die Gatherconf der umgezogenen Website
	 */
	public static void sendInvalidUrlEmail(Node node, Gatherconf conf) {
		WebgatherLogger.info("Schicke E-Mail mit Umzugsnotiz.");
		try {

			String siteName =
					conf.getName() == null ? node.getAggregationUri() : conf.getName();
			String mailMsg = "Die Website " + siteName + " ist ";
			if (conf.getUrlNew() == null) {
				mailMsg += "unbekannt verzogen.\n"
						+ "Bitte geben Sie auf diesem Webformular eine neue, gültige URL ein. Solange wird die Website nicht erneut eingesammelt: ";
			} else {
				mailMsg += "umgezogen.\n"
						+ "Bitte bestätigen Sie den Umzug auf diesem Webformular (URL kann dort vorher editiert werden): ";
			}
			mailMsg += Globals.urnbase + node.getAggregationUri() + "/crawler .";

			try {
				Mail.sendMail(mailMsg, "Die Website " + siteName + " ist umgezogen ! ");
			} catch (Exception e) {
				throw new RuntimeException("Email could not be sent successfully!");
			}
		} catch (Exception e) {
			WebgatherLogger.warn(e.toString());
		}
	}

	/**
	 * Diese Methode stößt einen neuen Webcrawl an.
	 * 
	 * @param node must be of type webpage: Die Webpage
	 */
	public void startCrawl(Node node) {
		Gatherconf conf = null;
		File crawlDir = null;
		String localpath = null;
		try {
			if (!"webpage".equals(node.getContentType())) {
				throw new HttpArchiveException(400, node.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			WebgatherLogger.debug("Starte Webcrawl für PID: " + node.getPid());
			conf = Gatherconf.create(node.getConf());
			WebgatherLogger.debug("Gatherer-Konfiguration: " + conf.toString());
			conf.setName(node.getPid());
			if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.heritrix)) {
				if (Globals.heritrix.isBusy()) {
					WebgatherLogger
							.error("Webgatherer is too busy! Please try again later.");
					throw new HttpArchiveException(403,
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
				new Create().createWebpageVersion(node, conf, crawlDir, localpath);
			} else if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.wpull)) {
				WpullCrawl wpullCrawl = new WpullCrawl(node, conf);
				wpullCrawl.createJob();
				/**
				 * Startet Job in neuem Thread, einschließlich CDN-Precrawl
				 */
				wpullCrawl.startJob();
				crawlDir = wpullCrawl.getCrawlDir();
				// localpath = wpullCrawl.getLocalpath();
				if (wpullCrawl.getExitState() != 0) {
					throw new RuntimeException("Crawl job returns with exit state "
							+ wpullCrawl.getExitState() + "!");
				}
				WebgatherLogger
						.debug("Path to WARC (crawldir):" + crawlDir.getAbsolutePath());
			} else {
				throw new RuntimeException(
						"Unknown crawler selection " + conf.getCrawlerSelection() + "!");
			}
		} catch (Exception e) {
			// WebgatherExceptionMail.sendMail(n.getPid(), conf.getUrl());
			WebgatherLogger.warn("Crawl of Webpage " + node.getPid() + ","
					+ conf.getUrl() + " has failed !\n\tReason: " + e.getMessage());
			WebgatherLogger.debug("", e);
			throw new RuntimeException(e);
		}

	} // Ende startCrawl

}
