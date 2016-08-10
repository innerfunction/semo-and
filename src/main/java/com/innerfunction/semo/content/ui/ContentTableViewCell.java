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
import android.text.Html;
import android.text.TextUtils;
import android.widget.TextView;

import com.nakardo.atableview.view.ATableViewCell;

/**
 * Table cell implementation for the content table view.
 * Note on cell heights: The iOS implementation dynamically adjusts the height of each cell to
 * match the height of the content text, but due to problems in this class with calculating text
 * height (particularly on the initial pass; and probably related to the underlying ATableView
 * adapater being used) this class just uses a fixed cell and content text height.
 * Created by juliangoacher on 25/07/16.
 */
public class ContentTableViewCell extends ATableViewCell {

    private int contentLineCount = 2;

    public ContentTableViewCell(ATableViewCell.ATableViewCellStyle style, String reuseIdentifier, Context context) {
        super( style, reuseIdentifier, context );
        /*
        getBackgroundView().setBackgroundColor( Color.YELLOW );
        setBackgroundColor( Color.WHITE );
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
            label.setLines( contentLineCount );
            label.setEllipsize( TextUtils.TruncateAt.END );
            label.setHeight( label.getLineHeight() * contentLineCount );
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
        int titleHeight = (int)textLabel.getTextSize();
        getTextLabel().setHeight( titleHeight );
        int contentHeight = 0;
        TextView contentLabel = getDetailTextLabel();
        if( contentLabel != null ) {
            contentHeight = (int)contentLabel.getTextSize() * (contentLineCount - 1);
            contentLabel.setHeight( contentHeight );
        }
        return titleHeight + contentHeight;
    }

}
