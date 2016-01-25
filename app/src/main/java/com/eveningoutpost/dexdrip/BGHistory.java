package com.eveningoutpost.dexdrip;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


public class BGHistory extends ActivityWithMenu {
    static String TAG = BGHistory.class.getName();
    public static String menu_name = "BG History";
    private boolean updateStuff;
    private boolean updatingPreviewViewport = false;
    private boolean updatingChartViewport = false;
    private BgGraphBuilder bgGraphBuilder;
    private Viewport holdViewport = new Viewport();
    private LineChartView            chart;
    private PreviewLineChartView     previewChart;
    private GregorianCalendar date;
    private DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
    private Button prevButton;
    private Button nextButton;
    private Button dateButton;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bghistory);

        date = new GregorianCalendar();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        setupButtons();
        setupCharts();

        Toast.makeText(this, (String) "Double tap or pinch to zoom.",
                Toast.LENGTH_LONG).show();
    }

    private void setupButtons() {
        this.prevButton = (Button) findViewById(R.id.button_prev);
        this.nextButton = (Button) findViewById(R.id.button_next);
        this.dateButton = (Button) findViewById(R.id.button_date);

        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                date.add(Calendar.DATE, -1);
                setupCharts();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                date.add(Calendar.DATE, 1);
                setupCharts();
            }
        });

        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialog dialog = new DatePickerDialog(BGHistory.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        date.set(year, monthOfYear, dayOfMonth);
                        setupCharts();
                    }
                }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
                dialog.show();
            }
        });

    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    private void setupCharts() {
        dateButton.setText(dateFormatter.format(date.getTime()));
        Calendar endDate = new GregorianCalendar();
        endDate.setTimeInMillis(date.getTimeInMillis());
        endDate.roll(Calendar.DATE, true);
        bgGraphBuilder = new BgGraphBuilder(this, date.getTimeInMillis(), endDate.getTimeInMillis());
        updateStuff = false;
        chart = (LineChartView) findViewById(R.id.chart);

        chart.setZoomType(ZoomType.HORIZONTAL);
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
            if (updateStuff) {
                holdViewport.set(newViewport.left, newViewport.top, newViewport.right, newViewport.bottom);
            }
        }

    }

}
