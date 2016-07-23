/*
 * BerryMotes
 * Copyright (C) 2014-2016 Daniel Triendl <trellmor@trellmor.com>
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

package com.trellmor.berrymotes.sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.BasicLogcatConfigurator;
import ch.qos.logback.classic.android.ContentProviderAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

import com.trellmor.berrymotes.util.Settings;
import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.provider.LogProvider;
import com.trellmor.berrymotes.provider.SubredditProvider;
import com.trellmor.berrymotes.util.NetworkNotAvailableException;
import com.trellmor.berrymotes.util.StorageNotAvailableException;

public class EmoteDownloader {

	private static final int THREAD_COUNT = 4;

	private final Context mContext;
	private final ContentResolver mContentResolver;
	private final Boolean mAllSubreddits;

	private final boolean mWiFiOnly;
	private int mNetworkType;
	private boolean mIsConnected;

	private SyncResult mSyncResult = null;

	private final Logger Log;
	public static final String LOG_FILE_NAME = "EmoteDownloader.log";

	public EmoteDownloader(Context context) {
		mContext = context;

		initLogging();

		Log = LoggerFactory.getLogger(EmoteDownloader.class);

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		mWiFiOnly = settings.getString(Settings.KEY_SYNC_CONNECTION,
				Settings.VALUE_SYNC_CONNECTION_WIFI).equals(
				Settings.VALUE_SYNC_CONNECTION_WIFI);
		mAllSubreddits = settings.getBoolean(Settings.KEY_SYNC_ALL_SUBREDDITS, true);

		mContentResolver = mContext.getContentResolver();
	}

	public void start(SyncResult syncResult) {
		Log.info("EmoteDownload started");

		this.updateNetworkInfo();

		mSyncResult = syncResult;

		if (!mIsConnected) {
			Log.error("Network not available");
			syncResult.stats.numIoExceptions++;
			return;
		}

		// Registers BroadcastReceiver to track network connection changes.
		IntentFilter filter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		NetworkReceiver receiver = new NetworkReceiver();
		mContext.registerReceiver(receiver, filter);

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

		try {
			checkCanDownload();

			Thread thread = new Thread(new SubredditDownloader(mContext, true));
			thread.start();
			thread.join();

			Cursor c = mContentResolver.query(SubredditProvider.CONTENT_URI_SUBREDDITS, new String[] {
					SubredditProvider.SubredditColumns._ID,
					SubredditProvider.SubredditColumns.COLUMN_NAME,
					SubredditProvider.SubredditColumns.COLUMN_ENABLED}, null, null, null);

			if (c != null && c.getCount() > 0) {
				c.moveToFirst();
				ArrayList<String> enabledSubreddits = new ArrayList<>();
				ArrayList<String> deleteSubreddits = new ArrayList<>();

				final int POS_ID = c.getColumnIndex(SubredditProvider.SubredditColumns._ID);
				final int POS_NAME = c.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_NAME);
				final int POS_ENABLED = c.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_ENABLED);

				do {
					if (mAllSubreddits || c.getInt(POS_ENABLED) == 1) {
						Runnable subredditEmoteDownloader = new SubredditEmoteDownloader(
								mContext, this, c.getString(POS_NAME));
						executor.execute(subredditEmoteDownloader);
						enabledSubreddits.add(c.getString(POS_NAME));
					} else {
						deleteSubreddits.add(c.getString(POS_NAME));
						// Reset last download date
						Uri uri = SubredditProvider.CONTENT_URI_SUBREDDITS.buildUpon().appendPath(String.valueOf(c.getInt(POS_ID))).build();
						ContentValues values = new ContentValues();
						values.put(SubredditProvider.SubredditColumns.COLUMN_LAST_SYNC, 0);
						mContentResolver.update(uri, values, null, null);
					}
				} while (c.moveToNext());

				Cursor cursorCurrent = mContentResolver.query(EmotesContract.Emote.CONTENT_URI_DISTINCT,
						new String[]{EmotesContract.Emote.COLUMN_SUBREDDIT}, null, null, null);

				if (cursorCurrent != null && cursorCurrent.getCount() > 0) {
					cursorCurrent.moveToFirst();

					final int POS_SUBREDDIT = cursorCurrent.getColumnIndex(EmotesContract.Emote.COLUMN_SUBREDDIT);

					do {
						String subreddit = cursorCurrent.getString(POS_SUBREDDIT);
						if (!enabledSubreddits.contains(subreddit)) {
							if (!deleteSubreddits.contains(subreddit)) {
								deleteSubreddits.add(subreddit);
							}
						}
					} while (cursorCurrent.moveToNext());
				}

				for (String subreddit : deleteSubreddits) {
					// Delete this subreddit
					deleteSubreddit(subreddit, mContentResolver);
				}
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}  catch (IOException e) {
			Log.error("Error reading from network: " + e.getMessage(), e);
			synchronized (mSyncResult) {
				mSyncResult.stats.numIoExceptions++;
				if (mSyncResult.delayUntil < 30 * 60)
					mSyncResult.delayUntil = 30 * 60;
			}
		} catch (InterruptedException e) {
			synchronized (mSyncResult) {
				syncResult.moreRecordsToGet = true;
			}

			Log.info("Sync interrupted");

			executor.shutdownNow();
			try {
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			} catch (InterruptedException e2) {
			}

			Thread.currentThread().interrupt();
		} finally {
			Log.info("Deleted emotes: {}", mSyncResult.stats.numDeletes);
			Log.info("Added emotes: {}", mSyncResult.stats.numInserts);

			// Unregisters BroadcastReceiver at the end
			mContext.unregisterReceiver(receiver);
		}

		Log.info("EmoteDownload finished");
	}

	public void deleteSubreddit(String subreddit, ContentResolver contentResolver) throws IOException {

		Log.debug("Removing emotes of {}", subreddit);
		Cursor c = contentResolver.query(
				EmotesContract.Emote.CONTENT_URI_DISTINCT,
				new String[] { EmotesContract.Emote.COLUMN_IMAGE },
				EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
				new String[] { subreddit }, null);

		if (c.moveToFirst()) {
			final int POS_IMAGE = c.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE);

			do {
				checkStorageAvailable();
				File file = new File(c.getString(POS_IMAGE));
				if (file.exists()) {
					file.delete();
				}
			} while (c.moveToNext());
		}

		c.close();

		int deletes = mContentResolver.delete(EmotesContract.Emote.CONTENT_URI,
				EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
				new String[] { subreddit });
		if (deletes > 0) {
			Log.info("{} deleted, removed {} emotes", subreddit, deletes);
		}
		synchronized (mSyncResult) {
			mSyncResult.stats.numDeletes += deletes;
		}
	}

	private void updateNetworkInfo() {
		synchronized (this) {
			ConnectivityManager cm = (ConnectivityManager) mContext
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = cm.getActiveNetworkInfo();

			mIsConnected = networkInfo != null && networkInfo.isConnected();

			if (networkInfo != null) {
				mNetworkType = networkInfo.getType();
			} else {
				mNetworkType = Integer.MIN_VALUE;
			}
		}
	}

	private boolean isConnected() {
		synchronized (this) {
			return mIsConnected;
		}
	}

	private boolean isAllowedNetworkType() {
		synchronized (this) {
			return !(mWiFiOnly && mNetworkType != ConnectivityManager.TYPE_WIFI);
		}
	}

	public void checkCanDownload() throws IOException {
		if (!this.isConnected()) {
			throw new NetworkNotAvailableException("No network connection");
		} else if (!this.isAllowedNetworkType()) {
			throw new NetworkNotAvailableException("Downloading on mobile is disabled");
		}
	}

	private boolean isStorageAvailable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	public void checkStorageAvailable() throws IOException {
		if (!this.isStorageAvailable()) {
			throw new StorageNotAvailableException("Storage not available");
		}
	}

	private void initLogging() {
		// reset the default context (which may already have been initialized)
		// since we want to reconfigure it
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		lc.reset();


		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
				.getLogger(Logger.ROOT_LOGGER_NAME);

		// Log to logcat
		BasicLogcatConfigurator.configureDefaultContext();

		ContentProviderAppender contentProviderAppender = new ContentProviderAppender(mContext.getApplicationContext());
		contentProviderAppender.setContext(lc);
		contentProviderAppender.setLogsUri(LogProvider.CONTENT_URI_LOGS);

		ThresholdFilter filter = new ThresholdFilter();
		filter.setContext(lc);
		filter.setLevel("INFO");
		filter.start();

		contentProviderAppender.addFilter(filter);
		contentProviderAppender.start();
		root.addAppender(contentProviderAppender);

		// If logging is enabled in settings, also log to file
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		if (settings.getBoolean(Settings.KEY_LOG, false)) {
			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(lc);
			encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
			encoder.start();

			FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
			fileAppender.setContext(lc);
			fileAppender
					.setFile(new File(mContext.getFilesDir(), LOG_FILE_NAME)
							.getAbsolutePath());
			fileAppender.setEncoder(encoder);
			fileAppender.start();
			root.addAppender(fileAppender);
		}
	}

	public void updateSyncResult(SyncResult syncResult) {
		synchronized (mSyncResult) {
			mSyncResult.stats.numAuthExceptions = syncResult.stats.numAuthExceptions;
			mSyncResult.stats.numIoExceptions = syncResult.stats.numIoExceptions;
			mSyncResult.stats.numParseExceptions = syncResult.stats.numParseExceptions;
			mSyncResult.stats.numConflictDetectedExceptions = syncResult.stats.numConflictDetectedExceptions;
			mSyncResult.stats.numInserts = syncResult.stats.numInserts;
			mSyncResult.stats.numUpdates = syncResult.stats.numUpdates;
			mSyncResult.stats.numDeletes = syncResult.stats.numDeletes;
			mSyncResult.stats.numEntries = syncResult.stats.numEntries;
			mSyncResult.stats.numSkippedEntries = syncResult.stats.numSkippedEntries;

			if (syncResult.tooManyDeletions)
				mSyncResult.tooManyDeletions = true;
			if (syncResult.tooManyRetries)
				mSyncResult.tooManyRetries = true;
			if (syncResult.fullSyncRequested)
				mSyncResult.fullSyncRequested = true;
			if (syncResult.partialSyncUnavailable)
				mSyncResult.partialSyncUnavailable = true;
			if (syncResult.moreRecordsToGet)
				mSyncResult.moreRecordsToGet = true;

			if (mSyncResult.delayUntil < syncResult.delayUntil)
				mSyncResult.delayUntil = syncResult.delayUntil;
		}
	}

	public class NetworkReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			EmoteDownloader.this.updateNetworkInfo();
		}
	}
}
