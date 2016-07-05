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

import android.util.Log;

import com.innerfunction.q.Q;
import com.innerfunction.semo.db.Column;
import com.innerfunction.semo.db.DB;
import com.innerfunction.semo.db.Table;

import org.json.simple.JSONValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A command scheduler with guarantees on command execution.
 *
 * Created by juliangoacher on 07/05/16.
 */
public class CommandScheduler {

    static final String Tag = CommandScheduler.class.getSimpleName();

    /**
     * A queue used to run commands.
     * All commands are run sequentially on the same background thread.
     */
    static final RunQueue ExecRunQueue = new RunQueue();

    /** Empty command list, used to indicate that a command has no follow on commands. */
    public static final List<Command> NoFollowOns = Collections.emptyList();

    /** The queue database. */
    private DB db;
    /** A list of commands currently being executed. */
    private List<CommandItem> execQueue;
    /** Pointer into the exec queue to the command currently being executed. */
    private int execIdx;
    /** Current batch number. */
    private int currentBatch = 0;
    /**
     * A map of command instances, keyed by name.
     * Commands which are protocol instances (i.e. CommandProtocol subclasses) have each of their
     * protocol commands mapped into the command namespace with the protocol command name as
     * a prefix, so e.g. { name => protocol } --> name.command1, name.command2 etc.
     */
    private Map<String,Command> commands = new HashMap<>();
    /**
     * Whether to delete queue database records after processing.
     * Defaults to true. When false, the 'status' field is used to track pending vs. executed
     * records; this mode is primarily useful for debugging.
     */
    private boolean deleteExecutedQueueRecords = true;

    /** An object representing a command item on the execution queue. */
    static class CommandItem {
        /** The ID of the command's DB record. */
        String rowID;
        /** The command name. */
        String name;
        /** A list of the command arguments. */
        List args;
        /** The command's execution priority. */
        Integer priority;
        /** Instantiate an empty command item. */
        CommandItem() {}
        /** Instantiate a new command item from a db record. */
        CommandItem(Map<String,?> record) {
            this.rowID = (String)record.get("id");
            this.name = (String)record.get("command");
            try {
                String json = (String)record.get("args");
                this.args = (List)JSONValue.parseWithException( json );
            }
            catch(org.json.simple.parser.ParseException e) {
                Log.e(Tag, "Parsing JSON args", e );
            }
        }
    }

    public CommandScheduler() {
        // Command database setup.
        db = new DB();
        db.setName("com.innerfunction.semo.command-scheduler");
        db.setVersion( 0 );
        // public void setTables(Table... tables)
        db.setTables(
            // public Table(String name, Column... columns)
            new Table("queue",
                // public Column(String name, String type, String tag)
                new Column("id",     "INTEGER PRIMARY KEY", "id"),
                new Column("batch",  "INTEGER"),
                new Column("command","TEXT"),
                new Column("args",   "TEXT"),
                new Column("status", "TEXT")));

        // Standard built-in command mappings.
        commands = new HashMap<>();
        commands.put("rm", new RmFileCommand() );
        commands.put("mv", new MvFileCommand() );
        commands.put("unzip", new UnzipCommand() );
    }

    /**
     * The name of the command queue database.
     */
    public void setQueueDBName(String name) {
        db.setName( name );
    }

    public void setCommands(Map<String,Command> commands) {
        for( String name : commands.keySet() ) {
            Command command = commands.get( name );
            if( command instanceof CommandProtocol ) {
                CommandProtocol protocol = (CommandProtocol)command;
                protocol.setCommandPrefix( name );
                // Iterate over the protocol's supported commands and add to the command
                // namespace under a fully qualified name.
                Map<String,Command> protocolCommands = protocol.getCommands();
                for( String subname : protocolCommands.keySet() ) {
                    String qualifiedName = String.format("%s.%s", name, subname );
                    this.commands.put( qualifiedName, command );
                }
            }
            else {
                this.commands.put( name, command );
            }
        }
        this.commands = commands;
    }

    public void setDeleteExecutedQueueRecords(boolean delete) {
        this.deleteExecutedQueueRecords = delete;
    }

    /** Execute all commands currently on the queue. */
    public void executeQueue() {
        if( execIdx > 0 ) {
            // Commands currently being executed from queue, leave these to be completed.
            return;
        }
        ExecRunQueue.dispatch( new Runnable() {
            @Override
            public void run() {
                // NOTE performQuery(String sql, Object... params);
                List<Map<String,?>> queueItems = db.performQuery("SELECT * FROM queue WHERE status='P' ORDER BY batch, id ASC");
                execQueue = new ArrayList<>();
                for( Map<String,?> queueItem : queueItems ) {
                    execQueue.add( new CommandItem( queueItem ) );
                }
                execIdx = 0;
                executeNextCommand();
            }
        } );
    }

    /** Append a new command to the queue. */
    public void appendCommand(final String name, final List args) {
        Log.d( Tag, String.format("Appending %s %s", name, args ) );
        ExecRunQueue.dispatch( new Runnable() {
            public void run() {
                String argsJSON = JSONValue.toJSONString( args );
                Map values = new HashMap<>();
                values.put("batch", currentBatch );
                values.put("command", name );
                values.put("args", argsJSON );
                values.put("status", "P" );
                // Only one pending command with the same name and args should exist for the same
                // batch at any time, so only insert record if no matching record found.
                // public int countInTable(String tableName, String where, Object... params)
                String batch = String.valueOf( currentBatch );
                int count = db.countInTable("queue","batch=? AND command=? AND args=? AND status=?", batch, name, argsJSON, "P" );
                if( count == 0 ) {
                    db.upsert("queue", values );
                }
            }
        } );
    }

    /** Append a new command to the queue. */
    public void appendCommand(String command, String... args) {
        String commandLine = String.format( command, args );
        CommandItem commandItem = parseCommandItem( commandLine );
        if( commandItem != null ) {
            appendCommand( commandItem.name, commandItem.args );
        }
    }

    /** Purge the current execution queue. */
    public void purgeQueue() {
        // Clear the execution queue, delete all queued commands.
        execQueue.clear();
        execIdx = 0;
        currentBatch = 0;
        Runnable purge = new Runnable() {
            public void run() {
                if( deleteExecutedQueueRecords ) {
                    db.deleteWhere("queue", "1=1" );
                }
                else {
                    db.performUpdate("UPDATE queue SET status='X' WHERE status='P'");
                }
            }
        };
        // If already running on the exec queue the run the purge synchronously; else add to end of
        // queue.
        if( ExecRunQueue.isRunningOnQueueThread() ) {
            purge.run();
        }
        else {
            ExecRunQueue.dispatch( purge );
        }
    }

    /** Purge the current command batch. */
    public void purgeCurrentBatch() {
        execQueue.clear();
        execIdx = 0;
        Runnable purge = new Runnable() {
            public void run() {
                String batch = String.valueOf( currentBatch );
                if( deleteExecutedQueueRecords ) {
                    db.deleteWhere("queue", "batch=?", batch );
                }
                else {
                    db.performUpdate("UPDATE queue SET status='X' WHERE status='P' AND batch=?", batch );
                }
            }
        };
        // If already running on the exec queue the run the purge synchronously; else add to end of
        // queue.
        if( ExecRunQueue.isRunningOnQueueThread() ) {
            purge.run();
        }
        else {
            ExecRunQueue.dispatch( purge );
        }
    }

    /** Execute the next command on the exec queue. */
    private void executeNextCommand() {
        ExecRunQueue.dispatch( new Runnable() {
            @Override
            public void run() {
                if( execQueue.size() == 0 ) {
                    // Do nothing if nothing on the queue.
                    execIdx = -1;
                    return;
                }
                if( execIdx > execQueue.size() - 1 ) {
                    // If moved past the end of the queue then try reading a new list of commands
                    // from the db.
                    execIdx = -1;
                    executeQueue();
                    return;
                }
                final CommandItem commandItem = execQueue.get( execIdx );
                // Iterate command pointer.
                execIdx++;
                // Find and execute the command.
                Log.d( Tag, String.format("Executing %s %s", commandItem.name, commandItem.args ) );
                Command command = commands.get( commandItem.name );
                if( command == null ) {
                    Log.e( Tag, String.format("Command not found: %s", commandItem.name ) );
                    purgeQueue();
                    return;
                }
                command.execute( commandItem.name, commandItem.args )
                    .then( new Q.Promise.Callback<List<Command>, Object>() {
                        public Object result(final List<Command> commands) {
                            ExecRunQueue.dispatch( new Runnable() {
                                public void run() {
                                    // Queue any new commands, delete current command from db.
                                    db.beginTransaction();
                                    for( Object item : commands ) {
                                        CommandItem command = parseCommandItem( item );
                                        if( command == null ) {
                                            // Indicates an unparseable command line string; just
                                            // continue to the next command.
                                            continue;
                                        }
                                        // Check for system commands.
                                        if("control.purge-queue".equals( command.name ) ) {
                                            purgeQueue();
                                            continue;
                                        }
                                        if("control.purge-current-batch".equals( command.name ) ) {
                                            purgeCurrentBatch();
                                            continue;
                                        }
                                        int batch = currentBatch;
                                        if( command.priority != null ) {
                                            batch += command.priority;
                                            // Negative priorities can place new commands at the head
                                            // of the queue; reset the exec queue to force a db read,
                                            // so that these commands are read into the head of the
                                            // exec queue.
                                            if( batch < currentBatch ) {
                                                execQueue.clear();
                                            }
                                        }
                                        Log.d( Tag, String.format("Appending %@ %@", command.name, command.args ) );
                                        Map<String, Object> values = new HashMap<>();
                                        values.put("batch", batch );
                                        values.put("command", command.name );
                                        values.put("args", JSONValue.toJSONString( command.args ) );
                                        values.put("status", "P" );
                                        db.insert("queue", values );
                                    }
                                    continueQueueProcessingAfterCommand( commandItem.rowID );
                                }
                            } );
                            return null;
                        }
                    } )
                    .error( new Q.Promise.ErrorCallback() {
                        public void error(Exception e) {
                            Log.e( Tag, String.format("Error executing command %s %s", commandItem.name, commandItem.args ), e );
                            // TODO: Review whether queue should be purged or not. Removed for now.
                            // Commands should detect errors caused by previous command failures and
                            // deal with accordingly.
                            // purgeQueue();
                            continueQueueProcessingAfterCommand( commandItem.rowID );
                        }
                    } );
            }
        } );
    }

    /** Continue queue processing after execution a command. */
    private void continueQueueProcessingAfterCommand(String rowID) {
        // Delete the command record from the queue.
        if( deleteExecutedQueueRecords ) {
            db.delete("queue", rowID );
        }
        else {
            Map<String,Object> values = new HashMap<>();
            values.put("id",     rowID );
            values.put("status", "X");
            db.update("queue", values );
        }
        db.commitTransaction();
        // Continue to next queued command.
        executeNextCommand();
    }

    /**
     * Parse a command item into a command descriptor.
     * The command item can be either:
     * 1. A dictionary instance with 'name' and 'args' entries;
     * 2. Or a command line string, which is parsed into 'name' and 'args' items.
     */
    private CommandItem parseCommandItem(Object item) {
        if( item instanceof Map ) {
            Map itemMap = (Map)item;
            String name = (String)itemMap.get("name");
            Object args = itemMap.get("args");
            if( name != null ) {
                if( args instanceof String ) {
                    args = Arrays.asList( ((String)args).split("\\s+") );
                }
                if( args instanceof List ) {
                    CommandItem command = new CommandItem();
                    command.name = name;
                    command.args = (List)args;
                    command.priority = (Integer)itemMap.get("priority");
                    return command;
                }
            }
        }
        else if( item instanceof String ) {
            String[] parts = ((String)item).split("\\s+");
            if( parts.length > 0 ) {
                CommandItem command = new CommandItem();
                command.name = parts[0];
                if( parts.length > 1 ) {
                    parts = Arrays.copyOfRange( parts, 1, parts.length - 1 );
                    command.args = Arrays.asList( parts );
                }
                else {
                    command.args = Collections.EMPTY_LIST;
                }
                return command;
            }
        }
        Log.w(Tag, String.format("Invalid command item: %s", item ) );
        return null;
    }

}
