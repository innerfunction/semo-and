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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.innerfunction.pttn.IOCContextAware;
import com.innerfunction.pttn.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SQL database wrapper.
 * Provides methods for performing DB operations - queries, inserts, updates & deletes.
 *
 * Created by juliangoacher on 09/05/16.
 */
public class DB implements Service, IOCContextAware {

    static final String Tag = DB.class.getSimpleName();

    /** The android context - needed for the database helper. */
    private Context androidContext;
    /** A helper for managing database initializations and upgrades. */
    private DBHelper helper;
    /** A map of tagged column names, by table. */
    private Map<String,Map<String,String>> taggedTableColumns;
    /** A map of column names, by table. */
    private Map<String,Set<String>> tableColumnNames;
    /** The database name. */
    private String name;
    /** The current database schema version number. */
    private int version;
    /** Flag indicating whether to reset the database at startup. */
    private boolean resetDatabase;
    /** Database table schemas + initial data. */
    private Map<String,Table> tables;

    public DB() {
        this.name = "semo";
        this.version = 0;
        this.tables = new HashMap<>();
        this.resetDatabase = false;
    }

    private DB(DB parent) {
        this.name = parent.name;
        this.version = parent.version;
        this.tables = parent.tables;
    }

    // IOCContextAware
    @Override
    public void setAndroidContext(Context context) {
        this.androidContext = context;
    }

    // Properties

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    // TODO Update the container to convert List<X> => X[] (i.e. array of X)
    // TODO Also note that iOS code accepts a map at this point (should be called schema?)
    public void setTables(Table... tables) {
        for( Table table : tables ) {
            Map<String,String> columnTags = new HashMap<>();
            Set<String> columnNames = new HashSet<>();
            for( Column column : table.columns ) {
                if( column.tag != null ) {
                    columnTags.put( column.tag, column.name );
                }
                columnNames.add( column.name );
            }
            taggedTableColumns.put( table.name, columnTags );
            tableColumnNames.put( table.name, columnNames );
            this.tables.put( table.name, table );
        }
    }

    public Map<String,Table> getTables() {
        return tables;
    }

    /**
     * Begin a DB transaction.
     */
    public boolean beginTransaction() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        return true;
    }

    /**
     * Commit a DB transaction.
     */
    public boolean commitTransaction() {
        boolean ok = false;
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.setTransactionSuccessful();
            ok = true;
        }
        catch(Exception e) {
            Log.e( Tag, "Committing transaction", e );
        }
        finally {
            db.endTransaction();
        }
        return ok;
    }

    /**
     * Rollback a DB transaction.
     */
    public boolean rollbackTransaction() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.endTransaction();
        return true;
    }

    /**
     * Return the name of the column with the named tag on the named table.
     * @param table
     * @param tag
     * @return A column name, or null if the table or tag isn't found.
     */
    public String getColumnForTag(String table, String tag) {
        Map<String, String> columns = taggedTableColumns.get( table );
        return columns != null ? columns.get( tag ) : null;
    }

    /**
     * Read an object from the database.
     * @param table     The name of the table containing the data.
     * @param id        The ID of the object to read.
     * @return A map containing the object's values.
     */
    public Map<String,Object> read(String table, String id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        return read( db, table, id );
    }

    private Map<String,Object> read(SQLiteDatabase db, String table, String id) {
        Map<String,Object> result = null;
        String idColumn = getColumnForTag( table, "id" );
        if( idColumn != null ) {
            result = read( db, table, idColumn, id );
        }
        else {
            Log.w( Tag, String.format("No ID column found for table %s", table ));
        }
        return result;
    }

    private Map<String,Object> read(SQLiteDatabase db, String table, String idColumn, String id) {
        Map<String,Object> result = null;
        try {
            String sql = String.format("SELECT * FROM %s WHERE %s=?", table, idColumn );
            String[] params = new String[]{ id };
            Cursor cursor = db.rawQuery( sql, params );
            if( cursor.moveToFirst() ) {
                result = readRowFromCursor( cursor );
            }
            cursor.close();
        }
        catch(Exception e) {
            Log.e( Tag, "read()", e );
        }
        return result;
    }
    /**
     * Query the DB.
     * @param sql   The SQL to execute.
     * @param args  Arguments to the SQL.
     * @return A list of map objects. Each map contains data from a single row of the query result.
     */
    public List<Map<String,Object>> performQuery(String sql, List<String> args) {
        return performQuery( sql, args.toArray( new String[args.size()] ));
    }

    /**
     * Query the DB.
     * @param sql   The SQL to execute.
     * @param args  Arguments to the SQL.
     * @return A list of map objects. Each map contains data from a single row of the query result.
     */
    public List<Map<String,Object>> performQuery(String sql, String... args) {
        List<Map<String,Object>> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.rawQuery( sql, args );
        int rowCount = cursor.getCount();
        if( cursor.moveToFirst() ) {
            for( int i = 0; i < rowCount; i++ ) {
                result.add( readRowFromCursor( cursor ) );
                cursor.moveToNext();
            }
        }
        cursor.close();
        return result;
    }

    /**
     * Perform an update in the DB.
     * @param sql   The SQL to execute.
     * @param args  Arguments to the SQL.
     * @return true if the statement executed successfully.
     */
    public boolean performUpdate(String sql, String... args) {
        boolean ok = true;
        SQLiteStatement statement = null;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            statement = db.compileStatement( sql );
            statement.bindAllArgsAsStrings( args );
            statement.executeUpdateDelete();
        }
        catch(SQLException e) {
            Log.e( Tag, "Error executing statement", e );
            ok = false;
        }
        finally {
            if( statement != null ) {
                statement.close();
            }
        }
        return ok;
    }

    /** Return the number of records matching the specified where clause in the specified table. */
    public int countInTable(String table, String where, String... args) {
        int count = 0;
        String sql = String.format("SELECT count(*) AS count FROM %s WHERE %s", table, where );
        List<Map<String,Object>> result = performQuery( sql, args );
        if( result.size() > 0 ) {
            Map<String,?> record = result.get( 0 );
            count = (Integer)record.get("count");
        }
        return count;
    }

    /**
     * Read data from a DB cursor.
     * @param cursor
     * @return A map containing all the values in the current cursor row.
     */
    private Map<String,Object> readRowFromCursor(Cursor cursor) {
        Map<String,Object> result = new HashMap<String,Object>();
        int ccount = cursor.getColumnCount();
        for( int i = 0; i < ccount; i++ ) {
            String cname = cursor.getColumnName( i );
            if( !cursor.isNull( i ) ) {
                switch( cursor.getType( i ) ) {
                case Cursor.FIELD_TYPE_NULL:
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    result.put( cname, cursor.getFloat( i ) );
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    result.put( cname, cursor.getInt( i ) );
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    result.put( cname, cursor.getString( i ) );
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    // Not supported.
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Insert a list of values into the database.
     * Notifies any observers of the 'db' model.
     * @param table         The table to insert the data into.
     * @param valuesList    A list of values to insert. Each item in the list is inserted into a single table record.
     *                      All of the values in each list item should correspond to a column of the table.
     * @return true if all values were inserted.
     */
    public boolean insert(String table, List<Map<String,Object>> valuesList) {
        boolean result = true;
        SQLiteDatabase db = helper.getWritableDatabase();
        // TODO willChangeValueForKey:table
        for( Map<String,Object> values : valuesList ) {
            result &= insert( db, table, values );
        }
        // TODO didChangeValueForKey:table
        return result;
    }

    /**
     * Insert an object into the database.
     * Notifies any observers of the 'db' model.
     * @param table     The name of the table to insert the object into.
     * @param values    The object values. Each item in the map should correspond to a column in the table.
     * @return true if the value was inserted.
     */
    public boolean insert(String table, Map<String,Object> values) {
        // TODO willChangeValueForKey:table
        boolean result = insert( helper.getWritableDatabase(), table, values );
        // TODO didChangeValueForKey:table
        return result;
    }

    protected boolean insert(SQLiteDatabase db, String table, Map<String,Object> values) {
        boolean result = true;
        ContentValues cvalues = toContentValues( table, values );
        if( cvalues.size() > 0 ) {
            try {
                db.insertOrThrow( table,  null, cvalues );
            }
            catch(SQLException e) {
                Log.e( Tag, "Inserting row", e );
                result = false;
            }
        }
        return result;
    }

    /**
     * Update an object in the database.
     * Notifies any observers of the 'db' model.
     * @param table     The table used to store the object data.
     * @param values    The values to update the object with. Each item in the map should correspond to a
     *                  column in the table. A value must be supplied for the ID column.
     * @return true if all values were inserted.
     */
    public boolean update(String table, Map<String,Object> values) {
        SQLiteDatabase db = helper.getWritableDatabase();
        // TODO willChangeValueForKey:table
        boolean result = update( db, table, values );
        if( result ) {
            // TODO didChangeValueForKey:table
        }
        else {
            String idColumn = getColumnForTag( table, "id" );
            String id = values.get( idColumn ).toString();
            Log.w( Tag, String.format("Updated failed: %s %s", table, id ));
        }
        return result;
    }

    private boolean update(SQLiteDatabase db, String table, Map<String,Object> values) {
        boolean result = false;
        String idColumn = getColumnForTag( table, "id" );
        if( idColumn != null ) {
            result = update( db, table, idColumn, values );
        }
        else {
            Log.w( Tag, String.format("No ID column found for table %s", table ));
        }
        return result;
    }

    private boolean update(SQLiteDatabase db, String table, String idColumn, Map<String,Object> values) {
        ContentValues cvalues = toContentValues( table, values );
        String id = values.get( idColumn ).toString();
        String whereClause = String.format("%s = ?", idColumn );
        String[] whereArgs = new String[]{ id };
        return db.update( table, cvalues, whereClause, whereArgs ) > 0;
    }

    /**
     * Update or insert an object in the database.
     * Notifies any observers of the 'db' model.
     * @param table     The table used to store the object data.
     * @param values    The values to update the object with. Each item in the map should correspond to a
     *                  column in the table. A value must be supplied for the ID column.
     * @return true if all values were inserted.
     */
    public boolean upsert(String table, Map<String,Object> values) {
        SQLiteDatabase db = helper.getWritableDatabase();
        // TODO willChangeValueForKey:table
        boolean result = upsert( db, table, values );
        if( result ) {
            // TODO didChangeValueForKey:table
        }
        else {
            String idColumn = getColumnForTag( table, "id" );
            String id = values.get( idColumn ).toString();
            Log.w( Tag, String.format("Updated failed: %s %s", table, id ));
        }
        return result;
    }

    private boolean upsert(SQLiteDatabase db, String table, Map<String,Object> values) {
        boolean update = false;
        String idColumn = getColumnForTag( table, "id" );
        if( idColumn != null ) {
            Object idValue = values.get( idColumn );
            if( idValue != null ) {
                String where = String.format("%s=?", idColumn );
                // TODO: Should all values be strings?
                int count = countInTable( table, where, idValue.toString() );
                update = (count == 1);
            }
        }
        if (update) {
            return update( db, table, idColumn, values );
        }
        else {
            return insert( db, table, values );
        }
    }

    /**
     * Merge a list of values into the database.
     * Notifies any observers of the 'db' model.
     * @param table         The name of the table to merge values into.
     * @param valuesList    A list of items to merge into the table data. Each item in the list should
     *                      contain a value for the ID column, and all other values should correspond to
     *                      columns in the table. If a row already exists in the table with the same ID
     *                      as an item, then the row is updated with that item's values. Otherwise, a new
     *                      row is inserted with the item's values.
     * @return true if all values were merged successfully into the db.
     */
    public boolean merge(String table, List<Map<String,Object>> valuesList) {
        boolean result = true;
        String idColumn = getColumnForTag( table, "id" );
        if( idColumn != null ) {
            SQLiteDatabase db = helper.getWritableDatabase();
            // TODO willChangeValueForKey:table
            int i = 0;
            for( Map<String,Object> values : valuesList ) {
                String id = values.get( idColumn ).toString();
                Map<String,Object> record = read( db, table, idColumn, id );
                if( record != null ) {
                    record.putAll( values );
                    result &= update( db, table, idColumn, record );
                }
                else {
                    result &= insert( db, table, values );
                }
            }
            // TODO didChangeValueForKey:table
        }
        else {
            Log.w( Tag, String.format("No ID column found for table %s", table ));
        }
        return result;
    }


    /**
     * Delete objects from the database.
     * Notifies any observers of the 'db' model.
     * @param table     The table to delete data from.
     * @param ids       An array of object IDs.
     */
    public boolean delete(String table, String... ids) {
        String idColumn = getColumnForTag( table, "id" );
        if( idColumn != null ) {
            return delete( table, idColumn, ids );
        }
        else {
            Log.w( Tag, String.format("No ID column found for table %s", table ));
        }
        return false;
    }

    public boolean delete(String table, List<String> ids) {
        String[] _ids = new String[ids.size()];
        return delete( table, ids.toArray( _ids ));
    }

    private boolean delete(String table, String idColumn, String[] ids) {
        boolean ok = false;
        if( ids.length > 0 ) {
            SQLiteDatabase db = helper.getWritableDatabase();
            // TODO willChangeValueForKey:table
            StringBuilder placeholders = new StringBuilder("?");
            for( int i = 1; i < ids.length; i++ ) {
                placeholders.append(",?");
            }
            String where = String.format("%s IN (%s)", idColumn, placeholders );
            int count = db.delete( table, where, ids );
            ok = (count == ids.length);
            // TODO didChangeValueForKey:table
        }
        return ok;
    }

    public int deleteWhere(String table, String where, String... args) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete( table, where, args );
    }

    /**
     * Convert a map of values to a ContentValues object.
     * @param tname     The name of the table values are being inserted into.
     * @param values    Mapped values. Only includes values with corresponding column names in the table.
     * @return
     */
    private ContentValues toContentValues(String tname, Map<String,Object> values) {
        ContentValues cvalues = new ContentValues();
        Set<String> columnNames = tableColumnNames.get( tname );
        if( columnNames != null ) {
            for( String cname : values.keySet() ) {
                if( columnNames.contains( cname ) ) {
                    Object cvalue = values.get( cname );
                    if( cvalue instanceof Number ) {
                        cvalues.put( cname, ((Number)cvalue).doubleValue() );
                    }
                    else if( cvalue instanceof Boolean ) {
                        cvalues.put( cname, (Boolean)cvalue );
                    }
                    else {
                        cvalues.put( cname, cvalue.toString() );
                    }
                }
            }
        }
        return cvalues;
    }

    /** Create and return a new instance of this database connection. */
    public DB newInstance() {
        DB db = new DB( this );
        db.startService();
        return db;
    }

    // Service interface
    @Override
    public void startService() {
        this.helper = new DBHelper( androidContext, this );
        if( resetDatabase ) {
            Log.w( Tag, String.format( "Resetting database %s", name ) );
            androidContext.deleteDatabase( name );
        }
        helper.getReadableDatabase();
    }

    @Override
    public void stopService() {}

}
