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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jan Schnasse
 *
 */
@SuppressWarnings("javadoc")
public class Urn implements java.io.Serializable {
	String resolver = Globals.urnResolverAddress;
	String urn = null;
	String resolvesTo = "NONE";
	int resolverStatus = 404;
	boolean success = false;

	/**
	 * @param urn
	 * @param httpUriOfResource
	 */
	public Urn(String urn) {
		this.urn = urn;
	}

	public void init(String httpUriOfResource) {
		try {

			if (getFinalURL(resolver + urn).toString().equals(httpUriOfResource)) {
				play.Logger.debug(
						"final URL(" + resolver + urn + ") equals " + httpUriOfResource);
				success = true;
			} else {
				play.Logger.debug("resolver+urn = " + resolver + urn);
				URL url = new URL(resolver + urn);
				HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setReadTimeout(1000 * 2);
				HttpURLConnection.setFollowRedirects(true);
				con.connect();
				resolverStatus = con.getResponseCode();
				play.Logger.debug("resolverStatus=" + resolverStatus);
				resolvesTo =
						parseAdressFromHtml(con.getInputStream(), httpUriOfResource);
				if (resolvesTo.equals(httpUriOfResource))
					play.Logger.debug(
							"httpUriOfResource=" + httpUriOfResource + "; success = true");
				success = true;
			}
		} catch (Exception e) {

		}
	}

	public String getFinalURL(String url) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setReadTimeout(1000 * 2);
		con.setInstanceFollowRedirects(false);
		con.connect();
		con.getInputStream();
		if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
				|| con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
				|| con.getResponseCode() == 307 || con.getResponseCode() == 303) {
			String redirectUrl = con.getHeaderField("Location");

			return getFinalURL(redirectUrl);
		}
		resolverStatus = con.getResponseCode();
		resolvesTo = con.getURL().toString();
		return url;
	}

	private String parseAdressFromHtml(InputStream inputStream,
			String httpUriOfResource) {
		String pid =
				httpUriOfResource.substring(httpUriOfResource.lastIndexOf('/'));

		try (Scanner scn = new Scanner(inputStream, "UTF-8")) {
			String content = scn.useDelimiter("\\A").next();
			Pattern pattern = Pattern.compile("\"(http://.*" + pid + ")\"");
			Matcher matcher = pattern.matcher(content);
			matcher.find();
			return matcher.group(1);
		}
	}

	public String getUrn() {
		return urn;
	}

	public String getResolvesTo() {
		return resolvesTo;
	}

	public void setResolvesTo(String resolvesTo) {
		this.resolvesTo = resolvesTo;
	}

	public void setUrn(String urn) {
		this.urn = urn;
	}

	public String getResolver() {
		return resolver;
	}

	public int getResolverStatus() {
		return resolverStatus;
	}

	public void setResolverStatus(int responseCode) {
		this.resolverStatus = responseCode;
	}

	public boolean getSuccess() {
		return success;
	}
}
