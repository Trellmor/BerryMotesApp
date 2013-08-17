package com.trellmor.BerryMotes.sync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.Environment;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trellmor.BerryMotes.SettingsActivity;
import com.trellmor.BerryMotes.provider.EmotesContract;
import com.trellmor.BerryMotes.util.DownloadException;
import com.trellmor.BerryMotes.util.NetworkNotAvailableException;
import com.trellmor.BerryMotes.util.StorageNotAvailableException;

public class EmoteDownloader {
	private static final String TAG = SyncAdapter.class.getName();

	private static final String HOST = "http://berrymotes.pew.cc/";
	private static final String EMOTES = "emotes.json.gz";

	private Context mContext;
	private boolean mDownloadNSFW;
	private AndroidHttpClient mHttpClient;
	private Date mLastModified;
	private int mNetworkType;
	private boolean mIsConnected;
	private String mBaseDir;
	private final ContentResolver mContentResolver;

	public EmoteDownloader(Context context) {
		mContext = context;

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		mDownloadNSFW = settings.getBoolean(SettingsActivity.KEY_SYNC_NSFW,
				false);
		mLastModified = new Date(settings.getLong(
				SettingsActivity.KEY_SYNC_LAST_MODIFIED, 0));

		mBaseDir = mContext.getExternalFilesDir(null) + File.separator;

		mContentResolver = mContext.getContentResolver();
	}

	public void start(SyncResult syncResult) {
		this.updateNetworkInfo();

		if (!mIsConnected) {
			Log.e(TAG, "Network not available");
			syncResult.stats.numIoExceptions++;
			return;
		}

		// Registers BroadcastReceiver to track network connection changes.
		IntentFilter filter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		NetworkReceiver receiver = new NetworkReceiver();
		mContext.registerReceiver(receiver, filter);

		mHttpClient = AndroidHttpClient.newInstance("BerryMotes Android sync");

		try {
			Map<String, EmoteData> emotes = this.downloadEmoteList();
			if (emotes != null) {
				this.downloadEmotes(emotes, syncResult);

				this.updateEmotes(emotes, syncResult);

				// If everything is ok, update the last modified date
				if (!syncResult.hasError()) {
					PreferenceManager
							.getDefaultSharedPreferences(mContext)
							.edit()
							.putLong(SettingsActivity.KEY_SYNC_LAST_MODIFIED,
									mLastModified.getTime()).commit();
				}
			}
		} catch (URISyntaxException e) {
			Log.wtf(TAG, "Emotes URL is malformed", e);
			syncResult.stats.numParseExceptions++;
			syncResult.delayUntil = 60 * 60;
			return;
		} catch (IOException e) {
			Log.e(TAG, "Error reading from network: " + e.toString(), e);
			syncResult.stats.numIoExceptions++;
			syncResult.delayUntil = 30 * 60;
			return;
		} finally {
			mHttpClient.close();
		}

		// Unregisters BroadcastReceiver at the end
		mContext.unregisterReceiver(receiver);
	}

	private void updateNetworkInfo() {
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

	private boolean canDownload() {
		if (!mIsConnected) {
			return false;
		}

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		boolean wiFiOnly = settings.getString(
				SettingsActivity.KEY_SYNC_CONNECTION,
				SettingsActivity.VALUE_SYNC_CONNECTION_WIFI).equals(
				SettingsActivity.VALUE_SYNC_CONNECTION_WIFI);

		return !(wiFiOnly && mNetworkType != ConnectivityManager.TYPE_WIFI);
	}

	private void checkCanDownload() throws IOException {
		if (!this.canDownload()) {
			throw new NetworkNotAvailableException(
					"Download currently not possible");
		}
	}

	private boolean isStorageAvailable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	private void checkStorageAvailable() throws IOException {
		if (!this.isStorageAvailable()) {
			throw new StorageNotAvailableException("Storage not available");
		}
	}

	private Map<String, EmoteData> downloadEmoteList()
			throws URISyntaxException, IOException {
		HttpRequestBase request = new HttpGet();
		request.setURI(new URI(HOST + EMOTES));
		request.setHeader("If-Modified-Since",
				DateUtils.formatDate(mLastModified));

		this.checkCanDownload();
		HttpResponse response = mHttpClient.execute(request);
		switch (response.getStatusLine().getStatusCode()) {
		case 200:
			// Download ok
			Header[] lastModified = response.getHeaders("last-modified");
			if (lastModified.length > 0) {
				try {
					mLastModified = DateUtils.parseDate(lastModified[0]
							.getValue());
				} catch (DateParseException e) {
					Log.e(TAG, "Error parsing last-modified header", e);
				}
			}

			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream is = entity.getContent();
				GZIPInputStream zis = new GZIPInputStream(is);
				try {
					Type mapType = new TypeToken<HashMap<String, EmoteData>>() {
					}.getType();

					Reader reader = new InputStreamReader(zis, "UTF-8");
					return new Gson().fromJson(reader, mapType);
				} finally {
					zis.close();
					is.close();
				}
			}
			break;
		case 304:
			break;
		default:
			throw new IOException("Unexpected HTTP response: "
					+ response.getStatusLine().getReasonPhrase());
		}
		return null;
	}

	private void downloadEmotes(Map<String, EmoteData> emotes,
			SyncResult syncResult) throws URISyntaxException, IOException {
		// Create .nomedia file to stop android from indexing the
		// emote images
		this.checkStorageAvailable();
		File nomedia = new File(mBaseDir + ".nomedia");
		if (!nomedia.exists()) {
			nomedia.getParentFile().mkdirs();
			nomedia.createNewFile();
		}

		for (Map.Entry<String, EmoteData> emote : emotes.entrySet()) {
			try {
				if (!downloadEmote(emote.getValue())) {
					emotes.remove(emote.getKey());
				}
			} catch (DownloadException e) {
				Log.e(TAG, e.getMessage(), e);
				emotes.remove(emote.getKey());
				syncResult.stats.numIoExceptions++;
				syncResult.delayUntil = 6 * 60 * 60; // No point in retrying
														// straight away
			}
		}
	}

	private boolean downloadEmote(EmoteData emote) throws IOException,
			URISyntaxException {
		if (emote.isNsfw() && !mDownloadNSFW) {
			return false;
		}

		for (EmoteData.Image image : emote.getImages()) {
			this.downloadImage(image);
		}
		return true;
	}

	private void downloadImage(EmoteData.Image image) throws IOException,
			URISyntaxException {
		this.checkStorageAvailable();

		File file = new File(mBaseDir + image.getImage());

		if (!file.exists()) {
			file.getParentFile().mkdirs();

			this.checkCanDownload();
			HttpGet request = new HttpGet();
			request.setURI(new URI(HOST + image.getImage()));
			HttpResponse response = mHttpClient.execute(request);
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new DownloadException("Download failed for \""
						+ image.getImage()
						+ "\" code: "
						+ String.valueOf(response.getStatusLine()
								.getStatusCode()));
			}

			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new DownloadException("Download failed for \""
						+ image.getImage() + "\"");
			}
			InputStream is = entity.getContent();
			try {
				OutputStream os = new FileOutputStream(file);
				try {
					byte[] buffer = new byte[1024];
					int read;

					while ((read = is.read(buffer)) != -1) {
						os.write(buffer, 0, read);
					}

					os.flush();
				} finally {
					os.close();
				}
			} finally {
				is.close();
			}
		}
	}

	public void updateEmotes(Map<String, EmoteData> emotes,
			SyncResult syncResult) {
		ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();

		// Delete everything
		syncResult.stats.numDeletes += mContentResolver.delete(
				EmotesContract.Emote.CONTENT_URI, null, null);

		// Generate batch insert
		for (Map.Entry<String, EmoteData> entry : emotes.entrySet()) {
			String name = entry.getKey();
			EmoteData emote = entry.getValue();
			for (EmoteData.Image image : emote.getImages()) {
				batch.add(ContentProviderOperation
						.newInsert(EmotesContract.Emote.CONTENT_URI)
						.withValue(EmotesContract.Emote.COLUMN_NAME, name)
						.withValue(EmotesContract.Emote.COLUMN_NSFW,
								(emote.isNsfw() ? 1 : 0))
						.withValue(EmotesContract.Emote.COLUMN_APNG,
								(emote.isApng() ? 1 : 0))
						.withValue(EmotesContract.Emote.COLUMN_IMAGE,
								mBaseDir + image.getImage())
						.withValue(EmotesContract.Emote.COLUMN_INDEX,
								image.getIndex())
						.withValue(EmotesContract.Emote.COLUMN_DELAY,
								image.getDelay()).build());
				syncResult.stats.numInserts++;
			}
		}
		try {
			mContentResolver
					.applyBatch(EmotesContract.CONTENT_AUTHORITY, batch);
			mContentResolver.notifyChange( //
					EmotesContract.Emote.CONTENT_URI, // URI where data was
														// modified
					null, // No local observer
					false); // IMPORTANT: Do not sync to network
		} catch (RemoteException e) {
			Log.e(TAG, "Error updating database: " + e.toString());
			syncResult.databaseError = true;
		} catch (OperationApplicationException e) {
			Log.e(TAG, "Error updating database: " + e.toString());
			syncResult.databaseError = true;
		}
	}

	public class NetworkReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			EmoteDownloader.this.updateNetworkInfo();
		}
	}
}
