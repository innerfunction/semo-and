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
import android.graphics.Color;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;

import com.innerfunction.semo.R;

/**
 * Attached by juliangoacher on 28/05/16.
 */
public class FormTextFieldView extends FormFieldView {

    private boolean isValid = true;
    private boolean isPassword = false;
    private boolean isEditable = true;
    private boolean isRequired = false;
    private boolean isSelected = false;
    private String hasSameValueAs;
    private EditText input;
    private String valueLabel;
    private LinearLayout inputLayout;

    public FormTextFieldView(Context context) {
        super( context );
        setIsInput( true );

        this.inputLayout = new LinearLayout( context );
        inputLayout.setGravity( Gravity.LEFT );
        LayoutParams editLayoutParams = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        editLayoutParams.setMargins( 20, 20, 20, 20 );
        inputLayout.setLayoutParams( editLayoutParams );

        EditText input = new EditText( context );
        input.setTextColor( Color.BLACK );
        input.setHintTextColor( Color.GRAY );
        input.setSingleLine();
        input.setInputType( InputType.TYPE_CLASS_TEXT );
        setInput( input );
    }

    public EditText getInput() {
        return input;
    }

    public void setInput(EditText input) {
        this.input = input;
        input.setLayoutParams( new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
        inputLayout.removeAllViews();
        inputLayout.addView( input );
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

    public
    @Override
    public boolean takeFieldFocus() {
        boolean focusable = isEditable && getForm().isEnabled();
        if( focusable ) {
            isSelected = true;
            // Animate transition to edit view.
            removeView( cellLayout );
            addView( inputLayout );
            YoYo.with( Techniques.FlipInX ).duration( 600 ).playOn( this );
            // Focus input and show keyboard after transition.
            this.post(new Runnable() {
                @Override
                public void run() {
                    inputLayout.postDelayed( new Runnable() {
                        @Override
                        public void run() {
                            inputLayout.requestFocus();
                            InputMethodManager keyboard
                                = (InputMethodManager)getContext().getSystemService( Context.INPUT_METHOD_SERVICE );
                            keyboard.showSoftInput( inputLayout, 0 );
                        }
                    }, 1000);
                }
            });
        }
        return focusable;
    }

    @Override
    public void releaseFieldFocus() {
        if( isSelected ) {
            isSelected = false;
            removeView( inputLayout );
            addView( cellLayout );
            YoYo.with( Techniques.FlipInX ).duration( 600 ).playOn( this );
            // Update the form data if the input value has changed.
            String currentValue = input.getText().toString();
            if( !currentValue.equals( originalValue ) ) {
                getFormMenu().setDataValue( getName(), currentValue );
            }
        }
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
        if( isValid ) {
            setAccessoryView( null );
        }
        else {
            showAccessoryImage( R.drawable.warning_icon, 50, 50 );
        }
        return isValid;
    }
}
