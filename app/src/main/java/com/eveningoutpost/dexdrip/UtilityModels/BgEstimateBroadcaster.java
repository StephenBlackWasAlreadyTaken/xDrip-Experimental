package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by adrian on 26/07/16.
 */
public class BgEstimateBroadcaster {

    public static final String ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate";
    public static final String EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate";
    public static final String EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope";
    public static final String EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName";
    public static final String EXTRA_SENSOR_BATTERY = "com.eveningoutpost.dexdrip.Extras.SensorBattery";
    public static final String EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time";
    public static final String EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw";


    // Constants for slope names
    public static final String SLOPE_DOUBLE_DOWN = "DoubleDown";
    public static final String SLOPE_SINGLE_DOWN = "SingleDown";
    public static final String SLOPE_FORTYFIVE_DOWN= "FortyFiveDown";
    public static final String SLOPE_FLAT = "Flat";
    public static final String SLOPE_FORTYFIVE_UP  = "FortyFiveUp";
    public static final String SLOPE_SINGLE_UP  = "SingleUp";
    public static final String SLOPE_DOUBLE_UP  = "DoubleUp";
    public static final String SLOPE_NONE = "NONE";
    public static final String SLOPE_NOT_COMPUTABLE = "NOT COMPUTABLE";


    /**
     * Will send a broadcast local to the phone for other apps (NightWatch, HAPP, ...) to receive the current bg data
     *
     * @param bgEstimate The value the CGM would show
     *
     * @param rawEstimate If there exists a notion of "calculated raw value" like Nightscout has, it can be passed. If it does not exist, please pass <code>0d</code>. It should just be used for visualization by the receiving app.
     *
     * @param timestamp The timestamp of this bgEstimate.
     *
     * @param slope The current slope as change in bg per millisecond.
     *              It can be calculated like this:
     *              <code>(last.bgEstimate - current.bgEstimate) / (last.timestamp - current.timestamp)</code>
     *
     * @param slopeName A symbolic representation of the slope.
     *                  Names accepted by receiving apps are: "DoubleDown", "SingleDown", "FortyFiveDown", "Flat", "FortyFiveUp", "SingleUp", "DoubleUp", "NONE" and "NOT COMPUTABLE".
     *                  This class defines String constants for those names.
     *                  On <code>null</code> it will default to "NONE".
     *
     * @param batteryLevel The battery level on a scale from 0 to 100. (Percentages)
     *
     * @param context The current context.
     *
     **/


    public static void broadcastBgEstimate(double bgEstimate, double rawEstimate, long timestamp, double slope, String slopeName, int batteryLevel, Context context) {

        //add data to bundle
        final Bundle bundle = new Bundle();
        bundle.putDouble(EXTRA_BG_ESTIMATE, bgEstimate);
        bundle.putDouble(EXTRA_BG_SLOPE, slope);
        bundle.putString(EXTRA_BG_SLOPE_NAME, slopeName == null ? SLOPE_NONE : slopeName);
        bundle.putInt(EXTRA_SENSOR_BATTERY, batteryLevel);
        bundle.putLong(EXTRA_TIMESTAMP, timestamp);
        bundle.putDouble(EXTRA_RAW, rawEstimate);

        //generate intent with data
        Intent intent = (new Intent(ACTION_NEW_BG_ESTIMATE))
                .putExtras(bundle)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        // send broadcast
        context.sendBroadcast(intent, null);
    }
}
