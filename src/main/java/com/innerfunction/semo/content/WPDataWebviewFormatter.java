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

import java.util.Map;

import static com.innerfunction.util.DataLiterals.*;

/**
 * Attached by juliangoacher on 07/07/16.
 */
public class WPDataWebviewFormatter implements DataFormatter {

    @Override
    public Object formatData(Object data) {
        Map<String,Object> _data = (Map<String,Object>)data;
        return m(
            kv("contentURL", _data.get("contentURL")),
            kv("content",    _data.get("postHTML"))
        );
    }
}
