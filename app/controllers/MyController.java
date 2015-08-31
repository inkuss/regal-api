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

import helper.HttpArchiveError;
import helper.HttpArchiveException;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import models.Globals;
import models.Message;
import models.Node;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.message;
import actions.Create;
import actions.Delete;
import actions.Index;
import actions.Modify;
import actions.Read;
import actions.Transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
public class MyController extends Controller {

    /**
     * The admin can do everything
     */
    public final static String ADMIN_ROLE = "edoweb-admin";
    /**
     * The editor sees everything but cannot run some batch processes
     */
    public final static String EDITOR_ROLE = "edoweb-editor";
    /**
     * The reader can read all data flagged with "public","restricted","remote"
     * The reader is not able to perform modifying, creating, or deleting
     * operations
     */
    public final static String READER_ROLE = "edoweb-reader";
    /**
     * The subscriber behaves like the reader but can als access date flagged
     * with "single"
     */
    public final static String SUBSCRIBER_ROLE = "edoweb-subscriber";
    /**
     * The remote user behaves like the reader
     */
    public final static String REMOTE_ROLE = "edoweb-remote";

    /**
     * The anonymous user can read everything flagged with "public".
     */
    public final static String ANONYMOUS_ROLE = "edoweb-anonymous";

    /**
     * private metadata is only visible to admins and editors
     */
    public final static String METADATA_ACCESSOR_PRIVATE = "private";
    /**
     * public metadata is visible to everyone
     */
    public final static String METADATA_ACCESSOR_PUBLIC = "public";

    /**
     * public data is visible to everyone
     */
    public final static String DATA_ACCESSOR_PUBLIC = "public";
    /**
     * private data is visible to admins and editors
     */
    public final static String DATA_ACCESSOR_PRIVATE = "private";
    /**
     * restricted data is visible to admins, editors, readers, subscribers and
     * remotes
     */
    public final static String DATA_ACCESSOR_RESTRICTED = "restricted";
    /**
     * single data is visible to admins, editors, and subscribers
     */
    public final static String DATA_ACCESSOR_SINGLE = "single";
    /**
     * remote data is visible to admins, editors, readers, subscribers and
     * remotes
     */
    public final static String DATA_ACCESSOR_REMOTE = "remote";

    /**
     * a mapper for all
     */
    public static ObjectMapper mapper = JsonUtil.mapper();

    static Read read = new Read();
    static Create create = new Create();
    static Index index = new Index();
    static Modify modify = new Modify();
    static Delete delete = new Delete();
    static Transform transform = new Transform();

    /**
     * @return Html or Json Output
     */
    public static Result AccessDenied() {
	Message msg = new Message("Access Denied!", 401);
	play.Logger.debug("\nResponse: " + msg.toString());
	if (request().accepts("text/html")) {
	    return HtmlMessage(msg);
	} else {
	    return JsonMessage(msg);
	}
    }

    private static void setJsonHeader() {
	response().setHeader("Access-Control-Allow-Origin", "*");
	response().setContentType("application/json");
    }

    /**
     * @param obj
     *            an arbitrary object
     * @return json serialization of obj
     */
    public static Result getJsonResult(Object obj) {
	setJsonHeader();
	try {
	    return ok(json(obj));
	} catch (Exception e) {
	    return internalServerError("Not able to create response!");
	}
    }

    protected static String json(Object obj) {
	try {
	    StringWriter w = new StringWriter();
	    mapper.writeValue(w, obj);
	    String result = w.toString();
	    return result;
	} catch (IOException e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    /**
     * @param msg
     *            the msg will be rendered as html using message view
     * @return a html rendering of msg
     */
    public static Result HtmlMessage(Message msg) {
	play.Logger.debug("\nResponse: " + msg.toString());
	return status(msg.getCode(), message.render(msg.toString()));
    }

    /**
     * @param msg
     *            the msg will be rendered as json
     * @return a json rendering of msg
     */
    public static Result JsonMessage(Message msg) {
	response().setHeader("Access-Control-Allow-Methods",
		"POST, GET, PUT, DELETE");
	response().setHeader("Access-Control-Max-Age", "3600");
	response()
		.setHeader("Access-Control-Allow-Headers",
			"Origin, X-Requested-With, Content-Type, Accept, Authorization, X-Auth-Token");
	response().setHeader("Access-Control-Allow-Credentials", "true");
	response().setHeader("Content-Type", "application/json; charset=utf-8");
	return status(msg.getCode(), msg.toString());
    }

    /**
     * @param accessScheme
     *            the accessScheme of the object
     * @param role
     *            the role of the user
     * @return true if the user is allowed to read the object
     */
    public static boolean readData_accessIsAllowed(String accessScheme,
	    String role) {
	if (!ADMIN_ROLE.equals(role)) {
	    if (DATA_ACCESSOR_PUBLIC.equals(accessScheme)) {
		return true;
	    } else if (DATA_ACCESSOR_RESTRICTED.equals(accessScheme)) {
		if (isWhitelisted(request().remoteAddress())) {
		    play.Logger
			    .info("IP "
				    + request().remoteAddress()
				    + " is white listed. Access to restricted data granted.");
		    return true;
		}
		if (READER_ROLE.equals(role) || SUBSCRIBER_ROLE.equals(role)
			|| REMOTE_ROLE.equals(role)) {
		    if (isWhitelisted(request().getHeader("UserIp"))) {
			play.Logger
				.info("IP "
					+ request().remoteAddress()
					+ " is white listed. Access to restricted data granted.");
			return true;
		    }
		}
		if (EDITOR_ROLE.equals(role)) {
		    return true;
		}
	    } else if (DATA_ACCESSOR_PRIVATE.equals(accessScheme)) {
		if (EDITOR_ROLE.equals(role))
		    return true;
	    } else if (DATA_ACCESSOR_SINGLE.equals(accessScheme)) {
		if (EDITOR_ROLE.equals(role)) {// ||
					       // SUBSCRIBER_ROLE.equals(role))
					       // {
		    return true;
		}
	    } else if (DATA_ACCESSOR_REMOTE.equals(accessScheme)) {
		if (EDITOR_ROLE.equals(role)) { // || REMOTE_ROLE.equals(role)
						// || READER_ROLE.equals(role)
						// ||
						// SUBSCRIBER_ROLE.equals(role))
						// {
		    return true;
		}
	    }
	} else {// if enter here you are admin
	    return true;
	}
	return false;
    }

    private static boolean isWhitelisted(String remoteAddress) {
	return Globals.ipWhiteList.containsKey(remoteAddress);
    }

    /**
     * @param publishScheme
     *            the publishScheme of the object
     * @param role
     *            the role of the user
     * @return true if the user is allowed to read the object
     */
    public static boolean readMetadata_accessIsAllowed(String publishScheme,
	    String role) {
	if (!ADMIN_ROLE.equals(role)) {
	    if (METADATA_ACCESSOR_PUBLIC.equals(publishScheme)) {
		return true;
	    } else if (METADATA_ACCESSOR_PRIVATE.equals(publishScheme)) {
		if (EDITOR_ROLE.equals(role)) {
		    return true;
		}
	    }
	} else {// if enter here you are admin
	    return true;
	}
	return false;
    }

    /**
     * @param role
     *            the role of the user
     * @return true if the user is allowed to modify the object
     */
    public static boolean modifyingAccessIsAllowed(String role) {
	if (ADMIN_ROLE.equals(role) || EDITOR_ROLE.equals(role))
	    return true;
	return false;
    }

    interface NodeAction {
	Result exec(Node node);
    }

    interface Action {
	Result exec(String userId);
    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class ReadMetadataAction {
	Promise<Result> call(String pid, NodeAction ca) {
	    return Promise
		    .promise(() -> {
			try {
			    Node node = null;
			    if (pid != null) {
				node = read.readNode(pid);
				String role = (String) Http.Context.current().args
					.get("role");
				String publishScheme = node.getPublishScheme();
				if (!readMetadata_accessIsAllowed(
					publishScheme, role)) {
				    return AccessDenied();
				}
			    }
			    return ca.exec(node);
			} catch (HttpArchiveException e) {
			    if (request().accepts("text/html")) {
				return HtmlMessage(new Message(e, e.getCode()));
			    }
			    return JsonMessage(new Message(e, e.getCode()));
			} catch (HttpArchiveError e) {
			    if (request().accepts("text/html")) {
				return HtmlMessage(new Message(e, e.getCode()));
			    }
			    return JsonMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
			    if (request().accepts("text/html")) {
				return HtmlMessage(new Message(e, 500));
			    }
			    return JsonMessage(new Message(e, 500));
			}
		    });
	}
    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class ReadDataAction {
	Promise<Result> call(String pid, NodeAction ca) {
	    return Promise.promise(() -> {
		try {
		    Node node = null;
		    if (pid != null) {
			node = read.readNode(pid);
			String role = (String) Http.Context.current().args
				.get("role");
			String accessScheme = node.getAccessScheme();
			if (!readData_accessIsAllowed(accessScheme, role)) {
			    return AccessDenied();
			}
		    }
		    return ca.exec(node);
		} catch (HttpArchiveException e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (HttpArchiveError e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (Exception e) {
		    return JsonMessage(new Message(e, 500));
		}
	    });
	}

    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class ListAction {
	Promise<Result> call(Action ca) {
	    return Promise.promise(() -> {
		try {
		    String role = (String) Http.Context.current().args
			    .get("role");
		    if (!readMetadata_accessIsAllowed("private", role)) {
			return AccessDenied();
		    }
		    return ca.exec(request().getHeader("UserId"));
		} catch (HttpArchiveException e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (HttpArchiveError e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (Exception e) {
		    return JsonMessage(new Message(e, 500));
		}
	    });
	}
    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class ModifyAction {
	Promise<Result> call(String pid, NodeAction ca) {
	    return Promise
		    .promise(() -> {
			String userId = "0";
			try {
			    String role = (String) Http.Context.current().args
				    .get("role");
			    userId = request().getHeader("UserId");
			    play.Logger.debug("Try to access with role: "
				    + role + "and user id " + userId);
			    if (!modifyingAccessIsAllowed(role)) {
				return AccessDenied();
			    }
			    Node node = null;
			    try {
				node = read.internalReadNode(pid);
				node.setLastModifiedBy(userId);
			    } catch (Exception e) {
				play.Logger
					.debug("Try to modify resource that can not be read!",
						e);
			    }

			    Result result = ca.exec(node);
			    if (userId != null && !userId.equals("0")
				    && !userId.equals("1")
				    && !userId.equals("UrnAllocator")) {
				play.Logger.info(json(modify
					.setObjectTimestamp(node)));
			    }
			    return result;
			} catch (HttpArchiveException e) {
			    return JsonMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
			    return JsonMessage(new Message(e, 500));
			}
		    });
	}
    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class CreateAction {
	Promise<Result> call(Action ca) {
	    return Promise.promise(() -> {
		try {
		    String role = (String) Http.Context.current().args
			    .get("role");
		    if (!modifyingAccessIsAllowed(role)) {
			return AccessDenied();
		    }
		    return ca.exec(request().getHeader("UserId"));
		} catch (HttpArchiveException e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (HttpArchiveError e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (Exception e) {
		    return JsonMessage(new Message(e, 500));
		}
	    });
	}
    }

    /**
     * @author Jan Schnasse
     *
     */
    public static class BulkActionAccessor {
	Promise<Result> call(Action ca) {
	    return Promise.promise(() -> {
		try {
		    String role = (String) Http.Context.current().args
			    .get("role");
		    if (!modifyingAccessIsAllowed(role)) {
			return AccessDenied();
		    }
		    return ca.exec(request().getHeader("UserId"));
		} catch (HttpArchiveException e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (HttpArchiveError e) {
		    return JsonMessage(new Message(e, e.getCode()));
		} catch (Exception e) {
		    return JsonMessage(new Message(e, 500));
		}
	    });
	}
    }

    /**
     * @param map
     * @return a pritn of the map
     */
    public static String mapToString(Map<String, String> map) {
	StringBuilder sb = new StringBuilder();
	Iterator<Entry<String, String>> iter = map.entrySet().iterator();
	while (iter.hasNext()) {
	    Entry<String, String> entry = iter.next();
	    sb.append(entry.getKey());
	    sb.append('=').append('"').append(entry.getValue()).append('"');
	    if (iter.hasNext()) {
		sb.append("\n\t'");
	    }
	}
	return sb.toString();

    }

    /**
     * @param d
     *            a string in format "yyyyy-mm-dd"
     * @return a Date representation of the String passed as param
     */
    public static Date createDateFromString(String d) {
	try {
	    SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
	    Date date = dt.parse(d);
	    return date;
	} catch (ParseException e) {
	    throw new HttpArchiveException(400, e);
	} catch (Exception e) {
	    throw new HttpArchiveException(500, e);
	}
    }

    protected static String getCurrentDate() {
	DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	Date date = new Date();
	return dateFormat.format(date);
    }
}
