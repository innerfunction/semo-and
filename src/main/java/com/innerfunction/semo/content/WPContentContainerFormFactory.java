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

import android.net.Uri;

import com.innerfunction.pttn.Configuration;
import com.innerfunction.pttn.Container;
import com.innerfunction.pttn.IOCObjectFactoryBase;
import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.pttn.app.ViewController;
import com.innerfunction.pttn.app.ViewControllerBehaviour;
import com.innerfunction.semo.form.FormView;
import com.innerfunction.semo.form.FormViewController;
import com.innerfunction.util.KeyPath;
import com.innerfunction.util.Maps;
import com.innerfunction.util.Null;
import com.innerfunction.util.UserDefaults;

import static com.innerfunction.util.DataLiterals.*;

import java.util.Map;

/**
 * Created by juliangoacher on 11/07/16.
 */
public class WPContentContainerFormFactory extends IOCObjectFactoryBase<FormViewController> {

    private WPContentContainer contentContainer;
    private UserDefaults userDefaults;

    private static final Map<String, Object> BaseConfiguration = m(
        kv( "*and-class", "com.innerfunction.semo.form.FormViewController" ),
        kv( "form", m(
            kv( "method", "POST" ),
            kv( "submitURL", "$SubmitURL" ),
            kv( "isEnabled", "$IsEnabled" ),
            kv( "fields", "$Fields" )
        ) )
    );
    private static final Map<String, Object> StdParams = m(
        kv( "ImageField", m(
            kv( "*ios-class", "IFFormImageField" )
        ) ),
        kv( "FirstnameField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "first_name" ),
            kv( "title", "First name" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) ),
            kv( "input", m(
                kv( "autocapitalization", "words" ),
                kv( "autocorrection", false ),
                kv( "style", "$InputStyle" )
            ) )
        ) ),
        kv( "LastnameField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "last_name" ),
            kv( "title", "Last name" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) ),
            kv( "input", m(
                kv( "autocapitalization", "words" ),
                kv( "autocorrection", false ),
                kv( "style", "$InputStyle" )
            ) )
        ) ),
        kv( "EmailField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "user_email" ),
            kv( "isRequired", true ),
            kv( "title", "Email" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) ),
            kv( "input", m(
                kv( "keyboard", "email" ),
                kv( "autocapitalization", "none" ),
                kv( "autocorrection", false ),
                kv( "style", "$InputStyle" )
            ) )
        ) ),
        kv( "UsernameField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "user_login" ),
            kv( "isRequired", true ),
            kv( "title", "Username" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) ),
            kv( "input", m(
                kv( "autocapitalization", "none" ),
                kv( "autocorrection", false ),
                kv( "style", "$InputStyle" )
            ) )
        ) ),
        kv( "PasswordField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "user_pass" ),
            kv( "isPassword", true ),
            kv( "isRequired", true ),
            kv( "title", "Password" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) )
        ) ),
        kv( "ConfirmPasswordField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormTextFieldView" ),
            kv( "name", "confirm_pass" ),
            kv( "isPassword", true ),
            kv( "title", "Confirm password" ),
            kv( "hasSameValueAs", "user_pass" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) )
        ) ),
        kv( "ProfileIDField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormHiddenFieldView" ),
            kv( "name", "ID" )
        ) ),
        kv( "SubmitField", m(
            kv( "*and-class", "com.innerfunction.semo.form.FormSubmitFieldView" ),
            kv( "title", "Login" ),
            kv( "titleLabel", m(
                kv( "style", "$TitleStyle" )
            ) )
        ) )
    );

    public WPContentContainerFormFactory(WPContentContainer contentContainer) {
        super( contentContainer.makeConfiguration( BaseConfiguration ) );
        this.contentContainer = contentContainer;
        this.userDefaults = AppContainer.getAppContainer().getUserDefaults();
    }

    @Override
    public FormViewController buildObject(Configuration configuration, Container container, String identifier) {
        String formType = configuration.getValueAsString("formType");
        String submitURL = "";
        final String loginAction = configuration.getValueAsString("loginAction");
        boolean isEnabled = true;
        ViewControllerBehaviour viewBehaviour = null;
        FormView.Delegate formDelegate = null;
        final AppContainer appContainer = AppContainer.getAppContainer();
        final WPAuthManager authManager = contentContainer.getAuthManager();
        if( "login".equals( formType ) ) {
            submitURL = authManager.getLoginURL();
            boolean checkForLogin = configuration.getValueAsBoolean("checkForLogin", true );
            if( checkForLogin ) {
                viewBehaviour = new WPContentLoginBehaviour( contentContainer, loginAction );
            }
            formDelegate = new FormView.Delegate() {
                @Override
                public void onSubmitOk(FormView form, Map<String,Object> data) {
                    Map<String,Object> profile = (Map<String,Object>)data.get("profile");
                    // Store user credentials & user info.
                    authManager.storeUserProfile( profile );
                    authManager.storeUserCredentials( form.getInputValues() );
                    // Dispatch the specified event.
                    appContainer.postMessage( loginAction, form );
                }
                @Override
                public void onSubmitError(FormView form, Map<String,Object> data) {
                    String action = String.format("post:toast+message=%s", Uri.encode("Login failure") );
                    appContainer.postMessage( action, form );
                }
            };
        }
        else if( "new-account".equals( formType ) ) {
            submitURL = authManager.getCreateAccountURL();
            formDelegate = new FormView.Delegate() {
                @Override
                public void onSubmitOk(FormView form, Map<String,Object> data) {
                    Map<String,Object> profile = (Map<String,Object>)data.get("profile");
                    // Store user credentials & user info.
                    authManager.storeUserProfile( profile );
                    authManager.storeUserCredentials( form.getInputValues() );
                    // Dispatch the specified event.
                    appContainer.postMessage( loginAction, form );
                }
                @Override
                public void onSubmitError(FormView form, Map<String,Object> data) {
                    String message = KeyPath.getValueAsString("error", data );
                    if( message == null ) {
                        message = "Account creation failure";
                    }
                    message = Uri.encode( message );
                    String action = String.format("post:toast+message=%s", message );
                    appContainer.postMessage( action, form );
                }
            };
        }
        else if( "profile".equals( formType ) ) {
            submitURL = authManager.getProfileURL();
            formDelegate = new FormView.Delegate() {
                @Override
                public void onSubmitOk(FormView form, Map<String,Object> data) {
                    Map<String,Object> profile = (Map<String,Object>)data.get("profile");
                    // Update stored user info.
                    authManager.storeUserProfile( profile );
                    String action = String.format("post:toast+message=%@", Uri.encode("Account updated") );
                    appContainer.postMessage( action, form );
                }
                @Override
                public void onSubmitError(FormView form, Map<String,Object> data) {
                    String message = KeyPath.getValueAsString("error", data );
                    if( message == null ) {
                        message = "Account update failure";
                    }
                    message = Uri.encode( message );
                    String action = String.format("post:toast+message=%s", message );
                    appContainer.postMessage( action, form );
                }
            };
        }
        // Prepare configuration parameters.
        Map<String,Object> params = Maps.extend( StdParams );
        params.put("SubmitURL",     submitURL );
        params.put("IsEnabled",     isEnabled );
        params.put("Fields",        Null.valueOrPlaceholder( configuration.getRawValue("fields") ) );
        params.put("TitleStyle",    Null.valueOrPlaceholder( configuration.getRawValue("titleStyle") ) );
        params.put("InputStyle",    Null.valueOrPlaceholder( configuration.getRawValue("inputStyle") ) );

        // Create the form configuration.
        configuration = configuration.configurationWithKeysExcluded("*factory", "formType", "fields");

        // Build the form.
        FormViewController formView = buildObject( configuration, container, params, identifier );
        formView.setBehaviour( viewBehaviour );

        // Additional form configuration.
        FormView form = formView.getForm();
        form.setDelegate( formDelegate );
        form.setHTTPClient( contentContainer.getHTTPClient() );
        if( "profile".equals( formType ) ) {
            form.setInputValues( authManager.getUserProfile() );
        }

        // Return the form.
        return formView;
    }

}