package com.eveningoutpost.dexdrip;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


public class BGHistory extends ActivityWithMenu {
    public static String menu_name = "BG History";
    static String TAG = BGHistory.class.getName();
    private boolean updatingPreviewViewport = false;
    private boolean updatingChartViewport = false;
    private Viewport holdViewport = new Viewport();
    private LineChartView chart;
    private PreviewLineChartView previewChart;
    private GregorianCalendar date1;
    private GregorianCalendar date2;
    private DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
    private Button dateButton1;
    private Button dateButton2;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bghistory);

        date1 = new GregorianCalendar();
        date1.set(Calendar.HOUR_OF_DAY, 0);
        date1.set(Calendar.MINUTE, 0);
        date1.set(Calendar.SECOND, 0);
        date1.set(Calendar.MILLISECOND, 0);
        date2 = new GregorianCalendar();
        date2.set(Calendar.HOUR_OF_DAY, 0);
        date2.set(Calendar.MINUTE, 0);
        date2.set(Calendar.SECOND, 0);
        date2.set(Calendar.MILLISECOND, 0);

        setupButtons();
        setupCharts();

        Toast.makeText(this, (String) "Double tap or pinch to zoom.",
                Toast.LENGTH_LONG).show();
    }

    private void setupButtons() {
        Button prevButton = (Button) findViewById(R.id.button_prev);
        Button nextButton = (Button) findViewById(R.id.button_next);
        this.dateButton1 = (Button) findViewById(R.id.button_date1);
        this.dateButton2 = (Button) findViewById(R.id.button_date2);


        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int daysBetween = daysBetween(date1, date2);
                date1.add(Calendar.DATE, -1 - daysBetween);
                date2.add(Calendar.DATE, -1 - daysBetween);
                setupCharts();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int daysBetween = daysBetween(date1, date2);
                date1.add(Calendar.DATE, 1 + daysBetween);
                date2.add(Calendar.DATE, 1 + daysBetween);
                setupCharts();
            }
        });

        dateButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialog dialog = new DatePickerDialog(BGHistory.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        date1.set(year, monthOfYear, dayOfMonth);
                        setupCharts();
                    }
                }, date1.get(Calendar.YEAR), date1.get(Calendar.MONTH), date1.get(Calendar.DAY_OF_MONTH));
                dialog.show();
            }
        });

        dateButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialog dialog = new DatePickerDialog(BGHistory.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        date2.set(year, monthOfYear, dayOfMonth);
                        setupCharts();
                    }
                }, date2.get(Calendar.YEAR), date2.get(Calendar.MONTH), date2.get(Calendar.DAY_OF_MONTH));
                dialog.show();
            }
        });


    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    private void setupCharts() {
        dateButton1.setText(dateFormatter.format(date1.getTime()));
        dateButton2.setText(dateFormatter.format(date2.getTime()));
        Calendar endDate = new GregorianCalendar();
        long startTime;
        if(date1.compareTo(date2)>0){
            endDate.setTimeInMillis(date1.getTimeInMillis());
            startTime = date2.getTimeInMillis();
        } else {
            endDate.setTimeInMillis(date2.getTimeInMillis());
            startTime = date1.getTimeInMillis();
        }
        endDate.add(Calendar.DATE, 1);
        int numValues = (daysBetween(date1, date2) + 1) * (60/5)*24;
        BgGraphBuilder bgGraphBuilder = new BgGraphBuilder(this, startTime, endDate.getTimeInMillis(), numValues);
        chart = (LineChartView) findViewById(R.id.chart);

        chart.setZoomType(ZoomType.HORIZONTAL);
        previewChart = (PreviewLineChartView) findViewById(R.id.chart_preview);
        previewChart.setZoomType(ZoomType.HORIZONTAL);

        chart.setLineChartData(bgGraphBuilder.lineData());
        chart.setOnValueTouchListener(bgGraphBuilder.getOnValueSelectTooltipListener());
        previewChart.setLineChartData(bgGraphBuilder.previewLineData());

        previewChart.setViewportCalculationEnabled(true);
        chart.setViewportCalculationEnabled(true);
        previewChart.setViewportChangeListener(new ViewportListener());
        chart.setViewportChangeListener(new ChartViewPortListener());
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
                updatingPreviewViewport = false;
            }
        }
    }

    private int daysBetween(Calendar calendar1, Calendar calendar2){
        Calendar first, second;
        if(calendar1.compareTo(calendar2)>0){
            first = calendar2;
            second = calendar1;
        } else {
            first = calendar1;
            second = calendar2;
        }
        int days = second.get(Calendar.DAY_OF_YEAR) - first.get(Calendar.DAY_OF_YEAR);
        Calendar temp = (Calendar) first.clone();
        while (temp.get(Calendar.YEAR) < second.get(Calendar.YEAR)){
            days = days + temp.getActualMaximum(Calendar.DAY_OF_YEAR);
            temp.add(Calendar.YEAR, 1);
        }
        return days;
    }

}
