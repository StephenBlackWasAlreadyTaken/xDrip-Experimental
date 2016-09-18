package com.eveningoutpost.dexdrip.G5Model;

/**
 * Created by joeginley on 3/28/16.
 */
public enum TransmitterStatus {
    UNKNOWN, BRICKED, LOW, OK;

    public static TransmitterStatus getBatteryLevel(int b) {
        if (b == 0x00) {
            return OK;
        }
        else {
            return LOW;
        }

    }
}
