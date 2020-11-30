/*
 * Copyright 2020 hbz NRW (http://www.hbz-nrw.de/)
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import play.Logger;
import helper.HttpArchiveException;

/**
 * Diese Klasse implementiert eine Forschungsdaten-Ressource. Üblicherweise ist
 * das eine Datei (beliebigen Typs), die zu einem Forschungsdaten-Objekt
 * (contentType=researchData) hochgeladen wird.
 * 
 * @author Ingolf Kuss kuss@hbz-nrw.de
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResearchDataResource implements java.io.Serializable {

	/**
	 * Eigenschaften dieser Klasse.
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger.ALogger ApplicationLogger =
			Logger.of("application");
	private Node parentNode = null;
	private String parentPid = null;
	private static String baseUrl = Globals.researchDataBaseUrl;
	private String collectionUrl = null;
	private String subPath = null;
	private String filename = null;
	private String resourcePid = null;
	private String urlString = null;
	private boolean available = false;
	private String contentType = null;

	/**
	 * Standard-Konstruktor der Klasse (ohne Parameter)
	 */
	public ResearchDataResource() {
		setDefaultCollectionUrl();
	}

	/*
	 * Getter- und Setter-Methoden
	 */

	/**
	 * @param parentNode Der übergeordnete Knoten (Node) vom contentType
	 *          "researchData"
	 */
	public void setParentNode(Node parentNode) {
		this.parentNode = parentNode;
		this.parentPid = parentNode.getPid();
	}

	/**
	 * @return Der übergeordnete Knoten (Node) vom contentType "researchData"
	 */
	public Node getParentNode() {
		return parentNode;
	}

	/**
	 * @param collectionUrl eine Bestandteil der URL zu der Ressource, der direkt
	 *          unterhalb von basUrl liegt. Standardwert ist "data".
	 * 
	 */
	public void setCollectionUrl(String collectionUrl) {
		if (collectionUrl == null || collectionUrl.isEmpty()) {
			return;
		}
		this.collectionUrl = collectionUrl;
	}

	/**
	 * @return eine Bestandteil der URL zu der Ressource, der direkt unterhalb von
	 *         basUrl liegt. Standardwert ist "data".
	 */
	public String getCollectionUrl() {
		return this.collectionUrl;
	}

	/**
	 * @param subPath Ein optionaler URL-Unterpfad unterhalb der ID des
	 *          Forschungsdaten-Objektes. Zur Strukturierung großer Anzahlen von
	 *          Dateien pro Forschungsdaten-Objekt. Standardwert: leer
	 */
	public void setSubPath(String subPath) {
		this.subPath = subPath;
	}

	/**
	 * @return Ein optionaler URL-Unterpfad unterhalb der ID des
	 *         Forschungsdaten-Objektes. Zur Strukturierung großer Anzahlen von
	 *         Dateien pro Forschungsdaten-Objekt. Standardwert: leer
	 */
	public String getSubPath() {
		return this.subPath;
	}

	/**
	 * @param filename Der Dateiname der Ressource (ohne Pfadangaben, aber mit
	 *          Dateiendung)
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @return Der Dateiname der Ressource (ohne Pfadangaben, aber mit
	 *         Dateiendung)
	 */
	public String getFilename() {
		return this.filename;
	}

	/**
	 * @param resourcePid eine PID, die man für das zugehörige to.science-Objekt
	 *          vergeben will. Sie kann leer sein (dann wird das System eine PID
	 *          generieren).
	 */
	public void setResourcePid(String resourcePid) {
		this.resourcePid = resourcePid;
	}

	/**
	 * @return eine PID, die man für das zugehörige to.science-Objekt vergeben
	 *         will. Sie kann leer sein.
	 */
	public String getResourcePid() {
		return this.resourcePid;
	}

	/**
	 * @return die URL, unter der diese Forschungsdatenressource zu erreichen ist.
	 */
	public String getUrlString() {
		if (urlString == null) {
			buildUrlString();
		}
		return this.urlString;
	}

	/**
	 * @return Ist die Ressource unter der URL urlString verfügbar (ja/nein) ?
	 */
	public boolean getAvailable() {
		return this.available;
	}

	/**
	 * @return Der contentType der Ressource. ContentType ist so etwas wie
	 *         "text/plan", "text/xml", "application/json" oder
	 *         "application/octet-stream". Bei Dateien (Typ "File") entspricht es
	 *         dem MimeType.
	 */
	public String getContentType() {
		if (this.contentType == null) {
			try {
				checkUrlExists();
			} catch (Exception e) {
				ApplicationLogger.warn(
						"ContentType konnte nicht ermittelt werden ! Returning empty String.");
				return "";
			}
		}
		return this.contentType;
	}

	/**
	 * Methoden dieser Klasse
	 */

	private void setDefaultCollectionUrl() {
		this.collectionUrl = Globals.researchDataCollectionUrl;
	}

	/**
	 * Diese Methode baut die URL, unter der die Ressource erreichbar ist,
	 * zusammen. Aus baseUrl, collectionUrl, parentPid, subPath und filename wird
	 * eine URL zusammengebaut, die auf die Ressource verweist.
	 */
	public void buildUrlString() {
		ApplicationLogger
				.debug("Begin building url string for filename " + filename);
		urlString = new String(baseUrl + "/" + collectionUrl + "/"
				+ Globals.defaultNamespace + ":" + parentPid);
		if (subPath != null && !subPath.isEmpty()) {
			urlString = urlString.concat("/" + subPath);
		}
		urlString = urlString.concat("/" + filename);
		ApplicationLogger.info("Built url string: " + urlString);
	}

	/**
	 * Diese Methode führt Konsistenzprüfungen aus. Bei inkonsistenten
	 * Eigenschaften der Klasseninstanz wird eine Ausnahme geschmissen.
	 */
	public void doConsistencyChecks() {
		try {
			ApplicationLogger.debug(
					"Perfomring consistency checks on research data resource for parent PID "
							+ parentNode.getPid());

			if (!"researchData".equals(parentNode.getContentType())) {
				throw new HttpArchiveException(400, parentNode.getContentType()
						+ " is not supported. Operation works only on to.science contentType:\"researchData\".");
			}

			checkUrlExists();
			if (available == false) {
				throw new RuntimeException(
						"Ressource " + filename + " nicht erreichbar !");
			}

		} catch (Exception e) {
			ApplicationLogger.error(
					"Konsistenzprüfung für Forschungsdaten-Ressource {} an Parent PID {} fehlgeschlagen !",
					filename, parentNode.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Prüfung, ob die Datei/Ressource existiert. Dazu wird ein HEAD Request auf
	 * die vollständige URL, die auf die Ressource verweist, gemacht. Ergebnis
	 * wird in der Variable "available" hinterlegt.
	 */
	public void checkUrlExists() {
		urlString = getUrlString();
		URL url = null;
		available = false;
		HttpURLConnection urlConnection = null;
		System.setProperty("http.keepAlive", "false");
		try {
			url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("HEAD");
			urlConnection.connect();
			ApplicationLogger.info("Forschungsdatenressource ist unter der URL "
					+ urlString + " verfügbar, yeah!");
			contentType = urlConnection.getHeaderField("Content-Type");
			available = true;
		} catch (MalformedURLException e) {
			ApplicationLogger.warn("Die für die Datei " + filename
					+ " ermittelte URL, " + urlString + ", ist fehlgeformt!");
			// e.printStackTrace();
			throw new IllegalStateException("Bad URL: " + url, e);
		} catch (IOException e) {
			ApplicationLogger.info("Die Forschungsdatenressource " + filename
					+ " ist unter der URL " + urlString + " nicht erreichbar, oh no!", e);
			available = false;
			// e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
	}

}
