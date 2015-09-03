package com.eveningoutpost.dexdrip.email;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/**
 * Created by adrian on 03/09/15.
 */
public class Emailer {

    public static void emailDatabase(String filename, Activity activity){
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filename)));
        shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"adrian.m@inbox.com"});
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "xDrip database export");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "This is my xDrip database.\n" +
                "Please feel free to use the data to improve the programme and share this export with other developers.");
        shareIntent.setType("message/rfc822");
        activity.startActivity(Intent.createChooser(shareIntent, "Select Email Client to share with xDrip developers:"));
    }
}
