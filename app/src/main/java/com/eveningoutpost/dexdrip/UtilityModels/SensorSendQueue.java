package com.eveningoutpost.dexdrip.UtilityModels;

import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Sensor;
import com.eveningoutpost.dexdrip.Models.UserError.Log;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table(name = "SensorSendQueue", id = BaseColumns._ID)
public class SensorSendQueue extends Model {

    @Column(name = "Sensor", index = true)
    public Sensor sensor;

    @Column(name = "success", index = true)
    public boolean success;

    
    public static List<SensorSendQueue> mongoQueue(boolean xDripViewerMode) {
        List<SensorSendQueue> values = new Select()
                .from(SensorSendQueue.class)
                .orderBy("_ID desc")
                .limit(xDripViewerMode ? 100 : 0)
                .execute();
        if (xDripViewerMode) {
             java.util.Collections.reverse(values);
        }
        return values;
        
    }

    public static void addToQueue(Sensor sensor) {
        SensorSendQueue sensorSendQueue = new SensorSendQueue();
        sensorSendQueue.sensor = sensor;
        sensorSendQueue.success = false;
        sensorSendQueue.save();
        Log.d("SensorQueue", "New value added to queue!");
    }
    
    public void deleteThis() {
        this.delete();
    }
}
