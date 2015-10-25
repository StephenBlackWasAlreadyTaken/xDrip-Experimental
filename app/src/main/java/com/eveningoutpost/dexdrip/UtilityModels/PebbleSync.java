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

    public static final int CHUNK_SIZE = 100;


    private Context mContext;
    private BgGraphBuilder bgGraphBuilder;
    private BgReading mBgReading;
    private static int lastTransactionId;
    private static int currentTransactionId;
    private static boolean messageInTransit = false;
    private static boolean transactionFailed = false;
    private static boolean transactionOk = false;
    private static boolean done = false;
    private static boolean sendingData = false;
    private static int current_size = 0;
    private static int image_size =0;
    private static byte [] chunk = new byte[CHUNK_SIZE];
    private static ByteBuffer buff = null;
    private static ByteArrayOutputStream stream = null;
    public static int retries = 0;

    private static short sendStep = 5;
    private PebbleDictionary dictionary = new PebbleDictionary();

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

        Log.i(TAG, "onStartCommand called.  Sending Data");
        transactionFailed = false;
        transactionOk = false;
        sendStep = 5;
        messageInTransit = false;
        done = true;
        sendingData = false;
        sendData();
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();
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
                PebbleKit.sendAckToPebble(context, transactionId);
                if ((lastTransactionId == 0 || transactionId != lastTransactionId) && !sendingData) {
                    lastTransactionId = transactionId;
                    Log.d(TAG, "Received Query. data: " + data.size() + ". sending ACK and data");
                    sendData();
                } else {
                    Log.d(TAG, "receiveData: lastTransactionId is " + String.valueOf(lastTransactionId) + ", sending NACK");
//                    PebbleKit.sendNackToPebble(context, transactionId);
                }
                transactionFailed = false;
                transactionOk = false;
                messageInTransit = false;
                sendingData = false;
                sendStep = 4;
            }
        });

        PebbleKit.registerReceivedAckHandler(mContext, new PebbleKit.PebbleAckReceiver(PEBBLEAPP_UUID) {
            @Override
            public void receiveAck(Context context, int transactionId) {
                Log.i(TAG, "receiveAck: Got an Ack for transactionId " + transactionId);
                currentTransactionId++;
                messageInTransit = false;
                transactionOk = true;
                transactionFailed = false;
                retries = 0;
                if (!done && sendingData) sendData();
            }
        });
        PebbleKit.registerReceivedNackHandler(mContext, new PebbleKit.PebbleNackReceiver(PEBBLEAPP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                Log.i(TAG, "receiveNack: Got an Nack for transactionId " + transactionId + ". Waiting and retrying.");

                if( retries < 3) {
                    transactionFailed = true;
                    transactionOk = false;                    retries++;
                    sendData();
                } else {
                    Log.i(TAG, "recieveNAck: exceeded retries.  Giving Up");
                    transactionFailed = false;
                    transactionOk = false;
                    //dictionary = null;
                    messageInTransit = false;
                    sendStep = 4;
                    retries = 0;
                    done = true;
                }
            }
        });
    }

    public void buildDictionary() {
        //PebbleDictionary dictionary = new PebbleDictionary();
        TimeZone tz = TimeZone.getDefault();
        Date now = new Date();
        int offsetFromUTC = tz.getOffset(now.getTime());
        if(dictionary == null){
            dictionary = new PebbleDictionary();
        }
        if(mBgReading != null) {
            Log.v(TAG, "buildDictionary: slopeOrdinal-" + slopeOrdinal() + " bgReading-" + bgReading() + " now-" + (int) now.getTime() / 1000 + " bgTime-" + (int) (mBgReading.timestamp / 1000) + " phoneTime-" + (int) (new Date().getTime() / 1000) + " bgDelta-" + bgDelta());
            dictionary.addString(ICON_KEY, slopeOrdinal());
            dictionary.addString(BG_KEY, bgReading());
            dictionary.addUint32(RECORD_TIME_KEY, (int) (((mBgReading.timestamp + offsetFromUTC) / 1000)));
        } else {
            Log.v(TAG, "buildDictionary: latest mBgReading is null, so sending default values");
            dictionary.addString(ICON_KEY, slopeOrdinal());
            dictionary.addString(BG_KEY, "?SN");
            dictionary.addUint32(RECORD_TIME_KEY, (int) ((new Date().getTime() + offsetFromUTC / 1000)));
        }
        dictionary.addString(BG_DELTA_KEY, bgDelta());
        dictionary.addUint32(PHONE_TIME_KEY, (int) ((new Date().getTime() + offsetFromUTC) / 1000));
        if(PreferenceManager.getDefaultSharedPreferences(mContext).getString("dex_collection_method", "DexbridgeWixel").compareTo("DexbridgeWixel")==0) {
            dictionary.addString(UPLOADER_BATTERY_KEY, bridgeBatteryString());
            dictionary.addString(NAME_KEY, "Bridge");
        } else {
            dictionary.addString(UPLOADER_BATTERY_KEY, phoneBattery());
            dictionary.addString(NAME_KEY, "Phone");
        }
        //return dictionary;
    }

    public void sendTrendToPebble () {
/*        if (!PebbleKit.isWatchConnected(mContext)){
            return;
        } */
        //PebbleDictionary dictionary = new PebbleDictionary();
        //create a sparkline bitmap to send to the pebble
        Log.i(TAG, "sendTrendToPebble called: sendStep= " + sendStep + ", messageInTransit= " + messageInTransit + ", transactionFailed= " + transactionFailed + ", sendStep= " + sendStep);
        if(!done && (sendStep == 1 && ((!messageInTransit && !transactionOk && !transactionFailed) || (messageInTransit && !transactionOk && transactionFailed)))) {
            if(!messageInTransit && !transactionOk && !transactionFailed) {
                Bitmap bgTrend = new BgSparklineBuilder(mContext)
                        .setHeightPx(82)
                        .setWidthPx(142)
                        .setStart(System.currentTimeMillis() - 60000 * 60 * 3)
                        .setBgGraphBuilder(bgGraphBuilder)
                        .build();
                //create a ByteArrayOutputStream
                stream = new ByteArrayOutputStream();

                //compress the bitmap into a PNG.  This makes the transfer smaller
                bgTrend.compress(Bitmap.CompressFormat.PNG, 100, stream);
                image_size = stream.size();
                buff = ByteBuffer.wrap(stream.toByteArray());
                //Prepare the TREND_BEGIN_KEY dictionary.  We expect the length of the image to always be less than 65535 bytes.
                if(buff != null) {
                    if (dictionary == null) {
                        dictionary = new PebbleDictionary();
                    }
                    dictionary.addInt16(TREND_BEGIN_KEY, (short) image_size);
                    Log.d(TAG, "sendTrendToPebble: Sending TREND_BEGIN_KEY to pebble, image size is " + image_size);
                } else {
                    Log.d(TAG, "sendTrendToPebble: Error converting stream to ByteBuffer, buff is null.");
                    sendStep = 4;
                    return;
                }
            }
            transactionFailed = false;
            transactionOk=false;
            messageInTransit = true;
            //PebbleKit.sendDataToPebbleWithTransactionId(mContext, PEBBLEAPP_UUID, dictionary, currentTransactionId);
            PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        }
        if(sendStep == 1 && !done && !messageInTransit && transactionOk && !transactionFailed){
            Log.i(TAG, "sendTrendToPebble: sendStep "+ sendStep + " complete.");
            dictionary.remove(TREND_BEGIN_KEY);
            current_size = 0;
            sendStep = 2;
            transactionOk = false;
        }
        if(!done && ((sendStep ==  2 && !messageInTransit ) || sendStep ==3 && transactionFailed)){
            if( !transactionFailed && !messageInTransit){
                // send image chunks to Pebble.
                Log.d(TAG, "sendTrendToPebble: current_size is " + current_size + ", image_size is " + image_size);
                if(current_size < image_size) {
                    dictionary.remove(TREND_DATA_KEY);
                    if ((image_size <= (current_size + CHUNK_SIZE))) {
                        Log.d(TAG, "sendTrendToPebble: sending chunk of size " + (image_size - current_size));
                        buff.get(chunk, 0, image_size - current_size);
                        sendStep = 3;
                    } else {
                        Log.d(TAG, "sendTrendToPebble: sending chunk of size " + CHUNK_SIZE);
                        buff.get(chunk, 0, CHUNK_SIZE);
                        current_size += CHUNK_SIZE;
                    }
                    dictionary.addBytes(TREND_DATA_KEY, chunk);
                }
            }
            Log.d(TAG, "sendTrendToPebble: Sending TREND_DATA_KEY to pebble, current_size is " + current_size);
            //PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
            transactionFailed = false;
            transactionOk = false;
            messageInTransit = true;
            //PebbleKit.sendDataToPebbleWithTransactionId(mContext, PEBBLEAPP_UUID, dictionary, currentTransactionId);
            PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        }
        if(sendStep == 3 && !done && !messageInTransit && transactionOk && !transactionFailed){
            Log.i(TAG, "sendTrendToPebble: sendStep "+ sendStep + " complete.");
            dictionary.remove(TREND_DATA_KEY);
            sendStep = 4;
            transactionOk = false;
            buff = null;
            stream = null;
        }
        if(!done && (sendStep == 4  && ((!messageInTransit && !transactionOk && !transactionFailed) || (messageInTransit && !transactionOk && transactionFailed)))) {
            if(!transactionFailed) {
                // prepare the TREND_END_KEY dictionary and send it.
                dictionary.addUint8(TREND_END_KEY, (byte) 0);
                Log.d(TAG, "sendTrendToPebble: Sending TREND_END_KEY to pebble.");
                //PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
            }
            transactionFailed = false;
            transactionOk = false;
            messageInTransit = true;
            //PebbleKit.sendDataToPebbleWithTransactionId(mContext, PEBBLEAPP_UUID, dictionary, currentTransactionId);
            PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        }
        if(sendStep == 4 && !done && transactionOk && !messageInTransit && !transactionFailed){
            Log.i(TAG, "sendTrendToPebble: sendStep "+ sendStep + " complete.");
            dictionary.remove(TREND_END_KEY);
            sendStep = 5;
            transactionFailed = false;
            transactionOk = false;
            done=true;
            current_size = 0;
            buff = null;
        }
    }

    public String bridgeBatteryString() {
        return String.format("%d", PreferenceManager.getDefaultSharedPreferences(mContext).getInt("bridge_battery", 0));
    }

    /* returns ByteBuffer
    public ByteBuffer createTrendBitmap () {
        Bitmap bgTrend = new BgSparklineBuilder(mContext)
                .setHeightPx(84)
                .setWidthPx(144)
                .setStart(System.currentTimeMillis() - 60000 * 60 * 3)
                .setBgGraphBuilder(bgGraphBuilder)
                .build();
        //create a ByteArrayOutputStream
        stream = new ByteArrayOutputStream();

        //compress the bitmap into a PNG.  This makes the transfer smaller
        bgTrend.compress(Bitmap.CompressFormat.PNG, 100, stream);
        image_size = stream.size();
        buff = ByteBuffer.wrap(stream.toByteArray());
        return buff;
    }*/

    public void sendData(){
//         if(mBgReading != null && PebbleKit.isWatchConnected(mContext)) {
         if (PebbleKit.isWatchConnected(mContext)) {
             if(sendStep == 5) {
                 sendStep = 0;
                 done=false;
             }
             Log.i(TAG, "sendData: messageInTransit= " + messageInTransit + ", transactionFailed= " + transactionFailed + ", sendStep= " + sendStep);
             if (sendStep == 0 && !messageInTransit && !transactionOk && !transactionFailed) {
                 mBgReading = BgReading.last();
                 sendingData = true;
                 buildDictionary();
                 sendDownload();
             }
             if (sendStep == 0 && !messageInTransit && transactionOk && !transactionFailed) {
                 sendStep = 1;
                 Log.i(TAG, "sendData: sendStep 0 complete, clearing dictionary");
                 dictionary.remove(ICON_KEY);
                 dictionary.remove(BG_KEY);
                 dictionary.remove(NAME_KEY);
                 dictionary.remove(BG_DELTA_KEY);
                 dictionary.remove(PHONE_TIME_KEY);
                 dictionary.remove(RECORD_TIME_KEY);
                 dictionary.remove(UPLOADER_BATTERY_KEY);
                 transactionOk = false;
             }
             /*if (sendStep > 0 && sendStep < 5) {
                    sendTrendToPebble();
             }
             if(sendStep == 5) {
             //*/if(sendStep == 1  || sendStep == 5) {
                 sendStep = 5;
                 Log.i(TAG, "sendData: finished sending.  sendStep = " +sendStep);
                 done = true;
                 transactionFailed = false;
                 transactionOk = false;
                 messageInTransit = false;
                 sendingData = false;
             }
         }
            /*if(sendStep == 0 && messageInTransit == false && transactionFailed == false){
                done=true;
            }*/
        }
        //sendTrendToPebble();

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

    //public void sendDownload(PebbleDictionary dictionary) {
    public void sendDownload() {
        //if (PebbleKit.isWatchConnected(mContext)) {
            if (dictionary != null && mContext != null) {
                Log.d(TAG, "sendDownload: Sending data to pebble");
                messageInTransit = true;
                transactionFailed = false;
                transactionOk = false;
                //PebbleKit.sendDataToPebbleWithTransactionId(mContext, PEBBLEAPP_UUID, dictionary, currentTransactionId);
                PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
            }
        //}
    }

    public int getBatteryLevel() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) { return 50; }
        return (int)(((float)level / (float)scale) * 100.0f);
    }

    public String slopeOrdinal(){
        if(mBgReading == null) return "0";
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

