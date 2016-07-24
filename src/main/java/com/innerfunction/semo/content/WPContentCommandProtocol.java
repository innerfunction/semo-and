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

import android.net.Uri;
import android.util.Log;

import com.innerfunction.q.Q;
import com.innerfunction.semo.commands.Command;
import com.innerfunction.semo.commands.CommandProtocol;
import com.innerfunction.semo.db.DB;
import com.innerfunction.util.Files;
import com.innerfunction.util.KeyPath;
import com.innerfunction.util.Paths;

import static com.innerfunction.util.DataLiterals.*;
import static com.innerfunction.semo.commands.CommandScheduler.CommandItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Attached by juliangoacher on 07/07/16.
 */
public class WPContentCommandProtocol extends CommandProtocol {

    static final String Tag = WPContentCommandProtocol.class.getSimpleName();

    public static final String BaseContentType = "semo:base-content";

    /** The file used to store downloaded feed result. */
    private File feedFile;
    /** Path to file used to store downloaded base content zip. */
    private File baseContentFile;
    /** Path to store downloaded content prior to deployment. */
    private String stagedContentPath;
    /** A flag indicating that a refresh is in progress. */
    private boolean refreshInProgress;
    /** The WP feed URL. Note that query parameters will be appened to the URL. */
    private String feedURL;
    /** A URL for doing a bulk-download of initial image content. */
    private String imagePackURL;
    /** The local database used to store post and content data. */
    private DB postDB;
    /** Path to directory holding staged content. */
    private String stagingPath;
    /** Path to directory holding base content. */
    private String baseContentPath;
    /** Path to directory containing pre-packaged content. */
    private String packagedContentPath;
    /** Path to directory hosting downloaded content. */
    private String contentPath;

    public WPContentCommandProtocol() {
        addCommand("refresh", new Command() {
            @Override
            public Q.Promise<List<CommandItem>> execute(String name, List args) {
                return WPContentCommandProtocol.this.refresh( args );
            }
        });
        addCommand("continue-download", new Command() {
            @Override
            public Q.Promise<List<CommandItem>> execute(String name, List args) {
                return WPContentCommandProtocol.this.continueDownload( args );
            }
        });
        addCommand("deploy-download", new Command() {
            @Override
            public Q.Promise<List<CommandItem>> execute(String name, List args) {
                return WPContentCommandProtocol.this.deployDownload( args );
            }
        });
        addCommand("unpack", new Command() {
            @Override
            public Q.Promise<List<CommandItem>> execute(String name, List args) {
                return WPContentCommandProtocol.this.unpack( args );
            }
        });
    }

    public void setFeedURL(String url) {
        this.feedURL = url;
    }

    public void setImagePackURL(String url) {
        this.imagePackURL = url;
    }

    public void setPostDB(DB db) {
        // Use a new copy of the post DB. This it to ensure thread-safe access to the db.
        this.postDB = db.newInstance();
    }

    public void setStagingPath(String path) {
        this.stagingPath = path;
        this.feedFile = new File( Paths.join( stagingPath, "feed.json") );
        this.baseContentFile = new File( Paths.join( stagingPath, "base-content.zip") );
        this.stagedContentPath = Paths.join( stagingPath, "content");
    }

    public void setBaseContentPath(String path) {
        this.baseContentPath = path;
    }

    public void setPackagedContentPath(String path) {
        this.packagedContentPath = path;
    }

    public void setContentPath(String path) {
        this.contentPath = path;
    }

    public Q.Promise<List<CommandItem>> refresh(List args) {
        List<CommandItem> commands = new ArrayList<>();

        if( !refreshInProgress ) {

            refreshInProgress = true;

            String refreshURL = feedURL.concat("/updates");
            Map<String,Object> argsMap = parseArgs( args, new String[0], m( kv("refreshURL", refreshURL )));
            refreshURL = KeyPath.getValueAsString("refreshURL", argsMap );
            String getURL = refreshURL;

            // Query post DB for the last modified time.
            List<Map<String,Object>> rs = postDB.performQuery("SELECT max(modified) FROM posts");
            if( rs.size() > 0 ) {
                // Previously downloaded posts exist, so read latest post modification time.
                Map<String,Object> record = rs.get( 0 );
                String modifiedTime = KeyPath.getValueAsString("max(modified)", record );
                // Construct feed URL with since parameter.
                modifiedTime = Uri.encode( modifiedTime );
                getURL = String.format("%s?since=%s", refreshURL, modifiedTime );
            }
            // If no posts, and no last modified time, then simply omit the 'since' parameter; the feed
            // will return all posts, starting at the earliest.

            String pageFile = Paths.join( stagingPath, "page.json");

            // Construct get command with url and file name to write result to, with 3 retries.
            CommandItem getCmd = new CommandItem("get", getURL, pageFile, 3 );
            // Construct process command to continue downloading the feed.
            CommandItem processCmd
                = new CommandItem( getQualifiedCommandName("continue-download"), refreshURL, pageFile );
            commands = Arrays.asList( getCmd, processCmd );
        }

        // Return commands.
        return Q.resolve( commands );
    }

    public Q.Promise<List<CommandItem>> continueDownload(List args) {
        // Could be resuming a download after an app restart, so always reset refreshInProgress to true.
        refreshInProgress = true;

        // Parse arguments. Allow the feed file path to be optionally specified as a command argument.
        Map<String,Object> argsMap = parseArgs( args, new String[]{ "refreshURL", "pageFile" }, m() );
        String refreshURL = KeyPath.getValueAsString("refreshURL", argsMap );
        String pageFilePath = KeyPath.getValueAsString("pageFile", argsMap );

        // List of generated commands.
        List<CommandItem> commands = new ArrayList<>();

        // Command to delete the current page file.
        CommandItem rmPageFileCmd = new CommandItem("rm", pageFilePath );

        boolean ok = false;
        Map<String,Object> pageData = null;
        // Check that previous page download succeeded.
        File pageFile = new File( pageFilePath );
        if( pageFile.exists() ) {
            // Read result of previous get.
            // Data format: { since:,  page: { size:, number:, count: }, items }
            pageData = (Map<String,Object>)Files.readJSON( pageFile );
            // Append page items to previously downloaded items.
            List pageItems = (List)pageData.get("items");
            List items = (List)Files.readJSON( feedFile );
            if( items != null ) {
                items.addAll( pageItems );
            }
            else {
                items = pageItems;
            }
            if( items != null ) {
                // Write items to feed file.
                ok = Files.writeJSON( feedFile, items );
            }
        }

        if( ok ) {
            // Check for multi-page feed response, and whether a next page request needs to be issued.
            int pageCount = KeyPath.getValueAsInt("page.pageCount", pageData );
            int pageNumber = KeyPath.getValueAsInt("page.pageNumber", pageData );
            if( pageNumber < pageCount ) {
                int page = pageNumber + 1;
                String since = KeyPath.getValueAsString("parameters.since", pageData );
                since = Uri.encode( since );
                String downloadURL = String.format("%s?page=%d&since=%s", refreshURL, page, since );

                // Construct get command with url and file name to write result to, with 3 retries.
                CommandItem getCmd = new CommandItem("get", downloadURL, pageFile, 2 );
                // Construct process command to continue downloading the feed.
                CommandItem continueCmd
                    = new CommandItem( getQualifiedCommandName("continue-download"), refreshURL, pageFile );

                commands = Arrays.asList( rmPageFileCmd, getCmd, continueCmd );
            }
            else {
                // All pages download, process the feed items next.
                CommandItem deployCmd = new CommandItem( getQualifiedCommandName("deploy-download") );

                commands = Arrays.asList( rmPageFileCmd, deployCmd );
            }
        }
        else {
            // Process failed for some reason; clean up and abort the download, try again on next refresh.
            feedFile.delete();
            refreshInProgress = false;
        }

        // Return commands.
        return Q.resolve( commands );
    }

    public Q.Promise<List<CommandItem>> deployDownload(List args) {
        // All feed pages should be downloaded by this point, and all updated feed items written to
        // the feed file.
        List<Map<String,Object>> feedItems = (List<Map<String,Object>>)Files.readJSON( feedFile );
        // List of generated commands.
        List<CommandItem> commands = new ArrayList<>();
        // Iterate over items and update post database, generate commands to download base content
        // & media items.
        postDB.beginTransaction();
        for( Map<String,Object> item : feedItems ) {
            String type = KeyPath.getValueAsString("type", item );
            if( BaseContentType.equals( type ) ) {
                // Download base content update.
                commands.add( new CommandItem("get", item.get("url"), baseContentFile, 3 ) );
                commands.add( new CommandItem("unzip", baseContentFile, baseContentPath ) );
                commands.add( new CommandItem("rm", baseContentFile ) );
            }
            else {
                // Upadate post item in database.
                String status = KeyPath.getValueAsString("status", item );
                if( "trash".equals( status ) ) {
                    // Item is deleted.
                    String postID = KeyPath.getValueAsString("id", item );
                    postDB.performUpdate("DELETE FROM posts WHERE id=?", postID );
                    postDB.performUpdate("DELETE FROM closures WHERE child=? OR parent=?", postID, postID );
                    // If attachment then delete file from content path.
                    if( "attachment".equals( type ) ) {
                        String filename = KeyPath.getValueAsString("filename", item );
                        String filepath = Paths.join( contentPath, filename );
                        commands.add( new CommandItem("rm", filepath ) );
                    }
                }
                else {
                    postDB.upsert("posts", item );
                    updateClosureTableForPost( item );
                    // Download attachment updates.
                    if( "attachment".equals( type ) ) {
                        String filename = KeyPath.getValueAsString("filename", item );
                        // NOTE that file is downloaded directly to the content path.
                        String filepath = Paths.join( contentPath, filename );
                        // Delete any previously downloaded copy of the file.
                        commands.add( new CommandItem("rm", filepath ) );
                        commands.add( new CommandItem("get", item.get("url"), filepath, 2 ) );
                    }
                }
            }
        }
        postDB.commitTransaction();
        // Tidy up.
        commands.add( new CommandItem("rm", feedFile.getAbsolutePath() ) );
        refreshInProgress = false;
        return Q.resolve( commands );
    }

    public Q.Promise<List<CommandItem>> unpack(List args) {
        List<CommandItem> commands = new ArrayList<>();
        // Parse arguments.
        Map<String,String> argsMap = parseArgs( args, new String[0], null );
        String packagedContentPath = KeyPath.getValueAsString("packagedContentPath", argsMap );
        if( packagedContentPath == null ) {
            packagedContentPath = this.packagedContentPath;
        }
        if( packagedContentPath != null ) {
            Date startTime = new Date();
            File feedFile = new File( Paths.join( packagedContentPath, "feed.json") );
            File baseContentFile = new File( Paths.join( packagedContentPath, "base-content.zip") );
            // Read initial posts data from packaged feed file.
            List<Map<String,Object>> feedItems = (List<Map<String,Object>>)Files.readJSON( feedFile );
            if( feedItems != null ) {
                // Iterate over items and update post database.
                postDB.beginTransaction();
                postDB.merge("posts", feedItems );
                rebuildClosureTable( feedItems );
                postDB.commitTransaction();
            }
            Date endTime = new Date();
            Log.d( Tag, String.format("Content unpack took %d s", (endTime.getTime() - startTime.getTime()) / 1000 ) );
            // Schedule command to unzip base content if the base content zip exists.
            if( baseContentFile.exists() ) {
                commands.add( new CommandItem("unzip", baseContentFile.getAbsolutePath(), contentPath ) );
            }
            // Schedule command to bulk download initial image content, if an image pack URL is given.
            if( imagePackURL != null ) {
                commands.add( new CommandItem("dlzip", imagePackURL, contentPath ) );
            }
        }
        return Q.resolve( commands );
    }


    private void updateClosureTableForPost(Map<String,Object> post) {
        String postID = KeyPath.getValueAsString("id", post );
        postDB.performUpdate("DELETE FROM closures WHERE ROWID IN ("+
            "SELECT link.ROWID FROM closures p, closures link, closures c, closures to_delete "+
            "WHERE p.parent = link.parent      AND c.child = link.child "+
            "AND p.child    = to_delete.parent AND c.parent= to_delete.child "+
            "AND (to_delete.parent = ? OR to_delete.child = ?) "+
            "AND to_delete.depth < 2)", postID, postID );

        // Re-insert entries for all direct children of the current post.
        postDB.performUpdate("INSERT INTO closures (parent, child, depth"+
            "SELECT parent, id, 1 FROM posts WHERE parent = ?", postID );

        insertClosureEntriesForPost( post );
    }

    private void insertClosureEntriesForPost(Map<String,Object> post) {
        String parent = KeyPath.getValueAsString("parent", post );
        String postID = KeyPath.getValueAsString("id", post );

        // NOTE that all of following updates assume that the closures table contains no
        // mappings for the post before the updates performed, otherwise duplicate mappings
        // will be created.

        // Insert entry mapping post to itself with depth of 0.
        postDB.insert("closures", m(
            kv("parent", postID ),
            kv("child", postID ),
            kv("depth", 0 )
        ));

/*  NOTE This moved to the updateClosureTableForPost function beause otherwise duplicate
    closure entries are created; this presumably happens because when this function is
    called from the rebuildClosureTable() function, the necessary child post entries are
    eventually added (i.e. re-added) later in the loop.

    TODO This whole insert/update procedure for the closure table needs to be reviewed,
    as it's not fully understood and it's not clear that it produces the required
    results in all circumstances

    // Insert entries for all direct children of the current post.
    [postDB performUpdate:@"INSERT INTO closures (parent, child, depth) \
            SELECT parent, id, 1 FROM posts WHERE parent = ?"
               withParams:@[ postid ]];
*/

        // Insert entries for all parents/ancestors.
        if( !(parent == null || "0".equals( parent )) ) {
            postDB.performUpdate("INSERT INTO closures (parent, child, depth) "+
                "SELECT p.parent, c.child, p.depth + c.depth + 1 "+
                "FROM closures p, closures c "+
                "WHERE p.child = ? AND c.parent = ?", parent, postID );
        }
    }

    private void rebuildClosureTable(List<Map<String,Object>> posts) {
        postDB.delete("closures","1 = 1");
        for( Map<String,Object> post : posts ) {
            insertClosureEntriesForPost( post );
        }
    }

}
