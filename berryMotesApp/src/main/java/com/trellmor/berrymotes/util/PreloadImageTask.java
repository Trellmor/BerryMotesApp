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

package com.trellmor.berrymotes.util;

import java.io.FileNotFoundException;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class PreloadImageTask extends AsyncTask<Uri, Void, Boolean> {
	private static final String TAG = PreloadImageTask.class.getName();

	private final Context mContext;
	private final PreloadImageTask.Callback mCallback;

	public PreloadImageTask(Context context, PreloadImageTask.Callback callback) {
		mContext = context;
		mCallback = callback;
	}

	@Override
	protected Boolean doInBackground(Uri... params) {
		try {
			mContext.getContentResolver().openFileDescriptor(params[0], "r");
			return true;
		} catch (FileNotFoundException e) {
			Log.e(TAG, "openFileDescriptor", e);
		}

		return false;
	}

	protected void onPostExecute(Boolean result) {
		if (mCallback != null)
			mCallback.onLoaded(result);
	}

	public interface Callback {
		void onLoaded(boolean result);
	}
}
