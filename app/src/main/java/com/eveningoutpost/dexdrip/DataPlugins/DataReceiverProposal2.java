package com.eveningoutpost.dexdrip.DataPlugins;

import android.os.Bundle;

/**
 * Created by adrian on 09/12/15.
 */
public interface DataReceiverProposal2 {

    public void onNewBgData(Bundle data);
    public void onNewCalData(Bundle data);
    public void onNewMeterReading(Bundle data);

}
