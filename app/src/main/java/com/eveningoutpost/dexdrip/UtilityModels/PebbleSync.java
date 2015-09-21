package com.eveningoutpost.dexdrip.UtilityModels;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.bson.ByteBuf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by THE NIGHTSCOUT PROJECT CONTRIBUTORS (and adapted to fit the needs of this project)
 */
public class PebbleSync extends Service {
    private final static String TAG = PebbleSync.class.getSimpleName();
    public static final UUID PEBBLEAPP_UUID = UUID.fromString("79f8ecb3-7214-4bfc-b996-cb95148ee6d3");
    public static final int ICON_KEY = 0;
    public static final int BG_KEY = 1;
    public static final int RECORD_TIME_KEY = 2;
    public static final int PHONE_TIME_KEY = 3;
    public static final int BG_DELTA_KEY = 4;
    public static final int UPLOADER_BATTERY_KEY = 5;
    public static final int NAME_KEY = 6;
    public static final int TREND_BEGIN_KEY = 7;
    public static final int TREND_DATA_KEY = 8;
    public static final int TREND_END_KEY = 9;


    private Context mContext;
    private BgGraphBuilder bgGraphBuilder;
    private BgReading mBgReading;
    private static int lastTransactionId;
    BroadcastReceiver newSavedBgReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        bgGraphBuilder = new BgGraphBuilder(mContext);
        mBgReading = BgReading.last();
        init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("broadcast_to_pebble", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Log.i(TAG, "STARTING SERVICE");
        sendData();
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();
        if(newSavedBgReceiver != null) {
            unregisterReceiver(newSavedBgReceiver);
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void init() {
        Log.i(TAG, "Initialising...");
        Log.i(TAG, "configuring PebbleDataReceiver");

        PebbleKit.registerReceivedDataHandler(mContext, new PebbleKit.PebbleDataReceiver(PEBBLEAPP_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                Log.d(TAG, "receiveData: transactionId is " + String.valueOf(transactionId));
                if (lastTransactionId == 0 || transactionId != lastTransactionId) {
                    lastTransactionId = transactionId;
                    Log.d(TAG, "Received Query. data: " + data.size() + ". sending ACK and data");
                    PebbleKit.sendAckToPebble(context, transactionId);
                    sendData();
                } else {
                    Log.d(TAG, "receiveData: lastTransactionId is "+ String.valueOf(lastTransactionId)+ ", sending NACK");
                    PebbleKit.sendNackToPebble(context,transactionId);
                }
            }
        });
    }

    public PebbleDictionary buildDictionary() {
        PebbleDictionary dictionary = new PebbleDictionary();
        TimeZone tz = TimeZone.getDefault();
        Date now = new Date();
        int offsetFromUTC = tz.getOffset(now.getTime());
        Log.v(TAG, "buildDictionary: slopeOrdinal-" + slopeOrdinal() + " bgReading-" + bgReading() + " now-"+ (int) now.getTime()/1000 + " bgTime-" + (int) (mBgReading.timestamp / 1000) + " phoneTime-" + (int) (new Date().getTime() / 1000) + " bgDelta-" + bgDelta());
        dictionary.addString(ICON_KEY, slopeOrdinal());
        dictionary.addString(BG_KEY, bgReading());
        dictionary.addUint32(RECORD_TIME_KEY, (int) (((mBgReading.timestamp + offsetFromUTC) / 1000)));
        dictionary.addUint32(PHONE_TIME_KEY, (int) ((new Date().getTime() + offsetFromUTC) / 1000));
        dictionary.addString(BG_DELTA_KEY, bgDelta());
        if(PreferenceManager.getDefaultSharedPreferences(mContext).getString("dex_collection_method", "DexbridgeWixel").compareTo("DexbridgeWixel")==0) {
            dictionary.addString(UPLOADER_BATTERY_KEY, bridgeBatteryString());
            dictionary.addString(NAME_KEY, "Bridge");
        } else {
            dictionary.addString(UPLOADER_BATTERY_KEY, phoneBattery());
            dictionary.addString(NAME_KEY, "Phone");
        }
        return dictionary;
    }
    public void sendTrendToPebble () {
        int current_size = 0;
        int image_size =0;
        byte [] chunk = new byte[1024];
        if (!PebbleKit.isWatchConnected(mContext)){
            return;
        }
        PebbleDictionary dictionary = new PebbleDictionary();
        //create a sparkline bitmap to send to the pebble
        Bitmap bgTrend = new BgSparklineBuilder(mContext)
                .setHeightPx(84)
                .setWidthPx(144)
                .setStart(System.currentTimeMillis() - 60000 * 60 * 3)
                .setBgGraphBuilder(bgGraphBuilder)
                .build();
        //create a ByteArrayOutputStream
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        //compress the bitmap into a PNG.  This makes the transfer smaller
        bgTrend.compress(Bitmap.CompressFormat.PNG, 100, stream);
        image_size = stream.size();
        ByteBuffer buff=ByteBuffer.wrap(stream.toByteArray());
        //Prepare the TREND_BEGIN_KEY dictionary.  We expect the length of the image to always be less than 65535 bytes.
        dictionary.addInt16(TREND_BEGIN_KEY, (short) image_size);
        Log.d(TAG, "sendTrendToPebble: Sending TREND_BEGIN_KEY to pebble, image size is " + bgTrend.getByteCount());
        PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        dictionary.remove(TREND_BEGIN_KEY);
        // send image chunks to Pebble.
        while (current_size > image_size){
            for(int i = 0; i < image_size; i=+1024) {
                if((image_size-i)<0) {
                    buff.get(chunk, i, image_size - 1024);
                } else {
                    buff.get(chunk, i, 1024);
                }
                dictionary.addBytes(TREND_DATA_KEY, chunk);
                PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
                dictionary.remove(TREND_DATA_KEY);
            }
        }

        // prepare the TREND_END_KEY dictionary and send it.
        dictionary.addUint8(TREND_END_KEY, (byte) 0);
        Log.d(TAG, "sendTrendToPebble: Sending TREND_END_KEY to pebble.");
        PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        dictionary.remove(TREND_END_KEY);
    }

    public String bridgeBatteryString() {
        return String.format("%d", PreferenceManager.getDefaultSharedPreferences(mContext).getInt("bridge_battery", 0));
    }

    public void sendData(){
        mBgReading = BgReading.last();
        if(mBgReading != null && PebbleKit.isWatchConnected(mContext)) {
            sendDownload(buildDictionary());
        }
        sendTrendToPebble();
    }

    public String bgReading() {
        return bgGraphBuilder.unitized_string(mBgReading.calculated_value);
    }

    public String bgDelta() {
        return new BgGraphBuilder(mContext).unitizedDeltaString(true, true);
    }

    public String phoneBattery() {
        return String.valueOf(getBatteryLevel());
    }

    public String bgUnit() {
        return bgGraphBuilder.unit();
    }

    public void sendDownload(PebbleDictionary dictionary) {
        if (PebbleKit.isWatchConnected(mContext)) {
            if (dictionary != null && mContext != null) {
                Log.d(TAG, "sendDownload: Sending data to pebble");
                PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
            }
        }
    }

    public int getBatteryLevel() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) { return 50; }
        return (int)(((float)level / (float)scale) * 100.0f);
    }

    public String slopeOrdinal(){
        String arrow_name = mBgReading.slopeName();
        if(arrow_name.compareTo("DoubleDown")==0) return "7";
        if(arrow_name.compareTo("SingleDown")==0) return "6";
        if(arrow_name.compareTo("FortyFiveDown")==0) return "5";
        if(arrow_name.compareTo("Flat")==0) return "4";
        if(arrow_name.compareTo("FortyFiveUp")==0) return "3";
        if(arrow_name.compareTo("SingleUp")==0) return "2";
        if(arrow_name.compareTo("DoubleUp")==0) return "1";
        if(arrow_name.compareTo("9")==0) return arrow_name;
        return "0";
    }
}

