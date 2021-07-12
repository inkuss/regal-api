/**
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
 * @author: I. Kuss
 */
package models;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.json.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

import archive.fedora.RdfUtils;
import helper.HttpArchiveException;
import helper.JsonMapper;
import play.mvc.Result;

/**
 * Diese Klasse verarbeitet, mappt und modelliert LRMI-Metadaten.
 * 
 * @author Ingolf Kuss
 *
 */
public class LrmiData implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	String content = "";
	Node node = null;
	RDFFormat format = null;

	/**
	 * Setzt den Inhalt der LRMI-Daten
	 * 
	 * @param content Der Inhalt im Format LRMI.json
	 */
	public void setContent(String content) {
		this.content = content;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public void setFormat(RDFFormat format) {
		this.format = format;
	}

	/**
	 * Diese Methode wandelt LRMI.json nach lobid2.json
	 * 
	 * @return Die Daten im Format lobid2.json
	 */
	public String lobidify2() {
		/* ToDo: Hier muss das Mapping von LRMI.json nach lobid2.json hin !!! */
		try {
			// Neues JSON-Objekt anlegen; für lobid2-Daten
			Map<String, Object> rdf = node.getLd2();

			// LRMIDaten nach JSONObject wandeln
			JSONObject jcontent = new JSONObject(content);

			JSONArray arr = jcontent.getJSONArray("@context");
			rdf.put("@context", arr.getString(0));

			String language = arr.getJSONObject(1).getString("@language");
			/*
			 * So etwas anlegen:
			 * "language":[{"@id":"http://id.loc.gov/vocabulary/iso639-2/deu","label":
			 * "Deutsch","prefLabel":"Deutsch"}]
			 */
			// eine Struktur {} anlegen:
			Map<String, Object> languageMap = new TreeMap<>();
			// languageMap.put("@id", "http://id.loc.gov/vocabulary/iso639-2/deu");
			languageMap.put("@id",
					"http://id.loc.gov/vocabulary/iso639-2/" + language);
			// languageMap.put("label", "Deutsch");
			// languageMap.put("prefLabel", "Deutsch");
			List<Map<String, Object>> languages = new ArrayList<>();
			languages.add(languageMap);
			rdf.put("language", languages);

			/*
			 * JsonMapper jsonMapper = new JsonMapper(node);
			 * jsonMapper.postprocessing(rdf); Private Methode ! => diese Methode als
			 * Teil von JsonMapper anlegen ?? => JsonMapper.getLd2WithLrmiData() ??
			 */
			// Verschiedene Rückgabetypen (experimentell):

			// JsonNode childJsonNode = new ObjectMapper().valueToTree(rdf);
			// return childJsonNode;

			// return getJsonResult(rdf) = return Resource.asJson(rdf);

			// String jsonString = JsonUtil.mapper().writeValueAsString(rdf);
			// return jsonString;

			return asRdf(rdf);
		} catch (JSONException je) {
			play.Logger.error("Content could not be mapped!", je);
			throw new RuntimeException("LRMI.json could not be mapped to lobid2.json",
					je);
		}
	}

	private String asRdf(Map<String, Object> result) {
		try {

			String rdf = RdfUtils.readRdfToString(
					new ByteArrayInputStream(json(result).getBytes("utf-8")),
					RDFFormat.JSONLD, format, "");
			return rdf;
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	private static String json(Object obj) {
		try {
			StringWriter w = new StringWriter();
			ObjectMapper mapper = JsonUtil.mapper();
			mapper.writeValue(w, obj);
			String result = w.toString();
			return result;
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

}
