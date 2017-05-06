package com.eveningoutpost.dexdrip.DataPlugins;

import android.support.annotation.Nullable;

/**
 * Created by adrian on 09/12/15.
 */
public interface DataReceiverProposal3 {

    public void onNewCalData(long timestamp, double slope, double intercept, @Nullable Double scale, @Nullable Double raw_filtered, @Nullable Double raw_unfiltered);
    public void onNewMeterReading(long timestamp); //add more parameters
    public void onNewBgData(long timestamp); //add more parameters

    //Add more functions. Every new Datatype the Library can handle would need a new function-stub

}
