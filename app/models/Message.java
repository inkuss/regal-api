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

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
@SuppressWarnings("javadoc")
@XmlRootElement(name = "message")
public class Message {
    String error;
    String text;
    int code;

    public Message(String text) {
	this.text = text;
	this.code = 200;
    }

    public Message(String text, int code) {
	this.text = text;
	this.code = code;
    }

    public Message(String text, Throwable t, int code) {
	this.error = getStackTrace(t);
	this.text = text;
	this.code = code;
    }

    public Message(Throwable t, int code) {
	this.error = getStackTrace(t);
	text = t.getMessage();
	this.code = code;
    }

    public String getText() {
	return text;
    }

    public void setText(String text) {
	this.text = text;
    }

    public int getCode() {
	return code;
    }

    public void setCode(int code) {
	this.code = code;
    }

    public String getError() {
	return error;
    }

    public void setError(String error) {
	this.error = error;
    }

    private String getStackTrace(Throwable t) {
	StringWriter sw = new StringWriter();
	t.printStackTrace(new PrintWriter(sw));
	return sw.toString();
    }

    @Override
    public String toString() {
	ObjectMapper mapper = JsonUtil.mapper();
	StringWriter w = new StringWriter();
	try {
	    mapper.writeValue(w, this);
	} catch (Exception e) {
	    e.printStackTrace();
	    return super.toString();
	}
	return w.toString();
    }

}
