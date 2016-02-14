package com.eveningoutpost.dexdrip.wearintegration;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * Created by adrian on 14/02/16.
 */
public class ExternalStatusBroadcastReceier extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        throw new RuntimeException();

        /*

        Intent service = new Intent(context, ExternalStatusService.class);
        service.setAction(ExternalStatusService.ACTION_NEW_EXTERNAL_STATUSLINE);
        service.putExtras(intent);
        startWakefulService(context, service);

        */
    }
}
