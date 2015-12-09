package com.eveningoutpost.dexdrip.DataPlugins;

import android.os.Bundle;

/**
 * Created by adrian on 09/12/15.
 */
public interface DataReceiver {

    public void onNewDataReceived(String type, Bundle data);

}
