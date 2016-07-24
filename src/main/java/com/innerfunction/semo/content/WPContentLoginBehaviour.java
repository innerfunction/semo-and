package com.innerfunction.semo.content;

import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.pttn.app.ViewController;
import com.innerfunction.pttn.app.ViewControllerBehaviour;

/**
 * A view behaviour which checks whether a user is currently logged into the app.
 * If a user is logged in then posts an action message. Useful for skipping the login form at
 * startup.
 *
 * Attached by juliangoacher on 12/07/16.
 */
public class WPContentLoginBehaviour implements ViewControllerBehaviour {

    private WPContentContainer contentContainer;
    private String loginAction;

    public WPContentLoginBehaviour(WPContentContainer container, String loginAction) {
        this.contentContainer = container;
        this.loginAction = loginAction;
    }

    @Override
    public void onResume(ViewController view) {
        // Check if user already logged in, if so then dispatch a specified event.
        if( contentContainer.getAuthManager().isLoggedIn() ) {
            AppContainer.getAppContainer().postMessage( loginAction, view );
        }
    }
}
