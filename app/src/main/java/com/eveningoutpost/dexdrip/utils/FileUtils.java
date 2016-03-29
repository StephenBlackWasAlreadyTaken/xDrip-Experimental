package com.eveningoutpost.dexdrip.utils;

import android.content.Context;
import android.os.Environment;
import com.eveningoutpost.dexdrip.R;

import java.io.File;

public class FileUtils {

    public static String getDirectoryName(Context context) {
      String appName = context.getString(R.string.app_name);
      return appName.toLowerCase();
    }
	public static boolean makeSureDirectoryExists( final String dir ) {
		final File file = new File( dir );
        return file.exists() || file.mkdirs();
	}

	public static String getExternalDir(Context context) {
		final StringBuilder sb = new StringBuilder();
		sb.append( Environment.getExternalStorageDirectory().getAbsolutePath() );
		sb.append( "/" + getDirectoryName(context) );

		final String dir = sb.toString();
		return dir;
	}

	public static String combine( final String path1, final String path2 ) {
		final File file1 = new File( path1 );
		final File file2 = new File( file1, path2 );
		return file2.getPath();
	}
}
