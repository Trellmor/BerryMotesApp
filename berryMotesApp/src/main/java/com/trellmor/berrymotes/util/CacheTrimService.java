/*
 * BerryMotes
 * Copyright (C) 2013-2016 Daniel Triendl <trellmor@trellmor.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.trellmor.berrymotes.util;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import com.trellmor.berrymotes.provider.LogProvider;

public class CacheTrimService extends Service {
	private static final String TAG = CacheTrimService.class.getName();
	private static final long MAX_CACHE_SIZE = 25 * 1024 * 1024; //25 MB
	private static final long MAX_CACHE_AGE = 7 * 24 * 60 * 60 * 1000; //One week

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		File cache = this.getCacheDir();
		new CacheTrimTask().execute(cache);
		new LogTrimTask(this).execute();
		
		return START_NOT_STICKY;
	}
	
	private class CacheTrimTask extends AsyncTask<File, Void, Void> {

		@Override
		protected Void doInBackground(File... params) {			
			File cache = params[0];
			
			if (cache.exists() && cache.isDirectory()) {
				File[] files = cache.listFiles();
				Arrays.sort(files, new Comparator<File>() {

					@Override
					public int compare(File lhs, File rhs) {
						if (lhs.lastModified() < rhs.lastModified()) {
							return 1;
						} else if (lhs.lastModified() > rhs.lastModified()) {
							return -1;
						} else {
							return 0;
						}
					}
					
				});
			
				long totalSize = 0;
				long maxAge = System.currentTimeMillis() - MAX_CACHE_AGE;
				for (File file : files) {
					if (totalSize > MAX_CACHE_SIZE || file.lastModified() < maxAge) {
						Log.d(TAG, "Deleting cached file " + file.getName());
						file.delete();				
					} else {	
						totalSize += file.length();
					}
				}
			}
			return null;
		}
		
	}

	private class LogTrimTask extends AsyncTask<Void, Void, Void> {
		private final Context mContext;

		public LogTrimTask(Context context) {
			mContext = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			ContentResolver contentResolver = mContext.getContentResolver();
			Cursor c = contentResolver.query(LogProvider.CONTENT_URI_LOGS, new String[]
					{LogProvider.LogsColumns._ID}, null, null,
					LogProvider.LogsColumns._ID + " DESC");
			if (c != null && c.getCount() > 1000) {
				final int POS_ID = c.getColumnIndex(LogProvider.LogsColumns._ID);
				c.moveToPosition(1000);
				final long TRUNC_ID = c.getLong(POS_ID);
				c.close();

				contentResolver.delete(LogProvider.CONTENT_URI_LOGS,
						LogProvider.LogsColumns._ID + " <= ?", new String[] {String.valueOf(TRUNC_ID)});
			}

			return null;
		}
	}
}
