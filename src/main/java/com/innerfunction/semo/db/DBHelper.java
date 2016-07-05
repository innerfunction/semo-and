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
package com.innerfunction.semo.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


/**
 * Created by juliangoacher on 09/05/16.
 */
public class DBHelper extends SQLiteOpenHelper {

    static final String Tag = DBHelper.class.getSimpleName();

    private DB dbWrapper;
    private Map<String,List<?>> initialData = new HashMap<>();

    public DBHelper(Context context, DB db) {
        super( context, db.getName(), null, db.getVersion() );
        this.dbWrapper = db;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Map<String,Table> tables = dbWrapper.getTables();
        for( String tableName : tables.keySet() ) {
            Table table = tables.get( tableName );
            db.execSQL( getCreateTableSQL( tableName, table ) );
            addInitialDataForTable( tableName, table );
        }
        initialize( db );
    }

    /**
     * Upgrade an existing database.
     * The upgrade process uses 'since' and 'until' properties on table and column configurations to
     * decide whether to create, modify or delete columns or tables (although note that SQL lite
     * only allows columns to be added).
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Map<String,Table> tables = dbWrapper.getTables();
        for( String tableName : tables.keySet() ) {
            Table table = tables.get( tableName );
            int since = Math.max( table.since, 0 );
            int until = table.until == -1 ? newVersion : table.until;
            String[] sql = new String[0];
            if( since <= oldVersion ) {
                // Table exists since before the current DB version, so should exist in the current DB.
                if( until < newVersion ) {
                    // Table not required in DB version being migrated to, so drop from database.
                    sql = new String[]{ String.format("DROP TABLE %s IF EXISTS", tableName ) };
                }
                else {
                    // Modify table.
                    sql = getAlterTableSQL( tableName, table, oldVersion, newVersion );
                }
            }
            else {
                // => since > oldVersion
                // Table shouldn't exist in the current database.
                if( until < newVersion ) {
                    // Table not required in version being migrated to, so no action required.
                    continue;
                }
                else {
                    // Create table.
                    sql = new String[]{ getCreateTableSQL( tableName, table ) };
                    addInitialDataForTable( tableName, table );
                }
            }
            for( String s : sql ) {
                try {
                    db.execSQL( s );
                }
                catch(Exception e) {
                    Log.e(Tag, String.format("Upgrading table %s", tableName), e );
                }
            }
        }
        initialize( db );
    }

    /**
     * Initialize the database by writing initial data into each table.
     * Table data is only written after table creation.
     * @param db
     */
    @SuppressWarnings("unchecked")
    private void initialize(SQLiteDatabase db) {
        Log.i(Tag,"Initializing database...");
        for( String tableName : initialData.keySet() ) {
            List<Map<String,Object>> data = (List<Map<String,Object>>)initialData.get( tableName );
            for( Map<String,Object> item : data ) {
                dbWrapper.insert( db, tableName, item );
            }
            Cursor c = db.rawQuery(String.format("select count() from %s", tableName ), null);
            if( c.moveToFirst() ) {
                int count = c.getInt( 0 );
                Log.i(Tag,String.format("Initializing %s: Inserted %d rows", tableName, count ));
            }
            else {
                Log.i(Tag,String.format("No rows inserted into %s", tableName ));
            }
        }
        // Delete all initial data after setup.
        initialData = null;
    }

    /**
     * Add initial data for a table to the set of data to be initialized.
     * @param name
     * @param table
     */
    private void addInitialDataForTable(String name, Table table) {
        if( table.data != null ) {
            initialData.put( name, table.data );
        }
    }

    /**
     * Return a SQL statement for creating a table schema.
     * @param name  The table name.
     * @param table The table configuration.
     * @return A string containing a SQL create table statement.
     */
    private String getCreateTableSQL(String name, Table table) {
        StringBuilder cols = new StringBuilder();
        for( Column column : table.columns ) {
            if( cols.length() > 0 ) {
                cols.append(',');
            }
            cols.append( column.name ).append(' ').append( column.type );
        }
        String sql = String.format("CREATE TABLE %s (%s)", name, cols );
        Log.d(Tag, sql );
        return sql;
    }

    /**
     * Return an array of SQL statements for modifying a table schema to match a new DB version.
     * @param tableName     The table name.
     * @param table         The table configuration.
     * @param oldVersion    The current DB version.
     * @param newVersion    The DB version being migrated to.
     * @return  An array of SQL statements.
     */
    private String[] getAlterTableSQL(String tableName, Table table, int oldVersion, int newVersion) {
        List<String> sqls = new ArrayList<>();
        for( Column column : table.columns ) {
            int since = Math.max( column.since, 0 );
            int until = column.until > -1 ? column.until : newVersion;
            if( since > oldVersion && !(until < newVersion) ) {
                String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, column.name, column.type );
                sqls.add( sql );
                Log.d(Tag, sql );
            }
            // NOTE: It's not possible to drop columns from a table in SQLite.
        }
        String[] result = new String[sqls.size()];
        return sqls.toArray( result );
    }
}