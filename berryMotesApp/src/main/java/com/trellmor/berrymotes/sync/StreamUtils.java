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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class StreamUtils {
	private static final String TAG = StreamUtils.class.getName();

	public interface ProgressCallback {
		void onProgress(long done);
	}

	public static void saveStreamToFile(InputStream is, File file) throws IOException {
		OutputStream os = new FileOutputStream(file);
		try {
			copy(is, os);
		} finally {
			closeStream(os);
		}
	}

	public static void copy(InputStream is, OutputStream os) throws IOException {
		copy(is, os, null);
	}

	public static void copy(InputStream is, OutputStream os, ProgressCallback progress) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		long done = 0;

		while ((read = is.read(buffer)) != -1) {
			os.write(buffer, 0, read);
			if (progress != null) {
				done += read;
				progress.onProgress(done);
			}
		}

		os.flush();
	}

	public static void closeStream(Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (IOException e) {
			Log.e(TAG, "Error closing: " + e.getMessage(), e);
		}
	}
}
