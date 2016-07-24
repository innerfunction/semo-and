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

/**
 * Attached by juliangoacher on 09/05/16.
 */
public class Column {

    protected String name;
    protected String type;
    protected String tag;
    protected int since = -1;
    protected int until = -1;

    public Column() {}

    public Column(String name, String type, String tag) {
        this.name = name;
        this.type = type;
        this.tag = tag;
    }

    public Column(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setSince(int since) {
        this.since = since;
    }

    public void setUntil(int until) {
        this.until = until;
    }
}
