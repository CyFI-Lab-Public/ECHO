package com.cyfi.autolauncher2;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ResultDBHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "cyfi.autolauncher2.db";

    private Context mContext = null;

    public ResultDBHelper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }

    public void clearJsiResultTable(SQLiteDatabase db){
        db.execSQL("DELETE FROM jsiresult");
    }
    public void clearDclResultTable(SQLiteDatabase db){
        db.execSQL("DELETE FROM dclresult");
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        String deleteDclSourceSinkTable = "DROP TABLE IF EXISTS dclsinksource";
        String deletejsiSinkTable = "DROP TABLE IF EXISTS jsisink";

        String createJsiResultsTableSQL = "CREATE TABLE IF NOT EXISTS jsiresult (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sinkMethod TEXT, " +
                "url TEXT," +
                "interfaceName TEXT," +
                "packageName TEXT, " +
                "entryPointMethod TEXT, " +
                "entryPointArgs TEXT," +
                "sinkArgs TEXT)";

        String createDclResultsTableSQL = "CREATE TABLE IF NOT EXISTS dclresult (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "method TEXT, " +
                "packageName TEXT, " +
                "type TEXT, " +
                "url TEXT," +
                "filePath TEXT," +
                "loadClassName TEXT, " +
                "loadMethodName TEXT, " +
                "isStatic INTEGER, " +
                "interfaceNames TEXT, " +
                "desFilePath TEXT, " +
                "stack TEXT)";

        String createDCLSinksourceTableSQL = "CREATE TABLE IF NOT EXISTS dclsinksource (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "method TEXT, " +
                "class TEXT, " +
                "type TEXT," +
                "line TEXT)";

        String createJSISinkTableSQL = "CREATE TABLE IF NOT EXISTS jsisink (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "method TEXT, " +
                "class TEXT, " +
                "type TEXT, " +
                "line TEXT)";

        String createModeTableSQL = "CREATE TABLE IF NOT EXISTS mode (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "packageName TEXT, " +
                "mode INTEGER)"; // mode 1: dcl, mode 2: jsi mode 3: dcl validation

        String createValidationResultTableSQL = "CREATE TABLE IF NOT EXISTS validationresult (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "packageName TEXT, " +
                "className TEXT, " +
                "methodName TEXT)"; // mode 1: dcl, mode 2: jsi mode 3: dcl validation

        db.execSQL(createModeTableSQL);

        db.execSQL(createJsiResultsTableSQL);
        db.execSQL(createDclResultsTableSQL);
        db.execSQL(createValidationResultTableSQL);


        db.execSQL(deleteDclSourceSinkTable);
        db.execSQL(deletejsiSinkTable);

        if(!doesTableExist(db, "dclsinksource")){
            db.execSQL(createDCLSinksourceTableSQL);
            addDCLSinkSourceToTable(db);
        }
        if(!doesTableExist(db, "jsisink")){
            db.execSQL(createJSISinkTableSQL);
            addJSISinkToTable(db);
        }
    }



    public void addDCLSinkSourceToTable(SQLiteDatabase db){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(mContext.getAssets().open("SourceSinkDCL.txt")));
            // do reading, usually loop until end of file reading
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                //process line
                String[] resolvedLine = resolveLine(mLine);
                if(resolvedLine == null){
                    continue;
                }
                String insertToSinksourceTableSQL = "INSERT INTO dclsinksource (method, class, type, line) VALUES ('" + resolvedLine[0] + "','" + resolvedLine[1] + "','" + resolvedLine[2] + "','" + mLine + "')";
                db.execSQL(insertToSinksourceTableSQL);
            }
        } catch (IOException e) {
            //log the exception
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    //log the exception
                }
            }
        }
    }


    public void addJSISinkToTable(SQLiteDatabase db){
        BufferedReader reader = null;
        try{
            reader = new BufferedReader(
                    new InputStreamReader(mContext.getAssets().open("SourceSinkJSI.txt")));
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                //process line
                String[] resolvedLine = resolveLine(mLine);
                if(resolvedLine == null){
                    continue;
                }

                String insertToSinksourceTableSQL = "INSERT INTO jsisink (method, class, type, line) VALUES ('" + resolvedLine[0] + "','" + resolvedLine[1] + "','" + resolvedLine[2] + "','" + mLine + "')";
                db.execSQL(insertToSinksourceTableSQL);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    //log the exception
                }
            }
        }
    }

    public String[] resolveLine(String line){
        String[] res = new String[3];
        if(!line.contains("->")){
            return null;
        }
        System.out.println(line);
        res[2] = line.substring(line.indexOf("->") + 2);
//        String tmp = line.substring(0, line.indexOf("->") - 1);
//        char[] cs = tmp.toCharArray();
        res[0] = line.substring(0, line.indexOf("->")).substring(0, line.lastIndexOf('>') - 1);
        res[1] = line.substring(1, line.indexOf(":"));
        return res;
    }


    public boolean doesTableExist(SQLiteDatabase db, String tableName) {
        Cursor cursor = db.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '" + tableName + "'", null);

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }



}
