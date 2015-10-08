package com.eveningoutpost.dexdrip;

import android.provider.BaseColumns;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import android.preference.ListPreference;

import ollie.Model;
import ollie.annotation.Column;
import ollie.annotation.Table;
import ollie.query.Select;
import com.eveningoutpost.dexdrip.UtilityModels.SensorSendQueue;

import java.util.UUID;

/**
 * Created by stephenblack on 10/29/14.
 */

@Table("Sensors")
public class Sensor extends Model {

//    @Expose
    @Column("started_at")
    public long started_at;

//    @Expose
    @Column("stopped_at")
    public long stopped_at;

//    @Expose
    //latest minimal battery level
    @Column("latest_battery_level")
    public int latest_battery_level;

//    @Expose
    @Column("uuid")
    public String uuid;

//  @Expose
  @Column("sensor_location")
  public String sensor_location;

    public static Sensor create(long started_at) {
        Sensor sensor = new Sensor();
        sensor.started_at = started_at;
        sensor.uuid = UUID.randomUUID().toString();

        sensor.save();
        SensorSendQueue.addToQueue(sensor);
        Log.d("SENSOR MODEL:", sensor.toString());
        return sensor;
    }

    public static Sensor currentSensor() {
        Sensor sensor = Select
                .from(Sensor.class)
                .where("started_at != 0")
                .where("stopped_at = 0")
                .orderBy("_ID desc")
                .limit(1)
                .fetchSingle();
        return sensor;
    }

    public static boolean isActive() {
        Sensor sensor = Select
                .from(Sensor.class)
                .where("started_at != 0")
                .where("stopped_at = 0")
                .orderBy("_ID desc")
                .limit(1)
                .fetchSingle();
        if(sensor == null) {
            return false;
        } else {
            return true;
        }
    }

    public static void updateSensorLocation(String sensor_location) {
        Sensor sensor = currentSensor();
        if (sensor == null) {
            Log.e("SENSOR MODEL:", "updateSensorLocation called but sensor is null");
            return;
        }
        sensor.sensor_location = sensor_location;
        sensor.save();
    }
}

