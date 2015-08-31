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
package helper;

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
@SuppressWarnings({ "serial", "javadoc" })
public class HttpArchiveError extends Error {

    int code = 500;

    public HttpArchiveError(int httpCode, String message) {
	super(message);
	code = httpCode;
    }

    public HttpArchiveError(int httpCode, Exception e) {
	super(e);
	code = httpCode;
    }

    public HttpArchiveError(int httpCode) {
	code = httpCode;
    }

    public int getCode() {
	return code;
    }

    public void setCode(int code) {
	this.code = code;
    }

}
