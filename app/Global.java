
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
import static play.mvc.Results.notFound;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import helper.DevLoggerContext;
import helper.mail.Mail;
import helper.oai.OaiDispatcher;
import models.Globals;
import play.Application;
import play.GlobalSettings;
import play.Play;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

/**
 * @author Jan Schnasse
 *
 */
public class Global extends GlobalSettings {
	@Override
	public void onStart(Application app) {
		if (play.api.Play.isProd(play.api.Play.current())) {
			startInProductionMode();
		} else {
			// play Framework läuft im Test- oder Entwicklungsmodus
			// Logger-Konfiguration für Entwickler/innen laden
			DevLoggerContext.doConfigure(Play.application().configuration()
					.getString("logger.config.developer"));
		}
	}

	private static void startInProductionMode() {
		try {
			// initializeContentModels();
			initializeSearchIndex();
			initializeTaskManager();
			sendMail();
			play.Logger.info("Success! Regal-API started!");
		} catch (Throwable t) {
			play.Logger.error("Failure at startup!", t);
			play.Logger.error("Fail :-( - Regal-API not properly started!");
			throw new RuntimeException(t);
		}

	}

	private static void sendMail() {
		try {
			// Ersetzen der E-Mail durch eine Log-Meldung bis zu einer umfassenderen
			// Lösung: EDOZWO-996
			// Mail.sendMail("", "New instance of regal-api is starting on " +
			// Globals.server);
			play.Logger
					.info("New instance of regal-api is starting on " + Globals.server);
		} catch (Throwable t) {
			play.Logger.warn("Failure at startup! Could not sent email!");
		}
	}

	private static void initializeTaskManager() {
		try {
			Globals.taskManager.init();
			Globals.taskManager.execute();
		} catch (Throwable t) {
			play.Logger
					.error("Failure at startup! Could not initialize Task Manager!");
			throw new RuntimeException("", t);
		}
	}

	private static void initializeSearchIndex() {
		try {
			Globals.search.init(Globals.namespaces);
		} catch (Throwable t) {
			play.Logger
					.error("Failure at startup! Could not initialize Search Index!");
			throw new RuntimeException("", t);
		}
	}

	private static void initializeContentModels() {
		try {
			for (int i = 0; i < Globals.namespaces.length; i++) {

				play.Logger
						.info("Init fedora content models for " + Globals.namespaces[i]);
				OaiDispatcher.initContentModels(Globals.namespaces[i]);
			}
			play.Logger.info("Init fedora content models for default namespace");
			OaiDispatcher.initContentModels("");
		} catch (Throwable t) {
			play.Logger
					.error("Failure at startup! Could not initialize Content Models!");
			throw new RuntimeException("", t);
		}
	}

	@Override
	public void onStop(Application app) {
		play.Logger.info("Application shutdown...");
		Globals.taskManager.shutdown();
	}

	@Override
	public Promise<Result> onHandlerNotFound(RequestHeader request) {
		return Promise.<Result> pure(notFound("Action not found " + request.uri()));
	}

	@Override
	public Action onRequest(Request request, Method actionMethod) {
		String host = request.getHeader("Host");
		String date = getDate();
		String httpReq = request.toString();
		String agent = request.getHeader("User-Agent");
		String userIp = request.getHeader("UserIp");

		play.Logger.info(String.format("%s %s [%s]  \"%s\" %s", host, userIp, date,
				httpReq, agent));
		play.Logger.debug(
				"\n" + request.toString() + "\n\t" + mapToString(request.headers()));
		return super.onRequest(request, actionMethod);
	}

	private String getDate() {
		SimpleDateFormat simpleDateFormat =
				new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss +SSS");
		return simpleDateFormat.format(new Date());
	}

	private static String mapToString(Map<String, String[]> map) {
		StringBuilder sb = new StringBuilder();
		Iterator<Entry<String, String[]>> iter = map.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, String[]> entry = iter.next();
			sb.append(entry.getKey());
			sb.append('=').append('"');
			sb.append(Arrays.toString(entry.getValue()));
			sb.append('"');
			if (iter.hasNext()) {
				sb.append("\n\t'");
			}
		}
		return sb.toString();

	}
}
