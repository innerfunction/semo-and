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
import com.innerfunction.semo.commands.RunQueue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

/**
 * An HTTP client.
 * TODO Probably makes sense to move this to its own package in core Pttn.
 * Created by juliangoacher on 08/07/16.
 */
public class HTTPClient {

    /** A HTTP client request. */
    static class Request {
        /** The URL being connected to. */
        URL url;
        /** The HTTP method, e.g. GET, POST. */
        String method;
        /** Optional request body data. */
        byte[] body;

        public Request(String url, String method) throws MalformedURLException {
            this.url = new URL( url );
            this.method = method;
        }

        public void setBody(String body) {
            this.body = body.getBytes();
        }

        Response connect() throws IOException, ProtocolException {
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            try {
                connection.setRequestMethod( method );
                // TODO Some of these connection settings should be configured via properties on the client.
                connection.setConnectTimeout( 5000 );
                connection.setReadTimeout( 5000 );
                if( body != null ) {
                    connection.setDoInput(true);
                    connection.setFixedLengthStreamingMode( body.length );
                    BufferedOutputStream out = new BufferedOutputStream( connection.getOutputStream() );
                    out.write( body );
                    out.flush();
                }
                // TODO: Differentiate between file and data downloads; data downloads kept in memory, file downloads go to disk.
                // TODO: Following code needs review.
                BufferedInputStream in = new BufferedInputStream( connection.getInputStream(), 1024 ); // NOTE buffer size
                byte[] body = new byte[0];
                int offset = 0;
                while( true ) {
                    if( body.length - offset < in.available() ) {
                        body = Arrays.copyOf( body, offset + in.available() );
                    }
                    int read = in.read( body, offset, body.length - offset );
                    if( read > -1 ) {
                        offset += read;
                    }
                    else break;
                }
                return new Response( url, connection.getResponseCode(), body );
            }
            finally {
                connection.disconnect();
            }
        }
    }

    /** A HTTP client response. */
    static class Response {

        String url;
        int statusCode;
        byte[] body;

        Response(URL url, int statusCode, byte[] body) {
            this.url = url.toString();
            this.statusCode = statusCode;
            this.body = body;
        }

        public String getRequestURL() {
            return url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public Map<String,Object> parseData() {
            // TODO Rename this to parse JSON? Check content type? What other content types need to be supported?
            return null;
        }
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

    public Q.Promise<Response> get(String url) throws MalformedURLException {
        return get( url, null );
    }

    public Q.Promise<Response> get(final String url, final Map<String,Object> data) throws MalformedURLException {
        // TODO Add request parameters to URL
        Request request = new Request( url, "GET");
        return submit( request );
    }

    public Q.Promise<Response> getFile(final String url) throws MalformedURLException {
        Request request = new Request( url, "GET");
        // TODO Probably need two request types - DataRequest and FileRequest
        return submit( request );
    }

    public Q.Promise<Response> post(final String url, final Map<String,Object> data) throws MalformedURLException {
        Request request = new Request( url, "POST");
        // TODO Encode and set request body
        return submit( request );
    }

    public Q.Promise<Response> submit(String method, String url, Map<String,Object> data) throws MalformedURLException {
        return "POST".equals( method ) ? post( url, data ) : get( url, data );
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

    static final RunQueue RequestQueue = new RunQueue();

    /**
     * Submit an HTTP action and handle authentication failures.
     */
    private Q.Promise<Response> submit(final Request request) {
        final Q.Promise<Response> promise = new Q.Promise<>();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Response response = request.connect();
                    // Check for authentication failures.
                    // TODO Following code needs to be reviewed - does it interact correctly with the request queue?
                    // TODO i.e. specifically the reauthenticate step, and the re-submit that follows it.
                    if( isAuthenticationErrorResponse( response ) ) {
                        // Try to reauthenticate and then resubmit the original request.
                        reauthenticate()
                            .then(new Q.Promise.Callback<Response, Response>() {
                                public Response result(Response response) {
                                    // Resubmit the request
                                    submit( request );
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
                }
                catch(IOException e) {
                    promise.reject( e );
                }
            }
        };
        // Place the request on the request queue.
        if( !RequestQueue.dispatch( task ) ) {
            promise.reject("Failed to dispatch to request queue");
        }
        return promise;
    }
}
