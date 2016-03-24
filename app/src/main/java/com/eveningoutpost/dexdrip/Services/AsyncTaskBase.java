package com.eveningoutpost.dexdrip.Services;

import android.content.Context;
import android.os.AsyncTask;
import android.os.PowerManager;

import com.eveningoutpost.dexdrip.Models.UserError.Log;

abstract class AsyncTaskBase extends AsyncTask<String, Void, Void > {
    protected final Context mContext;
    PowerManager.WakeLock wakeLock;
    private static int lockCounter = 0;
    private final String TAG; 

    abstract void readData();
    
    AsyncTaskBase(Context ctx, String tag) {
        mContext = ctx.getApplicationContext();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiReader");
        wakeLock.acquire();
        lockCounter++;
        TAG = tag;
        Log.e(TAG,"wakelock acquired " + lockCounter);
    }
    
    public Void doInBackground(String... urls) {
        try {
            readData();
        } finally {
            wakeLock.release();
            lockCounter--;
            Log.e(TAG,"wakelock released " + lockCounter);
        }
        return null;
    }
    
}