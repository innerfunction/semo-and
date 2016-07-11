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

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Created by juliangoacher on 25/05/16.
 */
public class FormSubmitFieldView extends FormFieldView implements FormView.LoadingIndicator {

    private ProgressBar loadingIndicator;

    public FormSubmitFieldView(Context context) {
        super( context );
        setIsSelectable( true );
        this.loadingIndicator = new ProgressBar( context, null, android.R.style.Widget_ProgressBar_Small );
        loadingIndicator.setIndeterminate( true );
        loadingIndicator.setVisibility( INVISIBLE );
        loadingIndicator.setLayoutParams( new LinearLayout.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
        addView( loadingIndicator );
    }

    @Override
    public void onSelectField() {
        getForm().submit();
    }

    @Override
    public void showFormLoading(boolean loading) {
        getTitleLabel().setVisibility( loading ? INVISIBLE : VISIBLE );
        loadingIndicator.setVisibility( loading ? VISIBLE : INVISIBLE );
    }
}
