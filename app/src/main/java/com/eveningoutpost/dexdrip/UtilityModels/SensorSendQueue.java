package com.eveningoutpost.dexdrip.UtilityModels;

import android.provider.BaseColumns;

import ollie.Model;
import ollie.annotation.Column;
import ollie.annotation.Table;
import ollie.query.Select;
import com.eveningoutpost.dexdrip.Sensor;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table("SensorSendQueue")
public class SensorSendQueue extends Model {

    @Column("Sensor")
    public Sensor sensor;

    @Column("success")
    public boolean success;


    public static SensorSendQueue nextSensorJob() {
        SensorSendQueue job = Select
                .from(SensorSendQueue.class)
                .where("success =", false)
                .orderBy("_ID desc")
                .limit(1)
                .fetchSingle();
        return job;
    }

    public static List<SensorSendQueue> queue() {
        return Select
                .from(SensorSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID desc")
                .execute();
    }

    public static void addToQueue(Sensor sensor) {
        SensorSendQueue sensorSendQueue = new SensorSendQueue();
        sensorSendQueue.sensor = sensor;
        sensorSendQueue.success = false;
        sensorSendQueue.save();
    }
}
