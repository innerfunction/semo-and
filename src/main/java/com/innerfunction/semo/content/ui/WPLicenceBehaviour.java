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
package com.innerfunction.semo.content.ui;

import com.innerfunction.pttn.Message;
import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.pttn.app.ViewController;
import com.innerfunction.pttn.app.ViewControllerBehaviour;
import com.innerfunction.semo.content.WPAuthManager;
import com.innerfunction.util.UserDefaults;

/**
 * A view behaviour providing functionality for presenting a licence to a user, and for
 * prompting the user to accept or reject the licence.
 * When this behaviour is attached to a view (controller), it first checks when the view
 * appears whether a flag is set which indicates that the licence has been accepted by the
 * current user.
 *
 * If the flag isn't set then the behaviour presents a modal view which should show the
 * licence text to the user and give them the option of accepting or rejecting the policy.
 * The behaviour recognizes two action messages:
 *
 * 1. licence/accept: Set the flag indicating licence acceptance, and hide the licence view.
 * 2. licence/reject: Hide the licence view and post the reject action (e.g. to log out again).
 *
 * Created by juliangoacher on 29/07/16.
 */
public class WPLicenceBehaviour implements ViewControllerBehaviour {

    /**
     * The name under which the licence accepted flag is stored in local settings.
     * Defaults to 'licenceAccepted'.
     */
    private String localsKey = "licenceAccepted";
    /**
     * The view used to display the licence text. The view should include controls (e.g. buttons
     * which the user can use to accept or reject the licence.
     */
    private ViewController licenceView;
    /* The action message to be posted if the user rejects the licence. */
    private String rejectAction;
    /**
     * A reference to the Wp auth manager. This is needed to get the username of the current logged in user,
     * so that the behaviour can detect if the current user has accepted the licence.
     * If not set then the behaviour uses just a default string value.
     */
    private WPAuthManager authManager;
    /** Local preferences. */
    private UserDefaults userDefaults = AppContainer.getAppContainer().getUserDefaults();
    /** The view controller this behaviour is attached to. */
    private ViewController owner;

    public void setLocalsKey(String localsKey) {
        this.localsKey = localsKey;
    }

    public void setLicenceView(ViewController view) {
        this.licenceView = view;
        view.addBehaviour( new ViewControllerBehaviour() {
            @Override
            public void onResume(ViewController view) {}
            @Override
            public boolean receiveMessage(Message message, Object sender) {
                // Forward messages on to licence view to this behaviour.
                return WPLicenceBehaviour.this.receiveMessage( message, sender );
            }
        } );
    }

    public void setRejectAction(String action) {
        this.rejectAction = action;
    }

    public void setAuthManager(WPAuthManager authManager) {
        this.authManager = authManager;
    }

    private String getUsername() {
        String username = authManager.getUsername();
        if( username == null ) {
            username = "<user>";
        }
        return username;
    }

    @Override
    public void onResume(ViewController view) {
        this.owner = view;
        String licenceAcceptedBy = userDefaults.getString( localsKey );
        boolean licenceAccepted = getUsername().equals( licenceAcceptedBy );
        if( !licenceAccepted ) {
            owner.showModalView( licenceView );
        }
    }

    @Override
    public boolean receiveMessage(Message message, Object sender) {
        if( message.hasName("licence/accept") ) {
            String username = getUsername();
            userDefaults.set( localsKey, username );
            owner.dismissModalView();
            return true;
        }
        if( message.hasName("licence/reject") ) {
            authManager.logout();
            owner.dismissModalView();
            AppContainer.getAppContainer().postMessage( rejectAction, owner );
            return true;
        }
        return false;
    }
}
