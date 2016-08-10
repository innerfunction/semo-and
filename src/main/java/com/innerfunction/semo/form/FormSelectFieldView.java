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
import android.util.Log;

import com.innerfunction.pttn.Configuration;
import com.innerfunction.pttn.Container;
import com.innerfunction.pttn.IOCContainerAware;
import com.innerfunction.pttn.app.ViewController;
import com.innerfunction.pttn.ui.table.TableViewController;
import com.innerfunction.util.KeyPath;
import com.nakardo.atableview.foundation.NSIndexPath;
import com.nakardo.atableview.internal.ATableViewCellAccessoryView;
import com.nakardo.atableview.view.ATableViewCell;

import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.innerfunction.util.DataLiterals.*;

/**
 * Created by juliangoacher on 28/07/16.
 */
public class FormSelectFieldView extends FormTextFieldView implements IOCContainerAware {

    static final String Tag = FormSelectFieldView.class.getSimpleName();

    /** The list of selectable items. */
    private List<Map<String, Object>> items = new ArrayList<>();
    /** The item used to represent no-selection. */
    private Map<String, Object> nullItem;
    /** The selected item. One of the entries in the 'items' list. */
    private Map<String, Object> selectedItem;
    /** A view used to display the select list. */
    private TableViewController itemsListView;
    /** The container - needed by the items list view. */
    private Container container;
    /** This view's configuration - also used to initialize the items list view. */
    private Configuration configuration;

    public FormSelectFieldView(Context context) {
        super( context );
        showDisclosureIndicator();
        setIsEditable( false );
        setIsSelectable( true );
    }

    public void setItems(JSONArray itemDefs) {
        // Ensure that each select item has a title and value. If either property is missing
        // then use the other to provide a value. Promote strings to title + values. If title
        // or value can't be resolved then don't add the item to the list.
        items.clear();
        for( Object itemDef : itemDefs ) {
            if( itemDef instanceof String ) {
                items.add( m( kv( "title", itemDef ), kv( "value", itemDef ) ) );
            }
            else if( itemDef instanceof Map ) {
                Map<String, Object> item = (Map<String, Object>)itemDef;
                Object title = item.get( "title" );
                Object value = item.get( "value" );
                if( !(title == null && value == null) ) {
                    if( title == null ) {
                        item.put( "title", value );
                    }
                    else if( value == null ) {
                        item.put( "value", title );
                    }
                    items.add( item );
                }
            }
        }
        // Reset the null item to add it to the start of the new items array.
        setNullItem( nullItem );
        // Reset the value to ensure the selected item is set.
        super.setValue( getValue() );
    }

    public void setNullItem(Object nullItem) {
        if( nullItem instanceof String ) {
            this.nullItem = m( kv("title", nullItem ) );
        }
        else if( nullItem instanceof Map ) {
            this.nullItem = (Map<String, Object>)nullItem;
        }
        if( this.nullItem != null ) {
            // Prepend the null item to the start of the items array.
            items.add( 0, this.nullItem );
        }
    }

    public void setSelectedItem(Map<String, Object> item) {
        this.selectedItem = item;
        super.setValue( item.get("value") );
    }

    @Override
    public void setValue(Object value) {
        super.setValue( value );
        selectedItem = null;
        if( value != null ) {
            for( Map<String, Object> item : items ) {
                if( value.equals( item.get("value") ) ) {
                    selectedItem = item;
                    break;
                }
            }
        }
        else selectedItem = nullItem;
    }

    @Override
    public String formatValue(String value) {
        return selectedItem == null ? "" : (String)selectedItem.get("title");
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean takeFieldFocus() {
        // Try to find index of selected list item.
        int index = -1;
        Object value = getValue();
        if( value == null && nullItem != null ) {
            index = 0;
        }
        else for( int i = 0; i < items.size(); i++ ) {
            Map<String, Object> item = items.get( i );
            if( value.equals( item.get("value") ) ) {
                index = i;
                break;
            }
        }

        // Create the select list.
        final int selectedIndex = index;
        itemsListView = new TableViewController( getContext() ) {
            @Override
            public void didSelectRowAtIndexPath(com.nakardo.atableview.view.ATableView tableView, NSIndexPath indexPath) {
                FormSelectFieldView field = FormSelectFieldView.this;
                field.setSelectedItem( field.items.get( indexPath.getRow() ) );
                field.releaseFieldFocus();
            }
            @Override
            public ATableViewCell cellForRowAtIndexPath(com.nakardo.atableview.view.ATableView tableView, NSIndexPath indexPath) {
                ATableViewCell cell = super.cellForRowAtIndexPath( tableView, indexPath );
                if( indexPath.getRow() == selectedIndex ) {
                    cell.setAccessoryType( ATableViewCellAccessoryView.ATableViewCellAccessoryType.Checkmark );
                }
                return cell;
            }
        };
        itemsListView.setIOCContainer( container );
        itemsListView.setContent( items );
        itemsListView.afterIOCConfigure( configuration );
        if( selectedIndex > -1 ) {
            itemsListView.setSelectedIndex( selectedIndex );
        }

        // Present the select list in a modal.
        ViewController viewController = getForm().getViewController();
        if( viewController != null ) {
            viewController.showModalView( itemsListView );
        }
        else {
            Log.w( Tag, "No view controller available, can't show modal view");
        }
        return true;
    }

    @Override
    public void releaseFieldFocus() {
        if( itemsListView != null ) {
            getForm().getViewController().dismissModalView();
            itemsListView = null;
        }
    }


    @Override
    public void setIOCContainer(Container container) {
        this.container = container;
    }

    @Override
    public void beforeIOCConfigure(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void afterIOCConfigure(Configuration configuration) {
        selectedItem = nullItem;
        // Check for default/initial value, set the field title accordingly.
        Object value = getValue();
        if( value == null ) {
            for( Map<String, Object> item : items ) {
                if( KeyPath.getValueAsBoolean("defaultValue", item ) ) {
                    setSelectedItem( item );
                    break;
                }
            }
        }
        else for( Map<String, Object> item : items ) {
            if( value.equals( item.get("value") ) ) {
                setSelectedItem( item );
                break;
            }
        }
    }

}
