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
import helper.Globals;
import play.Application;
import play.GlobalSettings;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;
import play.mvc.SimpleResult;
import static play.mvc.Results.*;

import java.lang.reflect.Method;

/**
 * @author Jan Schnasse
 *
 */
public class Global extends GlobalSettings {
    @Override
    public void onStart(Application app) {
	play.Logger.info("Application has started");
	Globals.search.init(Globals.namespaces);
    }

    @Override
    public void onStop(Application app) {
	play.Logger.info("Application shutdown...");
    }

    public Promise<SimpleResult> onHandlerNotFound(RequestHeader request) {
	return Promise.<SimpleResult> pure(notFound("Action not found "
		+ request.uri()));
    }

    @SuppressWarnings("rawtypes")
    public Action onRequest(Request request, Method actionMethod) {
	play.Logger.debug(request.toString());
	return super.onRequest(request, actionMethod);
    }
}
