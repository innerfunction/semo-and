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
import com.innerfunction.util.Files;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;

/**
 * A command to download a zip file from a remote location and unpack it to the device's filesystem.
 * NOTE: This is a fairly basic initial implementation of this command, needed to provide a bulk
 * image download function to the app (to downloaded initial image content after the app's
 * installation). The command doesn't block the command scheduler execution queue; rather the
 * command returns control to the scheduler as soon as the HTTP request has been submitted. The
 * command schedules two follow up commands (an unzip followed by an rm) once the request has
 * completed. The code needs to be reviewed WRT background execution. The command will loose state
 * if the app is terminated whilst the download is in progress - it may be possible instead for the
 * command to continue and resume despite this, but this needs further investigation.
 *
 * Arguments: <url> <path>
 * - url:       The URL to download.
 * - path:      A location to unzip the downloaded zip file to.
 *
 * Created by juliangoacher on 11/07/16.
 */
public class DownloadZipCommand implements Command {

    /** The HTTP client used to fetch URLs. */
    private Client httpClient;
    /** The command scheduler. */
    private CommandScheduler commandScheduler;

    public DownloadZipCommand(Client httpClient, CommandScheduler commandScheduler) {
        this.httpClient = httpClient;
        this.commandScheduler = commandScheduler;
    }

    @Override
    public Q.Promise<List<CommandItem>> execute(String name, List args) {
        final Q.Promise<List<CommandItem>> promise = new Q.Promise<>();
        if( args.size() > 1 ) {
            // Read arguments.
            final String url = args.get( 0 ).toString();
            final String unzipPath = args.get( 1 ).toString();
            // NOTE Files.unzip currently has no overwrite option.
            String overwrite = args.size() > 2
                ? args.get( 2 ).toString()
                : "no";
            // Start the download.
            try {
                httpClient.getFile( url )
                    .then( new Q.Promise.Callback<Response, Response>() {
                        @Override
                        public Response result(Response response) {
                            // Unzip the downloaded file.
                            File unzipDir = new File( unzipPath );
                            if( Files.unzip( response.getDataFile(), unzipDir ) != null ) {
                                promise.resolve( CommandScheduler.NoFollowOns );
                            }
                            else {
                                promise.reject("Failed to unzip download");
                            }
                            return response;
                        }
                    } )
                    .error( new Q.Promise.ErrorCallback() {
                        public void error(Exception e) {
                            String msg = String.format("Download from %s failed: %s", url, e.getMessage());
                            promise.reject( msg );
                        }
                    });
            }
            catch(MalformedURLException e) {
                promise.reject( e );
            }
        }
        else {
            promise.reject("Incorrect number of arguments");
        }
        return promise;
    }
}
