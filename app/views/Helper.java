package views;

import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

import actions.Read;
import helper.MyEtikettMaker;
import models.Gatherconf;
import models.Globals;
import models.Link;
import models.Node;

public class Helper {

	public static List<String> getOrderedListOfKeysFromContext(
			Map<String, Object> context) {
		return (List<String>) context.entrySet().stream()
				.sorted(new Comparator<Map.Entry<String, Object>>() {
					public int compare(Map.Entry<String, Object> o1,
							Map.Entry<String, Object> o2) {

						String s1 =
								(String) ((Map<String, Object>) o1.getValue()).get("weight");
						if (s1 == null)
							s1 = "99999";
						String s2 =
								(String) ((Map<String, Object>) o2.getValue()).get("weight");
						if (s2 == null)
							s2 = "99999";
						int i1 = Integer.parseInt(s1);
						int i2 = Integer.parseInt(s2);
						return i1 - i2;
					}
				}).map(e -> e.getKey()).collect(Collectors.toList());
	}

	public static String getDataLink(Map<String, Object> hit) {
		Object parts = hit.get("hasPart");
		if (parts instanceof List) {
			Object part = ((List) parts).get(0);
			if (part instanceof Map) {
				String id = (String) ((Map<String, Object>) part).get("@id");
				return ("/resource/" + id + "/data");
			}
		}
		String id = (String) hit.get("@id");
		return "/resource/" + id + "/data";
	}

	public static String getTitle(Map<String, Object> hit) {
		Object t = hit.get("title");
		if (t instanceof List) {
			Object l = ((List) t).get(0);
			if (l instanceof String) {
				return l + "";
			}

		}
		if (t instanceof Set) {
			return ((Set) t).iterator().next() + "";
		}

		return t + "";
	}

	public static String getSeries(Set<Map<String, Object>> hits) {
		return getBibliographicParent(hits, "series");
	}

	public static String getMultiVolumeWork(Set<Map<String, Object>> hits) {
		return getBibliographicParent(hits, "multiVolumeWork");
	}

	public static String getBibliographicParent(Set<Map<String, Object>> hits,
			String rel) {
		try {
			StringBuffer result = new StringBuffer();
			for (Map<String, Object> hit : hits) {
				// result.append("" + hit);
				String numbering = (String) hit.get("numbering");
				Map<String, Object> series =
						((Set<Map<String, Object>>) hit.get(rel)).iterator().next();
				String label = (String) series.get("prefLabel");
				String id = (String) series.get("@id");
				id = id.trim();
				String prefix = models.Globals.rechercheUrlPrefix.substring(0,
						models.Globals.rechercheUrlPrefix.length() - 1);
				String internLink = prefix + URLEncoder
						.encode("\"" + id + models.Globals.rechercheUrlSuffix, "utf-8");
				result.append(String.format(
						"<a title=\"Ähnliche Objekte suchen\" href=\"%s\"> %s</a>",
						internLink, label));
				result.append(String.format(
						"<span class=\"separator\">|</span><a href=\"%s\"><span class=\"glyphicon glyphicon-link\"></span></a>, Band %s",
						id, numbering));
			}
			return result.toString();
		} catch (Exception e) {
			play.Logger.warn("", e);
			return "Can't process data";
		}
	}

	public static String getWaybackLink(String pid) {
		try {
			play.Logger.debug("Get Waybacklinkg for " + pid);
			String waybackLink = "";
			Node node = new Read().readNode(pid);
			String confstring = node.getConf();
			if (confstring == null)

				return "../" + pid;

			ObjectMapper mapper = JsonUtil.mapper();
			Gatherconf conf = mapper.readValue(confstring, Gatherconf.class);
			if (conf.getOpenWaybackLink() == null
					|| conf.getOpenWaybackLink().isEmpty()) {
				String owDatestamp =
						new SimpleDateFormat("yyyyMMdd").format(conf.getStartDate());
				conf.setOpenWaybackLink(Globals.heritrix.openwaybackLink + owDatestamp
						+ "/" + conf.getUrl());

			}
			play.Logger.debug(waybackLink);
			waybackLink = conf.getOpenWaybackLink();
			return waybackLink != null ? waybackLink : "../" + pid;
		} catch (Exception e) {
			play.Logger.error("", e);
			return "../" + pid;

		}
	}

	public static List<Map<String, Object>> listContributions(
			Map<String, Object> h) {
		List<Map<String, Object>> result = new ArrayList<>();
		JsonNode hit = new ObjectMapper().valueToTree(h);
		for (JsonNode c : hit.at("/contribution")) {
			String name = c.at("/agent/0/label").asText();
			String role = c.at("/role/0/label").asText();
			String roleUri = c.at("/role/0/@id").asText();
			String uri = c.at("/agent/0/@id").asText();
			if (!"http://id.loc.gov/vocabulary/relators/ctb".equals(roleUri)
					&& !"http://id.loc.gov/vocabulary/relators/cre".equals(roleUri)) {
				Map<String, Object> contribution = new HashMap<>();
				contribution.put("id", uri);
				contribution.put("label", name);
				contribution.put("roleName", role);
				contribution.put("roleId", roleUri);
				result.add(contribution);
			}
		}
		return result;
	}

	public static List<Object> listSubjects(Map<String, Object> h) {
		List<Object> result1 = new ArrayList<>();
		List<Map<String, Object>> result2 = new ArrayList<>();
		JsonNode hit = new ObjectMapper().valueToTree(h);
		play.Logger.debug("hit.asText()=" + hit.asText());
		for (JsonNode c : hit.at("/subject")) {
			if (c.has("componentList")) {
				play.Logger.debug("c.asText()=" + c.asText());
				result1.add(getComponentList(c));
			} else {
				String label = c.at("/label").asText();
				if (label != null && !label.isEmpty()) {
					String uri = c.at("/@id").asText();
					if (uri.contains("rpb#nr")) {
						/**
						 * Schlagworte mit diesem prefix kommen nicht zur Anzeige
						 */
						continue;
					}
					String sourceId = c.at("/source/0/@id").asText();
					String source = c.at("/source/0/label").asText();
					String notation = c.at("/notation").asText();

					if (uri == null || uri.isEmpty()) {
						/**
						 * Wenn keine URI vorhanden ist, kann das label zur Suche benutzt
						 * werden. Der Suchstring wird unter
						 * views/tags/resourceView#displaySubject gebildet.
						 */
						uri = label;
					}
					play.Logger.debug("sourceId=" + sourceId + ", uri=" + uri
							+ ", notation=" + notation);
					Map<String, Object> subject = new HashMap<>();
					subject.put("id", uri);
					subject.put("label", label);
					subject.put("source", source);
					subject.put("sourceId", sourceId);
					subject.put("sourceName", getSubjectSource(sourceId, uri, notation));
					result2.add(subject);
				} else {
					play.Logger.debug("label == null");
					String uri = c.at("/@id").asText();
					if (uri.contains("rpb#nr")) {
						/**
						 * Schlagworte mit diesem prefix kommen nicht zur Anzeige
						 */
						continue;
					}
					String sourceId = c.at("/source/0/@id").asText();
					String source = c.at("/source/0/label").asText();
					String notation = c.at("/notation").asText();

					if (uri == null || uri.isEmpty()) {
						/**
						 * Wenn keine URI vorhanden ist, kann das label zur Suche benutzt
						 * werden. Der Suchstring wird unter
						 * views/tags/resourceView#displaySubject gebildet.
						 */
						uri = label;
					}
					play.Logger.debug("sourceId=" + sourceId + ", uri=" + uri
							+ ", notation=" + notation);
				}
			}
		}

		result1.addAll(result2.stream()
				.sorted((a, b) -> a.get("sourceName").toString()
						.compareTo(b.get("sourceName").toString()))
				.collect(Collectors.toList()));
		if (result1.isEmpty()) {
			for (JsonNode c : hit.at("/subject")) {

				String notation = c.at("/notation").asText();
				String name = c.at("/prefLabel").asText();
				String uri = c.at("/@id").asText();
				if (uri.contains("rpb#nr"))
					continue;
				String sourceId = uri;
				String source = "";

				Map<String, Object> subject = new HashMap<>();
				subject.put("id", uri);
				subject.put("label", name);
				subject.put("source", source);
				subject.put("sourceId", sourceId);
				subject.put("sourceName", getSubjectSource(sourceId, uri, notation));
				result1.add(subject);
			}
		}
		return result1;
	}

	public static String getSubjectSource(String sourceId, String uri,
			String notation) {
		String source = "";
		if (uri == null) {
			return "";
		}
		if ("https://w3id.org/lobid/rpb2".equals(sourceId)) {
			source = "lbz " + getLbzId(uri);
		} else if ("https://w3id.org/lobid/rpb".equals(sourceId)) {
			source = "rpb " + getRPbId(uri);
		} else if ("http://d-nb.info/gnd/4149423-4".equals(sourceId)) {
			if (notation != null && !notation.isEmpty()) {
				source = "ddc " + notation;
			} else {
				source = "ddc " + getDdcId(uri);
			}
		} else if ("http://d-nb.info/gnd/7749153-1".equals(sourceId)
				|| uri.startsWith("http://d-nb.info/gnd/")
				|| uri.startsWith("https://d-nb.info/gnd/")) {
			source = "gnd " + getGndId(uri);
		} else if ("http://purl.org/lobid/nwbib".equals(sourceId)) {
			source = "nwbib " + getNwbibId(uri);
		} else if (uri.startsWith(Globals.protocol + Globals.server + "/adhoc")) {
			source = "lokal";
		} else if (uri.startsWith("http://aims.fao.org/aos/agrovoc")
				|| uri.startsWith("https://aims.fao.org/aos/agrovoc")) {
			source = "agrovoc";
		}
		return source;
	}

	private static String getNwbibId(String uri) {
		try {
			return uri.split("#")[1].substring(1);
		} catch (Exception e) {

		}
		return "";
	}

	private static String getGndId(String uri) {
		try {
			String[] parts = uri.split("/");
			return parts[parts.length - 1];
		} catch (Exception e) {

		}
		return "";
	}

	private static String getDdcId(String uri) {
		try {
			String[] parts = uri.split("/");
			return parts[parts.length - 1];
		} catch (Exception e) {

		}
		return "";
	}

	private static String getRPbId(String uri) {
		try {
			return uri.split("#")[1].substring(1);
		} catch (Exception e) {

		}
		return "";
	}

	private static String getLbzId(String uri) {
		try {
			return uri.split("#")[1].substring(1);
		} catch (Exception e) {

		}
		return "";
	}

	private static List<Map<String, Object>> getComponentList(
			JsonNode componentList) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (JsonNode c : componentList.at("/componentList")) {
			String name = c.at("/label").asText();
			String uri = c.at("/@id").asText();
			String source = c.at("/source/0/label").asText();
			String sourceId = c.at("/source/0/@id").asText();
			String dateOfBirthAndDeath = c.at("/dateOfBirthAndDeath").asText();
			String dateOfDeath = c.at("/dateOfDeath").asText();
			String dateOfBirth = c.at("/dateOfBirth").asText();

			String biographicalDetails = createBiographicalDetails(
					dateOfBirthAndDeath, dateOfBirth, dateOfDeath);
			String notation = c.at("/notation").asText();
			play.Logger.debug(
					"sourceId=" + sourceId + ", uri=" + uri + ", notation=" + notation);
			Map<String, Object> subject = new HashMap<>();
			subject.put("id", uri);
			subject.put("label", name + biographicalDetails);
			subject.put("source", source);
			subject.put("sourceId", sourceId);
			subject.put("sourceName", getSubjectSource(sourceId, uri, notation));

			result.add(subject);
		}
		return result;
	}

	private static String createBiographicalDetails(String dateOfBirthAndDeath,
			String dateOfBirth, String dateOfDeath) {
		StringBuffer result = new StringBuffer();
		if (dateOfBirthAndDeath != null && !dateOfBirthAndDeath.isEmpty())
			return ", " + dateOfBirthAndDeath;

		if (dateOfBirth != null && !dateOfBirth.isEmpty()) {
			result.append(", ");
			result.append(dateOfBirth);
		}
		if (dateOfDeath != null && !dateOfDeath.isEmpty()) {
			if (result.length() == 0) {
				result.append(", ");
			}
			result.append("-" + dateOfDeath);
		}
		return result.toString();
	}

	public static String getRechercheUrl(String uri) {
		try {
			return "" + Globals.rechercheUrlPrefix + ""
					+ URLEncoder.encode(uri, "utf-8") + "" + Globals.rechercheUrlSuffix;
		} catch (Exception e) {
			play.Logger.warn("", e);
		}
		return "" + Globals.rechercheUrlPrefix + "" + uri + ""
				+ Globals.rechercheUrlSuffix;
	}

	public static List<Map<String, Object>> listAuthors(Map<String, Object> h) {
		List<Map<String, Object>> result = new ArrayList<>();
		JsonNode hit = new ObjectMapper().valueToTree(h);
		for (JsonNode c : hit.at("/contribution")) {
			String name = c.at("/agent/0/label").asText();
			String role = c.at("/role/0/label").asText();
			String roleUri = c.at("/role/0/@id").asText();
			String uri = c.at("/agent/0/@id").asText();

			if ("http://id.loc.gov/vocabulary/relators/ctb".equals(roleUri)
					|| "http://id.loc.gov/vocabulary/relators/cre".equals(roleUri)) {
				Map<String, Object> contribution = new HashMap<>();
				contribution.put("id", uri);
				contribution.put("label", name);
				contribution.put("roleName", role);
				contribution.put("roleId", roleUri);
				result.add(contribution);
			}
		}
		return result;
	}

	public static List<Map<String, Object>> listCreators(Map<String, Object> h) {
		List<Map<String, Object>> result = new ArrayList<>();
		JsonNode hit = new ObjectMapper().valueToTree(h);
		String doNotLinkToAdhocUris = Globals.protocol + Globals.server + "/adhoc";
		for (JsonNode c : hit.at("/creator")) {
			String name = c.at("/prefLabel").asText();
			String uri = c.at("/@id").asText();
			Map<String, Object> contribution = new HashMap<>();
			if (!uri.startsWith(doNotLinkToAdhocUris)) {
				contribution.put("id", uri);
			}
			contribution.put("label", name);
			result.add(contribution);
		}
		for (JsonNode c : hit.at("/contributor")) {
			String name = c.at("/prefLabel").asText();
			String uri = c.at("/@id").asText();
			Map<String, Object> contribution = new HashMap<>();
			if (!uri.startsWith(doNotLinkToAdhocUris)) {
				contribution.put("id", uri);
			}
			contribution.put("label", name);
			result.add(contribution);
		}
		return result;
	}

	public static boolean contributionContainsAdditionalFields(
			Map<String, Object> h) {
		JsonNode hit = new ObjectMapper().valueToTree(h);
		for (JsonNode c : hit.at("/contribution")) {
			String roleUri = c.at("/role/0/@id").asText();
			if (!"http://id.loc.gov/vocabulary/relators/ctb".equals(roleUri)
					&& !"http://id.loc.gov/vocabulary/relators/cre".equals(roleUri)) {
				return true;
			}
		}
		return false;
	}

	public static String getContainedIn(Set<Map<String, Object>> containedIn) {
		String label = "http://lobid.org";
		String uri = "http://lobid.org";
		try {
			JsonNode hit = new ObjectMapper().valueToTree(containedIn);
			uri = hit.at("/0/@id").asText();
			label = hit.at("/0/label").asText();
			if (uri != null && !uri.isEmpty()) {
				label = MyEtikettMaker.getLabelFromEtikettWs(uri);
			}
			return "<a href=\"" + uri + "\">" + label + "</a>";
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
		}
		return "<a href=\"" + uri + "\">" + label + "</a>";
	}

	public static String getLobidIsPartOf(Object isPartOf) {
		String label = "http://lobid.org";
		String uri = "http://lobid.org";
		try {
			JsonNode hit = new ObjectMapper().valueToTree(isPartOf);
			uri = hit.at("/hasSuperordinate/0/@id").asText();
			label = hit.at("/hasSuperordinate/0/label").asText();
			if (uri != null && !uri.isEmpty()) {
				label = MyEtikettMaker.getLabelFromEtikettWs(uri);
			}
			String numbering = hit.at("/numbering").asText();
			if (numbering != null && !numbering.isEmpty()) {

				String prefix = models.Globals.rechercheUrlPrefix.substring(0,
						models.Globals.rechercheUrlPrefix.length() - 1);
				String internLink = prefix + URLEncoder
						.encode("\"" + uri + models.Globals.rechercheUrlSuffix, "utf-8");
				String link = String.format(
						"<a title=\"Ähnliche Objekte suchen\" href=\"%s\"> %s</a>",
						internLink, label);
				String externLink = String.format(
						"<a href=\"%s\"><span class=\"glyphicon glyphicon-link\"></span></a>, %s",
						uri, numbering);

				return link + " " + externLink;
			}
			return label;
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
		}
		return "<a href=\"" + uri + "\">" + uri + "</a>";
	}

	public static Map<String, String> getPublicationMap(
			Collection<Map<String, Object>> publ) {
		Map<String, String> result = new HashMap<>();
		for (Map<String, Object> p : publ) {
			JsonNode hit = new ObjectMapper().valueToTree(p);
			String location = hit.at("/location").asText();
			String publishedBy = hit.at("/publishedBy").asText();
			String startDate = hit.at("/startDate").asText();

			// if (startDate != null && !startDate.isEmpty()) {
			// result.put("regal:publishYear", startDate);
			// }
			if (location != null && !location.isEmpty()) {
				result.put("regal:publishLocation", location);
			}
			if (publishedBy != null && !publishedBy.isEmpty()) {
				result.put("regal:publishedBy", publishedBy);
			}
		}
		return result;
	}

	public static Map<String, String> getFile(Map<String, Object> fileMap) {

		String id = "unknown";
		String fileName = "unknown";
		String fileSize = "unknown";
		String md5 = "unknown";
		String fileFormat = "unknown";
		try {
			JsonNode hit = new ObjectMapper().valueToTree(fileMap);
			fileName = hit.at("/fileLabel").asText();
			fileSize = hit.at("/size").asText();
			fileFormat = hit.at("/format").asText();
			md5 = hit.at("/checksum/checksumValue").asText();
			id = hit.at("/@id").asText();
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
		}
		Map<String, String> result = new TreeMap<>();
		result.put("fileName", fileName);
		result.put("fileSize", getPrettySize(fileSize));
		result.put("md5", md5);
		result.put("fileFormat", fileFormat);
		result.put("id", id);
		return result;
	}

	public static String getPrettySize(String fileSize) {
		String hrSize = "";
		DecimalFormat dec = new DecimalFormat("0.00");
		Long size = Long.parseLong(fileSize);
		double k = size / 1024.0;
		double m = size / 1048576.0;
		double g = size / 1073741824.0;
		if (g > 1) {
			hrSize = dec.format(g).concat("GB");
		} else if (m > 1) {
			hrSize = dec.format(m).concat("MB");
		} else if (k > 1) {
			hrSize = dec.format(k).concat("KB");
		} else {
			hrSize = dec.format(size).concat("B");
		}
		return hrSize;
	}

	public static Map<String, String> getLastModified(String pid) {
		Read read = new Read();
		Map<String, String> result = new HashMap<>();
		Node n = read.getLastModifiedChild(read.readNode(pid));
		result.put("id", n.getPid());
		result.put("title", getTitle(n.getLd2()));
		return result;
	}

	public static ViewerInfo getViewerInfo(Node node) {

		try {
			if (node.getFileChecksum() == null) {
				if (node.getPartsSorted() != null && !node.getPartsSorted().isEmpty()) {
					for (Link l : node.getPartsSorted()) {
						Node cn = new Read().internalReadNode(l.getObject());
						if (!"D".equals(cn.getState())
								&& "file".equals(cn.getContentType())) {
							return getViewerInfo(cn);
						}
					}
				}
				return null;
			}

			ViewerInfo info = new ViewerInfo(node);
			return info;
		} catch (Exception e) {
			play.Logger.debug("", e);
			return null;
		}
	}

	public static String getLinkAdressOrNull(String value) {
		for (String n : Globals.namespaces) {
			if (value.startsWith(n + ":")) {
				return "/resource/" + value;
			}
		}
		if (value.startsWith("http"))
			return value;
		return null;
	}
}
