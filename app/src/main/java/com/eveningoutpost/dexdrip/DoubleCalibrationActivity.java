package com.eveningoutpost.dexdrip;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;


public class DoubleCalibrationActivity  extends ActivityWithMenu {
    Button button;
    public static String menu_name = "Add Double Calibration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(CollectionServiceStarter.isBTShare(getApplicationContext())) {
            Intent intent = new Intent(this, Home.class);
            startActivity(intent);
            finish();
        }
        setContentView(R.layout.activity_double_calibration);
        addListenerOnButton();
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    public void addListenerOnButton() {

        button = (Button) findViewById(R.id.save_calibration_button);

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (Sensor.isActive()) {
                    EditText value_1 = (EditText) findViewById(R.id.bg_value_1);
                    EditText value_2 = (EditText) findViewById(R.id.bg_value_2);
                    String string_value_1 = value_1.getText().toString();
                    String string_value_2 = value_2.getText().toString();

                    if (!TextUtils.isEmpty(string_value_1)){
                        if(!TextUtils.isEmpty(string_value_2)) {
                            double calValue_1 = Double.parseDouble(string_value_1);
                            double calValue_2 = Double.parseDouble(string_value_2);

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(DoubleCalibrationActivity.this);
                            String unit = prefs.getString("units", "mgdl");
                            double calValue_1MGDL = ("mgdl".equals(unit))?calValue_1:calValue_1* Constants.MMOLL_TO_MGDL;
                            double calValue_2MGDL = ("mgdl".equals(unit))?calValue_2:calValue_2* Constants.MMOLL_TO_MGDL;

                            boolean inRange = true;

                            if(calValue_1MGDL <40 || calValue_1MGDL >400){
                                inRange = false;
                                value_1.setError(getString(R.string.out_of_range));
                            }

                            if(calValue_2MGDL <40 || calValue_2MGDL >400){
                                inRange = false;
                                value_2.setError(getString(R.string.out_of_range));
                            }



                            if (inRange) {
                                Calibration.initialCalibration(calValue_1, calValue_2, getApplicationContext());
                                Intent tableIntent = new Intent(v.getContext(), Home.class);
                                startActivity(tableIntent);
                                finish();
                            }
                        } else {
                            value_2.setError(getString(R.string.calibration_cannot_be_blank));
                        }
                    } else {
                        value_1.setError(getString(R.string.calibration_cannot_be_blank));
                    }
                } else {
                    Log.w("DoubleCalibration", "ERROR, sensor is not active");
                }
            }
        });

    }
}
