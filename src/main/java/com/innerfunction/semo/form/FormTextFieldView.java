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
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;

/**
 * Created by juliangoacher on 28/05/16.
 */
public class FormTextFieldView extends FormFieldView {

    private boolean isValid = true;
    private boolean isPassword = false;
    private boolean isEditable = true;
    private boolean isRequired = false;
    private String hasSameValueAs;
    private EditText input;
    private String valueLabel;

    public FormTextFieldView(Context context) {
        super( context );
        setIsInput( true );
    }

    @Override
    public void setTitle(String title) {
        super.setTitle( title );
        // self.textLabel.title = title;
        input.setHint( title );
    }

    public void setIsPassword(boolean isPassword) {
        this.isPassword = isPassword;
        if( isPassword ) {
            input.setTransformationMethod( PasswordTransformationMethod.getInstance() );
        }
        else {
            input.setTransformationMethod( null );
        }
    }

    public void setIsEditable(boolean isEditable) {
        this.isEditable = isEditable;
    }

    public void setIsRequired(boolean isRequired) {
        this.isRequired = isRequired;
    }

    public void setValueLabel(String label) {
        this.valueLabel = label;
    }

    @Override
    public void setValue(Object value) {
        String strValue = "";
        if( value instanceof String ) {
            strValue = (String)value;
        }
        else if( value != null ) {
            strValue = value.toString();
        }
        super.setValue( strValue );
        if( isPassword ) {
            StringBuilder sb = new StringBuilder();
            for( int i = 0; i < strValue.length(); i++ ) {
                sb.append('\u25CF');
            }
            strValue = sb.toString();
        }
        setValueLabel( strValue );
        // TODO Display value
        validate();
    }

    @Override
    public boolean takeFieldFocus() {
        boolean focusable = isEditable && getForm().isEnabled();
        if( focusable ) {
            // TODO Animate transition to edit view
        }
        return focusable;
    }

    @Override
    public void releaseFieldFocus() {
        // TODO
    }

    @Override
    public boolean validate() {
        isValid = true;
        String strValue = (String)getValue();
        if( isRequired && (strValue == null || strValue.trim().length() == 0) ) {
            isValid = false;
        }
        else if( hasSameValueAs != null ) {
            Object otherValue = getForm().getFieldValue( hasSameValueAs );
            if( otherValue == null ) {
                isValid = (strValue == null);
            }
            else {
                isValid = otherValue.equals( strValue );
            }
        }
        // TODO Show warning if invalid
        return isValid;
    }
}
