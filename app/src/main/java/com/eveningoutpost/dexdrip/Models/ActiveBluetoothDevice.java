package com.eveningoutpost.dexdrip.Models;

import ollie.Model;
import ollie.annotation.Column;
import ollie.annotation.Table;
import ollie.query.Select;

/**
 * Created by stephenblack on 11/3/14.
 */
@Table("ActiveBluetoothDevice")
public class ActiveBluetoothDevice extends Model {
    @Column("name")
    public String name;

    @Column("address")
    public String address;

    @Column("connected")
    public boolean connected;

    public static ActiveBluetoothDevice first() {
        return Select
                .from(ActiveBluetoothDevice.class)
                .orderBy("_ID asc")
                .fetchSingle();
    }

    public static void forget() {
        ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        if (activeBluetoothDevice != null) {
            activeBluetoothDevice.delete();
        }
    }

    public static void connected() {
        ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        if(activeBluetoothDevice != null) {
            activeBluetoothDevice.connected = true;
            activeBluetoothDevice.save();
        }
    }

    public static void disconnected() {
        ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        if(activeBluetoothDevice != null) {
            activeBluetoothDevice.connected = false;
            activeBluetoothDevice.save();
        }
    }

    public static boolean is_connected() {
        ActiveBluetoothDevice activeBluetoothDevice = ActiveBluetoothDevice.first();
        return (activeBluetoothDevice != null && activeBluetoothDevice.connected);
    }

}
