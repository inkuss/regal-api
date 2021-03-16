/*
 * Copyright 2015 hbz NRW (http://www.hbz-nrw.de/)
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

import models.ToScienceObject;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Result;
import play.test.Helpers;
import base.BaseModelTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.wordnik.swagger.core.util.JsonUtil;

import controllers.Resource;

/**
 * @author Jan Schnasse
 *
 */
@SuppressWarnings("javadoc")
public class ApiTest extends BaseModelTest {
	final static Logger logger = LoggerFactory.getLogger(ApiTest.class);

	@Test
	public void objectLifeCycle() {
		String pid = "test:1234567";
		ToScienceObject object = new ToScienceObject();
		object.setContentType("monograph");
		createObject(pid, object, 200);
		readObject(pid, 200);
		purgeObject(pid, 200);
		readObject(pid, 404);
	}

	@Test
	public void addParent() {
		String pid = "test:1234567";
		ToScienceObject object = new ToScienceObject();
		object.setContentType("monograph");
		object.setParentPid("test:1234568");

		String parentPid = "test:1234568";
		ToScienceObject parentObject = new ToScienceObject();
		parentObject.setContentType("monograph");

		createObject(parentPid, parentObject, 200);
		createObject(pid, object, 200);

		purgeObject(pid, 200);
		purgeObject(parentPid, 200);

		readObject(pid, 404);
		readObject(parentPid, 404);
	}

	@Test
	public void deleteHierarchy() {
		String pid = "test:1234567";
		ToScienceObject object = new ToScienceObject();
		object.setContentType("monograph");
		object.setParentPid("test:1234568");

		String parentPid = "test:1234568";
		ToScienceObject parentObject = new ToScienceObject();
		object.setContentType("monograph");

		createObject(parentPid, parentObject, 200);
		createObject(pid, object, 200);

		purgeObject(parentPid, 200);

		readObject(pid, 404);
		readObject(parentPid, 404);
	}

	private Result purgeObject(String pid, int httpStatus) {
		Result result = controllerCall(() -> Resource.deleteResource(pid, "true"));
		Assert.assertEquals(httpStatus, result.status());
		return result;
	}

	private Result readObject(String pid, int httpStatus) {
		Result result = controllerCall(() -> Resource.listResource(pid, ""));
		Assert.assertEquals(httpStatus, result.status());
		return result;
	}

	private Result createObject(String pid, ToScienceObject object, int httpStatus) {
		JsonNode body = JsonUtil.mapper().convertValue(object, JsonNode.class);
		Result result = controllerCall(() -> Resource.updateResource(pid), body);
		Assert.assertEquals(httpStatus, result.status());
		return result;
	}

	@SuppressWarnings("unused")
	private JsonNode asJson(Result result) {
		try {
			return JsonUtil.mapper().readTree(Helpers.contentAsString(result));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
