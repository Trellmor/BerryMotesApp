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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SyncService extends Service {
	// Storage for sync adapter
	private static SyncAdapter sSyncAdapter = null;
	
	// Object for thread safe locking
	private static final Object sSyncAdapterLock = new Object();
	
	@Override
	public void onCreate() {
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
			}
		}
	}

	/**
	 * Return an object that allows the system to invoke
	 * the sync adapter.
	 *
	 */
	
	@Override
	public IBinder onBind(Intent intent) {
		/*
		 * Get the object that allows external processes
		 * to call onPerformSync(). The object is created
		 * in the base class code when the SyncAdapter
		 * constructors call super()
		 */
		return sSyncAdapter.getSyncAdapterBinder();
	}
}
