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

import helper.DataciteMapper;
import helper.HttpArchiveException;
import helper.JsonMapper;
import helper.PdfText;
import helper.oai.JsonLDMapper;
import helper.oai.ModsMapper;
import helper.oai.OpenAireMapper;
import helper.oai.WglMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import models.DataciteRecord;
import models.DublinCoreData;
import models.Globals;
import models.MabRecord;
import models.Node;
import models.OpenAireData;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Element;

import archive.fedora.CopyUtils;
import archive.fedora.XmlUtils;
import converter.mab.RegalToMabMapper;

/**
 * @author Jan Schnasse
 *
 */
public class Transform {

	/**
	 * @param node pid with namespace:pid
	 * @return a aleph mab xml representation
	 * @throws UnsupportedEncodingException
	 */
	public MabRecord aleph(Node node) {
		try {
			RegalToMabMapper mapper = new RegalToMabMapper();
			MabRecord record;
			record = mapper.map(
					new ByteArrayInputStream(node.getMetadata2().getBytes("utf-8")),
					node.getPid());
			record.httpAdresse = Globals.urnbase + node.getPid();
			record.doi = node.getDoi();
			if (node.hasUrn())
				record.urn = node.getUrn();
			else if (node.hasUrnInMetadata())
				record.urn = node.getUrnFromMetadata();
			return record;
		} catch (UnsupportedEncodingException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * @param pid pid with namespace:pid
	 * @return a aleph mab xml representation
	 * @throws UnsupportedEncodingException
	 */
	public MabRecord aleph(String pid) {
		return aleph(new Read().readNode(pid));
	}

	/**
	 * @param pid pid with namespace:pid
	 * @return a URL to a pdfa conversion
	 */
	public String getPdfaUrl(String pid) {
		return getPdfaUrl(new Read().readNode(pid));
	}

	/**
	 * @param node a node with a pdf data stream
	 * @return a URL to a PDF/A Conversion
	 */
	public String getPdfaUrl(Node node) {
		String redirectUrl = null;
		try {
			String dataUri = getHttpDataUri(node);
			URL pdfaConverter = new URL(
					"http://nyx.hbz-nrw.de/pdfa/api/convertFromUrl?inputFile=" + dataUri);
			HttpURLConnection connection =
					(HttpURLConnection) pdfaConverter.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Accept", "application/xml");
			Element root = XmlUtils.getDocument(connection.getInputStream());
			List<Element> elements =
					XmlUtils.getElements("//resultFileUrl", root, null);
			if (elements.size() != 1) {
				throw new HttpArchiveException(500,
						"PDFa conversion returns wrong numbers of resultFileUrls: "
								+ elements.size());
			}
			redirectUrl = elements.get(0).getTextContent();
			return redirectUrl;
		} catch (MalformedURLException e) {
			throw new HttpArchiveException(500, e);
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}

	}

	private String getHttpDataUri(Node node) {
		return Globals.useHttpUris ? node.getDataUri()
				: Globals.protocol + Globals.server + "/resource/" + node.getDataUri();
	}

	private String getInternalDataUri(Node node) {
		return "http://localhost:" + Globals.getPort() + "/resource/"
				+ node.getDataUri();
	}

	/**
	 * @param node
	 * @return a epicur display for the pid
	 */
	public String epicur(Node node) {
		String url = Globals.urnbase + node.getPid();

		String urn = null;
		if (node.hasUrn())
			urn = node.getUrn();
		else if (node.hasUrnInMetadata())
			urn = node.getUrnFromMetadata();
		String status = "urn_new";
		String result = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<epicur xmlns=\"urn:nbn:de:1111-2004033116\" xsi:schemaLocation=\"urn:nbn:de:1111-2004033116 http://www.persistent-identifier.de/xepicur/version1.0/xepicur.xsd\">\n"
				+ "\t<administrative_data>\n" + "\t\t<delivery>\n"
				+ "\t\t\t<update_status type=\"" + status + "\"></update_status>\n"
				+ "\t\t</delivery>\n" + "\t</administrative_data>\n" + "<record>\n"
				+ "\t<identifier scheme=\"urn:nbn:de\">" + urn + "</identifier>\n"
				+ "\t<resource>\n" + "\t\t<identifier role=\"primary\" scheme=\"url\">"
				+ url + "</identifier>\n"
				+ "\t\t<format scheme=\"imt\">text/html</format>\n" + "\t</resource>"
				+ "</record>\n" + "</epicur> ";
		return result;
	}

	/**
	 * @param pid The pid of an object
	 * @return a wgl mapping
	 */
	public DublinCoreData wgl(String pid) {
		Node node = new Read().readNode(pid);
		String uri = Globals.urnbase + node.getPid();
		DublinCoreData data = new WglMapper(node, uri).getData();
		return data;
	}

	/**
	 * @param pid The pid of an object
	 * @return a openaire mapping
	 */
	public String openaire(String pid) {
		Node node = new Read().readNode(pid);
		String uri = Globals.urnbase + node.getPid();
		String result = new OpenAireMapper(node, uri).getData();
		return result;
	}

	/**
	 * @param pid The pid of an object
	 * @return a openaire mapping
	 */
	public String mods(String pid) {
		Node node = new Read().readNode(pid);
		String uri = Globals.urnbase + node.getPid();
		String result = new ModsMapper(node, uri).getData();
		return result;
	}

	/**
	 * @param pid the pid of a node with pdf data
	 * @return the plain text content of the pdf
	 */
	public Node pdfbox(String pid) {
		return pdfbox(new Read().readNode(pid));
	}

	/**
	 * @param node the node with pdf data
	 * @return the plain text content of the pdf
	 */
	public Node pdfbox(final Node node) {
		Node result = node;
		String pid = node.getPid();
		String mimeType = node.getMimeType();
		if (mimeType == null)
			throw new HttpArchiveException(404,
					"The node " + pid + " does not provide a mime type. No data found!");
		if (mimeType.compareTo("application/pdf") != 0)
			throw new HttpArchiveException(406,
					"Wrong mime type. Cannot extract text from " + mimeType);
		InputStream content = null;

		try {
			URL url = new URL(getInternalDataUri(node));
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			PdfText pdf = new PdfText();
			result.addFulltext(pdf.toString(connection.getInputStream()));
		} catch (MalformedURLException e) {
			throw new HttpArchiveException(500, e);
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		} finally {
			if (content != null)
				try {
					content.close();
				} catch (IOException e) {
					play.Logger.warn("", e);
				}
		}
		return result;
	}

	/**
	 * @param node the node with pdf data
	 * @return the plain text content of the pdf
	 */
	public String itext(Node node) {
		String pid = node.getPid();

		String mimeType = node.getMimeType();
		if (mimeType == null)
			throw new HttpArchiveException(404, "The node " + pid
					+ " does not provide a mime type. It may not even contain data at all!");
		if (mimeType.compareTo("application/pdf") != 0)
			throw new HttpArchiveException(406,
					"Wrong mime type. Cannot extract text from " + mimeType);
		URL content = null;
		try {
			content = new URL(getHttpDataUri(node));
			File pdfFile = CopyUtils.download(content);
			PdfText pdf = new PdfText();
			return pdf.itext(pdfFile);
		} catch (MalformedURLException e) {
			throw new HttpArchiveException(500, e);
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * @param node
	 * @return an xml string for datacite
	 */
	public String datacite(Node node, String doi) {
		DataciteRecord dc = DataciteMapper.getDataciteRecord(doi, node.getLd2());
		return dc.toString();
	}

}
