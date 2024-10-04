package com.cyfi.autolauncher2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SinkSourceContentProvider extends ContentProvider {
    private SQLiteDatabase mDb;
    private Context mContext;

    public static final String AUTHORLTY = "com.cyfi.autolauncher2.provider";


    public static final Uri DCLSINKSOURCE_URI = Uri.parse("content://"+AUTHORLTY+"/dclsinksource");
    public static final Uri JSISINK_URI = Uri.parse("content://"+AUTHORLTY+"/jsisink");

    public static final Uri JSIRESULT_URI = Uri.parse("content://"+AUTHORLTY+"/jsiresult");

    public static final Uri DCLRESULT_URI = Uri.parse("content://"+AUTHORLTY+"/dclresult");

    public static final Uri MODE_URI = Uri.parse("content://"+AUTHORLTY+"/mode");

    public static final Uri VALIDATION_RESULT_URI = Uri.parse("content://"+AUTHORLTY+"/validationresult");

    public static final int DCLSINKSOURCE_URI_CODE = 1;
    public static final int JSISINKS_URI_CODE = 2;
    public static final int DCL_RESULT_URI_CODE = 3;
    public static final int JSI_RESULT_URI_CODE = 4;
    public static final int MODE_URI_CODE = 5;
    public static final int VALIDATION_RESULT_URI_CODE = 6;


    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(AUTHORLTY, "dclsinksource", DCLSINKSOURCE_URI_CODE);
        sUriMatcher.addURI(AUTHORLTY, "jsisink", JSISINKS_URI_CODE);
        sUriMatcher.addURI(AUTHORLTY, "dclresult", DCL_RESULT_URI_CODE);
        sUriMatcher.addURI(AUTHORLTY, "jsiresult", JSI_RESULT_URI_CODE);
        sUriMatcher.addURI(AUTHORLTY, "mode", MODE_URI_CODE);
        sUriMatcher.addURI(AUTHORLTY, "validationresult", VALIDATION_RESULT_URI_CODE);

    }

    private String getTableName(Uri uri){
        switch(sUriMatcher.match(uri)){
            case DCLSINKSOURCE_URI_CODE:
                return "dclsinksource";
            case JSISINKS_URI_CODE:
                return "jsisink";
            case DCL_RESULT_URI_CODE:
                return "dclresult";
            case JSI_RESULT_URI_CODE:
                return "jsiresult";
            case MODE_URI_CODE:
                return "mode";
            case VALIDATION_RESULT_URI_CODE:
                return "validationresult";
            default:
                return null;
        }
    }


    private void initProvider(){
        mDb = new ResultDBHelper(mContext).getWritableDatabase();

    }


    @Override
    public boolean onCreate() {
        mContext = getContext();
        initProvider();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String table = getTableName(uri);
        if(table == null){
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        return mDb.query(table, projection, selection, selectionArgs, null, null, sortOrder, null);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        String table = getTableName(uri);
        if(table == null ){
            throw new IllegalArgumentException("UnSupported URI :"+uri);
        }
//        if(table.equals("dclresult")){
//            Log.d("Insert", "insert " + values.toString());
//        }
        mDb.insert(table,null,values);
        Log.d("EdXposed-Bridge-Controller", values.toString());

        mContext.getContentResolver().notifyChange(uri,null);
        return uri;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        String table = getTableName(uri);
        if(table == null ){
            throw new IllegalArgumentException("UnSupported URI :"+uri);
        }
        int count = mDb.delete(table,selection,selectionArgs);
        if(count>0){
            getContext().getContentResolver().notifyChange(uri,null);
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        String table = getTableName(uri);
        if(table == null ){
            throw new IllegalArgumentException("UnSupported URI :"+uri);
        }

        int row = mDb.update(table,values,selection,selectionArgs);
        if(row >0 ){
            getContext().getContentResolver().notifyChange(uri,null);
        }
        return row;
    }




}