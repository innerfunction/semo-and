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
package com.innerfunction.semo.commands;

import android.content.Context;

import com.innerfunction.q.Q;
import com.innerfunction.util.Assets;
import com.innerfunction.util.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Command to unzip a zip archive.
 * Arguments: [-asset] <zip> <to>
 * - -asset:    A switch indicating that the zip file is located in the app's assets folder.
 * - zip:       The path to a zip archive file.
 * - to:        The path to a directory to unzip the archive's contents into.
 *
 * Attached by juliangoacher on 13/05/16.
 */
public class UnzipCommand implements Command {

    private Assets assets;

    public UnzipCommand(Context context) {
        this.assets = new Assets( context );
    }

    @Override
    public Q.Promise<List<CommandScheduler.CommandItem>> execute(String name, List args) {
        if( args.size() < 2 ) {
            return Q.reject("Wrong number of arguments");
        }
        if( "-asset".equals( args.get( 0 ) ) ) {
            try {
                InputStream in = assets.openInputStream( args.get( 1 ).toString() );
                File toFile = new File( args.get( 2 ).toString() );
                if( Files.unzip( in, toFile ) == null ) {
                    return Q.reject( "Failed to unzip file" );
                }
            }
            catch(IOException e) {
                return Q.reject( e );
            }
        }
        else {
            File zipFile = new File( args.get( 0 ).toString() );
            File toFile = new File( args.get( 1 ).toString() );
            if( Files.unzip( zipFile, toFile ) == null ) {
                return Q.reject( "Failed to unzip file" );
            }
        }
        return Q.resolve( CommandScheduler.NoFollowOns );
    }

}
