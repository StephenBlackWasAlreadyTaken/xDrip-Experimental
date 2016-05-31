package com.eveningoutpost.dexdrip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.Constants;
import com.eveningoutpost.dexdrip.Models.ActiveBluetoothDevice;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.Services.WixelReader;
import com.eveningoutpost.dexdrip.Services.XDripViewer;
import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Intents;
import com.eveningoutpost.dexdrip.stats.StatsResult;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;
import com.eveningoutpost.dexdrip.utils.DatabaseUtil;
import com.eveningoutpost.dexdrip.utils.SdcardImportExport;
import com.eveningoutpost.dexdrip.wearintegration.WatchUpdaterService;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;

import java.io.File;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


public class Home extends ActivityWithMenu {
    static String TAG = Home.class.getName();
    public static String menu_name = "xDrip";
    private boolean updateStuff;
    private boolean updatingPreviewViewport = false;
    private boolean updatingChartViewport = false;
    private BgGraphBuilder bgGraphBuilder;
    private SharedPreferences mPreferences;
    private Viewport tempViewport = new Viewport();
    private Viewport holdViewport = new Viewport();
    private boolean isBTShare;
    private boolean isG5Share;
    private BroadcastReceiver _broadcastReceiver;
    private BroadcastReceiver newDataReceiver;
    private LineChartView            chart;
    private PreviewLineChartView     previewChart;
    private TextView                 dexbridgeBattery;
    private TextView                 currentBgValueText;
    private TextView                 notificationText;
    private TextView                 extraStatusLineText;
    private boolean                  alreadyDisplayedBgInfoCommon = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        checkEula();
        setContentView(R.layout.activity_home);

        this.dexbridgeBattery = (TextView) findViewById(R.id.textBridgeBattery);
        this.currentBgValueText = (TextView) findViewById(R.id.currentBgValueRealTime);
        this.notificationText = (TextView) findViewById(R.id.notices);
        this.extraStatusLineText = (TextView) findViewById(R.id.extraStatusLine);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            Log.d(TAG, "Maybe ignoring battery optimization");
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName) &&
                    !mPreferences.getBoolean("requested_ignore_battery_optimizations", false)) {
                Log.d(TAG, "Requesting ignore battery optimization");

                mPreferences.edit().putBoolean("requested_ignore_battery_optimizations", true).apply();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    private void checkEula() {
        boolean IUnderstand = mPreferences.getBoolean("I_understand", false);
        if (!IUnderstand) {
            Intent intent = new Intent(getApplicationContext(), LicenseAgreementActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkEula();
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0) {
                    updateCurrentBgInfo();
                }
            }
        };
        newDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                holdViewport.set(0, 0, 0, 0);
                updateCurrentBgInfo();
            }
        };
        if(BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(100);
            this.notificationText.setTextSize(40);
            this.extraStatusLineText.setTextSize(40);
        }
        else if(BgGraphBuilder.isLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(70);
            this.notificationText.setTextSize(34); // 35 too big 33 works 
            this.extraStatusLineText.setTextSize(35);
        }

        registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        registerReceiver(newDataReceiver, new IntentFilter(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA));
        holdViewport.set(0, 0, 0, 0);
        updateCurrentBgInfo();
    }

    private void setupCharts() {
        bgGraphBuilder = new BgGraphBuilder(this);
        updateStuff = false;
        chart = (LineChartView) findViewById(R.id.chart);

        chart.setZoomType(ZoomType.HORIZONTAL);

        //Transmitter Battery Level
        final Sensor sensor = Sensor.currentSensor();
        if (sensor != null && sensor.latest_battery_level != 0 && sensor.latest_battery_level <= Constants.TRANSMITTER_BATTERY_LOW && ! mPreferences.getBoolean("disable_battery_warning", false)) {
            Drawable background = new Drawable() {

                @Override
                public void draw(Canvas canvas) {

                    DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
                    int px = (int) (30 * (metrics.densityDpi / 160f));
                    Paint paint = new Paint();
                    paint.setTextSize(px);
                    paint.setAntiAlias(true);
                    paint.setColor(Color.parseColor("#FFFFAA"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setAlpha(100);
                    canvas.drawText("transmitter battery", 10, chart.getHeight() / 3 - (int) (1.2 * px), paint);
                    if(sensor.latest_battery_level <= Constants.TRANSMITTER_BATTERY_EMPTY){
                        paint.setTextSize((int)(px*1.5));
                        canvas.drawText("VERY LOW", 10, chart.getHeight() / 3, paint);
                    } else {
                        canvas.drawText("low", 10, chart.getHeight() / 3, paint);
                    }
                }

                @Override
                public void setAlpha(int alpha) {}
                @Override
                public void setColorFilter(ColorFilter cf) {}
                @Override
                public int getOpacity() {return 0;}
            };
            chart.setBackground(background);
        }
        previewChart = (PreviewLineChartView) findViewById(R.id.chart_preview);
        previewChart.setZoomType(ZoomType.HORIZONTAL);

        chart.setLineChartData(bgGraphBuilder.lineData());
        chart.setOnValueTouchListener(bgGraphBuilder.getOnValueSelectTooltipListener());
        previewChart.setLineChartData(bgGraphBuilder.previewLineData());
        updateStuff = true;

        previewChart.setViewportCalculationEnabled(true);
        chart.setViewportCalculationEnabled(true);
        previewChart.setViewportChangeListener(new ViewportListener());
        chart.setViewportChangeListener(new ChartViewPortListener());
        setViewport();
    }

    public void setViewport() {
        if (tempViewport.left == 0.0 || holdViewport.left == 0.0 || holdViewport.right >= (new Date().getTime())) {
            previewChart.setCurrentViewport(bgGraphBuilder.advanceViewport(chart, previewChart));
        } else {
            previewChart.setCurrentViewport(holdViewport);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (_broadcastReceiver != null ) {
            try {
                unregisterReceiver(_broadcastReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "_broadcast_receiver not registered", e);
            }
        }
        if (newDataReceiver != null) {
            try {
                unregisterReceiver(newDataReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "newDataReceiver not registered", e);
            }
        }
    }

    private void updateCurrentBgInfo() {
        setupCharts();
        final TextView notificationText = (TextView) findViewById(R.id.notices);
        notificationText.setText("");
        notificationText.setTextColor(Color.parseColor("#FF0000"));
        boolean isBTWixel = CollectionServiceStarter.isBteWixelorWifiandBtWixel(getApplicationContext());
        boolean isDexbridgeWixel = CollectionServiceStarter.isDexbridgeWixelorWifiandDexbridgeWixel(getApplicationContext());
        isBTShare = CollectionServiceStarter.isBTShare(getApplicationContext());
        isG5Share = CollectionServiceStarter.isBTG5(getApplicationContext());
        boolean isWifiWixel = CollectionServiceStarter.isWifiWixel(getApplicationContext());
        alreadyDisplayedBgInfoCommon = false; // reset flag
        
        boolean xDripViewer = XDripViewer.isxDripViewerMode(getApplicationContext());
        
        if(xDripViewer) {
            updateCurrentBgInfoForxDripViewer(notificationText);
        } else if (isBTShare) {
            updateCurrentBgInfoForBtShare(notificationText);
        } else if (isBTWixel || isDexbridgeWixel) {
            updateCurrentBgInfoForBtBasedWixel(notificationText);
        } else if (isWifiWixel) {
            updateCurrentBgInfoForWifiWixel(notificationText);
        }
        if (isG5Share) {
            updateCurrentBgInfoCommon(notificationText);
        }

        if (mPreferences.getLong("alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n ALL ALERTS DISABLED");
        } else if (mPreferences.getLong("low_alerts_disabled_until", 0) > new Date().getTime()
			&&
			mPreferences.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\nLOW AND HIGH ALERTS DISABLED");
        } else if (mPreferences.getLong("low_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\nLOW ALERTS DISABLED");
        } else if (mPreferences.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\nHIGH ALERTS DISABLED");
        }
        if(mPreferences.getBoolean("extra_status_line", false)) {
            extraStatusLineText.setText(extraStatusLine(mPreferences));
            extraStatusLineText.setVisibility(View.VISIBLE);
        } else {
            extraStatusLineText.setText("");
            extraStatusLineText.setVisibility(View.GONE);
        }
        NavigationDrawerFragment navigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), menu_name, this);
    }

    private void updateCurrentBgInfoForWifiWixel(TextView notificationText) {
        if (!WixelReader.IsConfigured(getApplicationContext())) {
            notificationText.setText("First configure your wifi wixel reader ip addresses");
            return;
        }

        updateCurrentBgInfoCommon(notificationText);

    }
    
    private void updateCurrentBgInfoForxDripViewer(TextView notificationText) {
        if (!XDripViewer.isxDripViewerConfigured(getApplicationContext())) {
            notificationText.setText("First configure Nightscout website address");
            return;
        }
        
        displayCurrentInfo();

    }
    private void updateCurrentBgInfoForBtBasedWixel(TextView notificationText) {
        if ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            notificationText.setText("Unfortunately your android version does not support Bluetooth Low Energy");
            return;
        }

        if (ActiveBluetoothDevice.first() == null) {
            notificationText.setText("First pair with your BT device!");
            return;
        }
        updateCurrentBgInfoCommon(notificationText);
    }

    private void updateCurrentBgInfoCommon(TextView notificationText) {
        if (alreadyDisplayedBgInfoCommon) return; // with bluetooth and wifi, skip second time
        alreadyDisplayedBgInfoCommon = true;

        final boolean isSensorActive = Sensor.isActive();
        if(!isSensorActive){
            notificationText.setText("Now start your sensor");
            return;
        }

        final long now = System.currentTimeMillis();
        if (Sensor.currentSensor().started_at + 60000 * 60 * 2 >= now) {
            double waitTime = (Sensor.currentSensor().started_at + 60000 * 60 * 2 - now) / 60000.0;
            notificationText.setText("Sensor Warmup (" + String.format("%.0f", waitTime) + " minutes remaining)");
            return;
        }

        if (BgReading.latest(2).size() > 1) {
            List<Calibration> calibrations = Calibration.latest(2);
            if (calibrations.size() > 1) {
                if (calibrations.get(0).possible_bad != null && calibrations.get(0).possible_bad == true && calibrations.get(1).possible_bad != null && calibrations.get(1).possible_bad != true) {
                    notificationText.setText("Possible bad calibration slope, please have a glass of water, wash hands, then recalibrate in a few!");
                }
                displayCurrentInfo();
            } else {
                notificationText.setText("Please enter two calibrations to get started!");
            }
        } else {
            if (BgReading.latestUnCalculated(2).size() < 2) {
                notificationText.setText("Please wait, need 2 readings from transmitter first.");
            } else {
                List<Calibration> calibrations = Calibration.latest(2);
                if (calibrations.size() < 2) {
                    notificationText.setText("Please enter two calibrations to get started!");
                }
            }
        }
    }

    private void updateCurrentBgInfoForBtShare(TextView notificationText) {
        if ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            notificationText.setText("Unfortunately your android version does not support Bluetooth Low Energy");
            return;
        }

        String receiverSn = mPreferences.getString("share_key", "SM00000000").toUpperCase();
        if (receiverSn.compareTo("SM00000000") == 0 || receiverSn.length() == 0) {
            notificationText.setText("Please set your Dex Receiver Serial Number in App Settings");
            return;
        }

        if (receiverSn.length() < 10) {
            notificationText.setText("Double Check Dex Receiver Serial Number, should be 10 characters, don't forget the letters");
            return;
        }

        if (ActiveBluetoothDevice.first() == null) {
            notificationText.setText("Now pair with your Dexcom Share");
            return;
        }

        if (!Sensor.isActive()) {
            notificationText.setText("Now choose start your sensor in your settings");
            return;
        }

        displayCurrentInfo();
    }

    private void displayCurrentInfo() {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);

        boolean isDexbridge = CollectionServiceStarter.isDexbridgeWixelorWifiandDexbridgeWixel(getApplicationContext());
        boolean displayBattery = mPreferences.getBoolean("display_bridge_battery",false);
        int bridgeBattery = mPreferences.getInt("bridge_battery", 0);

        if (isDexbridge && displayBattery) {
            if(BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
                this.dexbridgeBattery.setTextSize(25);
            } else if(BgGraphBuilder.isLargeTablet(getApplicationContext())) {
                this.dexbridgeBattery.setTextSize(18);
            }
            if (bridgeBattery == 0) {
                dexbridgeBattery.setText("xBridge Battery: Unknown, Waiting for packet");
                dexbridgeBattery.setTextColor(Color.WHITE);
            } else {
                dexbridgeBattery.setText("xBridge Battery: " + bridgeBattery + "%");
            }
            dexbridgeBattery.setTextColor(Color.parseColor("#00FF00"));
            if (bridgeBattery < 50 && bridgeBattery >30) dexbridgeBattery.setTextColor(Color.YELLOW);
            if (bridgeBattery <= 30) dexbridgeBattery.setTextColor(Color.RED);
            dexbridgeBattery.setVisibility(View.VISIBLE);
        } else {
            dexbridgeBattery.setText("");
            dexbridgeBattery.setVisibility(View.INVISIBLE);
        }

        if ((currentBgValueText.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) > 0) {
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }
        BgReading lastBgReading = BgReading.lastNoSenssor();
        boolean predictive = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("predictive_bg", false);
        if (isBTShare) {
            predictive = false;
        }
        if (lastBgReading != null) {
            displayCurrentInfoFromReading(lastBgReading, predictive);
        }
    }

    @NonNull
    public static String extraStatusLine(SharedPreferences prefs) {
        StringBuilder extraline = new StringBuilder();
        Calibration lastCalibration = Calibration.last();
        if (prefs.getBoolean("status_line_calibration_long", true) && lastCalibration != null){
            if(extraline.length()!=0) extraline.append(' ');
            extraline.append("slope = ");
            extraline.append(String.format("%.2f",lastCalibration.slope));
            extraline.append(' ');
            extraline.append("inter = ");
            extraline.append(String.format("%.2f", lastCalibration.intercept));
        }

        if(prefs.getBoolean("status_line_calibration_short", false) && lastCalibration != null) {
            if(extraline.length()!=0) extraline.append(' ');
            extraline.append("s:");
            extraline.append(String.format("%.2f",lastCalibration.slope));
            extraline.append(' ');
            extraline.append("i:");
            extraline.append(String.format("%.2f", lastCalibration.intercept));
        }

        if(prefs.getBoolean("status_line_avg", false)
                || prefs.getBoolean("status_line_a1c_dcct", false)
                || prefs.getBoolean("status_line_a1c_ifcc", false
                || prefs.getBoolean("status_line_in", false))
                || prefs.getBoolean("status_line_high", false)
                || prefs.getBoolean("status_line_low", false)){

            StatsResult statsResult = new StatsResult(prefs);

            if(prefs.getBoolean("status_line_avg", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getAverageUnitised());
            }
            if(prefs.getBoolean("status_line_a1c_dcct", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getA1cDCCT());
            }
            if(prefs.getBoolean("status_line_a1c_ifcc", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getA1cIFCC());
            }
            if(prefs.getBoolean("status_line_in", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getInPercentage());
            }
            if(prefs.getBoolean("status_line_high", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getHighPercentage());
            }
            if(prefs.getBoolean("status_line_low", false)) {
                if(extraline.length()!=0) extraline.append(' ');
                extraline.append(statsResult.getLowPercentage());
            }
        }
        if(prefs.getBoolean("status_line_time", false)) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            if(extraline.length()!=0) extraline.append(' ');
            extraline.append(sdf.format(new Date()));
        }
        return extraline.toString();
    }

    private void displayCurrentInfoFromReading(BgReading lastBgReading, boolean predictive) {
        double estimate = 0;
        if ((new Date().getTime()) - (60000 * 11) - lastBgReading.timestamp > 0) {
            notificationText.setText("Signal Missed");
            if (!predictive) {
                estimate = lastBgReading.calculated_value;
            } else {
                estimate = BgReading.estimated_bg(lastBgReading.timestamp + (6000 * 7));
            }
            currentBgValueText.setText(bgGraphBuilder.unitized_string(estimate));
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            if (notificationText.getText().length()==0){
                notificationText.setTextColor(Color.WHITE);
            }
            if (!predictive) {
                estimate = lastBgReading.calculated_value;
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                String slope_arrow = lastBgReading.slopeArrow();
                if (lastBgReading.hide_slope) {
                    slope_arrow = "";
                }
                currentBgValueText.setText(stringEstimate + " " + slope_arrow);
            } else {
                estimate = BgReading.activePrediction();
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                currentBgValueText.setText(stringEstimate + " " + BgReading.activeSlopeArrow());
            }
        }
        int minutes = (int)(System.currentTimeMillis() - lastBgReading.timestamp) / (60 * 1000);
        String minutesString;
        if(BgGraphBuilder.isXLargeTablet(getApplicationContext()) || BgGraphBuilder.isLargeTablet(getApplicationContext())) {
            minutesString = " Min ago";
        } else {
            minutesString = minutes==1 ?" Minute ago":" Minutes ago";
        }
        notificationText.append("\n" + minutes + minutesString);
        List<BgReading> bgReadingList = BgReading.latest(2);
        if(bgReadingList != null && bgReadingList.size() == 2) {
            // same logic as in xDripWidget (refactor that to BGReadings to avoid redundancy / later inconsistencies)?
            if(BgGraphBuilder.isXLargeTablet(getApplicationContext()) || BgGraphBuilder.isLargeTablet(getApplicationContext())) {
                notificationText.append("  ");
            } else {
                notificationText.append("\n");
            }
            notificationText.append(
                    bgGraphBuilder.unitizedDeltaString(true, true));
        }
        if(bgGraphBuilder.unitized(estimate) <= bgGraphBuilder.lowMark) {
            currentBgValueText.setTextColor(Color.parseColor("#FF0000"));
        } else if (bgGraphBuilder.unitized(estimate) >= bgGraphBuilder.highMark) {
            currentBgValueText.setTextColor(Color.parseColor("#FFFF00"));
        } else {
            currentBgValueText.setTextColor(Color.WHITE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);

        //wear integration
        if (!mPreferences.getBoolean("wear_sync", false)) {
            menu.removeItem(R.id.action_open_watch_settings);
            menu.removeItem(R.id.action_resend_last_bg);
        }

        //speak readings
        MenuItem menuItem =  menu.findItem(R.id.action_toggle_speakreadings);
        if(mPreferences.getBoolean("bg_to_speech_shortcut", false)){
            menuItem.setVisible(true);
            if(mPreferences.getBoolean("bg_to_speech", false)){
                menuItem.setChecked(true);
            } else {
                menuItem.setChecked(false);
            }
        } else {
            menuItem.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_resend_last_bg:
                startService(new Intent(this, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_RESEND));
                break;
            case R.id.action_open_watch_settings:
                startService(new Intent(this, WatchUpdaterService.class).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS));
        }

        if (item.getItemId() == R.id.action_export_database) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    int permissionCheck = ContextCompat.checkSelfPermission(Home.this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(Home.this,
                                new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                0);
                        return null;
                    } else {
                        return DatabaseUtil.saveSql(getBaseContext());
                    }

                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);
                    if (filename != null) {
                        SnackbarManager.show(
                                Snackbar.with(Home.this)
                                        .type(SnackbarType.MULTI_LINE)
                                        .duration(4000)
                                        .text("Exported to " + filename) // text to display
                                        .actionLabel("Share") // action button label
                                        .actionListener(new SnackbarUriListener(Uri.fromFile(new File(filename)))),
                                Home.this);
                    } else {
                        Toast.makeText(Home.this, "Could not export Database :(", Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();

            return true;
        }

        if (item.getItemId() == R.id.action_import_db) {
            startActivity(new Intent(this, ImportDatabaseActivity.class));
            return true;
        }



        if (item.getItemId() == R.id.action_export_csv_sidiary) {
          new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    int permissionCheck = ContextCompat.checkSelfPermission(Home.this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(Home.this,
                                new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                0);
                        return null;
                    } else {
                        return DatabaseUtil.saveCSV(getBaseContext());
                    }
                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);
                    if (filename != null) {
                        SnackbarManager.show(
                                Snackbar.with(Home.this)
                                        .type(SnackbarType.MULTI_LINE)
                                        .duration(4000)
                                        .text("Exported to " + filename) // text to display
                                        .actionLabel("Share") // action button label
                                        .actionListener(new SnackbarUriListener(Uri.fromFile(new File(filename)))),
                                Home.this);
                    } else {
                        Toast.makeText(Home.this, "Could not export CSV :(", Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();

            return true;
        }

        if (item.getItemId() == R.id.action_toggle_speakreadings) {
            mPreferences.edit().putBoolean("bg_to_speech", !mPreferences.getBoolean("bg_to_speech", false)).commit();
            invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void settingsSDcardExport(MenuItem myitem) {
        startActivity(new Intent(getApplicationContext(), SdcardImportExport.class));
    }


    private class ChartViewPortListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingPreviewViewport) {
                updatingChartViewport = true;
                previewChart.setZoomType(ZoomType.HORIZONTAL);
                previewChart.setCurrentViewport(newViewport);
                updatingChartViewport = false;
            }
        }
    }

    private class ViewportListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingChartViewport) {
                updatingPreviewViewport = true;
                chart.setZoomType(ZoomType.HORIZONTAL);
                chart.setCurrentViewport(newViewport);
                tempViewport = newViewport;
                updatingPreviewViewport = false;
            }
            if (updateStuff) {
                holdViewport.set(newViewport.left, newViewport.top, newViewport.right, newViewport.bottom);
            }
        }

    }

    class SnackbarUriListener implements ActionClickListener {
        Uri uri;

        SnackbarUriListener(Uri uri) {
            this.uri = uri;
        }

        @Override
        public void onActionClicked(Snackbar snackbar) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setType("application/octet-stream");
            startActivity(Intent.createChooser(shareIntent, "Share database..."));
        }
    }
}
