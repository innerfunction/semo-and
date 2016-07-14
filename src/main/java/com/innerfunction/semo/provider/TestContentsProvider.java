package com.innerfunction.semo.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.innerfunction.http.Client;
import com.innerfunction.http.Response;
import com.innerfunction.util.Files;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by juliangoacher on 14/07/16.
 */
public class TestContentsProvider extends ContentProvider {

    private File cacheDir;
    private Client httpClient;

    @Override
    public boolean onCreate() {
        this.cacheDir = Files.getCacheDir( getContext() );
        this.httpClient = new Client( getContext() );
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String filePath = uri.getPath();
        File cacheFile = new File( cacheDir, filePath );
        if( !cacheFile.exists() ) {
            String url = String.format("http://doit-mobile.com/%s", filePath );
            try {
                Response response = httpClient.getFile( url, cacheFile ).sync();
            }
            catch(Exception e) {
                throw new FileNotFoundException( filePath );
            }
        }
        return ParcelFileDescriptor.open( cacheFile, ParcelFileDescriptor.MODE_READ_ONLY );
    }

    @Override
    public int delete(Uri uri, String s, String[] as) {
        throw new UnsupportedOperationException("Op not supported");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("Op not supported");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Op not supported");
    }

    @Override
    public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
        throw new UnsupportedOperationException("Op not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String s, String[] as) {
        throw new UnsupportedOperationException("Op not supported");
    }
}
