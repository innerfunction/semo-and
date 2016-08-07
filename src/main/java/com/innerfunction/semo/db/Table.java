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

import com.innerfunction.pttn.IOCObjectAware;

import java.util.List;
import java.util.Map;

/**
 * Attached by juliangoacher on 09/05/16.
 */
public class Table implements IOCObjectAware {

    protected String name;
    protected Column[] columns;
    protected int since = -1;
    protected int until = -1;
    protected List<?> data;

    public Table() {}

    public Table(String name, Column... columns) {
        this.name = name;
        this.columns = columns;
    }

    public void setName(String name) {
        this.name = name;
    }
/*
    public void setColumns(List<Column> columns) {
        this.columns = columns.toArray( new Column[columns.size()] );
    }
*/
    public void setColumns(Map<String,Column> columns) {
        this.columns = new Column[columns.size()];
        int idx = 0;
        for( String name : columns.keySet() ) {
            Column column = columns.get( name );
            column.setName( name );
            this.columns[idx++] = column;
        }
    }

    public void setSince(int since) {
        this.since = since;
    }

    public void setUntil(int until) {
        this.until = until;
    }

    public void setData(List<?> data) {
        this.data = data;
    }

    @Override
    public void notifyIOCObject(Object object, String propertyName) {
        // If table hasn't explicitly configured with a name then take its name from the
        // property is bound to. This is to allow configurations in the following form:
        // { "tables": { "table_name": { ...table def... } }
        if( this.name == null ) {
            this.name = propertyName;
        }
    }
}
