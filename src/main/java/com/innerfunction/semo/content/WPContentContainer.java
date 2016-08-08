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

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.innerfunction.http.Client;
import com.innerfunction.pttn.Configuration;
import com.innerfunction.pttn.Container;
import com.innerfunction.pttn.IOCContainerAware;
import com.innerfunction.pttn.Message;
import com.innerfunction.pttn.MessageReceiver;
import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.pttn.app.NamedScheme;
import com.innerfunction.semo.commands.CommandScheduler;
import com.innerfunction.semo.db.DB;
import com.innerfunction.semo.db.DBFilter;
import com.innerfunction.uri.StandardURIHandler;
import com.innerfunction.uri.URIHandler;
import com.innerfunction.util.Files;
import com.innerfunction.util.KeyPath;
import com.innerfunction.util.Paths;
import com.innerfunction.util.Regex;
import com.innerfunction.util.StringTemplate;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;

import org.json.simple.JSONObject;

import static com.innerfunction.util.DataLiterals.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for Wordpress sourced content.
 *
 * Attached by juliangoacher on 29/05/16.
 */
public class WPContentContainer extends Container implements IOCContainerAware, MessageReceiver {

    static final String Tag = WPContentContainer.class.getSimpleName();

    /** The content container's container. */
    private Container iocContainer;
    /** Container configuration template. */
    private Configuration configTemplate;
    /** Command scheduler for unpack and refresh operations. */
    private CommandScheduler commandScheduler;
    /** Location for staging downloaded content prior to deployment. */
    private String stagingPath;
    /** The name of the posts DB. */
    private String postDBName;
    /** The URL of the WP posts feed. */
    private String feedURL;
    /** The location of pre-packaged post content, relative to the installed app. */
    private String packagedContentPath;
    /** The location of base content. */
    private String baseContentPath;
    /** The location of downloaded post content once deployed. */
    private String contentPath;
    /** The scheme name the URI handler is bound to; defaults to wp: */
    private String uriSchemeName;
    /** The WP realm name. Used for authentication, defaults to 'semo'. */
    private String wpRealm;
    /** Action to be posted when the container wants to show the login form. */
    private String showLoginAction;
    /** The posts DB instance. */
    private DB postDB;
    /** Whether to reset the post DB on start. (Useful for debug). */
    private boolean resetPostDB;
    /** Interval in minutes between checks for content updates. */
    private int updateCheckInterval;
    /** The content protocol instance; manages feed downloads. */
    private WPContentCommandProtocol contentProtocol;
    /** The wp: URI scheme. */
    private WPSchemeHandler uriScheme;
    /** Post list data formats. */
    private Map<String,DataFormatter> listFormats = new HashMap<>();
    /** Post data formats. */
    private Map<String,Object> postFormats = new HashMap<>();
    /** Template for generating post URIs. See uriForPostWithID: */
    private String postURITemplate;
    /** Factory for producing login and account management forms. */
    private WPContentContainerFormFactory formFactory;
    /** Map of pre-defined post filters, keyed by name. */
    private Map<String,DBFilter> filters = new HashMap<>();
    /** An object to use as the template context when rendering the client template for a post. */
    private WPClientTemplateContext clientTemplateContext;
    /** An object used to manage WP server authentication. */
    private WPAuthManager authManager;
    /** A HTTP client. */
    private Client httpClient;
    /** File utilities. */
    private Files files;
    /** The maximum number of rows to return for wp:search results. */
    private int searchResultLimit;
    /**
     * A map describing legal post-type relationships.
     * Allows legal child post types for a post type to be listed. Each map key is a parent post type,
     * and is mapped to either a child post type name (as a string), or a list of child post type names.
     * If a post type has no legal child post types then the type name should be mapped to an empty list.
     * Any post type not described in this property will allow any child post type.
     * Used by the getPostChildren: methods.
     */
    private JSONObject postTypeRelations;

    public WPContentContainer(Context context) {
        super( context, StandardURIHandler.getInstance( context ) );

        this.postDBName = "com.innerfunction.semo.content";
        this.feedURL = "";
        this.packagedContentPath = "";
        this.uriSchemeName = "wp";
        this.wpRealm = "semo";
        Object listFormats = m( kv("table", new WPDataTableFormatter() ) );
        this.listFormats = (Map<String,DataFormatter>)listFormats;
        this.postFormats = m( kv("webview", new WPDataWebviewFormatter() ) );
        this.postURITemplate = "{uriSchemeName}:/post/{postID}";

        // Configuration template. Note that the top-level property types are inferred from the
        // corresponding properties on the container object (i.e. self).
        Map<String,Object> template = m(
            kv("postDB", m(
                kv("name",           "$postDBName"),
                kv("version",        1),
                kv("resetDatabase",  true),
                // Table of wordpress posts.
                kv("tables", m(
                    kv( "posts", m(
                        kv( "columns", m(
                            kv( "id", m( kv( "type", "INTEGER" ), kv( "tag", "id" ) ) ), // Post ID
                            kv( "title", m( kv( "type", "TEXT" ) ) ),
                            kv( "type", m( kv( "type", "TEXT" ) ) ),
                            kv( "status", m( kv( "type", "TEXT" ) ) ),       // i.e. WP post status
                            kv( "modified", m( kv( "type", "TEXT" ) ) ),     // Modification date/time; ISO 8601 format string.
                            kv( "content", m( kv( "type", "TEXT" ) ) ),
                            kv( "imageid", m( kv( "type", "INTEGER" ) ) ),   // ID of the post's featured image.
                            kv( "location", m( kv( "type", "STRING" ) ) ),   // The post's location; packaged, downloaded or server.
                            kv( "url", m( kv( "type", "STRING" ) ) ),        // The post's WP URL.
                            kv( "filename", m( kv( "type", "TEXT" ) ) ),     // Name of associated media file (i.e. for attachments)
                            kv( "parent", m( kv( "type", "INTEGER" ) ) ),    // ID of parent page/post.
                            kv( "menu_order", m( kv( "type", "INTEGER" ) ) ) // Sort order; mapped to post.menu_order.
                        ) )
                    ) ),
                    // Table of parent/child post closures. Used to efficiently map descendant post relationships.
                    // See http://dirtsimple.org/2010/11/simplest-way-to-do-tree-based-queries.html for a simple description.
                    kv( "closures", m(
                        kv( "columns", m(
                            kv( "parent", m( kv( "type", "INTEGER" ) ) ),
                            kv( "child", m( kv( "type", "INTEGER" ) ) ),
                            kv( "depth", m( kv( "type", "INTEGER" ) ) )
                        ) )
                    ) )
                ))
            )),
            kv("contentProtocol", m(
                kv("feedURL",                "$feedURL"),
                kv("postDB",                 "@named:postDB"),
                kv("stagingPath",            "$stagingPath"),
                kv("packagedContentPath",    "$packagedContentPath"),
                kv("baseContentPath",        "$baseContentPath"),
                kv("contentPath",            "$contentPath")
            )),
            kv("clientTemplateContext", m(
                kv("*ios-class",             "IFWPClientTemplateContext"),
                kv("ext", m(
                    kv("childPosts", m(
                        kv("*ios-class",     "IFWPChildPostRendering")
                    ))
                ))
            )),
            kv("packagedContentPath",        "$packagedContentPath"),
            kv("contentPath",                "$contentPath"),
            kv("commandScheduler",           m(
                kv("*and-class", "com.innerfunction.semo.commands.CommandScheduler")
            ))
        );
        this.configTemplate = new Configuration( template, context );

        this.uriScheme = new WPSchemeHandler( this );

        // Cache locations.
        File storageDir = Files.getStorageDir( context );
        this.stagingPath = new File( storageDir, "com.innerfunction.semo.staging").getAbsolutePath();
        this.baseContentPath = new File( storageDir, "com.innerfunction.semo.base").getAbsolutePath();

        File cacheDir = Files.getCacheDir( context );
        this.contentPath = new File( cacheDir, "com.innerfunction.semo.content").getAbsolutePath();

        // Factory for producing login + account management forms.
        this.formFactory = new WPContentContainerFormFactory( this );

        this.httpClient = new Client( context );

        this.authManager = new WPAuthManager( this );
        httpClient.setAuthenticationDelegate( authManager );

        this.searchResultLimit = 100;

        this.files = new Files( context );
    }

    /** Unpack packaged content. */
    public void unpackPackagedContent() {
        int count = postDB.countInTable("posts", "1=1");
        if( count == 0 ) {
            commandScheduler.appendCommand("content.unpack");
            // Note: executeQueue() isn't called here but is called by the startService() method
            // after this method is called.
        }
    }

    /** Refresh content. */
    public void refreshContent() {
        commandScheduler.appendCommand("content.refresh");
        commandScheduler.executeQueue();
    }

    /** Download content from the specified URL and store in the content location using the specified filename. */
    public void getContentAndWriteToFile(String url, String filename) {
        String filepath = Paths.join( contentPath, filename );
        commandScheduler.appendCommand("get %s %s", url, filepath );
        commandScheduler.executeQueue();
    }

    /** Generate a URI to reference the post with the specified ID. */
    public String makeURIForPostWithID(String postID) {
        Map<String,Object> context = m( kv( "uriSchemeName", uriSchemeName ), kv( "postID", postID ) );
        return StringTemplate.render( postURITemplate, context );
    }

    /** Return the child posts of a specified post. Doesn't render the post content. */
    public List<Map<String,Object>> getPostChildren(String postID, Map<String,Object> params) {
        return getPostChildren( postID, params, false );
    }

    /** Return the child posts of a specified post. Optionally renders the post content. */
    public List<Map<String,Object>> getPostChildren(String postID, Map<String,Object> params, boolean renderContent) {
        if( params == null ) {
            params = new HashMap<>();
        }
        // Check the post type.
        Map<String,Object> postData = postDB.read("posts", postID );
        String postType = (String)postData.get("type");
        // Check for child type relations for this post type.
        Object childTypes = postTypeRelations.get( postType );
        if( childTypes != null ) {
            params.putAll( m( kv("type", childTypes ) ) );
        }
        params.put("parent", postID );
        // Create the query.
        DBFilter filter = new DBFilter();
        filter.setTable("posts");
        filter.setFilters( params );
        filter.setOrderBy("menu_order");
        // Query the database.
        List<Map<String,Object>> result = filter.applyTo( postDB,  null );
        // Render content for each child post.
        if( renderContent ) {
            List<Map<String,Object>> posts = new ArrayList<>();
            for( Map<String,Object> row : result ) {
                posts.add( renderPostContent( row ) );
            }
            result = posts;
        }
        return result;
    }

    /** Get all descendents of a post. Returns the posts children, grandchildren etc. */
    public Object getPostDescendants(String postID, Map<String,Object> params) {
        List<Map<String, Object>> result = postDB.performQuery( "SELECT posts.*"
            + " FROM posts, closures"
            + " WHERE closures.parent=? AND closures.child=posts.id AND depth > 0"
            + " ORDER BY depth, parent, menu_order", postID );
        boolean renderContent = "true".equals( params.get( "content" ) );
        if( renderContent ) {
            List<Map<String, Object>> posts = new ArrayList<>();
            for( Map<String, Object> row : result ) {
                posts.add( renderPostContent( row ) );
            }
            result = posts;
        }
        return result;
    }

    /**
     * Return data for a specified post.
     * TODO: Confirm that 'params' isn't needed here? (Compare to iOS code).
     */
    public Object getPost(String postID, Map<String,Object> params) {
        // Read the post data.
        Map<String,Object> postData = postDB.read("posts", postID );
        // Render the post content.
        postData = renderPostContent( postData );
        // Load the client template for the post type.
        String postType = (String)postData.get("type");
        String templateName = String.format("template-%s.html", postType );
        String templatePath = Paths.join( baseContentPath, templateName );
        if( !files.fileRefExists( templatePath ) ) {
            templatePath = Paths.join( baseContentPath, "template-single.html");
            if( !files.fileRefExists( templatePath ) ) {
                Log.w( Tag, String.format("Client template for post type '%s' not found at %s", postType, baseContentPath ) );
                return null;
            }
        }
        // Assume at this point that the template file exists.
        String template = files.readStringFromRef( templatePath );
        // Generate the full post HTML using the post data and the client template.
        Object context = clientTemplateContext.makeTemplateContextForPostData( postData );
        // Render the post template
        String postHTML = renderTemplate( template, context );
        // Generate a content URL within the base content directory - this to ensure that references
        // to base content can be resolved as relative references.
        /*
        String separator = (baseContentPath.endsWith("/") ? "" : "/");
        String contentURL = String.format("file://%s%s%s-%s.html", baseContentPath, separator, postType, postID );
        */
        String postFilename = String.format("%s-%s.html", postType, postID );
        String contentRef = Paths.join( baseContentPath, postFilename );
        String contentURL = Files.fileRefToURL( contentRef );
        // Add the post content and URL to the post data.
        postData.put("content", postHTML );
        postData.put("contentURL", contentURL );
        return postData;
    }

    /** Query the post database using a predefined filter. */
    public Object queryPostsUsingFilter(String filterName, Map<String,Object> params) {
        Object postData = null;
        if( filterName != null ) {
            DBFilter filter = filters.get( filterName );
            if( filter != null ) {
                postData = filter.applyTo( postDB, params );
            }
        }
        else {
            // Construct an anonymous filter instance.
            DBFilter filter = new DBFilter();
            filter.setTable("posts");
            filter.setOrderBy("menu_order");
            // Construct a set of filter parameters from the URI parameters.
            Regex re = new Regex("^(\\w+)\\.(.*)");
            Map<String,Object> filterParams = new HashMap<>();
            for( String paramName : params.keySet() ) {
                // The 'orderBy' parameter is a special name used to specify sort order.
                if( "_orderBy".equals( paramName ) ) {
                    filter.setOrderBy( KeyPath.getValueAsString("_orderBy", params ) );
                    continue;
                }
                String fieldName = paramName;
                String paramValue = KeyPath.getValueAsString( paramName, params );
                // Check for a comparison suffix on the name.
                String[] groups = re.matches( paramName );
                if( groups != null && groups.length > 1 ) {
                    fieldName = groups[0];
                    String comparison = groups[1];
                    if( "min".equals( comparison ) ) {
                        paramValue = String.format(">%s", paramValue );
                    }
                    else if( "max".equals( comparison ) ) {
                        paramValue = String.format("<%s", paramValue );
                    }
                    else if( "like".equals( comparison ) ) {
                        paramValue = String.format("LIKE %s", paramValue );
                    }
                    else if( "not".equals( comparison ) ) {
                        paramValue = String.format("NOT %s", paramValue );
                    }
                }
                filterParams.put( fieldName, paramValue );
            }
            // Remove any parameters not corresponding to a column on the posts table.
            filter.setFilters( postDB.filterColumnNamesForTable("posts", filterParams ) );
            // Apply the filter.
            postData = filter.applyTo( postDB, null );
        }
        String format = KeyPath.getValueAsString("_format", params );
        if( format == null ) {
            format = "table";
        }
        DataFormatter formatter = listFormats.get( format );
        if( formatter != null ) {
            postData = formatter.formatData( postData );
        }
        return postData;
    }

    /**
     * Search the post database for the specified text in the specified post types with an optional parent post.
     * When the parent post ID is specified, the search will be confined to that post and any of its
     * descendants (i.e. children, grand-children etc.).
     * TODO: Need to examine use cases, maybe postTypes should be List<String>.
     */
    public Object searchPostsForText(String text, String searchMode, String parentPostID, String... postTypes) {
        Object postData = null;
        String tables = "posts";
        String where = null;
        List<String> params = new ArrayList<>();
        text = String.format("%%%s%%", text );
        if( "exact".equals( searchMode ) ) {
            where = "title LIKE ? OR content LIKE ?";
            params.add( text );
            params.add( text );
        }
        else {
            List<String> terms = new ArrayList<>();
            String[] tokens = text.split(" ");
            for( String token : tokens ) {
                terms.add("(title LIKE ? OR content LIKE ?)");
                // TODO: Trim the token, check for empty tokens.
                String param = String.format("%%%s%%", token );
                params.add( param );
                params.add( param );
            }
            if( "any".equals( searchMode ) ) {
                where = TextUtils.join(" OR ", terms );
            }
            else if( "all".equals( searchMode ) ) {
                where = TextUtils.join(" AND ", terms );
            }
        }
        if( postTypes != null && postTypes.length > 0 ) {
            String typeClause;
            if( postTypes.length == 1 ) {
                typeClause = String.format("type='%s'", postTypes[0] );
            }
            else {
                typeClause = String.format("type IN ('%s')", TextUtils.join("','", postTypes ));
            }
            if( where != null ) {
                where = String.format("(%s) AND %s", where, typeClause );
            }
            else {
                where = typeClause;
            }
        }
        if( where == null ) {
            where = "1=1";
        }
        if( parentPostID != null ) {
            // If a parent post ID is specified then add a join to, and filter on, the closures
            // table.
            tables = tables.concat(", closures");
            where = where.concat(" AND closures.parent=? AND closures.child=posts.id");
            params.add( parentPostID );
        }
        String sql = String.format("SELECT posts.* FROM %s WHERE %s LIMIT %d", tables, where, searchResultLimit );
        postData = postDB.performQuery( sql, params );
        // TODO: Filters?
        DataFormatter formatter = listFormats.get("search");
        if( formatter == null ) {
            formatter = listFormats.get("table");
        }
        if( formatter != null ) {
            postData = formatter.formatData( postData );
        }
        return postData;
    }

    /** Render a post's content by evaluating template reference's within the content field. */
    public Map<String,Object> renderPostContent(Map<String,Object> postData) {
        Object context = clientTemplateContext.makeTemplateContextForPostData( postData );
        String contentHTML = renderTemplate( (String)postData.get("content"), context );
        postData.put( "content", contentHTML );
        return postData;
    }

    /** Show the login form. */
    public void showLoginForm() {
        AppContainer.getAppContainer().postMessage( showLoginAction, this );
    }

    /** Render a template with the specified data. */
    private String renderTemplate(String template, Object data) {
        String result = "";
        try {
            result = Mustache.compiler().compile( template ).execute( data );
        }
        catch(MustacheException me) {
            result = String.format("<h1>Template error</h1><pre>%s</pre>", me.getMessage() );
        }
        return result;
    }

    // IOCContainerAware

    @Override
    public void setIOCContainer(Container container) {
        this.iocContainer = container;
    }

    /** Called immediately before the object is configured by calls to its properties. */
    @Override
    public void beforeIOCConfigure(Configuration configuration) {

    }

    /** Called immediately after the object is configured by calls to its properties. */
    @Override
    public void afterIOCConfigure(Configuration configuration) {

        // Setup configuration parameters.
        Map<String,Object> parameters = m(
            kv("postDBName",             postDBName),
            kv("resetPostDB",            resetPostDB),
            kv("feedURL",                feedURL),
            kv("stagingPath",            stagingPath),
            kv("packagedContentPath",    packagedContentPath),
            kv("baseContentPath",        baseContentPath),
            kv("contentPath",            contentPath),
            kv("listFormats",            listFormats),
            kv("postFormats",            postFormats)
        );

        // TODO: There should be some standard method for doing the following, but need to consider what
        // the component configuration template pattern is exactly first.

        // Resolve a URI handler for the container's components, and add a modified named: scheme handler
        // pointed at this container.
        URIHandler uriHandler = configuration.getURIHandler();
        NamedScheme namedScheme = new NamedScheme( this );
        uriHandler = uriHandler.replaceURIScheme("named", namedScheme );

        // Create the container's component configuration and setup to use the new URI handler
        Configuration componentConfig = configTemplate.extendWithParameters( parameters );
        componentConfig.setURIHandler( uriHandler ); // This necessary for relative URIs within the config to work.
        /* TODO Confirm that root is no longer needed
        componentConfig.setRoot( this );
        */

        // Configure the container's components.
        configureWith( componentConfig );

        // Configure the command scheduler.
        if( commandScheduler != null ) {
            commandScheduler.setQueueDBName( String.format( "%s.scheduler", postDBName ) );
            if( contentProtocol != null ) {
                commandScheduler.setCommand( "content", contentProtocol );
            }

            GetURLCommand getCmd = new GetURLCommand( httpClient );
            getCmd.setMaxRequestsPerMinute( 30.0f );
            commandScheduler.setCommand( "get", getCmd );

            DownloadZipCommand dlzipCmd = new DownloadZipCommand( httpClient, commandScheduler );
            commandScheduler.setCommand( "dlzip", dlzipCmd );
        }
        else Log.w( Tag, "commandScheduler not found");
    }

    // MessageReceiver

    @Override
    public boolean receiveMessage(Message message, Object sender) {
        if( message.hasName("logout") ) {
            authManager.logout();
            showLoginForm();
            return true;
        }
        if( message.hasName("password-reminder") ) {
            authManager.showPasswordReminder();
            return true;
        }
        if( message.hasName("show-login") ) {
            showLoginForm();
            return true;
        }
        return false;
    }

    // Service

    // A handler for periodic execution of the content refresh.
    private Handler handler = new Handler();
    // A runner for executing the content refresh task
    private Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshContent();
            // Interval is defined in minutes, so convert to ms by multiplying (60 secs) x (1000 ms)
            handler.postDelayed( this, updateCheckInterval * 60000 );
        }
    };

    @Override
    public void startService() {
        super.startService();
        unpackPackagedContent();
        // Schedule content updates.
        if( updateCheckInterval > 0 ) {
            refreshTask.run();
        }
        else commandScheduler.executeQueue();   // Start command queue execution.
    }

    // Property getter/setters.

    public void setCommandScheduler(CommandScheduler scheduler) {
        this.commandScheduler = scheduler;
    }

    public void setPostDBName(String name) {
        this.postDBName = name;
    }

    public void setFeedURL(String url) {
        this.feedURL = url;
    }

    public String getFeedURL() {
        return feedURL;
    }

    public void setPackagedContentPath(String path) {
        this.packagedContentPath = path;
    }

    public void setBaseContentPath(String path) {
        this.baseContentPath = path;
    }

    public void setContentPath(String path) {
        this.contentPath = path;
    }

    public void setUriSchemeName(String name) {
        this.uriSchemeName = name;
    }

    public void setWpRealm(String realm) {
        this.wpRealm = realm;
    }

    public String getWpRealm() {
        return wpRealm;
    }

    public void setShowLoginAction(String action) {
        this.showLoginAction = action;
    }

    public void setPostDB(DB db) {
        this.postDB = db;
    }

    public DB getPostDB() {
        return postDB;
    }

    public void setResetPostDB(boolean reset) {
        this.resetPostDB = reset;
    }

    public void setUpdateCheckInterval(int interval) {
        this.updateCheckInterval = interval;
    }

    public void setContentProtocol(WPContentCommandProtocol protocol) {
        this.contentProtocol = protocol;
    }

    public void setUriScheme(WPSchemeHandler scheme) {
        this.uriScheme = scheme;
    }

    public WPSchemeHandler getUriScheme() {
        return uriScheme;
    }

    public void setListFormats(Map<String,DataFormatter> formats) {
        this.listFormats.putAll( formats );
    }

    public void setPostFormats(Map<String,Object> formats) {
        this.postFormats.putAll( formats );
    }

    public void setPostURITemplate(String template) {
        this.postURITemplate = template;
    }

    public void setFormFactory(WPContentContainerFormFactory factory) {
        this.formFactory = factory;
    }

    public WPContentContainerFormFactory getFormFactory() {
        return formFactory;
    }

    public void setFilters(Map filters) {
        this.filters = filters;
    }

    public void setClientTemplateContext(WPClientTemplateContext context) {
        this.clientTemplateContext = context;
    }

    public void setAuthManager(WPAuthManager authManager) {
        this.authManager = authManager;
    }

    public WPAuthManager getAuthManager() {
        return authManager;
    }

    public void setHTTPClient(Client client) {
        this.httpClient = client;
    }

    public Client getHTTPClient() {
        return httpClient;
    }

    public void setSearchResultLimit(int limit) {
        this.searchResultLimit = limit;
    }

    public void setPostTypeRelations(JSONObject relations) {
        this.postTypeRelations = relations;
    }
}
