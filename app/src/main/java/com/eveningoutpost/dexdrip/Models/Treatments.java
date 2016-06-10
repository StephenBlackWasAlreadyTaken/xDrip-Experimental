package com.eveningoutpost.dexdrip.Models;

/**
 * Created by jamorham on 20/02/2016.
 */

import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.google.gson.annotations.Expose;

// Nightscout Treatments database fields

@Table(name = "Treatments", id = BaseColumns._ID)
public class Treatments extends Model {
    @Expose
    @Column(name = "timestamp", index = true)
    public long timestamp;

    @Expose
    @Column(name = "eventType")
    public String eventType;

    @Expose
    @Column(name = "enteredBy")
    public String enteredBy;

    @Expose
    @Column(name = "notes")
    public String notes;

    @Expose
    @Column(name = "uuid", unique = true, onUniqueConflicts = Column.ConflictAction.IGNORE)
    public String uuid;

    @Expose
    @Column(name = "carbs")
    public double carbs;

    @Expose
    @Column(name = "insulin")
    public double insulin;

    @Expose
    @Column(name = "created_at")
    public String created_at;

}