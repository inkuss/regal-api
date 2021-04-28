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
package archive.fedora;

/**
 * Eine Klasse, die die Fedora-Klasse AddDatastream erweitert. Diese Klasse
 * erlaubt das Setzen zusätzlicher Parameter zu dem Datenstrom.
 * 
 * @author I. Kuss, hbz
 *
 */
public class AddDatastream
		extends com.yourmediashelf.fedora.client.request.AddDatastream {

	/**
	 * @param pid persistent identifier of the digital object
	 * @param dsId datastream identifier
	 */
	public AddDatastream(String pid, String dsId) {
		super(pid, dsId);
	}

	/**
	 * The size of the datastream.
	 * 
	 * @param dsSize the size in Bytes of the datastream
	 * @return this builder
	 */
	public AddDatastream dsSize(String dsSize) {
		addQueryParam("dsSize", dsSize);
		return this;
	}

}
