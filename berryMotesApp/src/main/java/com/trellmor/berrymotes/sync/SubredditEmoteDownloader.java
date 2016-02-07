/*
 * BerryMotes
 * Copyright (C) 2015-2016 Daniel Triendl <trellmor@trellmor.com>
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
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.provider.SubredditProvider;
import com.trellmor.berrymotes.util.DownloadException;
import com.trellmor.berrymotes.api.Endpoints;

class SubredditEmoteDownloader implements Runnable {
	private final Logger Log = LoggerFactory.getLogger(SubredditEmoteDownloader.class);

	private final Context mContext;
	private final EmoteDownloader mEmoteDownloader;
	private final ContentResolver mContentResolver;
	private final String mSubreddit;
	private Date mLastModified;
	private final File mBaseDir;
	private final SyncResult mSyncResult;

	private static final String EMOTES = "/emotes.json.gz";

	public SubredditEmoteDownloader(Context context,
			EmoteDownloader emoteDownloader, String subreddit) {
		mContext = context;
		mEmoteDownloader = emoteDownloader;
		mSubreddit = subreddit;

		mBaseDir = mContext.getExternalFilesDir(null);

		mContentResolver = mContext.getContentResolver();

		Cursor c = mContentResolver.query(SubredditProvider.CONTENT_URI_SUBREDDITS,
				new String[] {SubredditProvider.SubredditColumns.COLUMN_LAST_SYNC},
				SubredditProvider.SubredditColumns.COLUMN_NAME + " =?",
				new String[] {mSubreddit}, null);

		if (c.getCount() > 0) {
			c.moveToFirst();
			mLastModified = new Date(c.getLong(c.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_LAST_SYNC)));
		} else {
			mLastModified = new Date(0);
		}

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
					Log.debug("{}: Updating LAST_MODIFIED time to {}", mSubreddit, mLastModified);

					ContentValues values = new ContentValues();
					values.put(SubredditProvider.SubredditColumns.COLUMN_LAST_SYNC, mLastModified.getTime());
					mContentResolver.update(SubredditProvider.CONTENT_URI_SUBREDDITS, values,
							SubredditProvider.SubredditColumns.COLUMN_NAME + " =?", new String[]{mSubreddit});
				}
			}
		} catch (URISyntaxException e) {
			Log.error(mSubreddit + ": Emotes URL is malformed", e);
			mSyncResult.stats.numParseExceptions++;
			mSyncResult.delayUntil = 60 * 60;
		} catch (IOException e) {
			Log.error(mSubreddit + ": Error reading from network: " + e.getMessage(), e);
			mSyncResult.stats.numIoExceptions++;
			mSyncResult.delayUntil = 30 * 60;
		} catch (RemoteException | OperationApplicationException e) {
			Log.error(mSubreddit + ": Error updating database: " + e.getMessage(), e);
			mSyncResult.databaseError = true;
		} catch (InterruptedException e) {
			Log.info(mSubreddit + ": Sync interrupted");
			mSyncResult.moreRecordsToGet = true;
			Thread.currentThread().interrupt();
		} finally {
			mEmoteDownloader.updateSyncResult(mSyncResult);
		}
	}

	private List<EmoteImage> getEmoteList() throws IOException,
			RemoteException, OperationApplicationException, URISyntaxException,
			InterruptedException {
		Log.debug("{}: Getting emote list", mSubreddit);
		List<EmoteImage> emotes = downloadEmoteList();

		if (emotes != null) {
			checkInterrupted();

			HashMap<String, EmoteImage> emotesHash = new HashMap<>();
			int i = 0;
			while (i < emotes.size()) {
				EmoteImage emote = emotes.get(i);
				if (!emotesHash.containsKey(emote.getHash())) {
					emotesHash.put(emote.getHash(), emote);
				} else {
					EmoteImage collision = emotesHash.get(emote.getHash());
					Log.error("{}: Hash collision! " + emote.getImage() + " ("
							+ emote.getHash() + ") <-> "
							+ collision.getImage() + " ("
							+ collision.getHash() + ")", mSubreddit);
				}
				i++;
			}

			checkInterrupted();
			Cursor c = mContentResolver.query(
					EmotesContract.Emote.CONTENT_URI_DISTINCT, new String[] {
							EmotesContract.Emote.COLUMN_HASH,
							EmotesContract.Emote.COLUMN_IMAGE },
					EmotesContract.Emote.COLUMN_SUBREDDIT + "=?",
					new String[] { mSubreddit }, null);
			if (c != null) {
				ArrayList<ContentProviderOperation> batch = new ArrayList<>();
				try {
					if (c.moveToFirst()) {
						final int POS_HASH = c.getColumnIndex(EmotesContract.Emote.COLUMN_HASH);
						final int POS_IMAGE = c.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE);
						do {
							String hash = c.getString(POS_HASH);
							if (!emotesHash.containsKey(hash)) {
								Log.debug("{}: Removing {} ({}) (not in emote list)", mSubreddit, c.getString(POS_IMAGE), hash);
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
					Log.debug("{}: Removing emotes from DB", mSubreddit);
					applyBatch(batch);
					mSyncResult.stats.numDeletes += batch.size();
					if (batch.size() > 0) {
						Log.info("{}: Removed {} emotes from DB", mSubreddit, batch.size());
					}
				}
			}
		}
		return emotes;
	}

	private List<EmoteImage> downloadEmoteList() throws URISyntaxException,
			IOException, InterruptedException {
		checkInterrupted();
		Log.debug("{}: Downloading {}", mSubreddit, EMOTES);

		mEmoteDownloader.checkCanDownload();

		HttpURLConnection con = (HttpURLConnection) new URL(Endpoints.SYNC + mSubreddit + EMOTES).openConnection();
		try {
			con.setIfModifiedSince(mLastModified.getTime());
			con.connect();
			switch (con.getResponseCode()) {
				case HttpURLConnection.HTTP_OK:
					Log.debug("{}: {} loaded", mSubreddit, EMOTES);
					// Download ok
					mLastModified = new Date(con.getLastModified());

					checkInterrupted();

					File tmpFile = File.createTempFile(mSubreddit, null,
							mContext.getCacheDir());
					try {
						InputStream is = con.getInputStream();
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
							ArrayList<EmoteImage> emotes = new ArrayList<>();
							while (jsonReader.hasNext()) {
								EmoteImage emote = gson.fromJson(jsonReader,
										EmoteImage.class);
								emotes.add(emote);
							}
							jsonReader.endArray();

							Log.info("{}: Loaded {} , size: {}", mSubreddit, EMOTES, emotes.size());
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
				case HttpURLConnection.HTTP_NOT_MODIFIED:
					Log.debug("{}: {} already up to date (HTTP 304)", mSubreddit, EMOTES);
					break;
				case HttpURLConnection.HTTP_FORBIDDEN:
				case HttpURLConnection.HTTP_NOT_FOUND:
					Log.info("{}: {} missing on server, removing emotes", mSubreddit, EMOTES);
					mEmoteDownloader.deleteSubreddit(mSubreddit, mContentResolver);
					break;
				default:
					throw new IOException("Unexpected HTTP response: " + con.getResponseMessage());
			}
		} finally {
			 con.disconnect();
		}
		return null;
	}

	public void updateEmotes(List<EmoteImage> emotes) throws RemoteException,
			OperationApplicationException, InterruptedException {
		checkInterrupted();

		Log.debug("{}: Updating emote database", mSubreddit);

		// Build map of entries
		HashMap<String, EmoteImage> emoteHash = new HashMap<>();
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
			ArrayList<ContentProviderOperation> batch = new ArrayList<>();

			if (c.moveToFirst()) {
				final int POS_ID = c.getColumnIndex(EmotesContract.Emote._ID);
				final int POS_NAME = c.getColumnIndex(EmotesContract.Emote.COLUMN_NAME);
				final int POS_HASH = c.getColumnIndex(EmotesContract.Emote.COLUMN_HASH);

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
							Log.debug("{}: Removing {} ({}) from DB", mSubreddit, name, hash);
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
			Log.debug("{}: Removing emote names from DB", mSubreddit);
			checkInterrupted();
			applyBatch(batch);
			mSyncResult.stats.numDeletes += batch.size();
			if (batch.size() > 0) {
				Log.info("{}: Removed {} emote names from DB", mSubreddit, batch.size());
			}
		}

		// Generate batch insert
		checkInterrupted();
		ArrayList<ContentProviderOperation> batch = new ArrayList<>();
		String baseDir = mBaseDir.getAbsolutePath() + File.separator;
		for (EmoteImage emote : emotes) {
			for (String name : emote.getNames()) {
				Log.debug("{}: Adding {} to DB", mSubreddit, name);
				batch.add(ContentProviderOperation
						.newInsert(EmotesContract.Emote.CONTENT_URI)
						.withValue(EmotesContract.Emote.COLUMN_NAME, name)
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

		Log.debug("{}: Adding emote names to DB", mSubreddit);
		checkInterrupted();
		applyBatch(batch);
		mSyncResult.stats.numInserts += batch.size();
		if (batch.size() > 0) {
			Log.info("{}: Added {} emote names to DB", mSubreddit, batch.size());
		}
	}

	private void downloadEmotes(List<EmoteImage> emotes)
			throws URISyntaxException, IOException, InterruptedException {
		Log.debug("{}: Downloading emotes", mSubreddit);
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
					Log.warn("{}: Failed to download {}", mSubreddit, emote.getImage());
					emotes.remove(i);
					continue;
				}
			} catch (DownloadException e) {
				Log.error(mSubreddit + ": Failed to download " + emote.getImage() + ": " + e.getMessage(), e);

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
			Log.debug("{}: Downloading emote {}", mSubreddit, emote.getImage());

			file.getParentFile().mkdirs();

			mEmoteDownloader.checkCanDownload();
			HttpURLConnection con = (HttpURLConnection) new URL(Endpoints.SYNC + emote.getImage()).openConnection();
			try {
				con.connect();
				if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {throw new DownloadException("Download failed for \""
						+ emote.getImage()
						+ "\" code: "
						+ String.valueOf(con.getResponseCode()));
				}

				InputStream is = con.getInputStream();
				try {
					File tmpFile = new File(file.getAbsolutePath() + ".tmp");
					if (tmpFile.exists())
						tmpFile.delete();
					StreamUtils.saveStreamToFile(is, tmpFile);
					tmpFile.renameTo(file);
					Log.debug("{}: Downloaded emote {}", mSubreddit, emote.getImage());
				} finally {
					StreamUtils.closeStream(is);
				}

				mEmoteDownloader.checkStorageAvailable();
			} finally {
				con.disconnect();
			}
		}

		return file.exists();
	}

	private void applyBatch(ArrayList<ContentProviderOperation> operations)
			throws RemoteException, OperationApplicationException {
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
