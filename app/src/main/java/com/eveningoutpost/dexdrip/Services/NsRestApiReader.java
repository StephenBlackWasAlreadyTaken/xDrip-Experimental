package com.eveningoutpost.dexdrip.Services;

import java.io.IOException;
import java.util.List;

import com.eveningoutpost.dexdrip.Models.UserError.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit.Call;
import retrofit.Response;
import retrofit.http.GET;
import retrofit.http.Query;
import retrofit.Retrofit;
import retrofit.GsonConverterFactory;

import com.squareup.okhttp.logging.HttpLoggingInterceptor;
import com.squareup.okhttp.OkHttpClient;


public class NsRestApiReader {

	class NightscoutBg {
	    double xDrip_raw; // raw_data
	    double xDrip_age_adjusted_raw_value;
	    double xDrip_filtered; // filtered_data;
	    Long date; // timestamp
	    double sgv; // calculated_bg
	    double xDrip_calculated_value;
	}

	class NightscoutMbg {
	    Long date; // timestamp
	    double mbg; // calculated_bg
	    double xDrip_slope;
	    double xDrip_intercept;
	    double xDrip_estimate_raw_at_time_of_calibration;
	    double xDrip_slope_confidence;
	    double xDrip_sensor_confidence;
	    long xDrip_raw_timestamp;
	    String xDrip_sensor_uuid;
	}
	
   class NightscoutSensor {
       Long xDrip_started_at;
       Long xDrip_stopped_at;
       int xDrip_latest_battery_level;
       String xDrip_uuid;
   }
	
    private final static String TAG = NsRestApiReader.class.getName();

	
	private INsRestApi CreateNsMethods(String baseUrl) {
        Retrofit retrofit;

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();  
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient httpClient = new OkHttpClient();  
        // add your other interceptors ...

        // add logging as last interceptor
        httpClient.interceptors().add(logging);  // <-- this is the important line for logging

        Gson gson = new GsonBuilder().create();
        retrofit = new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(httpClient)
        .build();

        return retrofit.create(INsRestApi.class);
    }
    
    
    public List<NightscoutSensor> readSensorDataFromNs(String baseUrl, String key, long startTime, long maxCount) {
        INsRestApi methods = CreateNsMethods(baseUrl);
        List<NightscoutSensor> nightscoutSensors = null;
        try {
        
            Call<List<NightscoutSensor>> call = methods.getSensor(key,startTime, maxCount); 
            
            Response<List<NightscoutSensor>> response = call.execute();
            if(response == null) {
                Log.e(TAG,"readSensorDataFromNs  call.execute returned null");
                return null;
            }
            // http://stackoverflow.com/questions/32517114/how-is-error-handling-done-in-retrofit-2-i-can-not-find-the-retrofiterror-clas
            if(!response.isSuccess() && response.errorBody() != null) {
                Log.e(TAG,"readSensorDataFromNs  call.execute returned with error " + response.errorBody());
                return null;
            }
            nightscoutSensors = response.body();
            //
        } catch (IOException e ) {
            Log.e(TAG,"RetrofitError exception was cought " + e.toString());
            return null;
        }
        
        if(nightscoutSensors == null) {
            Log.e(TAG,"readSensorDataFromNs returned null");
            return null;
        }
        return nightscoutSensors;
    }

    public List<NightscoutMbg> readCalDataFromNs(String baseUrl, String key, long startTime, long maxCount) {
        INsRestApi methods = CreateNsMethods(baseUrl);
        List<NightscoutMbg> nightscoutMbgs = null;
        try {
        
            Call<List<NightscoutMbg>> call = methods.getMbg(key,startTime, maxCount); 
            
            Response<List<NightscoutMbg>> response = call.execute();
            if(response == null) {
                Log.e(TAG,"readCalDataFromNs  call.execute returned null");
                return null;
            }
            // http://stackoverflow.com/questions/32517114/how-is-error-handling-done-in-retrofit-2-i-can-not-find-the-retrofiterror-clas
            if(!response.isSuccess() && response.errorBody() != null) {
                Log.e(TAG,"readCalDataFromNs  call.execute returned with error " + response.errorBody());
                return null;
            }
            nightscoutMbgs = response.body();
            //
        } catch (IOException e ) {
            Log.e(TAG,"RetrofitError exception was cought", e);
            return null;
        }
        
        if(nightscoutMbgs == null) {
            Log.e(TAG,"readCalDataFromNs returned null");
            return null;
        }
        return nightscoutMbgs;
    }

    public List<NightscoutBg> readBgDataFromNs(String baseUrl, String key, long startTime, long maxCount) {
        Log.e(TAG,"readBgData Starting to read from retrofit");
        INsRestApi methods = CreateNsMethods(baseUrl);
        List<NightscoutBg> nightscoutBgs = null;
        try {
        
            Call<List<NightscoutBg>> call = methods.getSgv(key, startTime, maxCount); 
            
            Response<List<NightscoutBg>> response = call.execute();
            if(response == null) {
                Log.e(TAG,"readBgData  call.execute returned null");
                return null;
            }
            // http://stackoverflow.com/questions/32517114/how-is-error-handling-done-in-retrofit-2-i-can-not-find-the-retrofiterror-clas
            if(!response.isSuccess() && response.errorBody() != null) {
                Log.e(TAG,"readBgData  call.execute returned with error " + response.errorBody());
                return null;
            }
            nightscoutBgs = response.body();
            //
        } catch (IOException e ) {
            Log.e(TAG,"RetrofitError exception was cought", e);
            return null;
        }
        
        
        if(nightscoutBgs == null) {
            Log.e(TAG,"readBgData returned null");
            return null;
        }
        Log.e(TAG,"retrofit returning a list, size = " + nightscoutBgs.size());
        return nightscoutBgs;
    }

}
