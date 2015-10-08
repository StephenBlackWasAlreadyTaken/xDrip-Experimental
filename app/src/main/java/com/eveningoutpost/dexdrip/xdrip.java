package com.eveningoutpost.dexdrip;

import android.app.Application;

import com.crashlytics.android.Crashlytics;

import io.fabric.sdk.android.Fabric;
import ollie.Ollie;

/**
 * Created by stephenblack on 3/21/15.
 */

public class xdrip extends Application {
    public static int DB_VERSION = 37;

    @Override
    public void onCreate() {
        super.onCreate();
        Ollie.with(getApplicationContext())
                .setName("DexDrip.db")
                .setVersion(DB_VERSION)
                .setLogLevel(Ollie.LogLevel.NONE)
                .init();


        Fabric.with(this, new Crashlytics());
    }
}
