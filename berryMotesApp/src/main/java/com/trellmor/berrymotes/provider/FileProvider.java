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

package com.trellmor.berrymotes.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.trellmor.berrymotes.Settings;
import com.trellmor.berrymotes.sync.EmoteDownloader;
import com.trellmor.berrymotes.util.AnimatedGifEncoder;

public class FileProvider extends ContentProvider {
	private static final String TAG = FileProvider.class.getName();

	private static final int ROUTE_FILE = 1;
	private static final int ROUTE_EMOTE = 2;

	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		sUriMatcher.addURI(FileContract.CONTENT_AUTHORITY, FileContract.PATH_FILE + "/*", ROUTE_FILE);
		sUriMatcher.addURI(FileContract.CONTENT_AUTHORITY, FileContract.PATH_EMOTE + "/*", ROUTE_EMOTE);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
		case ROUTE_EMOTE:
			String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
			if ("log".equals(ext)) {
				return "text/plain";
			} else {
				return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
			}
		default:
			return null;
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
			File logFile = getLogFile(uri);
			if (logFile.exists()) {
				MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME,
						OpenableColumns.SIZE });

				cursor.addRow(new Object[] { logFile.getName(), logFile.length() });

				return cursor;
			}
			break;
		case ROUTE_EMOTE:
			String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
			if (!"".equals(ext)) {
				String name = uri.getLastPathSegment();
				File emote = getEmote(name);
				MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME,
						OpenableColumns.SIZE });

				cursor.addRow(new Object[] { emote.getName(), emote.length() });

				return cursor;
			}
			break;
		}
		return null;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		ParcelFileDescriptor pfd;
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
			pfd = ParcelFileDescriptor.open(getLogFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
			return pfd;
		case ROUTE_EMOTE:
			pfd = ParcelFileDescriptor.open(getEmote(uri.getLastPathSegment()), ParcelFileDescriptor.MODE_READ_WRITE);
			return pfd;
		default:
			throw new UnsupportedOperationException("Unsupported uri: " + uri.toString());
		}
	}

	private File getLogFile(Uri uri) {
		String fileName = uri.getLastPathSegment();
		if (!EmoteDownloader.LOG_FILE_NAME.equals(fileName)) {
			throw new SecurityException("Invalid file: " + fileName);
		}
		return new File(getContext().getFilesDir(), fileName);
	}

	private File getEmote(String name) {
		Context context = getContext();
		File cache = context.getCacheDir();
		File emote = new File(cache, name);

		if (!emote.exists()) {
			String emoteName = name;
			String emoteExt = ".png"; // assume png by default
			if (name.contains(".")) {
				emoteName = name.substring(0, name.indexOf("."));
				emoteExt = name.substring(name.indexOf(".")).toLowerCase();
			}

			Cursor cursor = context.getContentResolver().query(EmotesContract.Emote.CONTENT_URI,
					new String[] { EmotesContract.Emote.COLUMN_IMAGE, EmotesContract.Emote.COLUMN_DELAY },
					EmotesContract.Emote.COLUMN_NAME + "=?", new String[] { emoteName },
					EmotesContract.Emote.COLUMN_INDEX + " ASC");
			if (cursor != null && cursor.getCount() > 0) {
				cursor.moveToFirst();

				final int POS_IMAGE = cursor.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE);
				final int POS_DELAY = cursor.getColumnIndex(EmotesContract.Emote.COLUMN_DELAY);

				if (".gif".equals(emoteExt)) {
					AnimatedGifEncoder age = new AnimatedGifEncoder();
					age.setRepeat(0);
					OutputStream os;
					try {
						File tempEmote = File.createTempFile(name, null, cache);
						os = new FileOutputStream(tempEmote);
						age.start(os);
						do {
							Bitmap b = addWhiteBackground(cursor.getString(POS_IMAGE));

							age.addFrame(b);
							age.setDelay(cursor.getInt(POS_DELAY));
						} while (cursor.moveToNext());
						age.finish();
						os.flush();
						os.close();
						tempEmote.renameTo(emote);
					} catch (IOException e) {
						Log.e(TAG, "Generate gif " + name, e);
					}
				} else if (".png".equals(emoteExt)) {
					try {
						if (PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(
								Settings.KEY_BACKGROUND, true)) {
							Bitmap b = addWhiteBackground(cursor.getString(POS_IMAGE));

							File tmpDst = File.createTempFile(emote.getName(), null, emote.getParentFile());
							OutputStream out = new FileOutputStream(tmpDst);
							b.compress(CompressFormat.PNG, 80, out);
							out.close();

							tmpDst.renameTo(emote);
						} else {
							copy(new File(cursor.getString(POS_IMAGE)), emote);
						}
					} catch (IOException e) {
						Log.e(TAG, "Copy file " + name, e);
					}
				} else {
					throw new UnsupportedOperationException("Unsupported file type: " + emoteExt);
				}
			}

			if (cursor != null)
				cursor.close();
		}

		emote.setLastModified(System.currentTimeMillis());
		return emote;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}

	private Bitmap addWhiteBackground(String src) {
		Bitmap b = BitmapFactory.decodeFile(src);

		// Replace transparency in png with white
		Bitmap bg = Bitmap.createBitmap(b.getWidth(), b.getHeight(), b.getConfig());
		bg.eraseColor(Color.WHITE);
		Canvas canvas = new Canvas(bg);
		canvas.drawBitmap(b, 0f, 0f, null);
		b.recycle();

		return bg;
	}

	private void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);

		File tmpDst = File.createTempFile(dst.getName(), null, dst.getParentFile());
		OutputStream out = new FileOutputStream(tmpDst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();

		tmpDst.renameTo(dst);
	}

}
