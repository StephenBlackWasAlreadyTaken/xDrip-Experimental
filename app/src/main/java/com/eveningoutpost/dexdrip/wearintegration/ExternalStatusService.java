package com.eveningoutpost.dexdrip.wearintegration;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.utils.Preferences;

import java.util.Date;

/**
 * Created by adrian on 14/02/16.
 */
public class ExternalStatusService extends IntentService{
    //constants
    public static final String EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline";
    public static final String ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline";
    public static final String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE";

    public ExternalStatusService() {
        super("ExternalStatusService");
        setIntentRedelivery(true);
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        UserError.Log.e("Adrian", "onHandleIntent");


        if (intent == null)
            return;

        final String action = intent.getAction();

        UserError.Log.e("Adrian", "action = " + action);
        try {

            if (ACTION_NEW_EXTERNAL_STATUSLINE.equals(action)) {
                final String statusline = intent.getStringExtra(EXTRA_STATUSLINE);

                if(statusline != null) {
                    UserError.Log.e("Adrian", "statusline = " + statusline);
                    Intent intent1 = new Intent(this, Home.class);
                    intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent1);
                }
            }
        } finally {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
    }
}
