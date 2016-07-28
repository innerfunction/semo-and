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
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private int defaultTitleAlignment = -1;

    public FormTextFieldView(Context context) {
        super( context );
        setIsInput( true );

        this.inputLayout = new LinearLayout( context );
        inputLayout.setGravity( Gravity.LEFT );
        LayoutParams editLayoutParams = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT );
        editLayoutParams.setMargins( 20, 0, 20, 20 );
        inputLayout.setLayoutParams( editLayoutParams );

        EditText input = new EditText( context );
        input.setTextColor( Color.BLACK );
        input.setHintTextColor( Color.GRAY );
        input.setSingleLine();
        input.setInputType( InputType.TYPE_CLASS_TEXT );
        setInput( input );

        // Ensure value label is visible.
        getValueLabel().setVisibility( VISIBLE );

        setValue( null );
    }

    public EditText getInput() {
        return input;
    }

    public void setInput(EditText input) {
        if( this.input != input ) {
            this.input = input;
            input.setLayoutParams( new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
            inputLayout.removeAllViews();
            inputLayout.addView( input );

            //input.setRawInputType( InputType.TYPE_CLASS_TEXT );
            // Return key behaviour - see http://stackoverflow.com/questions/1489852/android-handle-enter-in-an-edittext
            input.setImeOptions( EditorInfo.IME_ACTION_GO );
            input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if( actionId == EditorInfo.IME_NULL ) {
                        int action = event.getAction();
                        if( action == KeyEvent.ACTION_DOWN ) {
                            return true;
                        }
                        if( action == KeyEvent.ACTION_UP ) {
                            getForm().moveFocusToNextField();
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
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
        getValueLabel().setText( label );
    }

    public void setHasSameValueAs(String hasSameValueAs) {
        this.hasSameValueAs = hasSameValueAs;
    }

    @Override
    public void setValue(Object value) {
        final TextView titleLabel = getTitleLabel();
        // Record the title label's default text alignement if not already recorded.
        if( defaultTitleAlignment < 0 ) {
            defaultTitleAlignment = titleLabel.getGravity();
        }
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
        strValue = strValue.trim();
        setValueLabel( strValue );
        // Set title size and alignment according to whether there is a value or not.
        ViewGroup.LayoutParams layoutParams = titleLabel.getLayoutParams();
        final boolean hasValue = strValue.length() > 0;
        if( hasValue ) {
            titleLabel.setGravity( defaultTitleAlignment );
            // Adjust width if value label needs space to display fully.
            int totalWidth = getLabelPanel().getMeasuredWidth();
            titleLabel.measure( 0, 0 );
            int titleWidth = titleLabel.getMeasuredWidth();
            TextView valueLabel = getValueLabel();
            valueLabel.measure( 0, 0 );
            int valueWidth = valueLabel.getMeasuredWidth();
            int overflow = totalWidth - titleWidth - valueWidth;
            if( overflow < 0 ) {
                layoutParams.width = totalWidth - overflow;
            }
            else {
                layoutParams.width = LayoutParams.WRAP_CONTENT;
            }
        }
        else {
            titleLabel.setGravity( Gravity.CENTER );
            layoutParams.width = LayoutParams.MATCH_PARENT;
        }
        titleLabel.setLayoutParams( layoutParams );
        validate();
    }

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
            setValue( input.getText().toString() );
        }
    }

    @Override
    public boolean validate() {
        isValid = true;
        String strValue = (String)getValue();
        // Check that field has a value if required.
        if( isRequired && (strValue == null || strValue.trim().length() == 0) ) {
            isValid = false;
        }
        // Otherwise check value is same as another field, if specified.
        else if( hasSameValueAs != null ) {
            Object otherValue = getForm().getFieldValue( hasSameValueAs );
            if( otherValue == null ) {
                isValid = (strValue == null);
            }
            else {
                isValid = otherValue.equals( strValue );
            }
        }
        // Show a warning icon if invalid, hide warning icon otherwise.
        if( isValid ) {
            setAccessoryView( null );
        }
        else {
            showAccessoryImage( R.drawable.warning_icon, 50, 50 );
        }
        return isValid;
    }
}
