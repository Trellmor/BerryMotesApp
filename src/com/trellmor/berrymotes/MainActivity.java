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

package com.trellmor.berrymotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.trellmor.berrymotes.R;
import com.trellmor.berrymotes.sync.EmoteDownloader;
import com.trellmor.berrymotes.sync.SyncService;
import com.trellmor.berrymotes.sync.SyncUtils;

public class MainActivity extends Activity {
	private static final String PREF_FIRST_RUN = "first_run";

	private TextView mTextStatus;

	private final Handler mHandler = new Handler();
	private final Runnable mTimerTask = new Runnable() {

		@Override
		public void run() {
			refreshServiceStatus();
			mHandler.postDelayed(this, 1000);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTextStatus = (TextView) findViewById(R.id.text_status);

		SyncUtils.createSyncAccount(this);

		if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				PREF_FIRST_RUN, false)) {
			showInfo();
			PreferenceManager.getDefaultSharedPreferences(this).edit()
					.putBoolean(PREF_FIRST_RUN, true).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();

		mHandler.postDelayed(mTimerTask, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();

		mHandler.removeCallbacks(mTimerTask);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		switch (item.getItemId()) {
		case R.id.menu_info:
			showInfo();
			return true;
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void buttonSyncClick(View view) {
		sync();
	}

	public void buttonCancelClick(View view) {
		cancel();
	}

	private void sync() {
		SyncUtils.triggerRefresh();
		refreshServiceStatus();
	}

	private void cancel() {
		SyncUtils.cancelSync();
	}

	private void showInfo() {
		LayoutInflater infalter = LayoutInflater.from(this);
		View view = infalter.inflate(R.layout.dialog_info, null);

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setTitle(R.string.title_dialog_info);
		alertDialog.setView(view);
		alertDialog.setPositiveButton(android.R.string.ok, null);
		alertDialog.create().show();
	}

	private void refreshServiceStatus() {
		mTextStatus.setVisibility(View.VISIBLE);

		if (SyncService.isServiceRunning(this)) {
			mTextStatus.setText(R.string.status_running);
		} else {
			mTextStatus.setText(R.string.status_idle);
		}
	}
}
