/*
 * BerryMotes android 
 * Copyright (C) 2014-2015 Daniel Triendl <trellmor@trellmor.com>
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.Environment;
import android.preference.PreferenceManager;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.BasicLogcatConfigurator;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

import com.google.gson.Gson;
import com.trellmor.berrymotes.Settings;
import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.util.CheckListPreference;
import com.trellmor.berrymotes.util.NetworkNotAvailableException;
import com.trellmor.berrymotes.util.StorageNotAvailableException;

public class EmoteDownloader {
	public static final String HOST = "http://berrymotes.pew.cc/";

	private static final String SUBREDDITS = "subreddits.json.gz";
	private static final String USER_AGENT = "BerryMotes Android sync";

	private static final int THREAD_COUNT = 4;

	private final Context mContext;
	private final ContentResolver mContentResolver;
	private final CheckListPreference mSubreddits;

	private final boolean mWiFiOnly;
	private int mNetworkType;
	private boolean mIsConnected;

	private AndroidHttpClient mHttpClient;

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
		mSubreddits = new CheckListPreference(settings.getString(
				Settings.KEY_SYNC_SUBREDDITS,
				Settings.DEFAULT_SYNC_SUBREDDITS),
				Settings.SEPARATOR_SYNC_SUBREDDITS,
				Settings.ALL_KEY_SYNC_SUBREDDITS);

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

		mHttpClient = AndroidHttpClient.newInstance(USER_AGENT);
		try {
			String[] subreddits = getSubreddits();

			for (String subreddit : subreddits) {
				if (mSubreddits.isChecked(subreddit)) {
					Runnable subredditEmoteDownloader = new SubredditEmoteDownloader(
							mContext, this, subreddit);
					executor.execute(subredditEmoteDownloader);
				} else {
					// Delete this subreddit
					deleteSubreddit(subreddit, mContentResolver);
					// Reset last download date
					SharedPreferences.Editor settings = PreferenceManager
							.getDefaultSharedPreferences(mContext).edit();
					settings.remove(Settings.KEY_SYNC_LAST_MODIFIED
							+ subreddit);
					settings.commit();
				}
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (URISyntaxException e) {
			Log.error("Emotes URL is malformed", e);
			synchronized (mSyncResult) {
				mSyncResult.stats.numParseExceptions++;
				if (mSyncResult.delayUntil < 60 * 60)
					mSyncResult.delayUntil = 60 * 60;
			}
			return;
		} catch (IOException e) {
			Log.error("Error reading from network: " + e.getMessage(), e);
			synchronized (mSyncResult) {
				mSyncResult.stats.numIoExceptions++;
				if (mSyncResult.delayUntil < 30 * 60)
					mSyncResult.delayUntil = 30 * 60;
			}
			return;
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
			Log.info("Deleted emotes: "
					+ Long.toString(mSyncResult.stats.numDeletes));
			Log.info("Added emotes: "
					+ Long.toString(mSyncResult.stats.numInserts));

			// Unregisters BroadcastReceiver at the end
			mContext.unregisterReceiver(receiver);

			mHttpClient.close();
		}

		Log.info("EmoteDownload finished");
	}

	public void deleteSubreddit(String subreddit,
			ContentResolver contentResolver) throws IOException {

		Log.info(" Removing emotes of " + subreddit);
		Cursor c = contentResolver.query(
				EmotesContract.Emote.CONTENT_URI_DISTINCT,
				new String[] { EmotesContract.Emote.COLUMN_IMAGE },
				EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
				new String[] { subreddit }, null);

		if (c.moveToFirst()) {
			final int POS_IMAGE = c
					.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE);

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
		Log.info("Removed emotes: " + Integer.toString(deletes));
		synchronized (mSyncResult) {
			mSyncResult.stats.numDeletes += deletes;
		}
	}

	private String[] getSubreddits() throws IOException, URISyntaxException {
		Log.debug("Downloading emote list");
		HttpRequestBase request = new HttpGet();
		request.setURI(new URI(HOST + SUBREDDITS));

		this.checkCanDownload();
		HttpResponse response = mHttpClient.execute(request);
		switch (response.getStatusLine().getStatusCode()) {
		case 200:
			Log.debug(SUBREDDITS + " loaded");

			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream is = entity.getContent();
				GZIPInputStream zis = null;
				Reader isr = null;
				try {
					zis = new GZIPInputStream(is);
					isr = new InputStreamReader(zis, "UTF-8");

					Gson gson = new Gson();
					return gson.fromJson(isr, String[].class);
				} finally {
					StreamUtils.closeStream(isr);
					StreamUtils.closeStream(zis);
					StreamUtils.closeStream(is);
				}
			}
			break;
		default:
			throw new IOException("Unexpected HTTP response: "
					+ response.getStatusLine().getReasonPhrase());
		}
		return null;
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

	private boolean canDownload() {
		synchronized (this) {
			return mIsConnected && !(mWiFiOnly && mNetworkType != ConnectivityManager.TYPE_WIFI);
		}
	}

	public void checkCanDownload() throws IOException {
		if (!this.canDownload()) {
			throw new NetworkNotAvailableException(
					"Download currently not possible");
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

		// Log to logcat
		BasicLogcatConfigurator.configureDefaultContext();

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

			ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
					.getLogger(Logger.ROOT_LOGGER_NAME);
			root.addAppender(fileAppender);
		}
	}

	public AndroidHttpClient getHttpClient() {
		return mHttpClient;
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
