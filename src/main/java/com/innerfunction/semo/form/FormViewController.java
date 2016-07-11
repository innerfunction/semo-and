package com.innerfunction.semo.form;

import android.content.Context;

import com.innerfunction.pttn.app.ViewController;

/**
 * Created by juliangoacher on 12/07/16.
 */
public class FormViewController extends ViewController {

    private FormView form;

    public FormViewController(Context context) {
        super( context );
        setLayout("view_activity_layout");
    }

    public FormView getForm() {
        return form;
    }
}
