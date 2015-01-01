/*
 * BerryMotes android 
 * Copyright (C) 2013 Daniel Triendl <trellmor@trellmor.com>
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

package com.trellmor.berrymotes;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

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
}
