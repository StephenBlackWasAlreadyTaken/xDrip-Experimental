package com.eveningoutpost.dexdrip.UtilityModels;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.Models.UserError.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Services.SyncService;
import com.eveningoutpost.dexdrip.ShareModels.Models.ShareUploadPayload;
import com.eveningoutpost.dexdrip.utils.BgToSpeech;
import com.eveningoutpost.dexdrip.ShareModels.BgUploader;
import com.eveningoutpost.dexdrip.WidgetUpdateService;
import com.eveningoutpost.dexdrip.wearintegration.WatchUpdaterService;
import com.eveningoutpost.dexdrip.xDripWidget;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table(name = "BgSendQueue", id = BaseColumns._ID)
public class BgSendQueue extends Model {

    @Column(name = "bgReading", index = true)
    public BgReading bgReading;

    @Column(name = "success", index = true)
    public boolean success;

    @Column(name = "mongo_success", index = true)
    public boolean mongo_success;

    @Column(name = "operation_type")
    public String operation_type;

    public static List<BgSendQueue> mongoQueue(boolean xDripViewerMode) {
    	List<BgSendQueue> values = new Select()
                .from(BgSendQueue.class)
                .where("mongo_success = ?", false)
                .where("operation_type = ?", "create")
                .orderBy("_ID desc")
                .limit(xDripViewerMode ? 500 : 30)
                .execute();
    	if (xDripViewerMode) {
    		 java.util.Collections.reverse(values);
    	}
    	return values;
    	
    }

    private static void addToQueue(BgReading bgReading, String operation_type) {
        BgSendQueue bgSendQueue = new BgSendQueue();
        bgSendQueue.operation_type = operation_type;
        bgSendQueue.bgReading = bgReading;
        bgSendQueue.success = false;
        bgSendQueue.mongo_success = false;
        bgSendQueue.save();
        Log.d("BGQueue", "New value added to queue!");
    }
    
    public static void handleNewBgReading(BgReading bgReading, String operation_type, Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "sendQueue");
        wakeLock.acquire();
        try {
        	
       		addToQueue(bgReading, operation_type);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            Intent updateIntent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA);
            context.sendBroadcast(updateIntent);

            if(AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, xDripWidget.class)).length > 0){
                context.startService(new Intent(context, WidgetUpdateService.class));
            }


            if (prefs.getBoolean("broadcast_data_through_intents", false)) {

                //prepare data
                double calculated_value = bgReading.calculated_value;
                boolean hide_slope = bgReading.hide_slope;
                String slopeName = hide_slope?null:bgReading.slopeName();
                int batteryLevel = getBatteryLevel(context);
                final long timestamp = bgReading.timestamp;
                Calibration cal = Calibration.last();
                double raw = NightscoutUploader.getNightscoutRaw(bgReading, cal);
                double slope = BgReading.currentSlope();

                //send broadcast
                BgEstimateBroadcaster.broadcastBgEstimate(calculated_value, raw, timestamp, slope, slopeName, batteryLevel, context);

                //just keep it alive for 3 more seconds to allow the watch to be updated
                // TODO: change NightWatch to not allow the system to sleep.
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "broadcastNightWatch").acquire(3000);

            }

            // send to wear
            if (prefs.getBoolean("wear_sync", false)) {

                /*By integrating the watch part of Nightwatch we inherited the same wakelock
                    problems NW had - so adding the same quick fix for now.
                    TODO: properly "wakelock" the wear (and probably pebble) services
                 */
                context.startService(new Intent(context, WatchUpdaterService.class));
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "quickFix3").acquire(15000);
            }

            // send to pebble
            if(prefs.getBoolean("broadcast_to_pebble", false)) {
                context.startService(new Intent(context, PebbleSync.class));
            }



            if (prefs.getBoolean("share_upload", false)) {
                Log.d("ShareRest", "About to call ShareRest!!");
                String receiverSn = prefs.getString("share_key", "SM00000000").toUpperCase();
                BgUploader bgUploader = new BgUploader(context);
                bgUploader.upload(new ShareUploadPayload(receiverSn, bgReading));
            }
            context.startService(new Intent(context, SyncService.class));

            //Text to speech
            Log.d("BgToSpeech", "gonna call speak");
            BgToSpeech.speak(bgReading.calculated_value, bgReading.timestamp);


        } finally {
            wakeLock.release();
        }
    }



    public void deleteThis() {
        this.delete();
    }

    public static int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 50;
        }
        return (int)(((float)level / (float)scale) * 100.0f);
    }
}
