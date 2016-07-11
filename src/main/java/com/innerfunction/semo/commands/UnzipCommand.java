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

import com.innerfunction.q.Q;
import com.innerfunction.util.Files;

import java.io.File;
import java.util.List;

/**
 * Command to unzip a zip archive.
 * Arguments: <zip> <to>
 * - zip:   The path to a zip archive file.
 * - to:    The path to a directory to unzip the archive's contents into.
 *
 * Created by juliangoacher on 13/05/16.
 */
public class UnzipCommand implements Command {

    @Override
    public Q.Promise<List<CommandScheduler.CommandItem>> execute(String name, List args) {
        if( args.size() < 2 ) {
            Q.reject("Wrong number of arguments");
        }
        File zipFile = new File( args.get( 0 ).toString() );
        File toFile = new File( args.get( 1 ).toString() );
        if( Files.unzip( zipFile, toFile ) == null ) {
            Q.reject("Failed to unzip file");
        }
        return Q.resolve( CommandScheduler.NoFollowOns );
    }

}
