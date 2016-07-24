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
 * Command to remove files or directories from the local filesystem.
 * Arguments: <path> [path...]
 * - path:  One or more paths to a file or directory to remove.
 *
 * Attached by juliangoacher on 13/05/16.
 */
public class RmFileCommand implements Command {

    @Override
    public Q.Promise<List<CommandScheduler.CommandItem>> execute(String name, List args) {
        for(Object arg : args) {
            String path = arg.toString();
            File file = new File( path );
            if( file.exists() ) {
                if( !Files.rm( file ) ) {
                    return Q.reject( String.format("Error removing file %s", file ) );
                }
            }
        }
        return Q.resolve( CommandScheduler.NoFollowOns );
    }

}
