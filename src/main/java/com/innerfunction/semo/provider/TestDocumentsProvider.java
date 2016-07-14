package com.innerfunction.semo.provider;

import android.database.Cursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsProvider;

import com.innerfunction.http.Client;
import com.innerfunction.http.Response;
import com.innerfunction.q.Q;
import com.innerfunction.util.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ArrayBlockingQueue;


/**
 * Created by juliangoacher on 14/07/16.
 */
public class TestDocumentsProvider /*extends DocumentsProvider */{

    private File cacheDir;
    private Client httpClient;
/*
    @Override
    public boolean onCreate() {
        this.cacheDir = Files.getCacheDir( getContext() );
        this.httpClient = new Client( getContext() );
        return true;
    }

    //@Override
    //public void attachInfo(Context context, ProviderInfo info) {
    //    this.cacheDir = Files.getCacheDir( context );
    //}

    @Override
    public Cursor queryRoots(String[] projection) {
        return null;
    }

    @Override
    public Cursor queryChildDocuments(String parentID, String[] projection, String sortOrder) {
        return null;
    }

    @Override
    public Cursor queryDocument(String documentID, String[] projection) {
        return null;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        File cacheFile = new File( cacheDir, documentId );
        if( !cacheFile.exists() ) {
            final ArrayBlockingQueue<Boolean> queue = new ArrayBlockingQueue<>( 1 );
            String url = String.format("http://doit-mobile.com/%s", documentId );
            try {
                httpClient.getFile( url, cacheFile )
                    .then( new Q.Promise.Callback<Response, Response>() {
                        @Override
                        public Response result(Response response) {
                            queue.add( true );
                            return response;
                        }
                    } )
                    .error( new Q.Promise.ErrorCallback() {
                        public void error(Exception e) {
                            queue.add( false );
                        }
                    } );
                // Block until the http response writes to the queue.
                boolean ok = queue.take();
                if( !ok ) {
                    throw new FileNotFoundException( cacheFile.getAbsolutePath() );
                }
            }
            catch(Exception e) {
                throw new FileNotFoundException( cacheFile.getAbsolutePath() );
            }
        }
        return ParcelFileDescriptor.open( cacheFile, ParcelFileDescriptor.MODE_READ_ONLY );
    }
    */
}
