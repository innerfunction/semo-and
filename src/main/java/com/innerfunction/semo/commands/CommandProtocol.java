package com.innerfunction.semo.commands;

import com.innerfunction.q.Q;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.innerfunction.semo.commands.CommandScheduler.CommandItem;

/**
 * A command protocol.
 * A command implementation that supports multiple different named commands, useful for
 * defining protocols composed of a number of related commands.
 *
 * Attached by juliangoacher on 13/05/16.
 */
public class CommandProtocol implements Command {

    /** A map of the protocol's named commands. */
    private Map<String,Command> commands = new HashMap<>();
    /** The command prefix. Used to namespace commands to this protocol. */
    private String commandPrefix;

    /** Return a set of command names supported by this protocol. */
    public Map<String,Command> getCommands() {
        return commands;
    }

    /** Set the protocol's command name prefix. */
    public void setCommandPrefix(String commandPrefix) {
        this.commandPrefix = commandPrefix;
    }

    /** Register a protocol command. */
    public void addCommand(String name, Command command) {
        commands.put( name, command );
    }

    /** Qualify a protocol command name with the current command prefix. */
    public String getQualifiedCommandName(String name) {
        return String.format("%s.%s", commandPrefix, name );
    }

    /**
     * Parse a command argument list.
     * Transforms an array of command arguments into a dictionary of name/value pairs.
     * Arguments can be defined by position, or by using named switches (e.g. -name value).
     * The names of positional arguments are specified using the argOrder list.
     */
    public Map<String,Object> parseArgs(List<String> args, Map<String, Object> defaults, String... argOrder) {
        if( defaults == null ) {
            defaults = new HashMap<>();
        }
        if( argOrder == null ) {
            argOrder = new String[0];
        }
        Map<String,Object> result = new HashMap<>( defaults );
        String name = null;
        Object value = null;
        int position = 0;
        // Iterate over each argument.
        for( String arg : args ) {
            // If argument starts with - then it is a switch.
            if( arg.charAt( 0 ) == '-' ) {
                // If we already have a name then it indicates a valueless switch; map the switch
                // name to binary true.
                if( name != null ) {
                    result.put( name, Boolean.TRUE );
                }
                // Subtract the - prefix from the switch name.
                name = arg.substring( 1 );
            }
            // We have switch name so next argument is the value.
            else if( name != null ) {
                value = arg;
            }
            // No switch specified so use argument position to read name.
            else if( position < argOrder.length ) {
                name = argOrder[position++];
                value = arg;
            }
            // If we have a name and value then map them into the result.
            if( name != null && value != null ) {
                result.put( name, value );
                name = null;
                value = null;
            }
        }
        return result;
    }

    @Override
    public Q.Promise<List<CommandItem>> execute(String name, List args) {
        // Split the protocol prefix from the name to get the actual command name.
        String[] nameParts = name.split("\\.");
        commandPrefix = nameParts[0]; // Hack to get the correct prefix used by the command protocol.
        String commandName = nameParts[1];
        // Find a handler block for the named command.
        Command command = commands.get( commandName );
        if( command != null ) {
            try {
                return command.execute( name, args );
            }
            catch(Exception e) {
                return Q.reject( e );
            }
        }
        // No handler found.
        return Q.reject( String.format("Unrecognized command name: %s", commandName ) );
    }

}
