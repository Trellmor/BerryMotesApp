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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.AndroidHttpClient;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trellmor.BerryMotes.SettingsActivity;

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

	public EmoteDownloader(Context context) {
		mContext = context;

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		mDownloadNSFW = settings.getBoolean(SettingsActivity.KEY_SYNC_NSFW,
				false);
		mLastModified = new Date(settings.getLong(
				SettingsActivity.KEY_SYNC_LAST_MODIFIED, 0));

		mBaseDir = mContext.getExternalFilesDir(null) + File.separator;
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
			this.downloadEmoteList();
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
			throw new IOException("Download currently not possible");
		}
	}

	private boolean isStorageAvailable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	private void checkStorageAvailable() throws IOException {
		if (!this.isStorageAvailable()) {
			throw new IOException("Storage not available");
		}
	}

	private void downloadEmoteList() throws URISyntaxException, IOException {
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
					Map<String, EmoteData> emotes = new Gson().fromJson(reader,
							mapType);

					// Create .nomedia file to stop android from indexing the
					// emote images
					this.checkStorageAvailable();
					File nomedia = new File(mBaseDir + ".nomedia");
					if (!nomedia.exists()) {
						nomedia.getParentFile().mkdirs();
						nomedia.createNewFile();
					}

					for (Map.Entry<String, EmoteData> emote : emotes.entrySet()) {
						downloadEmote(emote.getValue());
					}
				} finally {
					zis.close();
					is.close();
				}
			}
			break;
		case 304:
			// Not modified
			break;
		default:
			throw new IOException("Unexpected HTTP response: "
					+ response.getStatusLine().getReasonPhrase());
		}
	}

	private void downloadEmote(EmoteData emote) throws IOException,
			URISyntaxException {
		if (emote.isNsfw() && !mDownloadNSFW) {
			return;
		}

		for (EmoteData.Image image : emote.getImages()) {
			this.downloadImage(image);
		}
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
				throw new IOException("Download failed for \""
						+ image.getImage()
						+ "\" code: "
						+ String.valueOf(response.getStatusLine()
								.getStatusCode()));
			}

			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new IOException("Download failed for \""
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

	public class NetworkReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			EmoteDownloader.this.updateNetworkInfo();
		}
	}
}
