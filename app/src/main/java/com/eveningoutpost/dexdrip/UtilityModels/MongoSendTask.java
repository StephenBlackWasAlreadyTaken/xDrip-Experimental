package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Services.SyncService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by stephenblack on 12/19/14.
 */
public class MongoSendTask extends AsyncTask<String, Void, Void> {
        private Context context;

        PowerManager.WakeLock wakeLock;
        private static int lockCounter = 0;
        private Exception exception;
        private static final String TAG = MongoSendTask.class.getSimpleName();

        public MongoSendTask(Context pContext) {
            context = pContext;
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MongoSendTask");
            wakeLock.acquire();
            lockCounter++;
            Log.e(TAG,"MongosendTask - wakelock acquired " + lockCounter);

        }

        public Void doInBackground(String... urls) {
            try {
               	sendData();
            } finally {
                wakeLock.release();
                lockCounter--;
                Log.e(TAG,"MongosendTask wakelock released " + lockCounter);
            }
            return null;
        }
        
        private boolean sendData() {
        	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        	boolean nightWatchproMode = prefs.getBoolean("xDripViewer_upload_mode", false);
            List<CalibrationSendQueue>calibrationsQueue = CalibrationSendQueue.mongoQueue(nightWatchproMode);
            List<BgSendQueue> bgsQueue = BgSendQueue.mongoQueue( nightWatchproMode);

            try {
                List<BgReading> bgReadings = new ArrayList<BgReading>();
                List<Calibration> calibrations = new ArrayList<Calibration>();
                for (CalibrationSendQueue job : calibrationsQueue) {
                    calibrations.add(job.calibration);
                }
                for (BgSendQueue job : bgsQueue) {
                    bgReadings.add(job.bgReading);
                }

                if(bgReadings.size() + calibrations.size() > 0) {
                	Log.i(TAG, "uoader.upload called " + bgReadings.size());
                    NightscoutUploader uploader = new NightscoutUploader(context);
                    boolean uploadStatus = uploader.upload(bgReadings, calibrations, calibrations, nightWatchproMode);
                    if (uploadStatus) {
                    	Log.i(TAG, "Starting to delete objects from queue " + bgsQueue.size() + calibrationsQueue.size());
                        for (CalibrationSendQueue calibration : calibrationsQueue) {
                            calibration.deleteThis();
                        }
                        for (BgSendQueue bgReading : bgsQueue) {
                            bgReading.deleteThis();
                        }
                        Log.i(TAG, "finished deleting objects from queue " + bgReadings.size());
                    } else {
                    	Log.e(TAG, "uploader.upload returned false - exiting");
                    	return false;
                    }
                } else {
                    return false;
                }
            } catch (Exception e) {
            	Log.e(TAG, "cought exception", e);
                this.exception = e;
                // We will try again soon, But I want to make sure we are not in infinite loop.
                return false;
            }
            return true;
        }

    }
