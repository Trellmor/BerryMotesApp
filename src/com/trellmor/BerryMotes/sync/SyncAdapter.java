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

package com.trellmor.BerryMotes.sync;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	private static final String TAG = SyncAdapter.class.getName();

	/**
	 * Set up the sync adapter
	 */
	
	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}
	
	/**
	 * Set up the sync adapter. This form of the
	 * constructor maintains compatibility with Android 3.0
	 * and later platform versions
	 */	
	@SuppressLint("NewApi")
	public SyncAdapter(Context context, boolean autoInitialize, boolean allowParalellSyncs) {
		super(context, autoInitialize, allowParalellSyncs);
	}
	
	@Override
	public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {
	
		Log.i(TAG, "Sync start");
		

		Log.i(TAG, "Sync finished");
	}

}
