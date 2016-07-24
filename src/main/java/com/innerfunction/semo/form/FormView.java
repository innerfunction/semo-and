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
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.innerfunction.http.Client;
import com.innerfunction.http.Response;
import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.q.Q;
import com.innerfunction.semo.commands.CommandScheduler;
import com.innerfunction.util.Null;
import com.innerfunction.util.StringTemplate;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attached by juliangoacher on 14/05/16.
 */
public class FormView extends ScrollView {

    static final String Tag = FormView.class.getSimpleName();

    /**
     * An interface implemented by form fields capable of acting as form loading indicators.
     */
    public interface LoadingIndicator {
        /** Indicate the form's loading status. */
        void showFormLoading(boolean loading);
    }

    /**
     * A delegate interface for handling certain form lifecycle events.
     */
    public static class Delegate {
        /** Called after the form has submitted successfully. */
        public void onSubmitOk(FormView form, Map<String,Object> data) {}
        /** Called after the form has submitted but the server returns an error. */
        public void onSubmitError(FormView form, Map<String,Object> data) {}
        /**
         * Called when an exception occurs during a form submit.
         * The default implementation logs the error and calls the form's showErrorNotification()
         * method.
         */
        void onSubmitRequestException(FormView form, Exception error) {
            Log.e(Tag,String.format("%s %s", form.method, form.submitURL ), error );
            form.showErrorNotification("Form submit error");
        }
    }

    /** The android app context. */
    private Context androidContext;
    /** The form's fields layout. */
    private LinearLayout fieldLayout;
    /** The index of the currently focused field. */
    private int focusedFieldIdx;
    /** A map of default input values. */
    private Map<String,Object> defaultValues;
    /** A map of current input values. */
    private Map<String,Object> inputValues;
    /** The list of form fields. */
    private List<FormFieldView> fields;
    /** The form submit method, e.g. GET or POST. */
    private String method;
    /** The URL to submit the form to. */
    private String submitURL;
    /** An internal URI to post when submitting the form. */
    private String submitURI;
    /** Flag specifying whether the form is enabled or not. */
    private boolean isEnabled = true;
    /** A form loading indicator. */
    private LoadingIndicator loadingIndicator;
    /** An HTTP client. Used when submitting the form values to a server. */
    private Client httpClient;
    /** A form lifecycle delegate. */
    private Delegate delegate = new Delegate();

    public FormView(Context context) {
        super( context );
        this.androidContext = context;
        this.fieldLayout = new LinearLayout( context );

        // Set margins and add field layout to scroll view.
        LayoutParams params = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );
        params.setMargins( 10, 0, 10, 10 );
        fieldLayout.setLayoutParams( params );
        fieldLayout.setOrientation( LinearLayout.VERTICAL );
        fieldLayout.setBackgroundColor( Color.TRANSPARENT );

        setLayoutParams( new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
        addView( fieldLayout );
    }

    /** Set the form's HTTP client. */
    public void setHTTPClient(Client httpClient) {
        this.httpClient = httpClient;
    }

    /** Set the form lifecycle delegate. */
    public void setDelegate(Delegate delegate) {
        this.delegate = (delegate == null) ? new Delegate() : delegate;
    }

    /** Get the current value of a named field. */
    public Object getFieldValue(String fieldName) {
        // TODO Following is same as iOS code, but surely better to use the inputValues collection?
        for( FormFieldView field : fields ) {
            String name = field.getName();
            if( name != null && name.equals( fieldName ) ) {
                return field.getValue();
            }
        }
        return null;
    }

    /** Get the currently focused field. */
    public FormFieldView getFocusedField() {
        return fields.get( focusedFieldIdx );
    }

    /** Clear the current field focus. */
    public void clearFocusedField() {
        FormFieldView field = getFocusedField();
        field.releaseFieldFocus();
        field.setSelectedStatus( false );
    }

    /** Move field focus to the next focusable field. */
    public void moveFocusToNextField() {
        clearFocusedField();
        FormFieldView field;
        for( int idx = focusedFieldIdx + 1; idx < fields.size(); idx++ ) {
            field = fields.get( idx );
            if( field.takeFieldFocus() ) {
                scrollToFieldAtIndex( idx );
                field.setSelectedStatus( true );
                focusedFieldIdx = idx;
                break;
            }
        }
    }

    /** Reset all fields to the original values. */
    public void reset() {
        for( FormFieldView field : fields ) {
            String name = field.getName();
            if( name != null ) {
                Object value = defaultValues.get( name );
                field.setValue( value == Null.Placeholder ? null : value );
            }
        }
    }

    /**
     * Validate all field values.
     * If any field is invalid then moves field focus to the first invalid field, and then returns false.
     */
    public boolean validate() {
        boolean ok = true;
        for( int idx = 0; idx < fields.size(); idx++ ) {
            FormFieldView field = fields.get( idx );
            if( !field.validate() ) {
                scrollToFieldAtIndex( idx );
                ok = false;
                break;
            }
        }
        return ok;
    }

    /**
     * Submit the form.
     * Validates the form before submitting. Returns true if the form is valid and was submitted.
     */
    public boolean submit() {
        boolean ok = validate();
        if( ok ) {
            if( submitURL != null && httpClient != null ) {
                showSubmittingAppearance( true );
                try {
                    httpClient.submit( method, submitURL, getInputValues() )
                        .then( new Q.Promise.Callback<Response, Response>() {
                            @Override
                            public Response result(Response response) {
                                Map<String, Object> data = (Map<String,Object>)response.parseBodyData();
                                if( isSubmitErrorResponse( response ) ) {
                                    delegate.onSubmitError( FormView.this, data );
                                }
                                else {
                                    delegate.onSubmitOk( FormView.this, data );
                                }
                                showSubmittingAppearance( false );
                                return response;
                            }
                        })
                        .error( new Q.Promise.ErrorCallback() {
                            public void error(Exception e) {
                                delegate.onSubmitRequestException( FormView.this, e );
                            }
                        });
                }
                catch(MalformedURLException e) {
                    Log.e(Tag, String.format("Bad submitURL %s", submitURL ));
                    showSubmittingAppearance( false );
                    ok = false;
                }
            }
            else if( submitURI != null ) {
                showSubmittingAppearance( true );
                // The submit URI is an internal URI which the form will post as a message.
                // The URI property is treated as a template into which the form's values can be
                // inserted.
                String message = StringTemplate.render( submitURI, getInputValues(), true );
                AppContainer.getAppContainer().postMessage( message, this );
                showSubmittingAppearance( false );
            }
        }
        return ok;
    }

    /**
     * Update the form's visible state to show that it is submitting.
     */
    public void showSubmittingAppearance(boolean submitting) {
        if( loadingIndicator != null ) {
            loadingIndicator.showFormLoading( submitting );
        }
        this.isEnabled = !submitting;
    }

    /** Test if a form submit response is an error response. */
    public boolean isSubmitErrorResponse(Response response) {
        return response.getStatusCode() >= 400;
    }

    /**
     * Display a notification of a form error.
     */
    public void showErrorNotification(String message) {
        message = String.format("post:toast+message=%s", Uri.encode( message) );
        AppContainer.getAppContainer().postMessage( message, this );
    }

    /**
     * Return a list of the fields on this form within the same name group.
     */
    public List<FormFieldView> getFieldsInNameGroup(String name) {
        List<FormFieldView> group = new ArrayList<>();
        if( name != null ) {
            for( FormFieldView field : fields ) {
                if( name.equals( field.getName() ) ) {
                    group.add( field );
                }
            }
        }
        return group;
    }

    public void hideKeyboard() {
        // TODO: Need to review methods for resolving the current activity -
        // TODO: Would be better if the view had the reference directly.
        Activity activity = AppContainer.getAppContainer().getCurrentActivity();
        View view = activity.getCurrentFocus();
        if( view != null ) {
            InputMethodManager imm = (InputMethodManager)activity.getSystemService( Context.INPUT_METHOD_SERVICE );
            imm.hideSoftInputFromWindow( view.getWindowToken(), 0 );
        }
    }

    public void scrollToFieldAtIndex(int idx) {
        if( idx < fields.size() ) {
            final FormFieldView field = fields.get( idx );
            post(new Runnable() {
                @Override
                public void run() {
                    smoothScrollTo( 0, field.getBottom() );
                }
            });
        }
    }

    // Properties

    public void setFields(List<FormFieldView> fields) {
        fieldLayout.removeAllViews();
        this.fields = fields;
        this.defaultValues = new HashMap<>();
        for( FormFieldView field : fields ) {
            field.setForm( this );
            String name = field.getName();
            Object value = field.getValue();
            if( name != null && value != null ) {
                defaultValues.put( name, value );
            }
            if( field instanceof LoadingIndicator ) {
                this.loadingIndicator = (LoadingIndicator)field;
            }
            fieldLayout.addView( field );
        }
        // If input values have already been set then set again so that field values are populated.
        if( inputValues != null ) {
            setInputValues( inputValues );
        }
    }

    public void setMethod(String method) {
        this.method = method.toUpperCase();
    }

    public void setSubmitURL(String url) {
        this.submitURL = url;
    }

    public void setSubmitURI(String uri) {
        this.submitURI = uri;
    }

    public void setInputValues(Map<String,Object> values) {
        this.inputValues = values;
        for( FormFieldView field : fields ) {
            String name = field.getName();
            if( name != null ) {
                Object value = inputValues.get( name );
                if( value != null ) {
                    field.setValue( value == Null.Placeholder ? null : value );
                }
            }
        }
    }

    public Map<String,Object> getInputValues() {
        return inputValues;
    }

    public void setIsEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

}
