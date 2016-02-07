/*
 * BerryMotes
 * Copyright (C) 2015-2016 Daniel Triendl <trellmor@trellmor.com>
 *
 * BerryMotes is free software: you can redistribute it and/or modify
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

package com.trellmor.berrymotes.ui;

import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.trellmor.berrymotes.R;
import com.trellmor.berrymotes.api.Log;
import com.trellmor.berrymotes.api.UploadLogTask;
import com.trellmor.berrymotes.util.Installation;
import com.trellmor.berrymotes.util.Settings;
import com.trellmor.berrymotes.provider.FileContract;
import com.trellmor.berrymotes.provider.SubredditProvider;
import com.trellmor.berrymotes.sync.EmoteDownloader;
import com.trellmor.berrymotes.sync.SubredditDownloader;
import com.trellmor.berrymotes.sync.SyncUtils;

import java.io.File;

public class SettingsFragment extends PreferenceFragment {

	private Preference mPrefLogSend;
	private Preference mPrefLogDelete;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Add 'general' preferences.
		addPreferencesFromResource(R.xml.pref_general);
		addPreferencesFromResource(R.xml.pref_data_sync);
		addPreferencesFromResource(R.xml.pref_logging);

		bindPreferenceSummaryToValue(findPreference(Settings.KEY_SYNC_FREQUENCY));
		bindPreferenceSummaryToValue(findPreference(Settings.KEY_SYNC_CONNECTION));
		findPreference(Settings.KEY_SYNC_ALL_SUBREDDITS).setOnPreferenceChangeListener(sSubredditChangeListener);

		mPrefLogSend = findPreference(Settings.KEY_LOG_SEND);
		mPrefLogSend.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				sendLogFile();
				return true;
			}
		});

		mPrefLogDelete = findPreference(Settings.KEY_LOG_DELETE);
		mPrefLogDelete.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				File log = new File(getActivity().getFilesDir(), EmoteDownloader.LOG_FILE_NAME);
				if (log.exists()) {
					log.delete();
				}

				checkLogFile();
				return true;
			}
		});

		checkLogFile();

		//Fetch Subreddit list
		Thread t = new Thread(new SubredditDownloader(getActivity()));
		t.start();
	}

	private void checkLogFile() {
		boolean enabled = false;
		if (new File(getActivity().getFilesDir(), EmoteDownloader.LOG_FILE_NAME).exists()) {
			enabled = true;
		}
		mPrefLogDelete.setEnabled(enabled);
		mPrefLogSend.setEnabled(enabled);
	}

	private void sendLogFile() {
		FragmentManager fm = getFragmentManager();
		ReportDialog dialog = ReportDialog.newInstance(getString(R.string.pref_title_log_send), R.string.description);
		dialog.setReportDialogListener(new ReportDialog.ReportDialogListener() {
			@Override
			public void onReportDialogFinish(String email, String description) {
				Log log = new Log();
				log.setMail(email);
				log.setDescription(description);
				log.setInstance(Installation.id(getActivity()));

				UploadLogTask task = new UploadLogTask(getActivity().getApplicationContext());
				task.execute(log);
			}
		});
		dialog.show(fm, ReportDialog.DIALOG_TAG);

/*
		Intent intent = new Intent(Intent.ACTION_SEND);
		// Not perfect - ACTION_SENDTO would be preferred, but the default email
		// app ignores the EXTRA_STREAM if the Intent uses ACTION_SENDTO
		intent.setType("message/rfc822");
		intent.putExtra(Intent.EXTRA_SUBJECT, "BerryMotesApp sync log");
		intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "berrymotes-synclog@trellmor.com" });
		intent.putExtra(Intent.EXTRA_STREAM,
				FileContract.CONTENT_URI_FILE.buildUpon().appendPath(EmoteDownloader.LOG_FILE_NAME).build());
		startActivity(Intent.createChooser(intent, getText(R.string.send_log_chooser)));*/
	}

	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				if (preference.getKey().equals(Settings.KEY_SYNC_CONNECTION)) {
					if (stringValue.equals(Settings.VALUE_SYNC_CONNECTION_ALL)) {
						preference.setSummary(R.string.pref_description_sync_connection_all);
					} else {
						preference.setSummary(R.string.pref_description_sync_connection_wifi);

						// Stop current sync
						SyncUtils.cancelSync();
					}
				} else {
					// For list preferences, look up the correct display value
					// in the preference's 'entries' list.
					ListPreference listPreference = (ListPreference) preference;
					int index = listPreference.findIndexOfValue(stringValue);

					// Set the summary to reflect the new value.
					preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
				}
			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}

			if (preference.getKey().equals(Settings.KEY_SYNC_FREQUENCY)) {
				// Adjust sync frequency
				SyncUtils.setSyncFrequency(Integer.parseInt(stringValue));
			}
			return true;
		}
	};

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager
				.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
	}

	/**
	 * A preference value change listener that clears the last sync date
	 */
	private final Preference.OnPreferenceChangeListener sSubredditChangeListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			clearLastModified(preference.getContext());
			SyncUtils.cancelSync();
			return true;
		}

		private void clearLastModified(Context context) {
			ContentValues values = new ContentValues();
			values.put(SubredditProvider.SubredditColumns.COLUMN_LAST_SYNC, 0);
			context.getContentResolver().update(SubredditProvider.CONTENT_URI_SUBREDDITS, values, null, null);
		}
	};
}
