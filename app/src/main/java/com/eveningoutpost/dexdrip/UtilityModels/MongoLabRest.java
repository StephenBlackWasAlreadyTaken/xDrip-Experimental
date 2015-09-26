package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Services.TransmitterRawData;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;





import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by adrian on 24/09/15.*
 */
public class MongoLabRest {

    //*********************************************************************
    // FOR TESTING:
    // Should be read from settings/preferences in final version
    private static final String DATABASE = "mydb";
    private static final String API_KEY = "D2a6iaurh-oihXrraOquZSySx9QnT_Gs";
    // How to get an API-Key: http://docs.mongolab.com/data-api/#authentication
    // TODO: DELETE APIKEY! (provoke syntax error on purpose -> error while building if not set again)
    //*********************************************************************

    public static final String BASE_URL = "https://api.mongolab.com/api/1/databases/";
    public static final String TAG = "MongoLabRest";
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String dbName = prefs.getString("cloud_storage_mongodb_rest_database", "nightscout");
        String apiKey = prefs.getString("cloud_storage_mongodb_rest_key", "");
        
        return new MongoLabRest(dbName, apiKey, ctx);
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
        String collectionName = prefs.getString("cloud_storage_mongodb_collection", null);
        return sendToMongo(collectionName, json);
    }

    public boolean sendMBGToMongo(Calibration cal) {
        JSONObject json = new JSONObject();
        if (!populateJasonMBG(json, cal)) {
            return false;
        }
        
        String dsCollectionName = prefs.getString("cloud_storage_mongodb_device_status_collection", "devicestatus");
        return sendToMongo(dsCollectionName, json);
    }

    public boolean sendCALToMongo(Calibration cal) {
        JSONObject json = new JSONObject();
        if (!populateJasonCAL(json, cal)) {
            return false;
        }
        String dsCollectionName = prefs.getString("cloud_storage_mongodb_device_status_collection", "devicestatus");
        return sendToMongo(dsCollectionName, json);
    }

    public boolean sendDeviceStatusToMongo(int batteryLevel){
        JSONObject json = new JSONObject();
        if (!populateJasonDeviceStatus(json, batteryLevel)) {
            return false;
        }
        String dsCollectionName = prefs.getString("cloud_storage_mongodb_device_status_collection", "devicestatus");
        return sendToMongo(dsCollectionName, json);
    }


    public boolean sendToMongo(String collectionName, JSONObject json) {
        Log.d(TAG, "sendToMongo");
        String url = BASE_URL + dbName + "/collections/" + collectionName + "?apiKey=" + apiKey + UPSERT;

        boolean success = false;
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
            HttpResponse response = httpclient.execute(post);
            Log.d(TAG, "Send returned code is " + response.getStatusLine().getStatusCode());
            if( response.getStatusLine().getStatusCode() == 200) {
                success  = true;
            }
        } catch (ClientProtocolException e) {
            Log.e(TAG, "sendToMongo ClientProtocolException: ", e);
            return false;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "sendToMongo UnsupportedEncodingException: ", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "sendToMongo IOException: ", e);
            return false;
        }catch (Exception e) {
            Log.e(TAG, "sendToMongo Exception: ", e);
            return false;
        }
        
        return success;
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
            json.put("filtered", bgReading.filtered_data * 1000);
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
    
    
    
    public List<TransmitterRawData> fromJson(Context ctx, String data) {
        
        Gson gson = new Gson();
        Type listType = new TypeToken<List<TransmitterRawData>>(){}.getType();
        
        List<TransmitterRawData> trdList = gson.fromJson(data, listType); 
        
        //Toast.makeText(ctx, "objects read " + asd.size(), Toast.LENGTH_LONG).show();
        Log.e(TAG,  "objects read " + trdList.size());
        return trdList;
        
        
    }
    
    
    // This function is based on    
    //http://www.javacodegeeks.com/2012/09/simple-rest-client-in-java.html
    
    // To encode a query use:
    // web encoder: http://www.w3schools.com/jsref/tryit.asp?filename=tryjsref_encodeuricomponent 
    // after that replace 3d with "="
    
    public List<TransmitterRawData> readFromMongo(Context ctx, String collectionName) {

        List<TransmitterRawData> trdList = null;


        String sort = "s=%7B%22CaptureDateTime%22:%20-1%7D"; // &s={"CaptureDateTime":%20%201}
        String exists = "q=%7B%22RawValue%22%3A%20%7B%22%24exists%22%3A%20true%7D%7D"; //q={\"RawValue\": {\"$exists\": true}}

        String url = BASE_URL + dbName + "/collections/" + collectionName +"?" + exists + "&"  + sort+ "&l=1&apiKey=" + apiKey;


        HttpClient httpClient = new DefaultHttpClient();
        try {

            HttpGet httpGetRequest = new HttpGet(url);

            // Execute HTTP request
            HttpResponse httpResponse = httpClient.execute(httpGetRequest);

            Log.e(TAG, "read returned" + httpResponse.getStatusLine());
            //????????? Do  we need this line
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                //??? Should we close things here 
                return null;
            }

            // Get hold of the response entity
            HttpEntity entity = httpResponse.getEntity();

            // If the response does not enclose an entity, there is no need
            // to bother about connection release
            byte[] buffer = new byte[1024];
            String total = new String();
            if (entity != null) {
                InputStream inputStream = entity.getContent();
                try {
                    int bytesRead = 0;
                    BufferedInputStream bis = new BufferedInputStream(inputStream);
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, bytesRead);
                        System.out.println("writing chunk");
                        System.out.println(chunk);
                        Log.e(TAG, "read chunk" + chunk);
                        total += chunk;
                    }
                } catch (IOException ioException) {
                    // In case of an IOException the connection will be released
                    // back to the connection manager automatically
                    Log.e(TAG, "ReadFromMongo cought ioException ", ioException);
                } catch (RuntimeException runtimeException) {
                    // In case of an unexpected exception you may want to abort
                    // the HTTP request in order to shut down the underlying
                    // connection immediately.
                    Log.e(TAG, "ReadFromMongo cought runtimeException ", runtimeException);
                    httpGetRequest.abort();
                    runtimeException.printStackTrace();
                } finally {
                    // Closing the input stream will trigger connection release
                    trdList = fromJson(ctx ,total);
                    try {
                        inputStream.close();
                    } catch (Exception ignore) {
                        Log.e(TAG, "ReadFromMongo cought ignore exception", ignore);
                    }
                }
            }
        } catch (ClientProtocolException e) {
            // thrown by httpClient.execute(httpGetRequest)
            Log.e(TAG, "ReadFromMongo cought ClientProtocolExceptionn", e);
        } catch (IOException e) {
            // thrown by entity.getContent();
            Log.e(TAG, "ReadFromMongo cought IOException", e);
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            Log.e(TAG, "ReadFromMongo finally reached finally");
            httpClient.getConnectionManager().shutdown();
        }
        return trdList;
    }

}
