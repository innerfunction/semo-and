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
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * Attached by juliangoacher on 23/05/16.
 */
public class FormImageFieldView extends FormFieldView {

    private ImageView imageView;

    public FormImageFieldView(Context context) {
        super( context );
        this.imageView = new ImageView( context );
        imageView.setLayoutParams( new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT ) );
        imageView.setScaleType( ImageView.ScaleType.CENTER_INSIDE );
        setMainView( imageView );
        hideAccessoryView();
    }

    public void setImage(Drawable image) {
        imageView.setImageDrawable( image );
    }

    public Drawable getImage() {
        return imageView.getDrawable();
    }
}
