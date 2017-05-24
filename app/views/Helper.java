package views;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

}
