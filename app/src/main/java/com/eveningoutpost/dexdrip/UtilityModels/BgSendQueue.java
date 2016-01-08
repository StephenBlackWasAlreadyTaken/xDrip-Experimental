package com.eveningoutpost.dexdrip.UtilityModels;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Services.SyncService;
import com.eveningoutpost.dexdrip.ShareModels.BgUploader;
import com.eveningoutpost.dexdrip.ShareModels.Models.ShareUploadPayload;
import com.eveningoutpost.dexdrip.WidgetUpdateService;
import com.eveningoutpost.dexdrip.utils.BgToSpeech;
import com.eveningoutpost.dexdrip.wearintegration.WatchUpdaterService;
import com.eveningoutpost.dexdrip.xDripWidget;

import java.util.Collections;
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
                Log.i("SENSOR QUEUE:", "Broadcast data");
                final Bundle bundle = new Bundle();
                bundle.putDouble(Intents.EXTRA_BG_ESTIMATE, bgReading.calculated_value);

                //TODO: change back to bgReading.calculated_value_slope if it will also get calculated for Share data
                // bundle.putDouble(Intents.EXTRA_BG_SLOPE, bgReading.calculated_value_slope);
                bundle.putDouble(Intents.EXTRA_BG_SLOPE, BgReading.currentSlope());
                if (bgReading.hide_slope) {
                    bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, "9");
                } else {
                    bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, bgReading.slopeName());
                }
                bundle.putInt(Intents.EXTRA_SENSOR_BATTERY, getBatteryLevel(context));
                bundle.putLong(Intents.EXTRA_TIMESTAMP, bgReading.timestamp);

                Calibration cal = Calibration.last();
                bundle.putDouble(Intents.EXTRA_RAW, NightscoutUploader.getNightscoutRaw(bgReading, cal));
                Intent intent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent, Intents.RECEIVER_PERMISSION);

                //just keep it alive for 3 more seconds to allow the watch to be updated
                // TODO: change NightWatch to not allow the system to sleep.
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "broadcstNightWatch").acquire(3000);

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
            sendToBroadcastReceiverToDanaApp(context);

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

    private static void sendToBroadcastReceiverToDanaApp(Context context) {


        Intent intent = new Intent("danaR.action.BG_DATA");
        List<BgReading> latest6bgReadings = BgReading.latest(6);
        Collections.reverse(latest6bgReadings);

        int sizeRecords = latest6bgReadings.size();
        double deltaAvg30min = 0d;
        double deltaAvg15min = 0d;
        double avg30min = 0d;
        double avg15min = 0d;

        boolean notGood = false;

        if(sizeRecords >6) {
            for(int i = sizeRecords-6;i<sizeRecords ;i++) {
                short glucoseValueBeeingProcessed = (short) latest6bgReadings.get(i).calculated_value;
                if(glucoseValueBeeingProcessed < 40) {
                    notGood = true;
                    Log.i("sendToBroadcastReceiverToDanaApp", "sendToBroadcastReceiverToDanaApp data not good "+ latest6bgReadings.get(i).timestamp);
                }
                deltaAvg30min+= glucoseValueBeeingProcessed - latest6bgReadings.get(i-1).calculated_value;
                avg30min+=glucoseValueBeeingProcessed;
                if(i>=sizeRecords-3) {
                    avg15min += glucoseValueBeeingProcessed;
                    deltaAvg15min += glucoseValueBeeingProcessed - latest6bgReadings.get(i-1).calculated_value;
                }
            }
            deltaAvg30min/=6d;
            deltaAvg15min/=3d;
            avg30min/=6d;
            avg15min/=3d;
        }

        if(notGood) {
            Log.i("sendToBroadcastReceiverToDanaApp","sendToBroadcastReceiverToDanaApp data not good");
            return;
        }
        Bundle bundle = new Bundle();
        BgReading timeMatechedRecordCurrent = latest6bgReadings.get(sizeRecords - 1);
        bundle.putLong("time", timeMatechedRecordCurrent.timestamp);
        bundle.putInt("value", (int) timeMatechedRecordCurrent.calculated_value);
        bundle.putInt("delta", (int) (timeMatechedRecordCurrent.calculated_value - latest6bgReadings.get(sizeRecords - 2).calculated_value));
        bundle.putDouble("deltaAvg30min", deltaAvg30min);
        bundle.putDouble("deltaAvg15min", deltaAvg15min);
        bundle.putDouble("avg30min", avg30min);
        bundle.putDouble("avg15min", avg15min);

        intent.putExtras(bundle);

        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        Log.i("sendToBroadcastReceiverToDanaApp","sendToBroadcastReceiverToDanaApp.queryBroadcastReceivers "+x.size() +" "+x );


        context.sendBroadcast(intent);
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


