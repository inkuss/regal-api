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
package models;

import helper.Heritrix;
import helper.MyEtikettMaker;
import helper.TaskManager;
import helper.Wget;
import helper.oai.WglContributor;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import play.Play;
import archive.fedora.FedoraFacade;
import archive.fedora.FedoraFactory;
import archive.search.SearchFacade;
import authenticate.UserDB;
import de.hbz.lobid.helper.EtikettMakerInterface;

import com.google.common.net.InetAddresses;

/**
 * Global Settings and accessors for Elasticsearch and Fedora
 * 
 * @author Jan Schnasse
 *
 */
public class Globals {

	public static UserDB users = UserDB.getInstance();

	/**
	 * the server that hosts this app
	 */
	public static String server =
			Play.application().configuration().getString("regal-api.serverName");

	/**
	 * the server to where urns should point
	 */
	public static String urnbase =
			Play.application().configuration().getString("regal-api.urnbase");

	/**
	 * The Elasticsearch cluster
	 */
	public static String escluster =
			Play.application().configuration().getString("regal-api.escluster");

	/**
	 * An internal address available for requests from localhost
	 */
	public static String fedoraIntern =
			Play.application().configuration().getString("regal-api.fedoraIntern");

	/**
	 * a list of namespaces to create indexes for
	 */
	public static String[] namespaces = Play.application().configuration()
			.getString("regal-api.namespace").split("\\s*,[,\\s]*");

	public static String defaultNamespace = namespaces[0];

	/**
	 * defines how objects will be referenced internally
	 */
	public static boolean useHttpUris = false;

	/**
	 * password to access fedora
	 */
	public static String fedoraPassword = Play.application().configuration()
			.getString("regal-api.fedoraUserPassword");
	/**
	 * user to access fedora
	 */
	public static String fedoraUser =
			Play.application().configuration().getString("regal-api.fedoraUser");

	/**
	 * A config file for elasticsearch. Must be located at conf/ directory
	 */
	public static String elasticsearchSettings = "public-index-config.json";

	/**
	 * a globally available entry to elasticsearch
	 */
	public static SearchFacade search =
			new SearchFacade(Globals.escluster, Globals.namespaces);

	/**
	 * a globally available entry to Fedora
	 */
	public static FedoraFacade fedora = FedoraFactory.getFedoraImpl(
			Globals.fedoraIntern, Globals.fedoraUser, Globals.fedoraPassword);

	/**
	 * register jobs at taskManager to gain regular executions
	 */
	public static TaskManager taskManager = new TaskManager();

	/**
	 * defines a protocol used by this app
	 */
	public static String protocol =
			Play.application().configuration().getString("regal-api.protocol");

	/**
	 * Urn resolver used for testing urn resolving
	 * 
	 */
	public static String urnResolverAddress = Play.application().configuration()
			.getString("regal-api.urnResolverAddress");
	/**
	 * Address of oai provider to test if mabxml is provided properly
	 * 
	 */
	public static String oaiMabXmlAddress = Play.application().configuration()
			.getString("regal-api.oaiMabXmlAddress");
	/**
	 * Catalog address to test if resource is in catalog
	 */
	public static String alephAddress =
			Play.application().configuration().getString("regal-api.alephAddress");
	/**
	 * Lobid address to test if resource is in lobid
	 */
	public static String lobidAddress =
			Play.application().configuration().getString("regal-api.lobidAddress");
	/**
	 * Digitool address to link back to old digitool resources
	 */
	public static String digitoolAddress =
			Play.application().configuration().getString("regal-api.digitoolAddress");

	/**
	 * if set a urn will be coined and registered at the oai provider
	 */
	public static String urnTask =
			Play.application().configuration().getString("regal-api.urnTask");

	/**
	 * This task performs calls against datacite doi api
	 */
	public static String doiTask =
			Play.application().configuration().getString("regal-api.doiTask");

	/**
	 * if set the application will log a message on the defined interval
	 */
	public static String heartbeatTask =
			Play.application().configuration().getString("regal-api.heartbeatTask");

	/**
	 * A urn subnamespace that belongs to this application
	 */
	public static String urnSnid =
			Play.application().configuration().getString("regal-api.urnSnid");

	/**
	 * The type for fulltext-extracts made by pdfbox
	 */
	public static final String PDFBOX_OCR_TYPE = "pdfbox-ocr";

	/**
	 * prefix for fulltext index
	 */
	public static final String PDFBOX_OCR_INDEX_PREF = "fulltext_";

	/**
	 * prefix used for public es index
	 */
	public static final String PUBLIC_INDEX_PREF = "public_";

	/**
	 * Global heritrix instance
	 */
	public static Heritrix heritrix = new Heritrix();

	/**
	 * Global wget instance
	 */
	public static Wget wget = new Wget();

	/**
	 * Url to heritrix data directory
	 */
	public static String heritrixData = Play.application().configuration()
			.getString("regal-api.heritrix.dataUrl");

	/**
	 * Url to wget data directory
	 */
	public static String wgetData =
			Play.application().configuration().getString("regal-api.wget.dataUrl");

	/**
	 * Datacite provides a service for minting Dois. Configure your user here.
	 */
	public static String dataCiteUser =
			Play.application().configuration().getString("regal-api.dataciteUser");

	/**
	 * Datacite provides a service for minting Dois. Configure your password here.
	 */
	public static String dataCitePasswd = Play.application().configuration()
			.getString("regal-api.datacitePassword");
	/**
	 * Test Prefix 10.5072 Productive Prefix 10.4126
	 */
	public static String doiPrefix =
			Play.application().configuration().getString("regal-api.doiPrefix");
	/**
	 * Keystore Location for regal https connections
	 */
	public static String keystoreLocation = Play.application().configuration()
			.getString("regal-api.keystoreLocation");
	/**
	 * Keystore password for regal https connections
	 */
	public static String keystorePassword = Play.application().configuration()
			.getString("regal-api.keystorePassword");

	/**
	 * The setName for providing oai records to aleph
	 */
	public static String alephSetName =
			Play.application().configuration().getString("regal-api.alephSetName");

	/**
	 * Read cron expression for the webgatherer
	 */
	public static String webgatherTask =
			Play.application().configuration().getString("regal-api.webgatherTask");

	/*
	 * The setName for providing oai records to aleph
	 */
	public static Map<String, String> ipWhiteList =
			buildIpList(Play.application().configuration()
					.getString("regal-api.ipWhiteList").split("\\s*,[,\\s]*"));

	/**
	 * This formatter shall be used wherever dates are written
	 */
	public static SimpleDateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	/**
	 * the string for the mab field
	 */
	public static String mab655x =
			Play.application().configuration().getString("regal-api.mab655x");

	public static String doiResolverAddress = "https://dx.doi.org/";

	public static String etikettUrl =
			Play.application().configuration().getString("regal-api.etikett.url");

	public static String etikettUser =
			Play.application().configuration().getString("regal-api.etikett.user");
	public static String etikettPwd =
			Play.application().configuration().getString("regal-api.etikett.pwd");

	/**
	 * @param ipWhiteList
	 * @return a Map with ips
	 */
	public static Map<String, String> buildIpList(String[] ipWhiteList) {
		Map<String, String> ips = new HashMap<String, String>();
		for (String address : ipWhiteList) {
			if (InetAddresses.isInetAddress(address)) {
				ips.put(address, address);
			} else {
				ips.putAll(computeRange(address));
			}
		}
		play.Logger.info(
				"The following IPs can access restricted data with anonymous login: "
						+ ips);
		return ips;
	}

	private static Map<String, String> computeRange(String address) {
		Map<String, String> ips = new HashMap<String, String>();
		if (address.matches("^(?:[0-9]{1,3}\\.){2}[0-9]{1,3}$")) {
			for (int i = 0; i < 255; i++)
				ips.put(address + "." + i, address + "." + i);
		}
		return ips;
	}

	/**
	 * labels etc.
	 */
	public static EtikettMakerInterface profile = new MyEtikettMaker();

	/**
	 * @return the port of the play application
	 */
	public static int getPort() {
		try {
			return play.Play.application().configuration().getInt("http.port");
		} catch (Exception e) {
			return 9000;
		}
	}

	public static String rechercheUrlPrefix = Play.application().configuration()
			.getString("regal-api.rechercheUrl.prefix");
	public static String rechercheUrlSuffix = Play.application().configuration()
			.getString("regal-api.rechercheUrl.suffix");

	public static WglContributor wglContributor = new WglContributor();

	public static String thumbyUrl =
			Play.application().configuration().getString("regal-api.thumby.url");
	public static String zettelUrl =
			Play.application().configuration().getString("regal-api.zettel.url");
	public static String deepzoomeUrl =
			Play.application().configuration().getString("regal-api.deepzoom.url");
	public static String webharvestsDataDir = Play.application().configuration()
			.getString("regal-api.webharvests.dataDir");
	public static String webharvestsDataUrl = Play.application().configuration()
			.getString("regal-api.webharvests.dataUrl");
}
