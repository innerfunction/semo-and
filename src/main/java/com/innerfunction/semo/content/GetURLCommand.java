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

import com.innerfunction.http.Client;
import com.innerfunction.http.Response;
import com.innerfunction.q.Q;
import com.innerfunction.semo.commands.Command;
import com.innerfunction.semo.commands.CommandScheduler;
import com.innerfunction.semo.commands.CommandScheduler.CommandItem;

import android.os.Handler;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A command to get the contents of a URL and write it to a file.
 * Arguments: <url> <filename> <retries>
 * - url:       The URL to fetch.
 * - filename:  The name of the file to write the result to.
 * - attempt:   The number of attempts made to fetch the URL. Defaults to 0 and is incremented after
 *              each failed attempt. The command fails once maxRetries number of attempts has been
 *              made.
 *
 * Attached by juliangoacher on 11/07/16.
 */
public class GetURLCommand implements Command {

    static final int DefaultMaxRetries = 3;
    static final float RequestWindowSize = 5;

    /** The HTTP client used to fetch URLs. */
    private Client httpClient;
    /** A promise which will be resolved once the URL is fetched. */
    private Q.Promise<List<CommandItem>> promise;
    /** The name the command was invoked under. Needed for queueing retries. */
    private String commandName;
    /** The URL to fetch. */
    private String url;
    /** The name of the file to write the URL result to. */
    private String filename;
    /**
     * A list of immediately previous request times.
     * Used to control request rates.
     */
    private List<Long> requestWindow = new ArrayList<>();
    /** The maximum number of times a failed request should be retried before the command fails. */
    private int maxRetries = DefaultMaxRetries;
    /** The maximum number of requests per minute. Used to throttle requests. */
    private float maxRequestsPerMinute;
    /** An object used to schedule delayed commands. Used when throttling requests. */
    private Handler handler;

    public GetURLCommand(Client httpClient) {
        this.httpClient = httpClient;
        this.handler = new Handler();
    }

    public void setMaxRetries(int max) {
        this.maxRetries = max;
    }

    public void setMaxRequestsPerMinute(float max) {
        this.maxRequestsPerMinute = max;
    }

    @Override
    public Q.Promise<List<CommandItem>> execute(String name, List args) {
        this.commandName = name;
        this.promise = new Q.Promise<>();

        if( args.size() > 1 ) {

            this.url = args.get( 0 ).toString();
            this.filename = args.get( 1 ).toString();

            final int previousAttempts = (args.size() > 2)
                ? Integer.parseInt( args.get( 2 ).toString() )
                : 0;

            Runnable request = new Runnable() {
                @Override
                public void run() {
                    // Prune the request window to its maximum size, if necessary, before adding
                    // the current request time to the end of the window.
                    if( requestWindow.size() > RequestWindowSize ) {
                        requestWindow.remove( 0 );
                    }
                    requestWindow.add( System.currentTimeMillis() );
                    // Submit the request.
                    try {
                        httpClient.getFile( url )
                            .then( new Q.Promise.Callback<Response, Response>() {
                                @Override
                                public Response result(Response response) {
                                    // Copy downloaded file to target location.
                                    response.getDataFile().renameTo( new File( filename ) );
                                    promise.resolve( CommandScheduler.NoFollowOns );
                                    return response;
                                }
                            } )
                            .error( new Q.Promise.ErrorCallback() {
                                public void error(Exception e) {
                                    // Check for retries.
                                    int attempts = previousAttempts + 1;
                                    if( attempts < maxRetries ) {
                                        List<CommandItem> commands = new ArrayList<>();
                                        commands.add( new CommandItem( commandName, url, attempts ) );
                                        promise.resolve( commands );
                                    }
                                    else {
                                        promise.reject( "All retries used" );
                                    }
                                }
                            } );
                    }
                    catch(MalformedURLException e) {
                        promise.reject( e );
                    }
                }
            };
            // Request throttling - ensure number of requests per minute doesn't exceed some defined
            // maximum.
            // TODO Consider supporing per-domain (or even per-URL?) request limits. Also, perhaps
            // allow request limit to be set per-command? This would allow easy setup of different
            // channels for different usages.
            long timeToNextRequest = -1;
            if( maxRequestsPerMinute > 0 && requestWindow.size() > 0 ) {
                // The following code uses a request window containing n previous requests. The code
                // calculates the ideal number of msecs per request (i.e. to hit the maximum
                // requests per minute). The ideal time of the next request is then calculated by
                // multiplying the msecs-per-request by the size of the request window, and adding
                // this to the time of the first request in the window. The result may be positive
                // (indicating a future time); or negative (indicating a past time).
                long windowStart = requestWindow.get( 0 );
                float msecsPerRequest = (1.0f / maxRequestsPerMinute) * 60000.0f;
                long nextRequestTime = (long)(windowStart + (msecsPerRequest * (requestWindow.size() + 1)));
                timeToNextRequest = nextRequestTime - System.currentTimeMillis();
            }
            // If the ideal next request time is in the past then immediately execute the request.
            if( timeToNextRequest < 0 ) {
                request.run();
            }
            else {
                // Ideal next request time is in the future, so schedule the request to execute on
                // the queue after a suitable delay.
                handler.postDelayed( request, timeToNextRequest );
            }
        }
        else {
            promise.reject("Incorrect number of arguments");
        }
        return promise;
    }

}
