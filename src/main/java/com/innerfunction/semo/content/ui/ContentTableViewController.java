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

import com.innerfunction.pttn.app.AppContainer;
import com.innerfunction.pttn.ui.table.TableViewCellFactory;
import com.innerfunction.pttn.ui.table.TableViewController;
import com.innerfunction.semo.content.DataFormatter;
import com.innerfunction.util.StringTemplate;
import com.innerfunction.util.ValueMap;
import com.nakardo.atableview.foundation.NSIndexPath;
import com.nakardo.atableview.internal.ATableViewCellAccessoryView;
import com.nakardo.atableview.protocol.ATableViewDataSource;
import com.nakardo.atableview.protocol.ATableViewDelegate;
import com.nakardo.atableview.view.ATableViewCell;

import java.util.Map;

/**
 * Created by juliangoacher on 25/07/16.
 */
public class ContentTableViewController extends TableViewController {

    private DataFormatter dataFormatter;
    private Drawable rowImage;
    private Number rowImageHeight = 50;
    private int rowImageWidth = 50;
    private String action;
    private boolean showRowContent;
    private ContentTableViewCell layoutCell;

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

    protected String getReuseID() {
        return showRowContent ? "content" : "title";
    }

    protected ContentTableViewCell makeTableViewCell(String reuseID) {
        ATableViewCell.ATableViewCellStyle style = reuseID.equals("content")
            ? ATableViewCell.ATableViewCellStyle.Subtitle
            : ATableViewCell.ATableViewCellStyle.Default;
        return new ContentTableViewCell( style, reuseID, getContext() );
    }

    @Override
    protected ATableViewDataSource makeDataSource() {
        return new ATableViewDataSource() {
            @Override
            public int numberOfSectionsInTableView(com.nakardo.atableview.view.ATableView tableView) {
                return tableData.getSectionCount();
            }
            @Override
            public int numberOfRowsInSection(com.nakardo.atableview.view.ATableView tableView, int section) {
                return tableData.getSectionSize( section );
            }
            @Override
            public String titleForHeaderInSection(com.nakardo.atableview.view.ATableView tableView, int section) {
                return tableData.getSectionTitle( section );
            }
            @Override
            public ATableViewCell cellForRowAtIndexPath(com.nakardo.atableview.view.ATableView tableView, NSIndexPath indexPath) {
                String reuseID = getReuseID();
                ATableViewCell cell = dequeueReusableCellWithIdentifier( reuseID );
                if( cell instanceof ContentTableViewCell ) {
                    configureCell( (ContentTableViewCell)cell, indexPath );
                }
                else {
                    ContentTableViewCell ctvCell = makeTableViewCell( reuseID );
                    configureCell( ctvCell, indexPath );
                    cell = ctvCell;
                }
                return cell;
            }
        };
    }

    @Override
    protected ATableViewDelegate makeDelegate() {
        return new ATableViewDelegate() {
            @Override
            public void didSelectRowAtIndexPath(com.nakardo.atableview.view.ATableView tableView, NSIndexPath indexPath) {
                ValueMap rowData = tableData.getRowDataForIndexPath( indexPath );
                String action = rowData.getString("action");
                String _action = ContentTableViewController.this.action;
                if( action == null && _action != null ) {
                    // If no action on cell data, but action defined on table then eval as a
                    // template on the cell data.
                    action = StringTemplate.render( _action, rowData );
                }
                if( action != null ) {
                    AppContainer.getAppContainer().postMessage( action, ContentTableViewController.this );
                    tableData.clearFilter();
                }
            }
            @Override
            public int heightForRowAtIndexPath(com.nakardo.atableview.view.ATableView tableView, NSIndexPath indexPath) {
                if( layoutCell == null ) {
                    layoutCell = makeTableViewCell( getReuseID() );
                    layoutCell.setVisibility( INVISIBLE );
                    addView( layoutCell );
                }
                configureCell( layoutCell, indexPath );
                return layoutCell.getCellHeight();
            }
        };
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
