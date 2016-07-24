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

import com.innerfunction.uri.CompoundURI;
import com.innerfunction.uri.URIScheme;
import com.innerfunction.util.KeyPath;

import java.util.Map;

/**
 * Handler for URIs in the wp: scheme.
 * The wp: scheme provides access to Wordpress posts downloaded to the local content database.
 * The scheme supports the following forms:
 *
 *  wp:posts                Return a list of post data. The following URI parameters are available:
 *                          - type: Filter by post type.
 *                          - modifiedTime: With optional .min, .max modifiers; filter by post modification time.
 *                          - where: Specify an arbitrary SQL where clause on the posts table.
 *                          - filter: Apply a pre-defined filter.
 *                          - format: Apply a named formatter to the result. Defaults to 'listdata'.
 *
 *  wp:posts/{id}           Return data for a specific post. The following URI parameters are available:
 *                          - format: Apply a named formatter to the result. Defaults to 'webview'.
 *
 *  wp:posts/{id}/children  Return a list of all posts with the specified post as their parent.
 *                          The result is sorted by the 'order' field value.
 *
 *  wp:search               Perform a full text search of the post database. The following URI parameters are available:
 *                          - text: The text to search for. Can be a space separated list of word tokens.
 *                          - mode: The text search mode; one of the following:
 *                              - any: Return posts containing any of the words.
 *                              - all: Return only posts containing all of the words.
 *                              - exact: Return only posts containing the exact phrase.
 *
 * Attached by juliangoacher on 07/07/16.
 */
public class WPSchemeHandler implements URIScheme {

    private WPContentContainer contentContainer;

    public WPSchemeHandler(WPContentContainer contentContainer) {
        this.contentContainer = contentContainer;
    }

    @Override
    public Object dereference(CompoundURI uri, Map<String, Object> params) {
        // The following URI path forms are supported:
        // * posts:                 Query all posts, and possibly filter by specified parameters.
        // * posts/filter/{name}:   Query all posts and apply the named filter.
        // * posts/{id}             Return the post with the specified ID.
        // * posts/{id}/children:   Return the children of a post with the specified ID.
        // TODO: Would it make more sense to use the first name component - i.e. 'posts' in all of the above
        // examples - as the data format name? Or posts/{filter}, post/{filter}/{id} ? Note also that the list
        // filter will need to generate URIs referencing the post detail.
        String path = uri.getName();
        String[] parts = path.split("/");
        String postID;
        if( parts.length > 0 ) {
            if( "posts".equals( parts[0] ) ) {
                switch( parts.length ) {
                case 1:
                    return contentContainer.queryPostsUsingFilter( null, params );
                case 2:
                    postID = parts[1];
                    return contentContainer.getPost( postID, params );
                case 3:
                    postID = parts[1];
                    if( "children".equals( parts[2] ) ) {
                        return contentContainer.getPostChildren( postID, params );
                    }
                    // TODO: Deprecate this - mispelling.
                    if( "descendents".equals( parts[2]) ) {
                        return contentContainer.getPostDescendants( postID, params );
                    }
                    if( "descendants".equals( parts[2]) ) {
                        return contentContainer.getPostDescendants( postID, params );
                    }
                    if( "filter".equals( parts[1] ) ) {
                        return contentContainer.queryPostsUsingFilter( parts[2], params );
                    }
                default:
                    break;
                }
            }
            else if( "search".equals( parts[0] ) ) {
                String text = KeyPath.getValueAsString("text", params );
                String mode = KeyPath.getValueAsString("mode", params );
                String[] postTypes = null;
                String types = KeyPath.getValueAsString("types", params );
                if( types != null ) {
                    postTypes = types.split(",");
                }
                String parent = KeyPath.getValueAsString("parent", params );
                return contentContainer.searchPostsForText( text, mode, parent, postTypes );
            }
        }
        return null;
    }
}
