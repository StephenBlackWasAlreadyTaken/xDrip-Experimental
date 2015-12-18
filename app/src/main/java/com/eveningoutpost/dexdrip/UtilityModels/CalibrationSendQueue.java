package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Models.Calibration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table(name = "CalibrationSendQueue", id = BaseColumns._ID)
public class CalibrationSendQueue extends Model {

    @Column(name = "calibration", index = true)
    public Calibration calibration;

    @Column(name = "success", index = true)
    public boolean success;

    @Column(name = "mongo_success", index = true)
    public boolean mongo_success;

    public static List<CalibrationSendQueue> mongoQueue(boolean nightWatchproMode) {
    	List<CalibrationSendQueue> values =  new Select()
                .from(CalibrationSendQueue.class)
                .where("mongo_success = ?", false)
                .orderBy("_ID desc")
                .limit(4)
                .execute();
        
    	if (!nightWatchproMode) {
    		return values;
    	}
    	// swap the order of objects
    	ArrayList<CalibrationSendQueue> ret = new ArrayList<CalibrationSendQueue>(values.size());	

		int location = values.size() - 1;
		for(CalibrationSendQueue value : values) {
			ret.set(location, value);
			location--;
		}
		return ret;
    }
    public static void addToQueue(Calibration calibration, Context context) {
        CalibrationSendQueue calibrationSendQueue = new CalibrationSendQueue();
        calibrationSendQueue.calibration = calibration;
        calibrationSendQueue.success = false;
        calibrationSendQueue.mongo_success = false;
        calibrationSendQueue.save();
    }
    
    public void deleteThis() {
        this.delete();
    }
}
