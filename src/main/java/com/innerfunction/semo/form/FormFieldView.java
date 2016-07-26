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
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.semo.R;

import java.util.List;

/**
 * Attached by juliangoacher on 14/05/16.
 */
public class FormFieldView extends /*LinearLayout*/ FrameLayout {

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
    /** The cell's layout container. */
    protected View cellLayout;
    /** The main panel of the field layout. A left-aligned, vertically centered panel. */
    private LinearLayout mainPanel;
    /** The right side panel of the field layout. */
    private LinearLayout accessoryPanel;

    public FormFieldView(Context context) {
        super( context );

        LayoutParams layoutParams = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        layoutParams.setMargins( 20, 20, 20, 20 );
        setLayoutParams( layoutParams );

        LayoutInflater inflater = LayoutInflater.from( context );
        this.cellLayout = inflater.inflate( R.layout.formfield_cell_layout, this, false );
        this.addView( cellLayout );

        LinearLayout mainPanelParent = (LinearLayout)cellLayout.findViewById( R.id.main_panel );
        mainPanelParent.setLayoutParams( new LayoutParams( LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
        mainPanelParent.setGravity( Gravity.START|Gravity.CENTER_VERTICAL );
        // this layout allows to wrap the content ante center vertically in the parent.
        // Required when an image larger that the cell height is placed in the right cell, this layout makes the text center vertically
        this.mainPanel = new LinearLayout( context );
        mainPanel.setLayoutParams( new LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
        mainPanel.setBackgroundColor( Color.TRANSPARENT );
        mainPanelParent.addView( mainPanel );

        LinearLayout accessoryPanelParent = (LinearLayout)cellLayout.findViewById( R.id.accessory_panel );
        accessoryPanelParent.setGravity( Gravity.END|Gravity.CENTER_VERTICAL );
        // the layout to center vertically the image. A layout is required to call the setGravity method.
        this.accessoryPanel = new LinearLayout( context );
        accessoryPanel.setLayoutParams( new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
        accessoryPanel.setGravity( Gravity.END|Gravity.CENTER_VERTICAL );
        accessoryPanelParent.addView( accessoryPanel );

        this.titleLabel = new TextView( context );
        titleLabel.setLayoutParams( new LinearLayout.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
        setMainView( titleLabel );
    }

    public void setMainView(View view) {
        mainPanel.removeAllViews();
        if( view != null ) {
            mainPanel.addView( view );
        }
    }

    public void setAccessoryView(View view) {
        accessoryPanel.removeAllViews();
        if( view != null ) {
            accessoryPanel.addView( view );
        }
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

    public void showDisclosureIndicator() {
        showAccessoryImage( R.drawable.right_arrow, 50, 50 );
    }

    public void showAccessoryImage(int imageID, int width, int height) {
        Drawable image = ContextCompat.getDrawable( getContext(), imageID );
        showAccessoryImage( image, 50, 50 );
    }

    public void showAccessoryImage(Drawable image, int width, int height) {
        ImageView imageView = new ImageView( getContext() );
        imageView.setScaleType( ImageView.ScaleType.CENTER_CROP );
        imageView.setImageDrawable( image );
        // Set the image size
        imageView.setLayoutParams( new LayoutParams( width, height ) );
        mainPanel.setPadding( 10, 10, 10, 10 );
        mainPanel.setGravity( Gravity.END );

        setAccessoryView( imageView );
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
