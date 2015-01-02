/*
 * Copyright 2013 Google Inc.
 * Copyright 2013-2015 Daniel Triendl <daniel@pew.cc>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trellmor.berrymotes.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.Settings;

/**
 * Static helper methods for working with the sync framework.
 */
public class SyncUtils {
	private static final String CONTENT_AUTHORITY = EmotesContract.CONTENT_AUTHORITY;
	private static final String PREF_SETUP_COMPLETE = "setup_complete";

	/**
	 * Create an entry for this application in the system account list, if it
	 * isn't already there.
	 * 
	 * @param context
	 *            Context
	 */
	public static void createSyncAccount(Context context) {
		boolean newAccount = false;
		boolean setupComplete = PreferenceManager.getDefaultSharedPreferences(
				context).getBoolean(PREF_SETUP_COMPLETE, false);
		int syncFrequency = Integer.parseInt(PreferenceManager
				.getDefaultSharedPreferences(context).getString(
						Settings.KEY_SYNC_FREQUENCY, "6"));

		// Create account, if it's missing. (Either first run, or user has
		// deleted account.)
		Account account = GenericAccountService.getAccount();
		AccountManager accountManager = (AccountManager) context
				.getSystemService(Context.ACCOUNT_SERVICE);
		if (accountManager.addAccountExplicitly(account, null, null)) {
			// Inform the system that this account supports sync
			ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1);
			// Inform the system that this account is eligible for auto sync
			// when the network is up
			ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY,
					true);
			// Recommend a schedule for automatic synchronization. The system
			// may modify this based on other scheduled syncs and network
			// utilization.
			setSyncFrequency(syncFrequency);
			newAccount = true;
		}

		// Schedule an initial sync if we detect problems with either our
		// account or our local data has been deleted. (Note that it's possible
		// to clear app data WITHOUT affecting the account list, so wee need to
		// check both.)
		if (newAccount || !setupComplete) {
			triggerRefresh();
			PreferenceManager.getDefaultSharedPreferences(context).edit()
					.putBoolean(PREF_SETUP_COMPLETE, true).commit();
		}
	}

	/**
	 * Helper method to trigger an immediate sync ("refresh").
	 * 
	 * <p>
	 * This should only be used when we need to preempt the normal sync
	 * schedule. Typically, this means the user has pressed the "refresh"
	 * button.
	 * 
	 * Note that SYNC_EXTRAS_MANUAL will cause an immediate sync, without any
	 * optimization to preserve battery life. If you know new data is available
	 * (perhaps via a GCM notification), but the user is not actively waiting
	 * for that data, you should omit this flag; this will give the OS
	 * additional freedom in scheduling your sync request.
	 */
	public static void triggerRefresh() {
		Bundle b = new Bundle();
		// Disable sync backoff and ignore sync preferences. In other
		// words...perform sync NOW!
		b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		ContentResolver.requestSync(GenericAccountService.getAccount(), // Sync
																		// account
				EmotesContract.CONTENT_AUTHORITY, // Content authority
				b); // Extras
	}

	public static void setSyncFrequency(int hours) {
		// Only set up new sync if interval is positive, otherwise sync is
		// disabled
		if (hours > 0) {
			ContentResolver.addPeriodicSync(GenericAccountService.getAccount(),
					EmotesContract.CONTENT_AUTHORITY, new Bundle(),
					60 * 60 * hours);
		} else {
			ContentResolver.removePeriodicSync(
					GenericAccountService.getAccount(),
					EmotesContract.CONTENT_AUTHORITY, new Bundle());
		}
	}

	public static void cancelSync() {
		ContentResolver.cancelSync(GenericAccountService.getAccount(),
				EmotesContract.CONTENT_AUTHORITY);
	}
}
