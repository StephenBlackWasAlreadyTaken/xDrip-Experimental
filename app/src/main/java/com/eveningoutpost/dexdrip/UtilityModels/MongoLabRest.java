package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by adrian on 24/09/15.
 */
public class MongoLabRest {

    //*********************************************************************
    // FOR TESTING:
    // Should be read from settings/preferences in final version
    private static final String DATABASE = "mydb";
    private static final String API_KEY = ;
    // How to get an API-Key: http://docs.mongolab.com/data-api/#authentication
    // TODO: DELETE APIKEY! (provoke syntax error on purpose -> error while building if not set again)
    //*********************************************************************

    public static final String BASE_URL = "https://api.mongolab.com/api/1/databases/";
    public static final String TAG = "MongoLabRest";
    private static final String DEFAULT_BG_COLLECTION = "entries";
    private static final String DEFAULT_METER_COLLECTION = "entries";
    private static final String DEFAULT_CALIBRATION_COLLECTION = "entries";
    private static final String DEFAULT_DEVICESTATUS_COLLECTION = "devicestatus";
    private static final String UPSERT = "&u=true";
    private static final int SOCKET_TIMEOUT = 60000;
    private static final int CONNECTION_TIMEOUT = 30000;
    private String apiKey;
    private String dbName;
    private SharedPreferences prefs;


    public MongoLabRest(String dbName, String apiKey, Context mContext) {
        this.dbName = dbName;
        this.apiKey = apiKey;
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static MongoLabRest testInstance(Context ctx) {
        return new MongoLabRest(DATABASE, API_KEY, ctx);
    }

    public boolean sendSGVToMongo(List<BgReading> bgReadings) {
        for (BgReading bgReading : bgReadings) {
            if (!sendSGVToMongo(bgReading)) {
                return false;
            } else {
                Log.v(TAG, "sendSGVToMongo-success");
            }
        }
        return true;
    }

    public boolean sendMBGToMongo(List<Calibration> cals) {
        for (Calibration cal : cals) {
            if (!sendMBGToMongo(cal)) {
                return false;
            } else {
                Log.v(TAG, "sendMBGToMongo-success");
            }
        }
        return true;
    }

    public boolean sendCALToMongo(List<Calibration> cals) {
        for (Calibration cal : cals) {
            if (!sendCALToMongo(cal)) {
                return false;
            } else {
                Log.v(TAG, "sendCALToMongo-success");
            }
        }
        return true;
    }

    public boolean sendSGVToMongo(BgReading bgReading) {
        JSONObject json = new JSONObject();
        if (!populateJasonBG(json, bgReading)) {
            return false;
        }
        return sendToMongo(DEFAULT_BG_COLLECTION, json);
    }

    public boolean sendMBGToMongo(Calibration cal) {
        JSONObject json = new JSONObject();
        if (!populateJasonMBG(json, cal)) {
            return false;
        }
        return sendToMongo(DEFAULT_METER_COLLECTION, json);
    }

    public boolean sendCALToMongo(Calibration cal) {
        JSONObject json = new JSONObject();
        if (!populateJasonCAL(json, cal)) {
            return false;
        }
        return sendToMongo(DEFAULT_CALIBRATION_COLLECTION, json);
    }

    public boolean sendDeviceStatusToMongo(int batteryLevel){
        JSONObject json = new JSONObject();
        if (!populateJasonDeviceStatus(json, batteryLevel)) {
            return false;
        }
        return sendToMongo(DEFAULT_DEVICESTATUS_COLLECTION, json);
    }


    public boolean sendToMongo(String collectionName, JSONObject json) {
        Log.d(TAG, "sendToMongo");
        String url = BASE_URL + dbName + "/collections/" + collectionName + "?apiKey=" + apiKey + UPSERT;


        try {
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);
            HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);

            DefaultHttpClient httpclient = new DefaultHttpClient(params);

            HttpPost post = new HttpPost(url);
            String jsonString = json.toString();
            StringEntity se = new StringEntity(jsonString);
            post.setEntity(se);
            //post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");

            ResponseHandler responseHandler = new BasicResponseHandler();
            httpclient.execute(post, responseHandler);
        } catch (ClientProtocolException e) {
            Log.e(TAG, "failed: ", e);
            return false;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "failed: ", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "failed: ", e);
            return false;
        }
        return true;
    }


    private boolean populateJasonBG(JSONObject json, BgReading bgReading) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        format.setTimeZone(TimeZone.getDefault());
        try {
            json.put("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"));
            json.put("date", bgReading.timestamp);
            json.put("dateString", format.format(bgReading.timestamp));
            json.put("sgv", (int) bgReading.calculated_value);
            json.put("direction", bgReading.slopeName());
            json.put("type", "sgv");
            json.put("filtered", bgReading.ageAdjustedFiltered() * 1000);
            json.put("unfiltered", bgReading.usedRaw() * 1000);
            json.put("rssi", 100);
            json.put("noise", bgReading.noiseValue());
            return true;
        } catch (JSONException e) {
            return false;
        }
    }


    private boolean populateJasonMBG(JSONObject json, Calibration record) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        format.setTimeZone(TimeZone.getDefault());
        try {
            json.put("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"));
            json.put("type", "mbg");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            json.put("mbg", record.bg);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }


    private boolean populateJasonDeviceStatus(JSONObject json, int batteryLevel) {
        try {
            json.put("uploaderBattery", batteryLevel);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }


    private boolean populateJasonCAL(JSONObject json, Calibration record) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        format.setTimeZone(TimeZone.getDefault());
        try {
            json.put("device", "xDrip-" + prefs.getString("dex_collection_method", "BluetoothWixel"));
            json.put("type", "cal");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            if (record.check_in) {
                json.put("slope", (long) (record.first_slope));
                json.put("intercept", (long) ((record.first_intercept)));
                json.put("scale", record.first_scale);
            } else {
                json.put("slope", (long) (record.slope * 1000));
                json.put("intercept", (long) ((record.intercept * -1000) / (record.slope * 1000)));
                json.put("scale", 1);
            }
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
