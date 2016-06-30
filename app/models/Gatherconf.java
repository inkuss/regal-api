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
import java.io.StringWriter;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * @author Jan Schnasse
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gatherconf {

    @SuppressWarnings("javadoc")
    public enum Interval {
	annually, halfYearly, quarterly, monthly, weekly, daily, once
    };

    @SuppressWarnings("javadoc")
    public enum RobotsPolicy {
	classic, ignore, obey
    }

    String name;
    boolean active;
    String url;
    int deepness;
    RobotsPolicy robotsPolicy;
    Interval interval;
    Date startDate;
    String localDir;
    String openWaybackLink;
    String id;

    /**
     * Create a new configuration for the webgatherer
     */
    public Gatherconf() {
	url = null;
	active = true;
	deepness = -1;
	robotsPolicy = null;
	interval = null;
	startDate = null;
	name = null;
	openWaybackLink = null;
	id = null;
    }

    public boolean isActive() {
    	return active;
    }
    
    public void setActive(boolean active) {
    	this.active = active;
    }
    
    /**
     * @return the url of the website
     */
    public String getUrl() {
	return url;
    }

    /**
     * @param url
     *            a url to harvest data from
     */
    public void setUrl(String url) {
	this.url = url;
    }

    /**
     * @return number of levels
     */
    public int getDeepness() {
	return deepness;
    }

    /**
     * @param deepness
     *            the number of levels
     */
    public void setDeepness(int deepness) {
	this.deepness = deepness;
    }

    /**
     * @return define how robots.txt will be treated
     */
    public RobotsPolicy getRobotsPolicy() {
	return robotsPolicy;
    }

    /**
     * @param robotsPolicy
     */
    public void setRobotsPolicy(RobotsPolicy robotsPolicy) {
	this.robotsPolicy = robotsPolicy;
    }

    /**
     * @return a harvest interval
     */
    public Interval getInterval() {
	return interval;
    }

    /**
     * @param interval
     *            a harvest interval
     */
    public void setInterval(Interval interval) {
	this.interval = interval;
    }

    /**
     * @return first harvest time
     */
    public Date getStartDate() {
	return startDate;
    }

    /**
     * @param startDate
     */
    public void setStartDate(Date startDate) {
	this.startDate = startDate;
    }

    /**
     * @return the name will be used in heritrix as job name
     */
    public String getName() {
	return name;
    }

    /**
     * @param name
     *            he name will be used in heritrix as job name
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * @return a localDir with information stored by heritrix
     */
    public String getLocalDir() {
	return localDir;
    }

    /**
     * @param localDir
     */
    public void setLocalDir(String localDir) {
	this.localDir = localDir;
    }

    @Override
    public String toString() {
	ObjectMapper mapper = JsonUtil.mapper();
	StringWriter w = new StringWriter();
	try {
	    mapper.writeValue(w, this);
	} catch (Exception e) {
	    return super.toString();
	}
	return w.toString();
    }

    /**
     * @param json
     *            a json representation
     * @return a new Gatherconf build from json
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    @JsonIgnore
    public static Gatherconf create(String json) throws JsonParseException,
	    JsonMappingException, IOException {
	return (Gatherconf) JsonUtil.mapper().readValue(json, Gatherconf.class);
    }

    /**
     * Appropriate link to an openwayback instance to display the harvested warc
     * 
     * @param openWaybackLink
     */
    public void setOpenWaybackLink(String openWaybackLink) {
	this.openWaybackLink = openWaybackLink;
    }

    /**
     * @return allink to an openwayback instance that displays the harvested
     *         warc
     */
    public String getOpenWaybackLink() {
	return openWaybackLink;
    }

    /**
     * @return an internal id
     */
    public String getId() {
	return id;
    }

    /**
     * @param id
     *            an internal id
     */
    public void setId(String id) {
	this.id = id;
    }
}
