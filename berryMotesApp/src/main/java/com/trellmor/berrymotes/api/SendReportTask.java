/*
 * BerryMotes
 * Copyright (C) 2016 Daniel Triendl <trellmor@trellmor.com>
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

package com.trellmor.berrymotes.api;

import android.os.AsyncTask;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class SendReportTask extends AsyncTask<Report, Void, Error> {

	public interface SendReportCallback {
		void onReportSend();
		void onFail(Error error);
	}

	private SendReportCallback mCallback = null;

	public void setReportCallback(SendReportCallback callback) {
		mCallback = callback;
	}

	@Override
	protected Error doInBackground(Report... params) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(Endpoints.API.REPORT).openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			OutputStream os = connection.getOutputStream();
			Gson gson = new Gson();
			os.write(gson.toJson(params[0]).getBytes("UTF-8"));
			os.close();
			if (connection.getResponseCode() == HttpURLConnection.HTTP_CREATED) {
				return null;
			} else {
				InputStream is = connection.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				try {
					return gson.fromJson(isr, Error.class);
				} finally {
					isr.close();
					is.close();
				}
			}
		} catch (IOException e) {
			return new Error(1, e.getMessage());
		}
	}

	@Override
	protected void onPostExecute(Error result) {
		if (mCallback != null) {
			if (result != null) {
				mCallback.onFail(result);
			} else {
				mCallback.onReportSend();
			}
		}
	}

}
