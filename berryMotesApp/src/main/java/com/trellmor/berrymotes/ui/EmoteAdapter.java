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

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;

import com.trellmor.berrymotes.R;
import com.trellmor.berrymotes.loader.EmoteLoader;
import com.trellmor.berrymotes.loader.ScalingEmoteLoader;
import com.trellmor.berrymotes.provider.EmotesContract;

public class EmoteAdapter extends CursorAdapter implements ListAdapter {
	private final LayoutInflater mInflater;
	private final LruCache<String, Drawable> mCache;
	private final EmoteLoader mLoader;

	public EmoteAdapter(Context context) {
		super(context, null, 0);

		mInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mCache = new LruCache<>(100);
		mLoader = new ScalingEmoteLoader(context);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		View view = mInflater.inflate(R.layout.emote_item, parent, false);
		ImageView imageEmote = (ImageView) view.findViewById(R.id.image_emote);

		view.setTag(R.id.image_emote, imageEmote);
		view.setTag(R.id.task_load_image, null);

		return view;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		ImageView imageEmote = (ImageView) view.getTag(R.id.image_emote);
		LoadEmoteTask task = (LoadEmoteTask) view.getTag(R.id.task_load_image);
		if (task != null) {
			task.cancel(true);
			view.setTag(R.id.task_load_image, null);
		}

		String emote = cursor.getString(cursor.getColumnIndex(EmotesContract.Emote.COLUMN_NAME));

		Drawable d;
		synchronized (mCache) {
			d = mCache.get(emote);
		}
		if (d != null) {
			imageEmote.setImageDrawable(d);
		} else {
			imageEmote.setImageDrawable(null);
			String path = cursor.getString(cursor.getColumnIndex(EmotesContract.Emote.COLUMN_IMAGE));
			task = new LoadEmoteTask(view);
			view.setTag(R.id.task_load_image, task);
			task.execute(emote, path);
		}
	}

	private class LoadEmoteTask extends AsyncTask<String, Void, Drawable>
	{
		private final View mView;

		public LoadEmoteTask(View view) {
			mView = view;
		}

		@Override
		protected Drawable doInBackground(String... params) {
			String emote = params[0];
			String path = params[1];
			Drawable d = mLoader.fromPath(path);
			if (d != null) {
				mCache.put(emote, d);
				return d;
			} else {
				return null;
			}
		}

		@Override
		protected void onPostExecute (Drawable result) {
			if (result != null) {
				ImageView imageEmote = (ImageView) mView.getTag(R.id.image_emote);
				imageEmote.setImageDrawable(result);
			}
			mView.setTag(R.id.task_load_image, null);
		}
	}
}
