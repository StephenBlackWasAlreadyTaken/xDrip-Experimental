package com.eveningoutpost.dexdrip.Services;

import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.Models.TransmitterData;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Services.NsRestApiReader.NightscoutBg;
import com.eveningoutpost.dexdrip.Services.NsRestApiReader.NightscoutMbg;
import com.eveningoutpost.dexdrip.Services.NsRestApiReader.NightscoutSensor;
import com.eveningoutpost.dexdrip.UtilityModels.Notifications;
import com.eveningoutpost.dexdrip.R;

//Important note, this class is based on the fact that android will always run it one thread, which means it does not
//need synchronization

public class XDripViewer extends AsyncTaskBase {

    static Sensor sensor_exists = null; // Will hold reference to any existing sensor, to avoid looking in DB
    static Boolean isNightScoutMode = null;
    public final static String NIGHTSCOUT_SENSOR_UUID = "c5f1999c-4ec5-449e-adad-3980b172b921";

    private final static String TAG = XDripViewer.class.getName();
    
    XDripViewer(Context ctx) {
        super(ctx, TAG);
    }

    @Override
    public void readData() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String rest_addresses = prefs.getString("xdrip_viewer_ns_addresses", "");
            String hashedSecret = "";
            readSensorData(rest_addresses, hashedSecret);
            readCalData(rest_addresses, hashedSecret);
            readBgData(rest_addresses, hashedSecret);
        } catch (Exception e) {
            Log.e(TAG, "readData cought exception in xDrip viewer mode ", e);
            e.printStackTrace();
        }
    }


    private void readSensorData(String baseUrl, String key) {

        NsRestApiReader nsRestApiReader = new NsRestApiReader();
        Long LastReportedTime = 0L;

        Sensor lastSensor = Sensor.currentSensor();

        if(lastSensor != null) {
            LastReportedTime = (long)lastSensor.started_at;
        }
        Log.e(TAG, "readSensorData  LastReportedTime = " + LastReportedTime);

        List<NightscoutSensor> nightscoutSensors = nsRestApiReader.readSensorDataFromNs(baseUrl, key, LastReportedTime, 10 );
        if(nightscoutSensors == null) {
            Log.e(TAG, "readBgDataFromNs returned null");
            return;
        }

        ListIterator<NightscoutSensor> li = nightscoutSensors.listIterator(nightscoutSensors.size());
        long lastInserted = 0;
        while(li.hasPrevious()) {
            NightscoutSensor nightscoutSensor = li.previous();
            Log.e(TAG, "nightscoutSensor " + nightscoutSensor.xDrip_uuid + " " + nightscoutSensor.xDrip_started_at);
            if(nightscoutSensor.xDrip_started_at < lastInserted) {
                Log.e(TAG, "not inserting Sensor, since order is wrong. ");
                continue;
            }
            Sensor.createUpdate(nightscoutSensor.xDrip_started_at, nightscoutSensor.xDrip_stopped_at, nightscoutSensor.xDrip_latest_battery_level, nightscoutSensor.xDrip_uuid);
            lastInserted = nightscoutSensor.xDrip_started_at;
        }
    }  

    private void readCalData(String baseUrl, String key) {

        NsRestApiReader nsRestApiReader = new NsRestApiReader();
        Long LastReportedTime = 0L;

        Calibration lastCalibration = Calibration.last();

        if(lastCalibration != null) {
            LastReportedTime = (long)lastCalibration.timestamp;
        }
        Log.e(TAG, "readCalData  LastReportedTime = " + LastReportedTime);

        List<NightscoutMbg> nightscoutMbgs = nsRestApiReader.readCalDataFromNs(baseUrl, key, LastReportedTime, 10 );
        if(nightscoutMbgs == null) {
            Log.e(TAG, "readCalDataFromNs returned null");
            return;
        }

        ListIterator<NightscoutMbg> li = nightscoutMbgs.listIterator(nightscoutMbgs.size());
        long lastInserted = 0;
        while(li.hasPrevious()) {
            NightscoutMbg nightscoutMbg = li.previous();
            Log.e(TAG, "NightscoutMbg " + nightscoutMbg.mbg + " " + nightscoutMbg.date);
            if(nightscoutMbg.date < lastInserted) {
                Log.e(TAG, "not inserting calibratoin, since order is wrong. ");
                continue;
            }
            
            verifyViewerNightscoutMode(mContext, nightscoutMbg);
            
            Calibration.createUpdate(nightscoutMbg.xDrip_sensor_uuid, nightscoutMbg.mbg, nightscoutMbg.date, nightscoutMbg.xDrip_intercept, nightscoutMbg.xDrip_slope, nightscoutMbg.xDrip_estimate_raw_at_time_of_calibration,
                    nightscoutMbg.xDrip_slope_confidence , nightscoutMbg.xDrip_sensor_confidence, nightscoutMbg.xDrip_raw_timestamp);
            lastInserted = nightscoutMbg.date;
        }
    }    
    
    
    private void readBgData(String baseUrl, String key) {
        
        NsRestApiReader nsRestApiReader = new NsRestApiReader();
        Long LastReportedTime = 0L;
        TransmitterData lastTransmitterData = TransmitterData.last();
        if(lastTransmitterData != null) {
            LastReportedTime = lastTransmitterData.timestamp;
        }
        Log.e(TAG, "readBgData  LastReportedTime = " + LastReportedTime);
        
        List<NightscoutBg> nightscoutBgs = nsRestApiReader.readBgDataFromNs(baseUrl,key, LastReportedTime, 12 * 36 );
        if(nightscoutBgs == null) {
            Log.e(TAG, "readBgDataFromNs returned null");
            return;
        }
        Log.e(TAG, "readBgData  finished reading from ns");
        
        ListIterator<NightscoutBg> li = nightscoutBgs.listIterator(nightscoutBgs.size());
        long lastInserted = 0;
        while(li.hasPrevious()) {
            // also load to other table !!!
            NightscoutBg nightscoutBg = li.previous();
            Log.e(TAG, "nightscoutBg " + nightscoutBg.sgv + " " + nightscoutBg.xDrip_raw + " " + mContext);
            if(nightscoutBg.date == lastInserted) {
              Log.w(TAG, "not inserting packet, since it seems duplicate ");
              continue;
            }
            if(nightscoutBg.date < lastInserted) {
              Log.e(TAG, "not inserting packet, since order is wrong. ");
              continue;
            }
            
            verifyViewerNightscoutMode(mContext, nightscoutBg);
            
            TransmitterData.create((int)nightscoutBg.xDrip_raw, 100 /* ??????? */, nightscoutBg.date);
            BgReading.create(mContext, 
                    nightscoutBg.xDrip_raw != 0 ? nightscoutBg.xDrip_raw * 1000 : nightscoutBg.unfiltered,
                    nightscoutBg.xDrip_age_adjusted_raw_value,
                    nightscoutBg.xDrip_raw != 0 ? nightscoutBg.xDrip_filtered * 1000 : nightscoutBg.filtered,
                    nightscoutBg.date, 
                    nightscoutBg.xDrip_calculated_value != 0 ? nightscoutBg.xDrip_calculated_value : nightscoutBg.sgv,
                    nightscoutBg.xDrip_calculated_current_slope,
                    nightscoutBg.xDrip_hide_slope);
            
            lastInserted = nightscoutBg.date;
        }
        
        Log.e(TAG, "readBgData  finished with BgReading.create ");
        if(nightscoutBgs.size() > 0) {
            // Call the notification service only if we have new data...
            mContext.startService(new Intent(mContext, Notifications.class));
        }
    }
    
    static public boolean isxDripViewerMode(Context context) {
        return (context.getPackageName().equals("com.eveningoutpost.dexdrip")) ? false : true;
    }
    
    public static boolean isxDripViewerConfigured(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String recieversIpAddresses = prefs.getString("xdrip_viewer_ns_addresses", "");
        if(recieversIpAddresses == null || recieversIpAddresses.equals("") ||
                recieversIpAddresses.equals(ctx.getString(R.string.xdrip_viewer_ns_example))) {
            return false;
        }
        return true;
    }
    
    // This function checkes if we are looking at a nightscout site that is being uploaded by dexcom.
    // In this case, we will not see sensors, and we need to create one.
    // We identify such a mod by the fact that there are no sensors, and by the fact that on this bg,
    // we see device == dexcom
    // We make sure that all decisions will be cached.
    
    public static boolean IsNightScoutMode(Context context) {
        if (isNightScoutMode != null) {
            Log.e(TAG, "IsNightScoutMode returning " + isNightScoutMode); //???
            return isNightScoutMode;
        }
        if(!isxDripViewerMode(context)) {
            return false;
        }
        Sensor sensor = Sensor.last();
        if(sensor == null) {
            return false;
        }
        isNightScoutMode = new Boolean( sensor.uuid.equals(NIGHTSCOUT_SENSOR_UUID));
        Log.e(TAG, "IsNightScoutMode = " + isNightScoutMode);
        return isNightScoutMode;
    }
    
    private static void verifyViewerNightscoutMode(Context context, NightscoutBg nightscoutBg) {
        verifyViewerNightscoutModeSensor(nightscoutBg.device);
        if(!IsNightScoutMode(context)) {
            return;
        }
        // There are some fields that we might be missing, fix that
        if(nightscoutBg.unfiltered == 0 ) {
            nightscoutBg.unfiltered = nightscoutBg.sgv;
        }
        if(nightscoutBg.filtered == 0 ) {
            nightscoutBg.filtered = nightscoutBg.sgv;
        }
        
        AtomicBoolean hide = new AtomicBoolean();
        nightscoutBg.xDrip_calculated_current_slope = BgReading.slopefromName(nightscoutBg.direction, hide);
        nightscoutBg.xDrip_hide_slope = hide.get();
            
    }
    
    private static void verifyViewerNightscoutMode(Context context,  NightscoutMbg nightscoutMbg ) {
        verifyViewerNightscoutModeSensor(nightscoutMbg.device);
        if(!IsNightScoutMode(context)) {
            return;
        }
        // There are some fields that we might be missing, fix that
        nightscoutMbg.xDrip_sensor_uuid = NIGHTSCOUT_SENSOR_UUID;
            
    }
   
    
    private static void verifyViewerNightscoutModeSensor(String device) {
        if(sensor_exists != null) {
            // We already have a cached sensor, no need to continue.
            return;
        }
        sensor_exists = Sensor.last();
        if(sensor_exists != null) {
            // We already have a sensor, no need to continue.
            return;
        }
        
        if(device == null) {
            return;
        }
        if(!device.equals("dexcom")) {
            return;
        }
        // No sensor exists, uploader is dexcom, let's create one.
        Log.e(TAG, "verifyViewerNightscoutModeSensor creating nightscout sensor");
        Sensor.create(new Date().getTime(), NIGHTSCOUT_SENSOR_UUID);
        isNightScoutMode = new Boolean(true);
    }
    
    
/*
 * curl examples
 * curl -X GET --header "Accept: application/json api-secret: 6aaafe81264eb79d079caa91bbf25dba379ff6e2" "https://snirdar.azurewebsites.net/api/v1/entries/cal?count=122" -k
 * curl -X GET --header "Accept: application/json api-secret: 6aaafe81264eb79d079caa91bbf25dba379ff6e2" "https://snirdar.azurewebsites.net/api/v1/entries.json?find%5Btype%5D%5B%24eq%5D=cal&count=1" -k 
 * 
 * 
 *
 */
}
