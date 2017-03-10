package com.eveningoutpost.dexdrip.utils;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.Models.AlertType;
import com.eveningoutpost.dexdrip.Services.XDripViewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static com.eveningoutpost.dexdrip.utils.FileUtils.getExternalDir;

public class SdcardImportExport extends ActivityWithMenu {

    private final static String TAG = "jamorham sdcard";

    
    String getPreferenceFileName() {
      if(XDripViewer.isxDripViewerMode(getApplicationContext())) {
        return "shared_prefs/com.eveningoutpost.dexdrip.viewer_preferences.xml";
      } else {
        return "shared_prefs/com.eveningoutpost.dexdrip_preferences.xml";
      }
    }
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sdcard_import_export);
    }

    @Override
    public String getMenuName() {
        return getString(R.string.menu_sdcard);
    }

    public void savePreferencesToSD(View myview) {

        if (savePreferencesToSD()) {
            toast(getString(R.string.toast_preferences_saved, FileUtils.getDirectoryName(getApplicationContext())));
        } else {
            toast(getString(R.string.toast_couldnt_write_sdcard));
        }
    }

    public void loadPreferencesToSD(View myview) {
        if (loadPreferencesFromSD()) {
            toast(getString(R.string.toast_loaded_preferences));
            // shared preferences are cached so we need a hard restart
            android.os.Process.killProcess(android.os.Process.myPid());
        } else {
            toast(getString(R.string.toast_make_existing_sure, FileUtils.getDirectoryName(getApplicationContext())));
        }
    }

    public boolean savePreferencesToSD() {
        if (isExternalStorageWritable()) {
            boolean succeeded = AlertType.toSettings(getApplicationContext());
            if (succeeded) {
                succeeded &= dataToSDcopy(getPreferenceFileName());
            }
            return succeeded;
        } else {
            toast(getString(R.string.toast_sdcard_no_writeable_cannot_save));
            return false;
        }
    }

    public boolean loadPreferencesFromSD() {
        if (isExternalStorageWritable()) {
            return dataFromSDcopy(getPreferenceFileName());
        } else {
            toast(getString(R.string.toast_sdcard_not_readable));
            return false;
        }
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private String getCustomSDcardpath() {
        return getExternalDir(getApplicationContext()) + "/settingsExport";
    }

    private boolean dataToSDcopy(String filename) {
        File source_file = new File(getFilesDir().getParent() + "/" + filename);
        String path = getCustomSDcardpath();
        File fpath = new File(path);
        try {
            fpath.mkdirs();
            File dest_file = new File(path, source_file.getName());

            if (directCopyFile(source_file, dest_file)) {
                Log.i(TAG, "Copied success: " + filename);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making directory: " + path.toString());
            return false;
        }
        return false;
    }

    private boolean dataFromSDcopy(String filename) {
        File dest_file = new File(getFilesDir().getParent() + "/" + filename);
        File source_file = new File(getCustomSDcardpath() + "/" + dest_file.getName());
        try {
            dest_file.mkdirs();
            if (directCopyFile(source_file, dest_file)) {
                Log.i(TAG, "Copied success: " + filename);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making directory: " + dest_file.toString());
            return false;
        }
        return false;
    }

    private boolean directCopyFile(File source_filename, File dest_filename) {
        Log.i(TAG, "Attempt to copy: " + source_filename.toString() + " to " + dest_filename.toString());
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source_filename);
            out = new FileOutputStream(dest_filename);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return false;
    }

    private void toast(String msg) {
        try {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Toast msg: " + msg);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't display toast: " + msg);
        }
    }

    public void closeButton(View myview) {
        finish();
    }

}
