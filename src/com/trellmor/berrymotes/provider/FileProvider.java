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

package com.trellmor.berrymotes.provider;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.trellmor.berrymotes.sync.EmoteDownloader;

public class FileProvider extends ContentProvider {
	public static final int ROUTE_FILE = 1;

	private static final UriMatcher sUriMatcher = new UriMatcher(
			UriMatcher.NO_MATCH);
	static {
		sUriMatcher.addURI(FileContract.CONTENT_AUTHORITY,
				FileContract.PATH_FILE + "/*", ROUTE_FILE);
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
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
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
			File logFile = getLogFile(uri);
			if (logFile.exists()) {
				MatrixCursor cursor = new MatrixCursor(new String[] {
						OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });

				cursor.addRow(new Object[] { logFile.getName(),
						logFile.length() });

				return cursor;
			}
		default:
			return null;
		}
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		switch (sUriMatcher.match(uri)) {
		case ROUTE_FILE:
			ParcelFileDescriptor pdf;
			pdf = ParcelFileDescriptor.open(getLogFile(uri),
					ParcelFileDescriptor.MODE_READ_ONLY);
			return pdf;
		default:
			throw new UnsupportedOperationException("Unsupported uri: "
					+ uri.toString());
		}
	}

	private File getLogFile(Uri uri) {
		String fileName = uri.getLastPathSegment();
		if (!EmoteDownloader.LOG_FILE_NAME.equals(fileName)) {
			throw new SecurityException("Invalid file: " + fileName);
		}
		return new File(getContext().getFilesDir(), fileName);
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
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		return 0;
	}

}
