package com.eveningoutpost.dexdrip.Services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.ReadDataShare;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.CalRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.EGVRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.SensorRecord;
import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.driver.CdcAcmSerialDriver;
import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.driver.ProbeTable;
import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.driver.UsbSerialDriver;
import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.driver.UsbSerialProber;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.ForegroundServiceStarter;
import com.eveningoutpost.dexdrip.utils.BgToSpeech;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import rx.Observable;
import rx.functions.Action1;

public class DexOtgCollectionService extends Service {
    private static final String TAG = DexOtgCollectionService.class.getSimpleName();
    private Context mContext;
    private ForegroundServiceStarter foregroundServiceStarter;

    private UsbManager mUsbManager;
    private UsbSerialDriver mSerialDevice;
    private UsbDevice dexcom;
    private UsbDeviceConnection mConnection;
    public Service service;
    public int currentGattTask;
    public int step;
    public List<byte[]> writePackets;
    public int recordType;
    SharedPreferences prefs;
    ReadDataShare readData;
    Action1<byte[]> mDataResponseListener;
    public int successfulWrites;
    private BgToSpeech bgToSpeech;
    public boolean shouldDisconnect = false;
    public boolean is_page = false;

    //Gatt Tasks
    public final int GATT_NOTHING = 0;
    public final int GATT_SETUP = 1;
    public final int GATT_WRITING_COMMANDS = 2;
    public final int GATT_READING_RESPONSE = 3;
    private static final int IO_TIMEOUT = 3000;


    @Override
    public void onCreate() {
        super.onCreate();
        readData = new ReadDataShare(this);
        service = this;
        foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), service);
        foregroundServiceStarter.start();
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listenForChangeInSettings();
        bgToSpeech = BgToSpeech.setupTTS(getApplicationContext()); //keep reference to not being garbage collected
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(getApplicationContext().POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DexShareCollectionStart");
        wakeLock.acquire(40000);
        try {
            if (CollectionServiceStarter.isOtgDexcom(getApplicationContext())) {
                setFailoverTimer();
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (Sensor.currentSensor() == null) {
                setRetryTimer();
                return START_NOT_STICKY;
            }
            UserError.Log.i(TAG, "STARTING SERVICE");
            attemptConnection();
        } finally {
            if(wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
        return START_STICKY;
    }

    public SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if(key.compareTo("run_service_in_foreground") == 0) {
                UserError.Log.e("FOREGROUND", "run_service_in_foreground changed!");
                if (prefs.getBoolean("run_service_in_foreground", false)) {
                    foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), service);
                    foregroundServiceStarter.start();
                    UserError.Log.i(TAG, "Moving to foreground");
                } else {
                    service.stopForeground(true);
                    UserError.Log.i(TAG, "Removing from foreground");
                }
            }
        }
    };
    public void listenForChangeInSettings() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }
    public void setRetryTimer() {
        if (CollectionServiceStarter.isBTShare(getApplicationContext())) {
            BgReading bgReading = BgReading.last();
            long retry_in;
            if (bgReading != null) {
                retry_in = Math.min(Math.max((1000 * 30), (1000 * 60 * 5) - (new Date().getTime() - bgReading.timestamp) + (1000 * 5)), (1000 * 60 * 5));
            } else {
                retry_in = (1000 * 20);
            }
            UserError.Log.d(TAG, "Restarting in: " + (retry_in / (60 * 1000)) + " minutes");
            Calendar calendar = Calendar.getInstance();
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                alarm.setExact(alarm.RTC_WAKEUP, calendar.getTimeInMillis() + retry_in, PendingIntent.getService(this, 0, new Intent(this, DexOtgCollectionService.class), 0));
            } else {
                alarm.set(alarm.RTC_WAKEUP, calendar.getTimeInMillis() + retry_in, PendingIntent.getService(this, 0, new Intent(this, DexOtgCollectionService.class), 0));
            }
        }
    }
    public void setFailoverTimer() { //Sometimes it gets stuck in limbo on 4.4, this should make it try again
        if (CollectionServiceStarter.isBTShare(getApplicationContext())) {
            long retry_in = (1000 * 60 * 5);
            UserError.Log.d(TAG, "Fallover Restarting in: " + (retry_in / (60 * 1000)) + " minutes");
            Calendar calendar = Calendar.getInstance();
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarm.set(alarm.RTC_WAKEUP, calendar.getTimeInMillis() + retry_in, PendingIntent.getService(this, 0, new Intent(this, DexOtgCollectionService.class), 0));
        } else {
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void attemptConnection() {
        if (acquireSerialDevice()) {
            attemptRead();
        }
    }

    public void attemptRead() {
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock1 = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ReadingShareData");
        wakeLock1.acquire(60000);
        UserError.Log.d(TAG, "Attempting to read data");
        final Action1<Long> systemTimeListener = new Action1<Long>() {
            @Override
            public void call(Long s) {
                if (s != null) {
                    UserError.Log.d(TAG, "Made the full round trip, got " + s + " as the system time");
                    final long addativeSystemTimeOffset = new Date().getTime() - s;

                    final Action1<Long> dislpayTimeListener = new Action1<Long>() {
                        @Override
                        public void call(Long s) {
                            if (s != null) {
                                UserError.Log.d(TAG, "Made the full round trip, got " + s + " as the display time offset");
                                final long addativeDisplayTimeOffset = addativeSystemTimeOffset - (s * 1000);

                                UserError.Log.d(TAG, "Making " + addativeDisplayTimeOffset + " the the total time offset");

                                final Action1<EGVRecord[]> evgRecordListener = new Action1<EGVRecord[]>() {
                                    @Override
                                    public void call(EGVRecord[] egvRecords) {
                                        if (egvRecords != null) {
                                            UserError.Log.d(TAG, "Made the full round trip, got " + egvRecords.length + " EVG Records");
                                            BgReading.create(egvRecords, addativeSystemTimeOffset, getApplicationContext());
                                            {
                                                UserError.Log.d(TAG, "Releasing wl in egv");
                                                if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();
                                                UserError.Log.d(TAG, "released");
                                            }
                                            if (shouldDisconnect) {
                                                stopSelf();
                                            } else {
                                                setRetryTimer();
                                            }
                                        }
                                    }
                                };

                                final Action1<SensorRecord[]> sensorRecordListener = new Action1<SensorRecord[]>() {
                                    @Override
                                    public void call(SensorRecord[] sensorRecords) {
                                        if (sensorRecords != null) {
                                            UserError.Log.d(TAG, "Made the full round trip, got " + sensorRecords.length + " Sensor Records");
                                            BgReading.create(sensorRecords, addativeSystemTimeOffset, getApplicationContext());
                                            readData.getRecentEGVs(evgRecordListener);
                                        }
                                    }
                                };

                                final Action1<CalRecord[]> calRecordListener = new Action1<CalRecord[]>() {
                                    @Override
                                    public void call(CalRecord[] calRecords) {
                                        if (calRecords != null) {
                                            UserError.Log.d(TAG, "Made the full round trip, got " + calRecords.length + " Cal Records");
                                            Calibration.create(calRecords, addativeDisplayTimeOffset, getApplicationContext());
                                            readData.getRecentSensorRecords(sensorRecordListener);
                                        }
                                    }
                                };
                                readData.getRecentCalRecords(calRecordListener);
                            } else
                            if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();
                        }
                    };
                    readData.readDisplayTimeOffset(dislpayTimeListener);
                } else
                if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();

            }
        };
        readData.readSystemTime(systemTimeListener);
    }

    private boolean acquireSerialDevice() {
        if(mUsbManager != null && mSerialDevice != null && mConnection != null && dexcom != null) {
            return true;
        }
       findDexcom();
        if(mUsbManager == null) {
            UserError.Log.w(TAG, "USB manager is null");
            return false;
        }
        if(dexcom == null) {
            UserError.Log.d(TAG, "Dexcom not dound null");
            return false;
        }
        if( mUsbManager.hasPermission(dexcom)) {                                           // the system is allowing us to poke around this device

            ProbeTable customTable = new ProbeTable();                                           // From the USB library...
            customTable.addProduct(0x22A3, 0x0047, CdcAcmSerialDriver.class);       // ...Specify the Vendor ID and Product ID

            UsbSerialProber prober = new UsbSerialProber(customTable);                      // Probe the device with the custom values
            List<UsbSerialDriver> drivers = prober.findAllDrivers(mUsbManager);            // let's go through the list
            Iterator<UsbSerialDriver> foo = drivers.iterator();                                                                                                  // Invalid Return code
            while (foo.hasNext()) {                                                         // let's loop through
                UsbSerialDriver driver = foo.next();                                        // set fooDriver to the next available driver
                if (driver != null) {
                    UsbDeviceConnection connection = mUsbManager.openDevice(driver.getDevice());
                    if (connection != null) {
                        mSerialDevice = driver;

                        mConnection = connection;
                        UserError.Log.i(TAG, "CONNECTEDDDD!!");
                        return true;
                    }
                } else {
                    UserError.Log.w(TAG, "Driver was no good");
                }
            }
            UserError.Log.w(TAG, "No usable drivers found");
        } else {
            UserError.Log.w(TAG, "You dont have permissions for that dexcom!!");
        }
        return false;
    }

    public UsbDevice findDexcom() {
        UserError.Log.i(TAG, "Searching for dexcom");
        mUsbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        UserError.Log.i("USB MANAGER = ", mUsbManager.toString());
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        UserError.Log.i("USB DEVICES = ", deviceList.toString());
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        UserError.Log.i("USB DEVICES = ", String.valueOf(deviceList.size()));

        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            if (device.getVendorId() == 8867 && device.getProductId() == 71
                    && device.getDeviceClass() == 2 && device.getDeviceSubclass() ==0
                    && device.getDeviceProtocol() == 0){
                dexcom = device;
                UserError.Log.i(TAG, "Dexcom Found!");
                return device;
            } else {
                UserError.Log.w(TAG, "that was not a dexcom (I dont think)");
            }
        }
        return null;
    }

    public void writeCommand(List<byte[]> packets, int aRecordType, Action1<byte[]> dataResponseListener, boolean page) {
        is_page = page;
        mDataResponseListener = dataResponseListener;
        successfulWrites = 0;
        writePackets = packets;
        recordType = aRecordType;
        step = 0;
        currentGattTask = GATT_WRITING_COMMANDS;
        gattWritingStep();
    }

    private void gattWritingStep() {
        UserError.Log.d(TAG, "Writing command to the Gatt, step: " + step);
        int index = step;
        if (index <= (writePackets.size() - 1)) {
            UserError.Log.d(TAG, "Writing: " + writePackets.get(index) + " index: " + index);
            try {
                if(mSerialDevice != null && writePackets != null) {
                    mSerialDevice.getPorts().get(0).write(writePackets.get(index), IO_TIMEOUT);
                    gatReadingStep();
                }
            } catch (Exception e) {
                UserError.Log.e(TAG, "Unable to write to serial device.", e);
            }
        } else {
            UserError.Log.d(TAG, "Done Writing commands");
            clearGattTask();
        }
    }
    public void clearGattTask() {
        currentGattTask = GATT_NOTHING;
        step = 0;
    }

    private void gatReadingStep() {
        int size = (is_page ? 2122 : 256);
        byte[] readData = new byte[size];
        int len = 0;
        try {
            len = mSerialDevice.getPorts().get(0).read(readData, IO_TIMEOUT);
            Thread.sleep(100);
            String bytes = "";
            int readAmount = len;
            for (int i = 0; i < readAmount; i++) bytes += String.format("%02x", readData[i]) + " ";
            UserError.Log.d(TAG, "Read data: " + bytes);
            ////////////////////////////////////////////////////////////////////////////////////////

        } catch (Exception e) {
            UserError.Log.e(TAG, "Unable to read from serial device.", e);
        }
        byte[] data = Arrays.copyOfRange(readData, 0, len);
        Observable.just(data).subscribe(mDataResponseListener);
    }
}
