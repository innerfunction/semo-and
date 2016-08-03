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
 * Command to move a file or directory on the local filesystem to another location.
 * Arguments: <from> <to>
 * - from:  The file path to move.
 * - to:    Where to move the file or directory to.
 *
 * Attached by juliangoacher on 13/05/16.
 */
public class MvFileCommand implements Command {

    @Override
    public Q.Promise<List<CommandScheduler.CommandItem>> execute(String name, List args) {
        if( args.size() < 2 ) {
            return Q.reject("Wrong number of arguments");
        }
        File fromFile = new File( args.get( 0 ).toString() );
        File toFile = new File( args.get( 1 ).toString() );
        if( !Files.mv( fromFile, toFile ) ) {
            return Q.reject( String.format("Error moving file %s to %s", fromFile, toFile ) );
        }
        return Q.resolve( CommandScheduler.NoFollowOns );
    }

}
