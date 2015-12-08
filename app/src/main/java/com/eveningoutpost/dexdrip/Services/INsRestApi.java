package com.eveningoutpost.dexdrip.Services;

import java.util.List;

import retrofit.Call;
import retrofit.http.GET;
import retrofit.http.Query;
import retrofit.http.Headers;
import retrofit.http.Header;


import com.eveningoutpost.dexdrip.Services.NsRestApiReader.NightscoutBg;
import com.eveningoutpost.dexdrip.Services.NsRestApiReader.NightscoutMbg;

public interface INsRestApi {
    
    // gets all sgvs
    @GET("/api/v1/entries.json?find[type][$eq]=sgv")
    Call<List<NightscoutBg>> getSgv(
            @Header("Accept") String Accept,
            @Query("find[date][$gt]") long date,
            @Query("count") long count
    );
    
    @GET("/api/v1/entries.json?find[type][$eq]=cal")
    Call<List<NightscoutBg>> getCal(
            @Header("Accept") String Accept,
            @Query("find[date][$gt]") long date,
            @Query("count") long count
    );
    
    @GET("/api/v1/entries.json?find[type][$eq]=mbg")
    Call<List<NightscoutMbg>> getMbg(
            //@Header("Authorization") String authorization
            @Header("Accept") String Accept,
            @Query("find[date][$gt]") long date,
            @Query("count") long count
    );
}    
