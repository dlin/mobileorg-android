package com.matburt.mobileorg;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.content.Context;
import android.app.Application;
import java.util.ArrayList;
import java.util.HashMap;
import android.util.Log;
import android.content.SharedPreferences;
import java.io.File;
import android.os.Environment;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;

public class MobileOrgDatabase {
    private Context appcontext;
    private String lastStorageMode = "";
    private Resources r;
    public SQLiteDatabase appdb;
    public SharedPreferences appSettings;
    public static final String LT = "MobileOrg";

    public MobileOrgDatabase(Context appctxt) {
        this.appcontext = appctxt;
        this.appSettings = PreferenceManager.getDefaultSharedPreferences(appctxt);
        this.r = appctxt.getResources();
        this.initialize();
    }

    public void initialize() {
        String storageMode = this.appSettings.getString("storageMode", "");
        if (storageMode.equals("internal") ||
            storageMode.equals(""))
            this.appdb = this.appcontext.openOrCreateDatabase("MobileOrg", 0, null);
        else if (storageMode.equals("sdcard")) {
            File sdcard = Environment.getExternalStorageDirectory();
            File morgDir = new File(sdcard, "mobileorg");
            if(!morgDir.exists()) {
                morgDir.mkdir();
            }
            File morgFile = new File(morgDir, "mobileorg.db");
            this.appdb = SQLiteDatabase.openOrCreateDatabase(morgFile, null);
            Log.d(LT, "Setting database path to " + morgFile.getAbsolutePath());
        }
        else {
            ErrorReporter.displayError(this.appcontext,
                                       r.getString(R.string.error_opening_database));
            return;
        }
        this.appdb.execSQL("CREATE TABLE IF NOT EXISTS files"
                           + " (file VARCHAR, name VARCHAR,"
                           + " checksum VARCHAR);");
        this.appdb.execSQL("CREATE TABLE IF NOT EXISTS todos"
                           + " (tdgroup int, name VARCHAR,"
                           + " isdone INT)");
        this.appdb.execSQL("CREATE TABLE IF NOT EXISTS priorities"
                           + " (tdgroup int, name VARCHAR,"
                           + " isdone INT)");                           
        this.lastStorageMode = storageMode;
    }

    public void close() {
        this.appdb.close();
    }

    public void checkStorageMode() {
        String storageMode = this.appSettings.getString("storageMode", "");
        if (storageMode != this.lastStorageMode) {
            this.close();
            this.initialize();
        }
    }

    public HashMap<String, String> getOrgFiles() {
        this.checkStorageMode();
        HashMap<String, String> allFiles = new HashMap<String, String>();
        Cursor result = this.appdb.rawQuery("SELECT file, name FROM files", null);
        if (result != null) {
            if (result.getCount() > 0) {
                result.moveToFirst();
                do {
                    allFiles.put(result.getString(0),
                                 result.getString(1));
                } while(result.moveToNext());
            }
        }
        result.close();
        return allFiles;
    }

    public HashMap<String, String> getChecksums() {
        this.checkStorageMode();
        HashMap<String, String> fchecks = new HashMap<String, String>();
        Cursor result = this.appdb.rawQuery("SELECT file, checksum FROM files", null);
        if (result != null) {
            if (result.getCount() > 0) {
                result.moveToFirst();
                do {
                    fchecks.put(result.getString(0),
                                result.getString(1));
                } while (result.moveToNext());
            }
        }
        result.close();
        return fchecks;
    }

    public void removeFile(String filename) {
        this.checkStorageMode();
        this.appdb.execSQL("DELETE FROM files " +
                           "WHERE file = '"+filename+"'");
        Log.i(LT, "Finished deleting from files");
    }

    public void clearData() {
        this.checkStorageMode();
        this.appdb.execSQL("DELETE FROM data");
    }

    public void clearTodos() {
        this.checkStorageMode();
        this.appdb.execSQL("DELETE from todos");
    }

    public void clearPriorities() {
        this.checkStorageMode();
        this.appdb.execSQL("DELETE from priorities");
    }

    public void addOrUpdateFile(String filename, String name, String checksum) {
        this.checkStorageMode();
        Cursor result = this.appdb.rawQuery("SELECT * FROM files " +
                                       "WHERE file = '"+filename+"'", null);
        if (result != null) {
            if (result.getCount() > 0) {
                this.appdb.execSQL("UPDATE files set name = '"+name+"', "+
                              "checksum = '"+ checksum + "' where file = '"+filename+"'");
            }
            else {
                this.appdb.execSQL("INSERT INTO files (file, name, checksum) " +
                              "VALUES ('"+filename+"','"+name+"','"+checksum+"')");
            }
        }
        result.close();
    }

    public ArrayList<HashMap<String, Integer>> getTodos() {
        ArrayList<HashMap<String, Integer>> allTodos = new ArrayList<HashMap<String, Integer>>();
        Cursor result = this.appdb.rawQuery("SELECT tdgroup, name, isdone " +
                                            "FROM todos order by tdgroup",
                                            null);
        if (result != null) {
            HashMap<String, Integer> grouping = new HashMap<String, Integer>();
            int resultgroup = 0;
            if (result.getCount() > 0) {
                result.moveToFirst();
                do {
                    if (result.getInt(0) != resultgroup) {
                        allTodos.add(grouping);
                        grouping = new HashMap<String, Integer>();
                        resultgroup = result.getInt(0);
                    }
                    grouping.put(result.getString(1),
                                 result.getInt(2));
                } while(result.moveToNext());
                allTodos.add(grouping);
            }
        }
        result.close();
        return allTodos;
    }

    public ArrayList<ArrayList<String>> getPriorities() {
        ArrayList<ArrayList<String>> allPriorities = new ArrayList<ArrayList<String>>();
        Cursor result = this.appdb.rawQuery("SELECT tdgroup, name FROM priorities order by tdgroup",
                                            null);
        if (result != null) {
            ArrayList<String> grouping = new ArrayList();
            int resultgroup = 0;
            if (result.getCount() > 0) {
                result.moveToFirst();
                do {
                    if (result.getInt(0) != resultgroup) {
                        allPriorities.add(grouping);
                        grouping = new ArrayList();
                        resultgroup = result.getInt(0);
                    }
                    grouping.add(result.getString(1));
                } while(result.moveToNext());
                allPriorities.add(grouping);
            }
        }
        result.close();
        return allPriorities;
    }

    public void setTodoList(ArrayList<HashMap<String, Boolean>> newList) {
        this.clearTodos();
        int grouping = 0;
        for (HashMap<String, Boolean> entry : newList) {
            for (String key : entry.keySet()) {
                String isDone = "0";
                if (entry.get(key))
                    isDone = "1";
                this.appdb.execSQL("INSERT INTO todos (tdgroup, name, isdone) " +
                                   "VALUES (" + grouping + "," +
                                   "        '" + key + "'," +
                                   "        " + isDone + ")");
            }
            grouping++;
        }
    }

    public void setPriorityList(ArrayList<ArrayList<String>> newList) {
        this.clearPriorities();
        for (int idx = 0; idx < newList.size(); idx++) {
            for (int jdx = 0; jdx < newList.get(idx).size(); jdx++) {
                this.appdb.execSQL("INSERT INTO priorities (tdgroup, name, isdone) " +
                                   "VALUES (" + Integer.toString(idx) + "," +
                                   "        '" + newList.get(idx).get(jdx) + "'," +
                                   "        0)");
            }
        }
    }
}