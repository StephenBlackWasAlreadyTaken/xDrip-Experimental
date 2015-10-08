package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import ollie.Model;
import ollie.annotation.Column;
import ollie.annotation.Table;
import ollie.query.Select;
import com.eveningoutpost.dexdrip.Models.Calibration;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table("CalibrationSendQueue")
public class CalibrationSendQueue extends Model {

    @Column("calibration")
    public Calibration calibration;

    @Column("success")
    public boolean success;

    @Column("mongo_success")
    public boolean mongo_success;

    public static CalibrationSendQueue nextCalibrationJob() {
        CalibrationSendQueue job = Select
                .from(CalibrationSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID desc")
                .limit(1)
                .fetchSingle();
        return job;
    }

    public static List<CalibrationSendQueue> queue() {
        return Select
                .from(CalibrationSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID asc")
                .execute();
    }
    public static List<CalibrationSendQueue> mongoQueue() {
        return Select
                .from(CalibrationSendQueue.class)
                .where("mongo_success = ?", false)
                .orderBy("_ID desc")
                .limit(20)
                .execute();
    }
    public static void addToQueue(Calibration calibration, Context context) {
        CalibrationSendQueue calibrationSendQueue = new CalibrationSendQueue();
        calibrationSendQueue.calibration = calibration;
        calibrationSendQueue.success = false;
        calibrationSendQueue.mongo_success = false;
        calibrationSendQueue.save();
    }

    public void markMongoSuccess() {
        mongo_success = true;
        save();
    }
}
