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
package controllers;

import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import models.Message;
import models.Transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.libs.F.Promise;
import play.mvc.Result;
import actions.BasicAuth;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 *         Api is documented using swagger. See:
 *         https://github.com/wordnik/swagger-ui
 * 
 */
@BasicAuth
@Api(value = "/utils", description = "The utils endpoint provides rpc style methods.")
@SuppressWarnings("javadoc")
public class MyUtils extends MyController {
    final static Logger logger = LoggerFactory.getLogger(MyUtils.class);

    @ApiOperation(produces = "application/json,application/html", nickname = "index", value = "index", notes = "Adds resource to private elasticsearch index", response = List.class, httpMethod = "POST")
    public static Promise<Result> index(@PathParam("pid") String pid,
	    @QueryParam("index") final String indexName) {
	return new IndexAction().call(pid, node -> {
	    String result = index.index(node);
	    return JsonMessage(new Message(result));
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "indexAll", value = "indexAll", notes = "Adds resource to private elasticsearch index", response = List.class, httpMethod = "POST")
    public static Promise<Result> indexAll(
	    @QueryParam("index") final String indexName) {
	return new BulkActionAccessor().call((userId) -> {
	    String indexNameWithDatestamp = indexName + "-" + getCurrentDate();
	    actions.BulkAction bulk = new actions.BulkAction();
	    bulk.execute(indexName, userId, nodes -> {
		return index.indexAll(nodes, indexNameWithDatestamp);
	    });
	    response().setHeader("Transfer-Encoding", "Chunked");
	    return ok(bulk.getChunks());
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "reinitOaisets", value = "reinitOaisets", notes = "Updates the oaisets of all resources", response = List.class, httpMethod = "POST")
    public static Promise<Result> reinitOaisets(
	    @QueryParam("namespace") final String namespace) {
	return new BulkActionAccessor().call((userId) -> {
	    actions.BulkAction bulk = new actions.BulkAction();
	    bulk.execute(namespace, userId, nodes -> {
		return modify.reinitOaiSets(nodes);
	    });
	    response().setHeader("Transfer-Encoding", "Chunked");
	    return ok(bulk.getChunks());
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "lobidifyAll", value = "lobidifyAll", notes = "Updates the bibliographic metadata of all resources", response = List.class, httpMethod = "POST")
    public static Promise<Result> lobidifyAll(
	    @QueryParam("namespace") final String namespace) {
	return new BulkActionAccessor().call((userId) -> {
	    actions.BulkAction bulk = new actions.BulkAction();
	    bulk.execute(namespace, userId, nodes -> {
		return modify.lobidify(nodes);
	    });
	    response().setHeader("Transfer-Encoding", "Chunked");
	    return ok(bulk.getChunks());
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "addUrnToAll", value = "addUrnToAll", notes = "Attempts to add urns to all resources", response = List.class, httpMethod = "POST")
    public static Promise<Result> addUrnToAll(
	    @QueryParam("namespace") final String namespace,
	    @QueryParam("snid") final String snid,
	    @QueryParam("fromBefore") final String fromBefore) {
	return new BulkActionAccessor().call((userId) -> {
	    Date fromBeforeDate = createDateFromString(fromBefore);
	    actions.BulkAction bulk = new actions.BulkAction();
	    bulk.execute(namespace, userId, nodes -> {
		String msg = modify.addUrnToAll(nodes, snid, fromBeforeDate);
		play.Logger.info(msg);
		return msg;
	    });
	    response().setHeader("Transfer-Encoding", "Chunked");
	    return ok(bulk.getChunks());
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "removeFromIndex", value = "removeFromIndex", notes = "Removes resource to elasticsearch index", httpMethod = "DELETE")
    public static Promise<Result> removeFromIndex(@PathParam("pid") String pid) {
	return new ModifyAction().call(pid, node -> {
	    String result = index.remove(node);
	    return JsonMessage(new Message(result));
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "lobidify", value = "lobidify", notes = "Fetches metadata from lobid.org and PUTs it to /metadata.", httpMethod = "POST")
    public static Promise<Result> lobidify(@PathParam("pid") String pid) {
	return new ModifyAction().call(pid, node -> {
	    String result = modify.lobidify(node);
	    return JsonMessage(new Message(result));
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "addUrn", value = "addUrn", notes = "Adds a urn to the /metadata of the resource.", httpMethod = "POST")
    public static Promise<Result> addUrn(@QueryParam("id") final String id,
	    @QueryParam("namespace") final String namespace,
	    @QueryParam("snid") final String snid) {
	return new BulkActionAccessor().call((userId) -> {
	    String result = modify.addUrn(id, namespace, snid, userId);
	    return JsonMessage(new Message(result));
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "replaceUrn", value = "replaceUrn", notes = "Replaces a urn on the /metadata of the resource.", httpMethod = "POST")
    public static Promise<Result> replaceUrn(@QueryParam("id") final String id,
	    @QueryParam("namespace") final String namespace,
	    @QueryParam("snid") final String snid) {
	return new BulkActionAccessor().call((userId) -> {
	    String result = modify.replaceUrn(
		    read.readNode(namespace + ":" + id), snid, userId);
	    return JsonMessage(new Message(result));
	});
    }

    @ApiOperation(produces = "application/json,application/html", nickname = "initContentModels", value = "initContentModels", notes = "Initializes default transformers.", httpMethod = "POST")
    public static Promise<Result> initContentModels(
	    @DefaultValue("") @QueryParam("namespace") String namespace) {
	return new BulkActionAccessor().call((userId) -> {

	    int port = getPort();
	    List<Transformer> transformers = new Vector<Transformer>();
	    transformers.add(new Transformer(namespace + "epicur", "epicur",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "epicur"));
	    transformers.add(new Transformer(namespace + "oaidc", "oaidc",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "oaidc"));
	    transformers.add(new Transformer(namespace + "pdfa", "pdfa",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "pdfa"));
	    transformers.add(new Transformer(namespace + "pdfbox", "pdfbox",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "pdfbox"));
	    transformers.add(new Transformer(namespace + "aleph", "aleph",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "aleph"));
	    transformers.add(new Transformer(namespace + "mets", "mets",
		    "http://edoweb-anonymous:nopwd@" + "localhost:" + port
			    + "/resource/(pid)." + namespace + "mets"));
	    create.contentModelsInit(transformers);
	    String result = "Reinit contentModels " + namespace + "epicur, "
		    + namespace + "oaidc, " + namespace + "pdfa, " + namespace
		    + "pdfbox, " + namespace + "aleph, " + namespace + "mets";
	    return JsonMessage(new Message(result));
	});
    }

    private static int getPort() {
	try {
	    return play.Play.application().configuration().getInt("http.port");
	} catch (Exception e) {
	    return 9000;
	}
    }
}
