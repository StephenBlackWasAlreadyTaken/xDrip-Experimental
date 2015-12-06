package com.eveningoutpost.dexdrip;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.activeandroid.Cache;
import com.crashlytics.android.Crashlytics;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.IdempotentMigrations;

import io.fabric.sdk.android.Fabric;

/**
 * Created by stephenblack on 3/21/15.
 */

public class xdrip extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        CollectionServiceStarter collectionServiceStarter = new CollectionServiceStarter(this);
        collectionServiceStarter.start(this);
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_notifications, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_source, false);
        new IdempotentMigrations(this).performAll();

        //create index
        SQLiteDatabase db = Cache.openDatabase();
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS bgtimestampindex ON bgreadings(timestamp)");
    }
}
