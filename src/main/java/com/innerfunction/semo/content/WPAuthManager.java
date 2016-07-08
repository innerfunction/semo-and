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

import android.text.TextUtils;

import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.q.Q;
import com.innerfunction.util.KeyPath;
import com.innerfunction.util.Null;
import com.innerfunction.util.Paths;

import static com.innerfunction.util.DataLiterals.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for managing user authentication via Wordpress login and registration.
 * Created by juliangoacher on 08/07/16.
 */
public class WPAuthManager implements HTTPClient.AuthenticationDelegate {

    private WPContentContainer container;
    private String wpRealm;
    private String feedURL;
    private List<String> profileFieldNames;

    public WPAuthManager(WPContentContainer container) {
        this.container = container;
        this.wpRealm = container.getWPRealm();
        this.feedURL = container.getFeedURL();
        this.profileFieldNames = Arrays.asList("ID", "first_name", "last_name", "user_email");
    }

    public String getLoginURL() {
        return Paths.join(feedURL, "account/login");
    }

    public String getRegistrationURL() {
        return Paths.join(feedURL, "account/create");
    }

    public String getCreateAccountURL() {
        return getRegistrationURL();
    }

    public String getProfileURL() {
        return Paths.join(feedURL, "account/profile");
    }

    public void setProfileFieldNames(List<String> names) {
        this.profileFieldNames = names;
    }

    public String getWPRealmKey(String key) {
        return String.format("%s/%s", wpRealm, key);
    }

    public boolean isLoggedIn() {
        return userDefaults.getBoolean(getWPRealmKey("logged-in"));
    }

    public void storeUserCredentials(Map<String, Object> values) {
        String username = KeyPath.getValueAsString("user_login", values);
        String password = KeyPath.getValueAsString("user_pass", values);
        // NOTE this will work for all forms - login, create account + update profile. In the latter
        // case, if the password is not updated then password will be empty and the keystore won't
        // be updated.
        if( username != null && username.length() > 0 && password != null && password.length() > 0 ) {
            userDefaults.set( m(
                kv( getWPRealmKey("user_login"), username ),
                kv( getWPRealmKey("user_pass"), password ),
                kv( getWPRealmKey("logged-in"), true )
            ));
        }
    }

    public void storeUserProfile(Map<String, Object> values) {
        // Store standard profile values.
        Map<String, Object> profileValues = new HashMap<>();
        for( String key : profileFieldNames ) {
            Object value = KeyPath.getValueAsString(key, values);
            String profileKey = getWPRealmKey(key);
            if( value != null ) {
                profileValues.put(profileKey, value);
            }
            else {
                profileValues.put(profileKey, Null.Placeholder);
            }
        }
        // Search for and store any meta data values.
        List<String> metaKeys = new ArrayList<>();
        for( String key : values.keySet() ) {
            if( key.startsWith("meta_") ) {
                Object value = values.get(key);
                String profileKey = getWPRealmKey(key);
                profileValues.put(profileKey, value);
                metaKeys.add(key);
            }
        }
        // Store list of meta-data keys.
        String metaDataKeys = TextUtils.join(",", metaKeys);
        String profileKey = getWPRealmKey("metaDataKeys");
        // Store values.
        userDefaults.set(profileValues);
    }

    public Map<String, Object> getUserProfile() {
        Map<String, Object> values = new HashMap();
        String profileKey = getWPRealmKey("user_login");
        values.put("user_login", userDefaults.getString(profileKey));
        // Read standard profile fields.
        for( String name : profileFieldNames ) {
            profileKey = getWPRealmKey(name);
            Object value = userDefaults.get(profileKey);
            if( value != null ) {
                values.put(name, value);
            }
        }
        // Read profile meta-data.
        profileKey = getWPRealmKey("metaDataKeys");
        String[] metaDataKeys = TextUtils.split( userDefaults.getString(profileKey), ",");
        for( String metaDataKey : metaDataKeys ) {
            profileKey = getWPRealmKey(metaDataKey);
            Object value = userDefaults.get(profileKey);
            if( value != null ) {
                values.put(metaDataKey, value);
            }
        }
        // Return result.
        return values;
    }

    public String getUsername() {
        return userDefaults.getString(getWPRealmKey("user_login"));
    }

    public void logout() {
        userDefaults.set(getWPRealmKey("logged-in"), false);
    }

    public void showPasswordReminder() {
        // Fetch the password reminder URL from the server.
        String url = Paths.join(feedURL, "account/password-reminder");
        container.getHTTPClient().get(url)
            .then(xxx);
        /*
        .then((id)^(IFHTTPClientResponse *response) {
            id data = [response parseData];
            NSString *reminderURL = data[@"lost_password_url"];
            if (reminderURL) {
                // Open the URL in the device browser.
                [[UIApplication sharedApplication] openURL:[NSURL URLWithString:reminderURL]];
            }
            return nil;
        });
        */
    }

    private void handleReauthenticationFailure() {
        String message = "Reauthentication%20failure";
        String toastAction = String.format("post:toast+message=%s", message );
        AppContainer.getAppContainer().postMessage( toastAction, container );
        container.showLoginForm();
    }

    public boolean isAuthenticationErrorResponse(HTTPClient client, HTTPClient.Response response) {
        String requestURL = response.getRequestURL();
        // Note that authentication failures returned by login don't count as authentication errors
        // here.
        return response.getStatusCode() == 401 && !requestURL.equals( getLoginURL() );
    }

    public Q.Promise<HTTPClient.Response> reauthenticateUsingHTTPClient(HTTPClient client) {
        final Q.Promise<HTTPClient.Response> promise = new Q.Promise<HTTPClient.Response>();
        // Read username and password from local storage and keychain.
        String username = userDefaults.getString( getWPRealmKey("user_login") );
        String password = userDefaults.getString( getWPRealmKey("user_pass") );
        if( username != null && password != null ) {
            // Submit a new login request.
            Map<String,Object> data = m(
                kv("user_login", username ),
                kv("user_pass",  password )
            );
            client.post( getLoginURL(), data )
            .then((id)^(IFHTTPClientResponse *response) {
                if (response.httpResponse.statusCode == 201) {
                    [promise resolve:response];
                }
                else {
                    [self handleReauthenticationFailure];
                    [promise reject:response];
                }
                return nil;
            })
            .fail(^(id error) {
                [self handleReauthenticationFailure];
                [promise reject:error];
            });
        }
        else {
            handleReauthenticationFailure();
            promise.reject("Reauthentication failure: Username and password not available");
        }
        return promise;
    }

}
