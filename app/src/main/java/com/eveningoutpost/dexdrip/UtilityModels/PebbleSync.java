package com.eveningoutpost.dexdrip.UtilityModels;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
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
    public static final int MESSAGE_KEY = 10;
    public static final int VIBE_KEY = 11;
    public static final int SYNC_KEY = 1000;
    public static final int PLATFORM_KEY = 1001;
    public static final int VERSION_KEY = 1002;

    public static final int CHUNK_SIZE = 100;


    private Context mContext;
    private BgGraphBuilder bgGraphBuilder;
    private BgReading mBgReading;
    private static int lastTransactionId=0;
    private static int currentTransactionId=0;
    private static boolean messageInTransit = false;
    private static boolean transactionFailed = false;
    private static boolean transactionOk = false;
    private static boolean done = false;
    private static boolean sendingData = false;
    private static int current_size = 0;
    private static int image_size =0;
    private static byte [] chunk;
    private static ByteBuffer buff = null;
    private static ByteArrayOutputStream stream = null;
    public static int retries = 0;
    private boolean no_signal = false;
    private static long pebble_platform = 0;
    private static String pebble_app_version = "";
    private static long pebble_sync_value = 0;

    private static short sendStep = 5;
    private PebbleDictionary dictionary = new PebbleDictionary();
//    private AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
    private static boolean sentInitialSync = false;


    // local statics for Pebble side load from app.
    private static final String WATCHAPP_FILENAME = "xDrip-pebble2.pbw";


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
        bgGraphBuilder = new BgGraphBuilder(mContext);

        Log.i(TAG, "onStartCommand called.  Sending Sync Request");
        transactionFailed = false;
        transactionOk = false;
        sendStep = 5;
        messageInTransit = false;
        done = true;
        sendingData = false;
        dictionary.addInt32(SYNC_KEY, 0);
        PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
        dictionary.remove(SYNC_KEY);
        /*if(pebble_app_version.isEmpty() && !sentInitialSync){
            setResponseTImer();
        }*/
        if(pebble_app_version.isEmpty() && sentInitialSync){
            Log.d(TAG, "onStartCommand: No Response and no pebble_app_version.  Sideloading...");
        }
        Log.d(TAG, "onStart: Pebble App Version not known.  Sending Version Request");
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
                lastTransactionId = transactionId;
                Log.d(TAG, "Received Query. data: " + data.size() + ".");
                if(data.size() >0){
                    pebble_sync_value = data.getUnsignedIntegerAsLong(SYNC_KEY);
                    pebble_platform = data.getUnsignedIntegerAsLong(PLATFORM_KEY);
                    pebble_app_version = data.getString(VERSION_KEY);
                    Log.d(TAG,"receiveData: pebble_sync_value="+pebble_sync_value+", pebble_platform="+pebble_platform+", pebble_app_version="+pebble_app_version);
                } else {
                    Log.d(TAG,"receiveData: pebble_app_version not known");
                }
                PebbleKit.sendAckToPebble(context, transactionId);
                transactionFailed = false;
                transactionOk = false;
                messageInTransit = false;
                sendStep = 5;
                /*if (pebble_app_version.isEmpty()) {
                    Log.i(TAG,"receiveData: Side Loading Pebble App");
                    //sideloadInstall(mContext, WATCHAPP_FILENAME);
                }*/
                sendData();
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

                if (retries < 3) {
                    transactionFailed = true;
                    transactionOk = false;
                    messageInTransit = false;
                    retries++;
                    sendData();
                } else {
                    Log.i(TAG, "recieveNAck: exceeded retries.  Giving Up");
                    transactionFailed = false;
                    transactionOk = false;
                    messageInTransit = false;
                    sendStep = 4;
                    retries = 0;
                    done = true;
                }
            }
        });
    }

    public void buildDictionary() {
        TimeZone tz = TimeZone.getDefault();
        Date now = new Date();
        int offsetFromUTC = tz.getOffset(now.getTime());
        if(dictionary == null){
            dictionary = new PebbleDictionary();
        }
        if(mBgReading != null) {
            Log.v(TAG, "buildDictionary: slopeOrdinal-" + slopeOrdinal() + " bgReading-" + bgReading() + " now-" + (int) now.getTime() / 1000 + " bgTime-" + (int) (mBgReading.timestamp / 1000) + " phoneTime-" + (int) (new Date().getTime() / 1000) + " bgDelta-" + bgDelta());
            no_signal = ((new Date().getTime()) - (60000 * 11) - mBgReading.timestamp >0);
            if(!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_show_arrows", false)) {
                dictionary.addString(ICON_KEY, "0");
            } else {
                dictionary.addString(ICON_KEY, slopeOrdinal());
            }
            if(no_signal){
                dictionary.addString(BG_KEY, "?RF");
                dictionary.addInt8(VIBE_KEY, (byte) 0x01);
            } else {
                dictionary.addString(BG_KEY, bgReading());
                dictionary.addInt8(VIBE_KEY, (byte) 0x00);
            }
            dictionary.addUint32(RECORD_TIME_KEY, (int) (((mBgReading.timestamp + offsetFromUTC) / 1000)));
            if(PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_show_delta", false)) {
                if (no_signal) {
                    dictionary.addString(BG_DELTA_KEY, "No Signal");
                } else {
                   dictionary.addString(BG_DELTA_KEY, bgDelta());
                }
            } else {
                dictionary.addString(BG_DELTA_KEY, "");
            }
            String msg = PreferenceManager.getDefaultSharedPreferences(mContext).getString("pebble_special_value","");
            if(bgReading().compareTo(msg)==0) {
                dictionary.addString(MESSAGE_KEY, PreferenceManager.getDefaultSharedPreferences(mContext).getString("pebble_special_text", "BAZINGA!"));
            }else {
                dictionary.addString(MESSAGE_KEY, "");
            }
        } else {
            Log.v(TAG, "buildDictionary: latest mBgReading is null, so sending default values");
            dictionary.addString(ICON_KEY, slopeOrdinal());
            dictionary.addString(BG_KEY, "?SN");
            //dictionary.addString(BG_KEY, bgReading());
            dictionary.addUint32(RECORD_TIME_KEY, (int) ((new Date().getTime() + offsetFromUTC / 1000)));
            dictionary.addString(BG_DELTA_KEY, "No Sensor");
            dictionary.addString(MESSAGE_KEY, "");
        }
        dictionary.addUint32(PHONE_TIME_KEY, (int) ((new Date().getTime() + offsetFromUTC) / 1000));
        if(PreferenceManager.getDefaultSharedPreferences(mContext).getString("dex_collection_method", "DexbridgeWixel").compareTo("DexbridgeWixel")==0 &&
                PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("display_bridge_battery", true)) {
            dictionary.addString(UPLOADER_BATTERY_KEY, bridgeBatteryString());
            dictionary.addString(NAME_KEY, "Bridge");
        } else {
            dictionary.addString(UPLOADER_BATTERY_KEY, phoneBattery());
            dictionary.addString(NAME_KEY, "Phone");
        }
    }

    public void sendTrendToPebble () {
        //create a sparkline bitmap to send to the pebble
        Log.i(TAG, "sendTrendToPebble called: sendStep= " + sendStep + ", messageInTransit= " + messageInTransit + ", transactionFailed= " + transactionFailed + ", sendStep= " + sendStep);
        if(!done && (sendStep == 1 && ((!messageInTransit && !transactionOk && !transactionFailed) || (messageInTransit && !transactionOk && transactionFailed)))) {
            if(!messageInTransit && !transactionOk && !transactionFailed) {
                if(!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_display_trend",false)){
                    sendStep = 5;
                    transactionFailed = false;
                    transactionOk = false;
                    done=true;
                    current_size = 0;
                    buff = null;

                }
                boolean highLine = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_high_line", false);
                boolean lowLine = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_low_line", false);
                String trendPeriodString = PreferenceManager.getDefaultSharedPreferences(mContext).getString("pebble_trend_period", "3");
                Integer trendPeriod = Integer.parseInt(trendPeriodString);
                Log.d(TAG,"sendTrendToPebble: highLine is " + highLine + ", lowLine is "+ lowLine +",trendPeriod is "+ trendPeriod);
                Bitmap bgTrend = new BgSparklineBuilder(mContext)
                        .setBgGraphBuilder(bgGraphBuilder)
                        .setStart(System.currentTimeMillis() - 60000 * 60 * trendPeriod)
                        .setEnd(System.currentTimeMillis())
                        .setHeightPx(84)
                        .setWidthPx(144)
                        .showHighLine(highLine)
                        .showLowLine(lowLine)
                        .setTinyDots()
                        .setSmallDots()
                        .build();
                //encode the trend bitmap as a PNG
                int depth = 16;
                if(pebble_platform == 0) {
                    Log.d(TAG,"sendTrendToPebble: Encoding trend as Monochrome.");
                    depth = 2;
                }
                byte[] img = SimpleImageEncoder.encodeBitmapAsPNG(bgTrend, true, depth, true);
                image_size = img.length;
                buff = ByteBuffer.wrap(img);
                bgTrend.recycle();
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
                        chunk = new byte[image_size - current_size];
                        Log.d(TAG, "sendTrendToPebble: sending chunk of size " + (image_size - current_size));
                        buff.get(chunk, 0, image_size - current_size);
                        sendStep = 3;
                    } else {
                        chunk = new byte[CHUNK_SIZE];
                        Log.d(TAG, "sendTrendToPebble: sending chunk of size " + CHUNK_SIZE);
                        buff.get(chunk, 0, CHUNK_SIZE);
                        current_size += CHUNK_SIZE;
                    }
                    dictionary.addBytes(TREND_DATA_KEY, chunk);
                }
            }
            Log.d(TAG, "sendTrendToPebble: Sending TREND_DATA_KEY to pebble, current_size is " + current_size);
            transactionFailed = false;
            transactionOk = false;
            messageInTransit = true;
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
            }
            transactionFailed = false;
            transactionOk = false;
            messageInTransit = true;
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

    public void sendData(){
         if (PebbleKit.isWatchConnected(mContext)) {
             if(sendStep == 5) {
                 sendStep = 0;
                 done=false;
                 dictionary.remove(ICON_KEY);
                 dictionary.remove(BG_KEY);
                 dictionary.remove(NAME_KEY);
                 dictionary.remove(BG_DELTA_KEY);
                 dictionary.remove(PHONE_TIME_KEY);
                 dictionary.remove(RECORD_TIME_KEY);
                 dictionary.remove(UPLOADER_BATTERY_KEY);
             }
             Log.i(TAG, "sendData: messageInTransit= " + messageInTransit + ", transactionFailed= " + transactionFailed + ", sendStep= " + sendStep);
             if (sendStep == 0 && !messageInTransit && !transactionOk && !transactionFailed) {
                 mBgReading = BgReading.last();
                 sendingData = true;
                 buildDictionary();
                 sendDownload();
             }
             if (sendStep == 0 && !messageInTransit && transactionOk && !transactionFailed) {
                 Log.i(TAG, "sendData: sendStep 0 complete, clearing dictionary");
                 dictionary.remove(ICON_KEY);
                 dictionary.remove(BG_KEY);
                 dictionary.remove(NAME_KEY);
                 dictionary.remove(BG_DELTA_KEY);
                 dictionary.remove(PHONE_TIME_KEY);
                 dictionary.remove(RECORD_TIME_KEY);
                 dictionary.remove(UPLOADER_BATTERY_KEY);
                 transactionOk = false;
                 sendStep = 1;
             }
             if (sendStep > 0 && sendStep < 5) {
                 if(!PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_display_trend",false)){
                     sendStep = 5;
                 } else {
                     sendTrendToPebble();
                 }
             }
             if(sendStep == 5) {
                 sendStep = 5;
                 Log.i(TAG, "sendData: finished sending.  sendStep = " +sendStep);
                 done = true;
                 transactionFailed = false;
                 transactionOk = false;
                 messageInTransit = false;
                 sendingData = false;
             }
         }
    }

    public String bgReading() {
        Sensor sensor = new Sensor();
        if(PreferenceManager.getDefaultSharedPreferences(mContext).getString("dex_collection_method", "DexbridgeWixel").compareTo("DexbridgeWixel")==0 ) {
            Log.d(TAG, "bgReading: found xBridge wixel, sensor.isActive=" +sensor.isActive()+", sensor.stopped_at="+sensor.currentSensor().stopped_at+", sensor.started_at="+sensor.currentSensor().started_at);
            if (!(sensor.isActive())) {
                Log.d(TAG, "bgReading: No active Sensor");
                return "?SN";
            }
            if ((sensor.currentSensor().started_at + 60000 * 60 * 2 >= System.currentTimeMillis())) {
                return "?CD";
            }
        }
        return bgGraphBuilder.unitized_string(mBgReading.calculated_value);
    }

    public String bgDelta() {
        return new BgGraphBuilder(mContext).unitizedDeltaString(PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pebble_show_delta_units", false), true);
    }

    public String phoneBattery() {
        return String.valueOf(getBatteryLevel());
    }

    public String bgUnit() {
        return bgGraphBuilder.unit();
    }

    //public void sendDownload(PebbleDictionary dictionary) {
    public void sendDownload() {
        if (dictionary != null && mContext != null) {
            Log.d(TAG, "sendDownload: Sending data to pebble");
            messageInTransit = true;
            transactionFailed = false;
            transactionOk = false;
            PebbleKit.sendDataToPebble(mContext, PEBBLEAPP_UUID, dictionary);
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

    public static void sideloadInstall(Context ctx, String assetFilename) {
        try {
            // Read .pbw from assets/
            Intent intent = new Intent(Intent.ACTION_VIEW);
            File file = new File(ctx.getExternalFilesDir(null), assetFilename);
            InputStream is = ctx.getResources().getAssets().open(assetFilename);
            OutputStream os = new FileOutputStream(file);
            byte[] pbw = new byte[is.available()];
            is.read(pbw);
            os.write(pbw);
            is.close();
            os.close();

            // Install via Pebble Android app
            intent.setDataAndType(Uri.fromFile(file), "application/pbw");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (IOException e) {
            Toast.makeText(ctx, "App install failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }
    /*public void setResponseTImer(){
        long wakeTime = new Date().getTime() + 3000;
        PendingIntent serviceIntent = PendingIntent.getService(this, 0, new Intent(this, this.getClass()), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);
        } else
            alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, serviceIntent);

    }*/
}

