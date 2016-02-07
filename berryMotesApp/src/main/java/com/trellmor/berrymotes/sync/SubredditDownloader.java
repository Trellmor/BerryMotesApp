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

package com.trellmor.berrymotes.sync;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trellmor.berrymotes.api.Endpoints;
import com.trellmor.berrymotes.util.Settings;
import com.trellmor.berrymotes.provider.SubredditProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

public class SubredditDownloader implements Runnable {
	private final Logger Log = LoggerFactory.getLogger(SubredditDownloader.class);

	private static final String SUBREDDITS = "subreddits.json.gz";
	private static final Lock sLock = new ReentrantLock();
	private final boolean sWait;

	private final Context mContext;

	public SubredditDownloader(Context context) {
		this(context, false);
	}

	public SubredditDownloader(Context context, boolean wait) {
		mContext = context;
		sWait = wait;
	}

	@Override
	public void run() {
		if (sWait) {
			sLock.lock();
		} else if (!sLock.tryLock()) {
			return;
		}
		try {
			URL url = new URL(Endpoints.SYNC + SUBREDDITS);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			try {
				con.setIfModifiedSince(PreferenceManager.getDefaultSharedPreferences(mContext).getLong(Settings.KEY_SYNC_LAST_MODIFIED, 0));
				con.connect();
				switch (con.getResponseCode()) {
					case HttpURLConnection.HTTP_OK:
						Log.debug("{} loaded", SUBREDDITS);

						InputStream is = con.getInputStream();
						GZIPInputStream zis = null;
						Reader isr = null;
						List<Subreddit> subreddits;
						try {
							zis = new GZIPInputStream(is);
							isr = new InputStreamReader(zis, "UTF-8");

							Gson gson = new Gson();
							subreddits = gson.fromJson(isr, new TypeToken<ArrayList<Subreddit>>(){}.getType());
						} finally {
							StreamUtils.closeStream(isr);
							StreamUtils.closeStream(zis);
							StreamUtils.closeStream(is);
						}
						updateSubreddits(subreddits);
						SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
						editor.putLong(Settings.KEY_SYNC_LAST_MODIFIED, con.getLastModified());
						editor.commit();
						break;
					case HttpURLConnection.HTTP_NOT_MODIFIED:
						break;
					default:
						throw new IOException("Unexpected HTTP response: "
								+ con.getResponseMessage());
				}
			} finally {
				con.disconnect();
			}
		} catch (MalformedURLException e) {
			Log.error("Emotes URL is malformed", e);
		} catch (IOException e) {
			Log.error("Error reading from network: " + e.getMessage(), e);
		} finally {
			sLock.unlock();
		}
	}

	private void updateSubreddits(List<Subreddit> subreddits) {
		ContentResolver resolver = mContext.getContentResolver();
		Cursor c = resolver.query(SubredditProvider.CONTENT_URI_SUBREDDITS, new String[]{
				SubredditProvider.SubredditColumns._ID,
				SubredditProvider.SubredditColumns.COLUMN_NAME
		}, null, null, null);

		HashMap<String, Subreddit> subredditHash = new HashMap<>();
		for (Subreddit subreddit : subreddits) {
			subredditHash.put(subreddit.getName(), subreddit);
		}

		ArrayList<ContentProviderOperation> batch = new ArrayList<>();

		if (c != null && c.getCount() > 0) {
			c.moveToFirst();

			final int POS_ID = c.getColumnIndex(SubredditProvider.SubredditColumns._ID);
			final int POS_NAME = c.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_NAME);

			do {
				String name = c.getString(POS_NAME);
				if (subredditHash.containsKey(name)) {
					Subreddit subreddit = subredditHash.get(name);

					batch.add(ContentProviderOperation.newUpdate(
							SubredditProvider.CONTENT_URI_SUBREDDITS.buildUpon().appendPath(String.valueOf(c.getInt(POS_ID))).build())
							.withValue(SubredditProvider.SubredditColumns.COLUMN_SIZE, subreddit.getSize())
							.withValue(SubredditProvider.SubredditColumns.COLUMN_ADDED, subreddit.getAdded().getTime())
							.build());

					subredditHash.remove(name);
				} else {
					Uri subredditUri = SubredditProvider.CONTENT_URI_SUBREDDITS.buildUpon().appendPath(String.valueOf(c.getInt(POS_ID))).build();
					batch.add(ContentProviderOperation.newDelete(subredditUri).build());
				}
			} while (c.moveToNext());
		}

		for (Subreddit subreddit : subredditHash.values()) {
			batch.add(ContentProviderOperation
					.newInsert(SubredditProvider.CONTENT_URI_SUBREDDITS)
					.withValue(SubredditProvider.SubredditColumns.COLUMN_NAME, subreddit.getName())
					.withValue(SubredditProvider.SubredditColumns.COLUMN_ADDED, subreddit.getAdded().getTime())
					.withValue(SubredditProvider.SubredditColumns.COLUMN_SIZE, subreddit.getSize())
					.build());
		}

		try {
			resolver.applyBatch(SubredditProvider.CONTENT_AUTHORITY, batch);
			resolver.notifyChange(SubredditProvider.CONTENT_URI_SUBREDDITS, null, false);
		} catch (RemoteException | OperationApplicationException e) {
			Log.error("Error updating database: " + e.getMessage(), e);
		}
	}
}
