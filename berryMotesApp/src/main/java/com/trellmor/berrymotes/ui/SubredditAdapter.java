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

package com.trellmor.berrymotes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.trellmor.berrymotes.R;
import com.trellmor.berrymotes.provider.SubredditProvider;

public class SubredditAdapter extends ResourceCursorAdapter implements ListAdapter {
	private int mPosName;
	private int mPosSize;
	private int mPosAdded;

	private static final long ONE_MONTH = 60L * 60L * 24L * 30L * 1000L;

	public SubredditAdapter(Context context) {
		super(context, R.layout.item_subreddit, null, 0);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		SpannableStringBuilder spanBuilder = new SpannableStringBuilder();
		spanBuilder.append(cursor.getString(mPosName));

		spanBuilder.append(String.format(" (%.2fMB)", cursor.getDouble(mPosSize) / 1024 / 1024, 2));

		if (cursor.getLong(mPosAdded) > (System.currentTimeMillis() - ONE_MONTH)) {
			spanBuilder.append(" ");
			int start = spanBuilder.length();
			spanBuilder.append(context.getString(R.string.new_subreddit));
			spanBuilder.setSpan(new SuperscriptSpan(), start, spanBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			spanBuilder.setSpan(new RelativeSizeSpan(0.75f), start, spanBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			spanBuilder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.strawberry)), start, spanBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}

		((TextView) view).setText(spanBuilder);
	}

	@Override
	public Cursor swapCursor(Cursor newCursor) {
		Cursor oldCursor = super.swapCursor(newCursor);
		if (newCursor != null) {
			mPosName = newCursor.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_NAME);
			mPosSize = newCursor.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_SIZE);
			mPosAdded = newCursor.getColumnIndex(SubredditProvider.SubredditColumns.COLUMN_ADDED);
		}
		return oldCursor;
	}
}
