/*
 * BerryMotes android 
 * Copyright (C) 2014 Daniel Triendl <trellmor@trellmor.com>
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.trellmor.berrymotes.SettingsActivity;
import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.util.DownloadException;

public class SubredditEmoteDownloader implements Runnable {
	private Logger Log = LoggerFactory.getLogger(EmoteDownloader.class);

	private final Context mContext;
	private final EmoteDownloader mEmoteDownloader;
	private final ContentResolver mContentResolver;
	private final String mSubreddit;
	private Date mLastModified;
	private File mBaseDir;
	private SyncResult mSyncResult;

	private boolean mDownloadNSFW;

	private static final String EMOTES = "/emotes.json.gz";

	public SubredditEmoteDownloader(Context context,
			EmoteDownloader emoteDownloader, String subreddit) {
		mContext = context;
		mEmoteDownloader = emoteDownloader;
		mSubreddit = subreddit;

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		mDownloadNSFW = settings.getBoolean(SettingsActivity.KEY_SYNC_NSFW,
				false);
		mLastModified = new Date(settings.getLong(
				SettingsActivity.KEY_SYNC_LAST_MODIFIED + mSubreddit, 0));

		mBaseDir = mContext.getExternalFilesDir(null);

		mContentResolver = mContext.getContentResolver();

		mSyncResult = new SyncResult();
	}

	@Override
	public void run() {
		try {
			List<EmoteImage> emotes = this.getEmoteList();

			if (emotes != null) {
				this.downloadEmotes(emotes);

				this.updateEmotes(emotes);

				// If everything is ok, update the last modified date
				if (!mSyncResult.hasError()) {
					Log.info("Updating LAST_MODIFIED time to "
							+ mLastModified.toString());
					PreferenceManager
							.getDefaultSharedPreferences(mContext)
							.edit()
							.putLong(
									SettingsActivity.KEY_SYNC_LAST_MODIFIED
											+ mSubreddit,
									mLastModified.getTime()).commit();
				}
			}
		} catch (URISyntaxException e) {
			Log.error("Emotes URL is malformed", e);
			mSyncResult.stats.numParseExceptions++;
			mSyncResult.delayUntil = 60 * 60;
			return;
		} catch (IOException e) {
			Log.error("Error reading from network: " + e.getMessage(), e);
			mSyncResult.stats.numIoExceptions++;
			mSyncResult.delayUntil = 30 * 60;
			return;
		} catch (RemoteException e) {
			Log.error("Error updating database: " + e.getMessage(), e);
			mSyncResult.databaseError = true;
		} catch (OperationApplicationException e) {
			Log.error("Error updating database: " + e.getMessage(), e);
			mSyncResult.databaseError = true;
		} catch (InterruptedException e) {
			Log.info("Sync interrupted");
			mSyncResult.moreRecordsToGet = true;
			Thread.currentThread().interrupt();
		} finally {
			mEmoteDownloader.updateSyncResult(mSyncResult);
		}
	}

	private List<EmoteImage> getEmoteList() throws IOException,
			RemoteException, OperationApplicationException, URISyntaxException,
			InterruptedException {
		Log.debug("Getting emote list");
		List<EmoteImage> emotes = downloadEmoteList();

		if (emotes != null) {
			checkInterrupted();

			HashMap<String, EmoteImage> emotesHash = new HashMap<String, EmoteImage>();
			int i = 0;
			while (i < emotes.size()) {
				EmoteImage emote = emotes.get(i);
				if (!mDownloadNSFW && emote.isNsfw()) {
					Log.debug("Skipped emote " + emote.getImage() + " (NSFW)");
					emotes.remove(i);
					continue;
				} else {
					if (!emotesHash.containsKey(emote.getHash())) {
						emotesHash.put(emote.getHash(), emote);
					} else {
						EmoteImage collision = emotesHash.get(emote.getHash());
						Log.error("Hash collission! " + emote.getImage() + " ("
								+ emote.getHash() + ") <-> "
								+ collision.getImage() + " ("
								+ collision.getHash() + ")");
					}
				}
				i++;
			}
			Log.info("Removed ignored emotes, "
					+ Integer.toString(emotesHash.size()) + " left.");

			checkInterrupted();
			Cursor c = mContentResolver.query(
					EmotesContract.Emote.CONTENT_URI_DISTINCT, new String[] {
							EmotesContract.Emote.COLUMN_HASH,
							EmotesContract.Emote.COLUMN_IMAGE },
					EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
					new String[] { mSubreddit }, null);
			if (c != null) {
				ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
				try {
					if (c.moveToFirst()) {
						final int POS_HASH = c
								.getColumnIndex(EmotesContract.Emote.COLUMN_HASH);
						final int POS_IMAGE = c
								.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE);
						do {
							String hash = c.getString(POS_HASH);
							if (!emotesHash.containsKey(hash)) {
								Log.debug("Removing " + c.getString(POS_IMAGE)
										+ " (" + hash + ") (not in emote list)");
								batch.add(ContentProviderOperation
										.newDelete(
												EmotesContract.Emote.CONTENT_URI)
										.withSelection(
												EmotesContract.Emote.COLUMN_HASH
														+ "=?",
												new String[] { hash }).build());

								mEmoteDownloader.checkStorageAvailable();
								File file = new File(c.getString(POS_IMAGE));
								if (file.exists()) {
									file.delete();
								}
							}
						} while (c.moveToNext());
					}

					c.close();

				} finally {
					Log.debug("Removing emotes from DB");
					applyBatch(batch);
					mSyncResult.stats.numDeletes += batch.size();
					Log.info("Removed " + Integer.toString(batch.size())
							+ " emotes from DB");
				}
			}
		}
		return emotes;
	}

	private List<EmoteImage> downloadEmoteList() throws URISyntaxException,
			IOException, InterruptedException {
		checkInterrupted();
		Log.debug("Downloading " + mSubreddit + EMOTES);
		HttpRequestBase request = new HttpGet();
		request.setURI(new URI(EmoteDownloader.HOST + mSubreddit + EMOTES));
		request.setHeader("If-Modified-Since",
				DateUtils.formatDate(mLastModified));

		mEmoteDownloader.checkCanDownload();
		HttpResponse response = mEmoteDownloader.getHttpClient().execute(
				request);
		switch (response.getStatusLine().getStatusCode()) {
		case 200:
			Log.debug(mSubreddit + EMOTES + " loaded");
			// Download ok
			Header[] lastModified = response.getHeaders("last-modified");
			if (lastModified.length > 0) {
				try {
					mLastModified = DateUtils.parseDate(lastModified[0]
							.getValue());
				} catch (DateParseException e) {
					Log.error("Error parsing last-modified header", e);
				}
			}

			HttpEntity entity = response.getEntity();
			if (entity != null) {
				checkInterrupted();

				File tmpFile = File.createTempFile(mSubreddit, null,
						mContext.getCacheDir());
				try {
					InputStream is = entity.getContent();
					try {
						mEmoteDownloader.checkStorageAvailable();
						StreamUtils.saveStreamToFile(is, tmpFile);
					} finally {
						StreamUtils.closeStream(is);
					}

					FileInputStream fis = null;
					BufferedInputStream bis = null;
					GZIPInputStream zis = null;
					Reader isr = null;
					JsonReader jsonReader = null;
					checkInterrupted();

					try {
						fis = new FileInputStream(tmpFile);
						bis = new BufferedInputStream(fis);
						zis = new GZIPInputStream(bis);
						isr = new InputStreamReader(zis, "UTF-8");
						jsonReader = new JsonReader(isr);

						jsonReader.beginArray();
						Gson gson = new Gson();
						ArrayList<EmoteImage> emotes = new ArrayList<EmoteImage>();
						while (jsonReader.hasNext()) {
							EmoteImage emote = gson.fromJson(jsonReader,
									EmoteImage.class);
							emotes.add(emote);
						}
						jsonReader.endArray();

						Log.info("Loaded " + mSubreddit + EMOTES + ", size: "
								+ Integer.toString(emotes.size()));
						return emotes;
					} finally {
						StreamUtils.closeStream(jsonReader);
						StreamUtils.closeStream(isr);
						StreamUtils.closeStream(zis);
						StreamUtils.closeStream(bis);
						StreamUtils.closeStream(fis);
					}
				} finally {
					tmpFile.delete();
				}
			}
			break;
		case 304:
			Log.info(mSubreddit + EMOTES + " already up to date (HTTP 304)");
			break;
		case 403:
		case 404:
			Log.info(mSubreddit + " missing on server, removing emotes");
			mEmoteDownloader.deleteSubreddit(mSubreddit, mContentResolver);
			break;
		default:
			throw new IOException("Unexpected HTTP response: "
					+ response.getStatusLine().getReasonPhrase());
		}
		return null;
	}

	public void updateEmotes(List<EmoteImage> emotes) throws RemoteException,
			OperationApplicationException, InterruptedException {
		checkInterrupted();

		Log.debug("Updating emote database");

		// Build map of entries
		HashMap<String, EmoteImage> emoteHash = new HashMap<String, EmoteImage>();
		for (EmoteImage emote : emotes) {
			emoteHash.put(emote.getHash(), emote);
		}

		checkInterrupted();
		Cursor c = mContentResolver.query(
				EmotesContract.Emote.CONTENT_URI_DISTINCT, new String[] {
						EmotesContract.Emote._ID,
						EmotesContract.Emote.COLUMN_NAME,
						EmotesContract.Emote.COLUMN_HASH },
				EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
				new String[] { mSubreddit }, null);
		if (c != null) {
			ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();

			if (c.moveToFirst()) {
				final int POS_ID = c.getColumnIndex(EmotesContract.Emote._ID);
				final int POS_NAME = c
						.getColumnIndex(EmotesContract.Emote.COLUMN_NAME);
				final int POS_HASH = c
						.getColumnIndex(EmotesContract.Emote.COLUMN_HASH);

				do {
					String hash = c.getString(POS_HASH);
					String name = c.getString(POS_NAME);
					EmoteImage emote = emoteHash.get(hash);
					if (emote != null) {
						if (emote.getNames().contains(name)) {
							emote.getNames().remove(name);
							if (emote.getNames().size() == 0) {
								// Already in db, no need to insert
								emoteHash.remove(hash);
								emotes.remove(emote);
							}
						} else {
							Log.debug("Removing " + name + " (" + hash
									+ ") from DB");
							Uri deleteUri = EmotesContract.Emote.CONTENT_URI
									.buildUpon()
									.appendPath(
											Integer.toString(c.getInt(POS_ID)))
									.build();
							batch.add(ContentProviderOperation.newDelete(
									deleteUri).build());
						}
					}
				} while (c.moveToNext());
			}

			c.close();

			// Delete all emotes that no longer exist
			Log.debug("Removing emotes names from DB");
			checkInterrupted();
			applyBatch(batch);
			mSyncResult.stats.numDeletes += batch.size();
			Log.info("Removed " + Integer.toString(batch.size())
					+ " emotes names from DB");
		}

		// Generate batch insert
		checkInterrupted();
		ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
		String baseDir = mBaseDir.getAbsolutePath() + File.separator;
		for (EmoteImage emote : emotes) {
			for (String name : emote.getNames()) {
				Log.debug("Adding " + name + " to DB");
				batch.add(ContentProviderOperation
						.newInsert(EmotesContract.Emote.CONTENT_URI)
						.withValue(EmotesContract.Emote.COLUMN_NAME, name)
						.withValue(EmotesContract.Emote.COLUMN_NSFW,
								(emote.isNsfw() ? 1 : 0))
						.withValue(EmotesContract.Emote.COLUMN_APNG,
								(emote.isApng() ? 1 : 0))
						.withValue(EmotesContract.Emote.COLUMN_IMAGE,
								baseDir + emote.getImage())
						.withValue(EmotesContract.Emote.COLUMN_HASH,
								emote.getHash())
						.withValue(EmotesContract.Emote.COLUMN_INDEX,
								emote.getIndex())
						.withValue(EmotesContract.Emote.COLUMN_DELAY,
								emote.getDelay())
						.withValue(EmotesContract.Emote.COLUMN_SUBREDDIT,
								emote.getSubreddit()).build());
			}
		}

		Log.debug("Adding emotes names to DB");
		checkInterrupted();
		applyBatch(batch);
		mSyncResult.stats.numInserts += batch.size();
		Log.info("Added " + Integer.toString(batch.size())
				+ " emotes names to DB");
	}

	private void downloadEmotes(List<EmoteImage> emotes)
			throws URISyntaxException, IOException, InterruptedException {
		Log.debug("Downloading emotes");
		// Create .nomedia file to stop android from indexing the emote images
		mEmoteDownloader.checkStorageAvailable();
		File nomedia = new File(mBaseDir, ".nomedia");
		if (!nomedia.exists()) {
			nomedia.getParentFile().mkdirs();
			nomedia.createNewFile();
		}

		int i = 0;
		while (i < emotes.size()) {
			EmoteImage emote = emotes.get(i);
			try {
				if (!downloadEmote(emote)) {
					Log.warn("Failed to download " + emote.getImage());
					emotes.remove(i);
					continue;
				}
			} catch (DownloadException e) {
				Log.error(e.getMessage(), e);
				Log.info("Failed to download " + emote.getImage());

				emotes.remove(i);
				mSyncResult.stats.numIoExceptions++;

				continue;
			}
			i++;
		}
	}

	private boolean downloadEmote(EmoteImage emote) throws IOException,
			URISyntaxException, InterruptedException {
		checkInterrupted();

		mEmoteDownloader.checkStorageAvailable();
		File file = new File(mBaseDir, emote.getImage());

		if (!file.exists()) {
			Log.debug("Downloading emote " + emote.getImage());

			file.getParentFile().mkdirs();

			mEmoteDownloader.checkCanDownload();
			HttpGet request = new HttpGet();
			request.setURI(new URI(EmoteDownloader.HOST + emote.getImage()));
			HttpResponse response = mEmoteDownloader.getHttpClient().execute(
					request);
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new DownloadException("Download failed for \""
						+ emote.getImage()
						+ "\" code: "
						+ String.valueOf(response.getStatusLine()
								.getStatusCode()));
			}

			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new DownloadException("Download failed for \""
						+ emote.getImage() + "\"");
			}

			mEmoteDownloader.checkStorageAvailable();
			InputStream is = entity.getContent();
			try {
				File tmpFile = new File(file.getAbsolutePath() + ".tmp");
				if (tmpFile.exists())
					tmpFile.delete();
				StreamUtils.saveStreamToFile(is, tmpFile);
				tmpFile.renameTo(file);
				Log.debug("Downloaded emote " + emote.getImage());
			} finally {
				StreamUtils.closeStream(is);
			}
		}

		return file.exists();
	}

	private void applyBatch(ArrayList<ContentProviderOperation> operations)
			throws RemoteException, OperationApplicationException,
			InterruptedException {
		mContentResolver.applyBatch(EmotesContract.CONTENT_AUTHORITY,
				operations);
		mContentResolver.notifyChange(//
				EmotesContract.Emote.CONTENT_URI, // URI where data was modified
				null, // No local observer
				false); // IMPORTANT: Do not sync to network
	}

	private void checkInterrupted() throws InterruptedException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException();
		}
	}
}
