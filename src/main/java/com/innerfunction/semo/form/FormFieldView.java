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
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.innerfunction.pttn.app.AppContainer;

import java.util.List;

/**
 * Created by juliangoacher on 14/05/16.
 */
public class FormFieldView extends LinearLayout {

    static final int DefaultHeight = 45;

    /** The field's parent form. */
    private FormView form;
    /** Flag indicating whether the field represents an input. */
    private boolean isInput;
    /** Flag indicating whether the field is selectable. */
    private boolean isSelectable;
    /** The field name. */
    private String name;
    /** The field value. */
    private Object value;
    /** An action message to be posted when the field is selected. */
    private String selectAction;
    /** The field display height. */
    private int height = DefaultHeight;
    /** The field's background colour. */
    private int backgroundColor = Color.WHITE;
    /** The field's background colour when selected. */
    private int focusedBackgroundColor = Color.LTGRAY;
    /** The field's background image. */
    private Drawable backgroundImage;
    /** The field's background image when selected. */
    private Drawable focusedBackgroundImage;
    /** The field's title. */
    private String title;
    /** A text view used to display the field title. */
    private TextView titleLabel;
    /** A list of all the fields in this field's group. */
    private List<FormFieldView> fieldGroup;

    public FormFieldView(Context context) {
        super( context );
        this.titleLabel = new TextView( context );
        titleLabel.setLayoutParams( new LinearLayout.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
        addView( titleLabel );
    }

    public boolean takeFieldFocus() {
        return false;
    }

    public void releaseFieldFocus() {

    }

    public boolean validate() {
        return true;
    }

    public void setSelectedStatus(boolean selected) {
        setBackgroundColor( selected ? focusedBackgroundColor : backgroundColor );
        // TODO: Background image.
    }

    public void onSelectField() {
        if( selectAction != null ) {
            AppContainer.getAppContainer().postMessage( selectAction, this );
        }
    }

    // Properties

    public void setForm(FormView form) {
        this.form = form;
    }

    public FormView getForm() {
        return form;
    }

    public void setIsInput(boolean isInput) {
        this.isInput = isInput;
    }

    public void setIsSelectable(boolean isSelectable) {
        this.isSelectable = isSelectable;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setSelectAction(String selectAction) {
        this.selectAction = selectAction;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setBackgroundImage(Drawable image) {
        this.backgroundImage = image;
    }

    public void setFocusedBackgroundImage(Drawable image) {
        this.focusedBackgroundImage = image;
    }

    public void setTitle(String title) {
        this.title = title;
        titleLabel.setText( title );
    }

    public TextView getTitleLabel() {
        return titleLabel;
    }
}
