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

import com.innerfunction.pttn.Configuration;
import com.innerfunction.pttn.Container;
import com.innerfunction.pttn.IOCContainerAware;
import com.innerfunction.semo.db.DB;
import com.innerfunction.util.KeyPath;
import com.innerfunction.util.Maps;
import com.innerfunction.util.Paths;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data context implementation for the client template.
 * The client template is used to generate post HTML pages using the latest mobile
 * theme. The main purpose of this class is to replace image attachment references
 * with URLs referencing the attachment file in its current location, and to replace
 * post references with appropriate URIs.
 *
 * Attached by juliangoacher on 07/07/16.
 */
public class WPClientTemplateContext implements IOCContainerAware {

    private WPContentContainer contentContainer;
    private AttachmentsProxy attachmentsProxy;
    private PostsProxy postsProxy;

    @Override
    public void setIOCContainer(Container container) {
        this.contentContainer = (WPContentContainer)container;
        this.attachmentsProxy = new AttachmentsProxy( contentContainer );
        this.postsProxy = new PostsProxy( contentContainer );
    }

    /** Called immediately before the object is configured by calls to its properties. */
    @Override
    public void beforeIOCConfigure(Configuration configuration) {
        // No-op
    }

    /** Called immediately after the object is configured by calls to its properties. */
    @Override
    public void afterIOCConfigure(Configuration configuration) {
        // No-op
    }

    public Map<String,Object> makeTemplateContextForPostData(Map<String,Object> postData) {
        Map<String,Object> data = Maps.extend( postData );
        data.put("attachment", attachmentsProxy );
        data.put("post", postsProxy );
        data.put("ext", new ExtProxy( postData, this, contentContainer ) );
        return data;
    }

    /**
     * A stub implementation of the map interface.
     * Should be subclassed and an implementation of the get() method provided.
     */
    static abstract class MapStub implements Map<String,Object> {

        private Set<String> keys;

        MapStub(String... keys) {
            this.keys = new HashSet<>();
            for( String key : keys ) {
                this.keys.add( key );
            }
        }

        @Override
        public void clear() {
            // No-op
        }

        @Override
        public boolean containsKey(Object key) {
            return keys.contains( key );
        }

        @Override
        public boolean containsValue(Object value) {
            return false; // Not implemented
        }

        @Override
        public Set<Map.Entry<String,Object>> entrySet() {
            return null; // Not implemented
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Set<String> keySet() {
            return keys;
        }

        @Override
        public Object put(String key, Object value) {
            return null; // Not implemented
        }

        @Override
        public void putAll(Map<? extends String,? extends Object> values) {
            // Not implemented.
        }

        @Override
        public Object remove(Object key) {
            return null; // Not implemented
        }

        @Override
        public int size() {
            return 0; // Not implemented
        }

        @Override
        public Collection<Object> values() {
            return null; // Not implemented
        }
    }

    static class AttachmentsProxy extends MapStub {

        private WPContentContainer container;
        private DB postDB;
        private String packagedContentPath;
        private String contentPath;
        /**
         * A cache of previously requested attachments, keyed by ID.
         * When reading from a map, jMustache calls containsKey(..) before calling get(..)
         * so it makes sense for get(..) to maintain a cache of attachment requests and for
         * containsKey(..) to forward all requests to get(..).
         */
        private Map cache = new HashMap();

        public AttachmentsProxy(WPContentContainer container) {
            super();
            this.container = container;
            this.postDB = (DB)container.getNamed("postDB");
            this.packagedContentPath = (String)container.getNamed("packagedContentPath");
            this.contentPath = (String)container.getNamed("contentPath");
        }

        @Override
        public boolean containsKey(Object key) {
            return get( key ) != null;
        }

        @Override
        public Object get(Object key) {
            // First check for cached responses - see comment on cache member.
            if( cache.containsKey( key ) ) {
                return cache.get( key );
            }
            // The template placeholder is in the form {attachment.x}, where 'x' is an attachment
            // post ID. Read the attachment data from the posts DB and base on its 'location' value,
            // return one of the following:
            // * packaged:   Attachment file is packaged with the app; return file: URL pointing at
            //               the file under the packaged content path.
            // * downloaded: Attachment file has been downloaded from the server; return a file: URL
            //               pointing at the file under the content path.
            // * server:     Attachment file hasn't been downloaded and is still on the server;
            //               return its server URL.
            String postID = (String)key;
            Map<String,Object> attachment = postDB.read("posts", postID );
            String location = KeyPath.getValueAsString("location", attachment );
            String filename = KeyPath.getValueAsString("filename", attachment );
            String url = KeyPath.getValueAsString("url", attachment );
            boolean cacheResult = true;
            if( "packaged".equals( location ) ) {
                String path = Paths.join( packagedContentPath, filename );
                url = String.format("file://%s", path );
            }
            else if( "downloaded".equals( location ) ) {
                String path = Paths.join( contentPath, filename );
                File file = new File( path );
                if( file.exists() ) {
                    url = String.format("file://%s", path );
                }
                else {
                    // File probably removed by system to free disk space. The attachment URL will
                    // be returned instead, meaning that the webview can attempt to download the
                    // file from the server if a connection is available.
                    // Start a download of the file through the content protocol. Note that if the
                    // webview was able to download the file then the protocol should be able to
                    // load from the cache.
                    container.getContentAndWriteToFile( url, filename );
                    // Don't cache the result, want the download to become available.
                    cacheResult = false;
                }
            }
            // Else location == 'server' or other. Use the attachment URL to download from server.

            // Cache the result before returning.
            if( cacheResult ) {
                cache.put( key, url );
            }

            return url;
        }
    }

    static class PostsProxy extends MapStub {

        private DB postDB;
        private WPContentContainer container;

        public PostsProxy(WPContentContainer container) {
            super();
            this.postDB = (DB)container.getNamed("postDB");
            this.container = container;
        }

        @Override
        public Object get(Object key) {
            String postID = (String)key;
            Map<String,Object> post = postDB.read("posts", postID );
            String location = KeyPath.getValueAsString("location", post );
            String uri;
            if( "server".equals( location ) ) {
                uri = KeyPath.getValueAsString("url", post );
            }
            else {
                uri = container.makeURIForPostWithID( postID );
            }
            return uri;
        }
    }

    static class ExtProxy extends MapStub {

        private Map<String,Object> postData;
        private WPClientTemplateContext context;
        private WPContentContainer container;

        public ExtProxy(Map<String,Object> postData, WPClientTemplateContext context, WPContentContainer container) {
            super("childPosts");
            this.postData = postData;
            this.context = context;
            this.container = container;
        }

        @Override
        public Object get(Object key) {
            if( "childPosts".equals( key ) ) {
                // Read the list of child posts.
                String postID = KeyPath.getValueAsString("id", postData );
                List<Map<String,Object>> childPosts = container.getPostChildren( postID, null, true );
                // Generate a child context for each child post.
                List<Map<String,Object>> childContexts = new ArrayList<>();
                for( Map<String,Object> childPost : childPosts ) {
                    childContexts.add( context.makeTemplateContextForPostData( childPost ) );
                }
                return childContexts;
            }
            return null;
        }
    }
}
