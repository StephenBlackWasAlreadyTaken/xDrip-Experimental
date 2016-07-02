package com.eveningoutpost.dexdrip.Models;

import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;

import java.util.Date;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.UtilityModels.AlertPlayer;

/**
 * Created by stephenblack on 11/29/14.
 */

@Table(name = "Notifications", id = BaseColumns._ID)
public class UserNotification extends Model {

    // For 'other alerts' this will be the time that the alert should be raised again.
    // For calibration alerts this is the time that the alert was played.
    @Column(name = "timestamp", index = true)
    public double timestamp;

    @Column(name = "message")
    public String message; 

    @Column(name = "bg_alert")
    public boolean bg_alert;

    @Column(name = "calibration_alert")
    public boolean calibration_alert;

    @Column(name = "double_calibration_alert")
    public boolean double_calibration_alert;

    @Column(name = "extra_calibration_alert")
    public boolean extra_calibration_alert;

    @Column(name = "bg_unclear_readings_alert")
    public boolean bg_unclear_readings_alert;

    @Column(name = "bg_missed_alerts")
    public boolean bg_missed_alerts;

    @Column(name = "bg_rise_alert")
    public boolean bg_rise_alert;

    @Column(name = "bg_fall_alert")
    public boolean bg_fall_alert;
    
    private final static String TAG = AlertPlayer.class.getSimpleName();

    
/*    
    public static UserNotification lastBgAlert() {
        return new Select()
                .from(UserNotification.class)
                .where("bg_alert = ?", true)
                .orderBy("_ID desc")
                .executeSingle();
    }
*/
    
    
    public static UserNotification lastCalibrationAlert() {
        return new Select()
                .from(UserNotification.class)
                .where("calibration_alert = ?", true)
                .orderBy("_ID desc")
                .executeSingle();
    }
    public static UserNotification lastDoubleCalibrationAlert() {
        return new Select()
                .from(UserNotification.class)
                .where("double_calibration_alert = ?", true)
                .orderBy("_ID desc")
                .executeSingle();
    }
    public static UserNotification lastExtraCalibrationAlert() {
        return new Select()
                .from(UserNotification.class)
                .where("extra_calibration_alert = ?", true)
                .orderBy("_ID desc")
                .executeSingle();
    }

    
    public static UserNotification GetNotificationByType(String type) {
        type = type + " = ?";
        return new Select()
        .from(UserNotification.class)
        .where(type, true)
        .orderBy("_ID desc")
        .executeSingle();
    }
    
    public static void DeleteNotificationByType(String type) {
        UserNotification userNotification = UserNotification.GetNotificationByType(type);
        if (userNotification != null) {
            userNotification.delete();
        }
    }
    
    public static void snoozeAlert(String type, long snoozeMinutes) {
        UserNotification userNotification = GetNotificationByType(type);
        if(userNotification == null) {
            Log.e(TAG, "Error snoozeAlert did not find an alert for type " + type);
            return;
        }
        userNotification.timestamp = new Date().getTime() + snoozeMinutes * 60000;
        userNotification.save();
        
    }
    
    public static UserNotification create(String message, String type, long timestamp) {
        UserNotification userNotification = new UserNotification();
        userNotification.timestamp = timestamp; //new Date().getTime();
        userNotification.message = message;
        if (type == "bg_alert") {
            userNotification.bg_alert = true;
        } else if (type == "calibration_alert") {
            userNotification.calibration_alert = true;
        } else if (type == "double_calibration_alert") {
            userNotification.double_calibration_alert = true;
        } else if (type == "extra_calibration_alert") {
            userNotification.extra_calibration_alert = true;
        } else if (type == "bg_unclear_readings_alert") {
            userNotification.bg_unclear_readings_alert = true;
        } else if (type == "bg_missed_alerts") {
            userNotification.bg_missed_alerts = true;
        } else if (type == "bg_rise_alert") {
            userNotification.bg_rise_alert = true;
        } else if (type == "bg_fall_alert") {
            userNotification.bg_fall_alert = true;
        }
        userNotification.save();
        return userNotification;

    }
}
