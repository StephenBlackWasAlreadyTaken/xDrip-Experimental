package com.eveningoutpost.dexdrip.Services;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.DexdripPacket;
import com.eveningoutpost.dexdrip.Models.TransmitterData;
import com.eveningoutpost.dexdrip.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.HC05Attributes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Created by Radu Iliescu on 1/24/2015.
 *
 * Bluetooth (nonBLE) reader service
 */
public class BluetoothReader extends Thread {
    private final static String TAG = BluetoothReader.class.getName();
    private final static int minReadSize = 8;

    private static BluetoothReader singleton = null;
    private Context mContext;

    public synchronized static BluetoothReader startBluetoothReader(BluetoothDevice device, Context context) {
        if (singleton == null) {
            singleton = new BluetoothReader();
        }

        singleton.mDevice = device;
        singleton.mContext = context;
        singleton.restart();

        return singleton;
    }

    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;

    private UUID uuid = UUID.fromString(HC05Attributes.UUIDString);
    private boolean isRunning = false;

    private void restart() {
        if (isRunning) {
            bluetooth_stop();
        } else {
            this.start();
        }
    }

    private void saveTransmitterData(TransmitterData transmitterData) {
        Sensor sensor = Sensor.currentSensor();
        if (sensor != null) {
            BgReading.create(transmitterData.raw_data, mContext, new Date().getTime());
            sensor.latest_battery_level = transmitterData.sensor_battery_level;
            sensor.save();
        } else {
            Log.w(TAG, "No Active Sensor, Data only stored in Transmitter Data");
        }
    }

    /* data format "len raw dex_battery wixelbattery"
     * where len in the length of string "raw dex_battery wixel_battery"
     */
    private void readDataFromStream(InputStream stream) throws IOException {
        byte[] buffer = new byte[100];
        int totalRead = 0;
        int readSize;
        String str;
        Arrays.fill(buffer, (byte)0);
        /* read the len -> first number until " " */
        do {
            totalRead += stream.read(buffer, totalRead, 1);
            str = new String(buffer, 0, totalRead);
        } while (!str.contains(" "));

        readSize = Integer.parseInt(str.substring(0, totalRead - 1));
        totalRead = 0;
        buffer = new byte[readSize];

        while (totalRead < readSize) {
            totalRead += stream.read(buffer, totalRead, readSize - totalRead);
        }
        str = new String(buffer, "UTF-8");
        Log.d(TAG, "received data size: "  + readSize + " data: " + str);

        TransmitterData transmitterData = TransmitterData.create(buffer, totalRead, new Date().getTime());
        if (transmitterData != null) {
            saveTransmitterData(transmitterData);
        }
    }

    private void readBinaryData(InputStream stream) throws IOException {
        byte packet_header[] = new byte[2];
        int total_read = 0;
        byte len;
        while (total_read < 2) {
            total_read += stream.read(packet_header, total_read, 2 - total_read);
        }
        Log.d(TAG, "got new packet - type " + packet_header[0] + " len " + packet_header[1]);
        len = packet_header[1];
        total_read = 0;

        byte payload[] = new byte[packet_header[1]];
        while (total_read < len) {
            total_read += stream.read(payload, total_read, len - total_read);
        }

        if (packet_header[0] == DexdripPacket.PACKET_DATA) {
            TransmitterData transmitterData = TransmitterData.createFromBinary(payload);
            if (transmitterData != null)
                saveTransmitterData(transmitterData);
            else
                throw new IOException("Incompatible wixel API");
        } else {
            /* packet unknown - something got seriously wrong reset bluetooth connection */
            throw new IOException("Unexpected packet");
        }
    }

    public void run() {
        InputStream stream = null;
        boolean exception = true;
        isRunning = true;

        while (isRunning && !interrupted()) {
            try {
                if (exception) {
                    connect();
                    stream = mSocket.getInputStream();
                }

                exception = false;
                readBinaryData(stream);
                //readDataFromStream(stream);
            } catch (IOException e) {
                Log.i(TAG, "bluetooth exception " + e);
                exception = true;
            }

            try {
                sleep(5000);
            } catch (InterruptedException e) {}
        }
        Log.i(TAG, "Bluetooth reader exited");
        isRunning = false;
    }

    private void bluetooth_stop() {
        try {
            mSocket.close();
        } catch ( IOException e) { }
    }

    private void connect() throws IOException {
        BluetoothSocket tmp = null;

        if (mDevice == null) {
            Log.e(TAG, "Can't connect no device selected");
            throw new IOException("No bluetooth device");
        }
        // Get a BluetoothSocket to connect with the given BluetoothDevice
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        mSocket.connect();

        Log.d(TAG, "connected to bluetooth device");
    }
}
