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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.trellmor.berrymotes.R;

public class SubredditSortDialog extends DialogFragment {
	private static final String ARG_CURRENT = "current";
	public static final String DIALOG_TAG = "fragment_subreddit_sort";

	public static final int SORT_NAME = 0;
	public static final int SORT_SIZE = 1;
	public static final int SORT_ADDED = 2;

	private SubredditSortDialogListener mListener = null;

	interface SubredditSortDialogListener {
		void onSubredditSortDialogFinish(int sort);
	}

	public SubredditSortDialog() {

	}

	public static SubredditSortDialog newInstance(int currentSort) {
		SubredditSortDialog dialog = new SubredditSortDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_CURRENT, currentSort);
		dialog.setArguments(args);
		return dialog;
	}

	public void setSubredditSortDialogListener(SubredditSortDialogListener listener) {
		this.mListener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		String[] items = new String[] {
				getString(R.string.sort_name),
				getString(R.string.sort_size),
				getString(R.string.sort_added)
		};

		return new AlertDialog.Builder(getActivity())
				.setTitle(getString(R.string.title_sort))
				.setSingleChoiceItems(items, getArguments().getInt(ARG_CURRENT), null)
				.setPositiveButton(R.string.sort,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (mListener != null) {
									int selected = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
									mListener.onSubredditSortDialogFinish(selected);
								}
							}
						})
				.setNegativeButton(R.string.cancel, null)
				.create();
	}
}
