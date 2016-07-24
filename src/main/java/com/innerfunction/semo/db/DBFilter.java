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
package com.innerfunction.semo.db;

import android.text.TextUtils;

import com.innerfunction.util.Regex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Attached by juliangoacher on 01/06/16.
 */
public class DBFilter {

    private List<String> paramNames;
    private String sql;
    private String table;
    private Map<String,Object> filters;
    private String orderBy;
    private String predicateOp = "AND";

    public DBFilter() {}

    public List<Map<String,Object>> applyTo(DB db, Map<String,Object> params) {
        // Prepare the SQL. If the filter has been configured using table/filters/orderBy properties
        // then this.sql will be null on first call.
        if( this.sql == null && table != null ) {
            List<String> terms = new ArrayList<>();
            terms.add("SELECT * FROM");
            terms.add( table );
            if( this.filters != null ) {
                terms.add("WHERE");
                // Regex pattern for detecting filter values that contain a predicate.
                Regex predicatePattern = new Regex("^\\s*(=|<|>|LIKE\\s|NOT\\s)");
                boolean insertPredicateOp = false;
                for( String filterName : filters.keySet() ) {
                    if( insertPredicateOp ) {
                        terms.add( predicateOp );
                    }
                    terms.add( filterName );
                    Object filterValue = filters.get( filterName );
                    if( filterValue instanceof List ) {
                        // Use a WHERE ... IN (...) to query for an array of values.
                        filterValue = TextUtils.join(",", (List)filterValue );
                        terms.add( String.format("IN (%s)", filterValue ) );
                    }
                    else {
                        // Convert a non-string filter value to a string.
                        String strFilterValue;
                        if( filterValue instanceof String ) {
                            strFilterValue = (String)filterValue;
                        }
                        else {
                            strFilterValue = filterValue.toString();
                        }
                        if( predicatePattern.test( strFilterValue ) ) {
                            terms.add( strFilterValue );
                        }
                        else if( strFilterValue.charAt( 0 ) == '?' ) {
                            // ? prefix indicates a parameterized value; don't quote in the SQL.
                            terms.add( String.format("= %s", strFilterValue ) );
                        }
                        else {
                            // Escape single quotes in the value.
                            strFilterValue = strFilterValue.replaceAll("'", "\\'");
                            terms.add( String.format("= '%s'", strFilterValue ) );
                        }
                    }
                    insertPredicateOp = true;
                }
            }
            if( orderBy != null ) {
                terms.add("ORDER BY");
                terms.add( orderBy );
            }
            sql = TextUtils.join(" ", terms );
        }
        // If still no SQL then the filter hasn't been configured correctly.
        if( sql == null ) {
            return Collections.EMPTY_LIST;
        }
        // Construct parameters for the SQL query.
        List<String> sqlParams = new ArrayList<>();
        for( String paramName : paramNames ) {
            Object value = params.get( paramName );
            if( value != null ) {
                sqlParams.add( value.toString() );
            }
            else {
                sqlParams.add( DB.NullParameterValue );
            }
        }
        // Execute the SQL and return the result.
        List<Map<String,Object>> result = db.performQuery( sql, sqlParams );
        return result;
    }

    public void setSql(String sql) {
        // Extract parameter names from the SQL string. Parameter names appear as ?xxx in the SQL.
        paramNames = new ArrayList<>();
        Regex re = new Regex("\\?(\\w+)(.*)");
        String groups[] = re.matches( sql );
        while( groups.length > 0 ) {
            paramNames.add( groups[1] );
            groups = re.matches( groups[2] );
        }
        // Replace all argument placeholders with just '?' in the SQL.
        this.sql = sql.replaceAll("\\?\\w+", "?");
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setFilters(Map<String,Object> filters) {
        this.filters = filters;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

}
