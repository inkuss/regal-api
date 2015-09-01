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
package actions;

import models.User;
import play.Play;
import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 * @see <a
 *      href="http://digitalsanctum.com/2012/06/07/basic-authentication-in-the-play-framework-using-custom-action-annotation/http://digitalsanctum.com/2012/06/07/basic-authentication-in-the-play-framework-using-custom-action-annotation">digitalsanctum</a>
 */
public class BasicAuthAction extends Action<BasicAuth> {

    private static final String AUTHORIZATION = "authorization";
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String REALM = "Basic realm=\"Please enter edoweb-anonymous with any password for guest access\"";

    @Override
    public F.Promise<Result> call(Http.Context context) throws Throwable {

	String authHeader = context.request().getHeader(AUTHORIZATION);
	if (authHeader == null) {
	    return unauthorized(context);
	}

	String auth = authHeader.substring(6);
	byte[] decodedAuth = new sun.misc.BASE64Decoder().decodeBuffer(auth);
	String[] credString = new String(decodedAuth, "UTF-8").split(":");

	if (credString == null || credString.length != 2) {
	    return unauthorized(context);
	}

	String username = credString[0];
	String password = credString[1];

	User authUser = getAuthenticatedUser(username, password);
	if (authUser != null) {
	    context.args.put("role", authUser.getRole());
	    return delegate.call(context);
	}
	return unauthorized(context);
    }

    private User getAuthenticatedUser(String username, String password) {
	try {
	    String userImpl = Play.application().configuration()
		    .getString("regal-api.userImpl");
	    Class<?> cl = null;
	    cl = Class.forName(userImpl);
	    return ((User) cl.newInstance()).authenticate(username, password);
	} catch (Throwable e) {
	    play.Logger.debug("", e);
	    return null;
	}

    }

    private play.libs.F.Promise<Result> unauthorized(Http.Context context) {
	context.response().setHeader(WWW_AUTHENTICATE, REALM);
	return F.Promise.promise(new F.Function0<Result>() {
	    @Override
	    public Result apply() throws Throwable {
		return unauthorized();
	    }
	});
    }
}