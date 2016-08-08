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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Html;
import android.widget.TextView;

import com.nakardo.atableview.view.ATableViewCell;

/**
 * Created by juliangoacher on 25/07/16.
 */
public class ContentTableViewCell extends ATableViewCell {

    public ContentTableViewCell(ATableViewCell.ATableViewCellStyle style, String reuseIdentifier, Context context) {
        super( style, reuseIdentifier, context );
        /*
        getTextLabel().setBackgroundColor( Color.CYAN );
        TextView detail = getDetailTextLabel();
        if( detail != null ) {
            detail.setBackgroundColor( Color.YELLOW );
        }
        */
    }

    public void setTitle(String title) {
        TextView label = getTextLabel();
        label.setText( title );
    }

    public String getTitle() {
        return getTextLabel().getText().toString();
    }

    public void setContent(String content) {
        TextView label = getDetailTextLabel();
        if( label != null ) {
            String text = Html.fromHtml( content ).toString().trim();
            label.setText( text );
            // This calculation is repeated in the getCellHeight() method, but it is necessary to
            // do it here to ensure that multi-line display.
            int height = label.getLineHeight() * label.getLineCount();
            label.setHeight( height );
            // TODO There is a problem here in that the first page's worth of cells always calculate
            // TODO a height of 0 at this point; this probably happens because the cell hasn't been
            // TODO attached to the window yet.
        }
    }

    public String getContent() {
        TextView label = getDetailTextLabel();
        if( label != null ) {
            return label.getText().toString();
        }
        return "";
    }

    public int getCellHeight() {
        TextView textLabel = getTextLabel();
        textLabel.measure( 0, 0 );
        /*
        final int titleHeight = textLabel.getLineHeight();
        final int contentHeight = getContentHeight();
        post( new Runnable() {
            @Override
            public void run() {
                getTextLabel().setHeight( titleHeight );
                getTextLabel().setText("CH:"+contentHeight);
                if( contentHeight > 0 ) {
                    getDetailTextLabel().setHeight( contentHeight );
                }
            }
        });
        */
        int titleHeight = textLabel.getLineHeight();
        int contentHeight = getContentHeight();
        getTextLabel().setHeight( titleHeight );
        if( contentHeight > 0 ) { // This check in case of null detail label
            getDetailTextLabel().setHeight( contentHeight );
        }
        return titleHeight + contentHeight;
    }

    public int getContentHeight() {
        int height = 0;
        TextView contentLabel = getDetailTextLabel();
        if( contentLabel != null ) {
            contentLabel.measure( 0, 0 );
            height = contentLabel.getLineHeight() * contentLabel.getLineCount();
            contentLabel.setHeight( height );
        }
        return height;
    }

}
