package com.eveningoutpost.dexdrip.Services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.ReadDataShare;
import com.eveningoutpost.dexdrip.Models.AlertType;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.UserNotification;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.AlertPlayer;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.ForegroundServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Notifications;

import java.util.Calendar;
import java.util.Date;

public class MissedReadingService extends IntentService {
    int otherAlertSnooze;
    private final static String TAG = MissedReadingService.class.getSimpleName();

    public MissedReadingService() {
        super("MissedReadingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefs;
        boolean bg_missed_alerts;
        Context context;
        int bg_missed_minutes;
        
        
        context = getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        bg_missed_alerts =  prefs.getBoolean("bg_missed_alerts", false);
        bg_missed_minutes =  readPerfsInt(prefs, "bg_missed_minutes", 30);
        otherAlertSnooze =  readPerfsInt(prefs, "other_alerts_snooze", 20);
        long now = new Date().getTime();
        Log.d(TAG, "MissedReadingService onHandleIntent");
        if (!bg_missed_alerts) {
        	// we should not do anything in this case. if the ui, changes will be called again
        	return;
        }

        if (BgReading.getTimeSinceLastReading() >= (bg_missed_minutes * 1000 * 60) &&
                prefs.getLong("alerts_disabled_until", 0) <= now &&
                inTimeFrame(prefs)) {
            Notifications.bgMissedAlert(context);
            checkBackAfterSnoozeTime( prefs, now);
        } else  {
            
            long disabletime = prefs.getLong("alerts_disabled_until", 0) - now;
            
            long missedTime = bg_missed_minutes* 1000 * 60 - BgReading.getTimeSinceLastReading();
            long alarmIn = Math.max(disabletime, missedTime);
            checkBackAfterMissedTime(alarmIn);
        }
    }
    
    private boolean inTimeFrame(SharedPreferences prefs) {
        
        int startMinutes = prefs.getInt("missed_readings_start", 0);
        int endMinutes = prefs.getInt("missed_readings_end", 0);
        boolean allDay = prefs.getBoolean("missed_readings_all_day", true);

        return AlertType.s_in_time_frame(allDay, startMinutes, endMinutes);
    }

    public void checkBackAfterSnoozeTime( SharedPreferences prefs, long now) {
    	// This is not 100% acurate, need to take in account also the time of when this alert was snoozed.
        UserNotification userNotification = UserNotification.GetNotificationByType("bg_missed_alerts");
        if(userNotification == null) {
            // No active alert exists
            setAlarm(otherAlertSnooze * 1000 * 60, false);
/*            
        	// The alert exists and it is not snoozed. We should alert again soon.
            int otherAlertSnooze = MissedReadingService.readPerfsInt(prefs, "other_alerts_snooze", 20);
            
            double unclearReadingReraiseTime;
            boolean disableAlertsReraise = prefs.getBoolean("disable_alerts_reraise", false);
            if(disableAlertsReraise) {
                unclearReadingReraiseTime = (double)(MissedReadingService.readPerfsInt(prefs, "other_alerts_reraise_sec", 60)) / 60.0;
            } else {
                unclearReadingReraiseTime = otherAlertSnooze;
            }
            
        	setAlarm((int) (unclearReadingReraiseTime * 60000), true);
*/
        } else {
            // we have an alert that is snoozed until userNotification.timestamp
            setAlarm((long)userNotification.timestamp - now , true);
        }
    }

    public void checkBackAfterMissedTime(long alarmIn) {
        setAlarm(alarmIn, false);
    }

    // alarmIn is relative time
    public void setAlarm(long alarmIn, boolean force) {
        if(!force && (alarmIn < 5 * 60 * 1000)) {
            // No need to check more than once every 5 minutes
            alarmIn = 5 * 60 * 1000;
        }
    	Log.d(TAG, "Setting timer to  " + alarmIn / 60000 + " minutes from now" );
        Calendar calendar = Calendar.getInstance();
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        long wakeTime = calendar.getTimeInMillis() + alarmIn;
        PendingIntent serviceIntent = PendingIntent.getService(this, 0, new Intent(this, this.getClass()), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);
        } else
            alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);
    }
    
    static public int readPerfsInt(SharedPreferences prefs, String name, int defaultValue) {
        try {
            return Integer.parseInt(prefs.getString(name, "" + defaultValue));
             
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    static public long getOtherAlertReraiseSec(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean disableAlertsReraise = prefs.getBoolean("disable_alerts_reraise", false);
        if(disableAlertsReraise) {
            return readPerfsInt(prefs, "other_alerts_reraise_sec", 60);
        } else {
            return 60 * readPerfsInt(prefs, "other_alerts_snooze", 20);
        }

    }
}
