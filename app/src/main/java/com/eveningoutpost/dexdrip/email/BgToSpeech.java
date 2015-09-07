package com.eveningoutpost.dexdrip.email;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.eveningoutpost.dexdrip.UtilityModels.Constants;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Created by adrian on 07/09/15.
 */
public class BgToSpeech {

    private static BgToSpeech instance;
    private final Context context;

    private TextToSpeech tts = null;

    public static BgToSpeech getSingleton(Context context){

        if(instance == null) {
            instance = new BgToSpeech(context);
        }
        return instance;
    }

    private BgToSpeech(Context context){
        this.context = context;
    }



    public void speak(double value){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (! prefs.getBoolean("bg_to_speech", false)){
            return;
        }
        if(tts == null){
            tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {

                    if (status == TextToSpeech.SUCCESS) {

                        int result = tts.setLanguage(Locale.getDefault());
                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("BgToSpeech", "Default system language is not supported");
                            result = tts.setLanguage(Locale.ENGLISH);
                        }

                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("BgToSpeech", "English is not supported");
                            tts = null;
                        }

                    } else {
                        tts= null;
                    }
                }
            });
        }

        if (tts == null) {
            return;
        }

        boolean doMgdl = (prefs.getString("units", "mgdl").equals("mgdl"));

        String text = "";

        DecimalFormat df = new DecimalFormat("#");
        if (value >= 400) {
            text = "high";
        } else if (value >= 40) {
            if(doMgdl) {
                df.setMaximumFractionDigits(0);
                text =  df.format(value);
            } else {
                df.setMaximumFractionDigits(1);
                df.setMinimumFractionDigits(1);
                text =  df.format(value* Constants.MGDL_TO_MMOLL);
            }
        } else if (value > 12) {
            text =  "low";
        } else {
            text = "no value";
        }
        Log.d("BgToSpeech", "speaking: " + text);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }


    public static void installTTSData(Context ctx){
        Intent intent = new Intent();
        intent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

}
