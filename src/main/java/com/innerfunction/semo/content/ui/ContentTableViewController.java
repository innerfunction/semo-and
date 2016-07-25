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
import android.graphics.drawable.Drawable;

import com.innerfunction.pttn.ui.table.TableViewController;
import com.innerfunction.semo.content.DataFormatter;
import com.innerfunction.util.ValueMap;
import com.nakardo.atableview.foundation.NSIndexPath;
import com.nakardo.atableview.internal.ATableViewCellAccessoryView;

import java.util.Map;

/**
 * Created by juliangoacher on 25/07/16.
 */
public class ContentTableViewController extends TableViewController {

    private DataFormatter dataFormatter;
    private Drawable rowImage;
    private Number rowImageHeight;
    private int rowImageWidth;
    private String action;
    private boolean showRowContent;

    public ContentTableViewController(Context context) {
        super( context );
    }

    @Override
    public void setContent(Object content) {
        if( dataFormatter != null ) {
            content = dataFormatter.formatData( content );
        }
        super.setContent( content );
    }

    private void configureCell(ContentTableViewCell cell, NSIndexPath indexPath) {
        ValueMap data = tableData.getRowDataForIndexPath( indexPath );

        cell.setTitle( data.getString("title") );
        if( showRowContent ) {
            cell.setContent( data.getString("content") );
        }

        int imageHeight = data.getNumber("imageHeight", rowImageHeight ).intValue();
        if( imageHeight == 0 ) {
            imageHeight = 40;
        }
        int defaultImageWidth = rowImageWidth != 0 ? rowImageWidth : imageHeight;
        int imageWidth = data.getNumber("imageWidth", defaultImageWidth ).intValue();
        Drawable image = tableData.loadImage( data.getString("image") ); // TODO Resize the image?
        if( image != null ) {
            cell.getImageView().setImageDrawable( image );
            // TODO Rounded corners
        }
        else {
            cell.getImageView().setImageDrawable( null );
        }

        if( !(this.action == null && data.getString("action") == null) ) {
            cell.setAccessoryType( ATableViewCellAccessoryView.ATableViewCellAccessoryType.DisclosureIndicator );
        }
        else {
            cell.setAccessoryType( ATableViewCellAccessoryView.ATableViewCellAccessoryType.None );
        }
    }

    public void setDataFormatter(DataFormatter dataFormatter) {
        this.dataFormatter = dataFormatter;
    }

    public void setRowImage(Drawable image) {
        this.rowImage = image;
    }

    public void setRowImageHeight(int height) {
        this.rowImageHeight = height;
    }

    public void setRowImageWidth(int width) {
        this.rowImageWidth = width;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setShowRowContent(boolean show) {
        this.showRowContent = show;
    }
}
