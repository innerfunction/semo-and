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
package com.innerfunction.semo.form;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.innerfunction.pttn.app.ViewController;

/**
 * Attached by juliangoacher on 12/07/16.
 */
public class FormViewController extends ViewController {

    private FormView form;

    public FormViewController(Context context) {
        super( context );
        setLayoutName("view_activity_layout");
        this.form = new FormView( context );
        form.setViewController( this );
    }

    @Override
    public View onCreateView(Activity activity) {
        addView( form );
        return form;
    }

    @Override
    public void onResume() {
        form.refresh();
    }

    public FormView getForm() {
        return form;
    }

    public void setForm(FormView form) {
        this.form = form;
    }

    public void setBackgroundColor(int color) {
        this.form.setBackgroundColor( color );
    }
}
