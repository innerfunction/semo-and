// Copyright 2016 InnerFunction Ltd.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License
package com.innerfunction.semo.content;

import com.innerfunction.q.Q;

import java.util.Map;

/**
 * Created by juliangoacher on 08/07/16.
 */
public class HTTPClient {

    static class Response {}

    /** A wrapper for a discrete HTTP action. */
    interface Action {
        /** Submit the action. */
        Q.Promise<Response> submit();
    }

    /**
     * An interface to be implemented by classes providing authentication related functionality.
     */
    public interface AuthenticationDelegate {
        /** Test whether a response represents an authentication error. */
        boolean isAuthenticationErrorResponse(HTTPClient client, Response response);
        /** Perform a reauthentication. */
        Q.Promise<Response> reauthenticateUsingHTTPClient(HTTPClient client);
    }

    private AuthenticationDelegate authenticationDelegate;

    public void setAuthenticationDelegate(AuthenticationDelegate delegate) {
        this.authenticationDelegate = delegate;
    }

    public Q.Promise<Response> get(String url) {
        return null;
    }

    public Q.Promise<Response> get(String url, Map<String,Object> data) {
        return null;
    }

    public Q.Promise<Response> getFile(String url) {
        return null;
    }

    public Q.Promise<Response> post(String url, Map<String,Object> data) {

    }

    public Q.Promise<Response> submit(String method, String url, Map<String,Object> data) {
        if( "POST".equals( method ) ) {
            return post( url, data );
        }
        return get( url, data );
    }

    private boolean isAuthenticationErrorResponse(Response response) {
        if( authenticationDelegate != null ) {
            return authenticationDelegate.isAuthenticationErrorResponse( this, response );
        }
        return false;
    }

    private Q.Promise<Response> reauthenticate() {
        if( authenticationDelegate != null ) {
            return authenticationDelegate.reauthenticateUsingHTTPClient( this );
        }
        return Q.reject("Authentication delegate not available");
    }

    /**
     * Submit an HTTP action and handle authentication failures.
     */
    private Q.Promise<Response> submit(final Action action) {
        final Q.Promise<Response> promise = new Q.Promise<>();
        // Submit the action.
        action.submit()
            .then( new Q.Promise.Callback<Response,Response>() {
                public Response result(Response response) {
                    // Check for authentication failures.
                    if( isAuthenticationErrorResponse( response ) ) {
                        // Try to reauthenticate and then resubmit the original request.
                        reauthenticate()
                            .then(new Q.Promise.Callback<Response, Response>() {
                                public Response result(Response response) {
                                    promise.resolve( action.submit() );
                                    return response;
                                }
                            })
                            .error(new Q.Promise.ErrorCallback() {
                                public void error(Exception e) {
                                    promise.reject( e );
                                }
                            });
                    }
                    else {
                        promise.resolve( response );
                    }
                    return response;
                }
            })
            .error( new Q.Promise.ErrorCallback() {
                public void error(Exception e) {
                    promise.reject( e );
                }
            });
        return promise;
    }
}
