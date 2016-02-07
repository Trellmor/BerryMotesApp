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

package com.trellmor.berrymotes.ui;

import java.util.ArrayList;
import java.util.List;

import com.trellmor.berrymotes.provider.EmotesContract;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.CursorAdapter;

class EmoteLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
	private final Context mContext;
	private final CursorAdapter mAdapter;

	private static final String[] PROJECTION = new String[] {
			EmotesContract.Emote._ID,
			EmotesContract.Emote.COLUMN_NAME,
			EmotesContract.Emote.COLUMN_IMAGE,
			EmotesContract.Emote.COLUMN_APNG };

	public static final String ARG_QUERY = "query";

	public EmoteLoaderCallbacks(Context context, CursorAdapter adapter) {
		mContext = context;
		mAdapter = adapter;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		StringBuilder selection = new StringBuilder(EmotesContract.Emote.COLUMN_INDEX + "=?");
		String[] selectionArgs;
		if (args != null && args.containsKey(ARG_QUERY)) {
			String[] selections = args.getString(ARG_QUERY).trim().split(" +");

			List<String> names = new ArrayList<>(selections.length);
			List<String> subreddits = new ArrayList<>(selections.length);
			for (String sel : selections) {
				if (sel.startsWith("sr:")) {
					sel = sel.replaceFirst("sr:", "");
					if (!"".equals(sel))
						subreddits.add(sel);
				} else {
					names.add(sel);
				}
			}

			selectionArgs = new String[1 + names.size() + subreddits.size()];
			if (names.size() > 0) {
				selection.append(" AND (");
				for (int i = 0; i < names.size(); i++) {
					if (i > 0) selection.append(" AND ");
					selection.append(EmotesContract.Emote.COLUMN_NAME).append(" LIKE ?");
					selectionArgs[i + 1] = "%" + names.get(i) + "%";
				}
				selection.append(")");
			}

			if (subreddits.size() > 0) {
				selection.append(" AND (");
				for (int i = 0; i < subreddits.size(); i++) {
					if (i > 0) selection.append(" OR ");
					selection.append(EmotesContract.Emote.COLUMN_SUBREDDIT).append(" LIKE ?");
					selectionArgs[i + 1 + names.size()] = "%" + subreddits.get(i) + "%";
				}
				selection.append(")");
			}
		} else {
			selectionArgs = new String[1];
		}
		selectionArgs[0] = "0";

		return new CursorLoader(mContext, EmotesContract.Emote.CONTENT_URI,
				PROJECTION, selection.toString(), selectionArgs,
				EmotesContract.Emote.COLUMN_NAME + " ASC");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mAdapter.changeCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.changeCursor(null);
	}

}
