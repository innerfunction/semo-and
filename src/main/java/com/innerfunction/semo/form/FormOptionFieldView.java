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

import com.innerfunction.semo.R;

import java.util.List;

/**
 * Attached by juliangoacher on 23/05/16.
 */
public class FormOptionFieldView extends FormFieldView {

    private boolean optionSelected;
    private String optionValue;

    public FormOptionFieldView(Context context) {
        super( context );
        setIsInput( true );
    }

    @Override
    public void onSelectField() {
        List<FormFieldView> fieldGroup = getForm().getFieldsInNameGroup( getName() );
        for( FormFieldView field : fieldGroup ) {
            if( field instanceof FormOptionFieldView ) {
                ((FormOptionFieldView)field).setOptionSelected( false );
            }
            field.setValue( null );
        }
        setOptionSelected( true );
    }

    public boolean isSelectable() {
        return true;
    }

    public void setOptionSelected(boolean selected) {
        this.optionSelected = selected;
        if( selected ) {
            setValue( optionValue );
            showAccessoryImage( R.drawable.checkmark, 50, 50 );
        }
        else {
            setValue( null );
            showAccessoryImage( null, 0, 0, 0 );
        }
    }

    public boolean getOptionSelected() {
        return optionSelected;
    }

    public void setOptionValue(String value) {
        this.optionValue = value;
        if( optionSelected ) {
            setValue( value );
        }
    }

    public String getOptionValue() {
        return optionValue;
    }
}
