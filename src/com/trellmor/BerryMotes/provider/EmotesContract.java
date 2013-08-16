package com.trellmor.BerryMotes.provider;

import android.net.Uri;

public class EmotesContract {
	private EmotesContract() {
	}

	/**
	 * Content provider authority.
	 */
	public static final String CONTENT_AUTHORITY = "com.trellmor.BerryMotes";

	/**
	 * Base URI. (content://com.example.android.network.sync.basicsyncadapter)
	 */
	public static final Uri BASE_CONTENT_URI = Uri.parse("content://"
			+ CONTENT_AUTHORITY);
}
