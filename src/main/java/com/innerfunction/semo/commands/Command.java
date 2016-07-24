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

import java.util.List;
import static com.innerfunction.semo.commands.CommandScheduler.CommandItem;

/**
 * A schedulable command.
 *
 * Attached by juliangoacher on 07/05/16.
 */
public interface Command {

    /**
     * Execute the command with the specified name and arguments.
     * Returns a deferred promise which may resolve to an array of new commands to
     * be queued for execution after the current, and any other commands, complete.
     */
    Q.Promise<List<CommandItem>> execute(String name, List args);

}
