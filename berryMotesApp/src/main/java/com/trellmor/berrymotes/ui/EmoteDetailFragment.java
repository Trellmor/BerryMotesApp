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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.trellmor.berrymotes.EmoteGetter;
import com.trellmor.berrymotes.R;
import com.trellmor.berrymotes.api.*;
import com.trellmor.berrymotes.loader.ScalingEmoteLoader;
import com.trellmor.berrymotes.provider.EmotesContract;
import com.trellmor.berrymotes.util.Installation;

/**
 * A fragment representing a single Emote detail screen. This fragment is either
 * contained in a {@link EmoteGridActivity} in two-pane mode (on tablets) or a
 * {@link EmoteDetailActivity} on handsets.
 */
public class EmoteDetailFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Cursor> {
	/**
	 * The fragment argument representing the item ID that this fragment
	 * represents.
	 */
	public static final String ARG_EMOTE_ID = "emote_id";

	public static final int LOADER_ID = 2000;

	private Button mTextEmoteName;
	private ImageView mImageEmote;
	private boolean mViewLoaded = false;
	private boolean mDataLoaded = false;
	private long mEmoteId;

	private Drawable mEmote;
	private String mName = null;
	private String mSubreddit = null;
	private boolean mIsAPNG;

	/**
	 * The fragment's current callback object, which is notified of list item
	 * clicks.
	 */
	private Callbacks mCallbacks = sDummyCallbacks;

	/**
	 * A callback interface that all activities containing this fragment must
	 * implement. This mechanism allows activities to be notified of item
	 * selections.
	 */
	public interface Callbacks {
		/**
		 * Callback for when an item has been selected.
		 */
		public void onEmoteLoaded(String name, boolean apng);
	}

	/**
	 * A dummy implementation of the {@link Callbacks} interface that does
	 * nothing. Used only when this fragment is not attached to an activity.
	 */
	private static final Callbacks sDummyCallbacks = new Callbacks() {
		@Override
		public void onEmoteLoaded(String name, boolean apng) {
		}
	};


	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public EmoteDetailFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getArguments().containsKey(ARG_EMOTE_ID)) {
			mEmoteId = getArguments().getLong(ARG_EMOTE_ID);
			getLoaderManager().initLoader(LOADER_ID, null, this);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_emote_detail,
				container, false);

		mTextEmoteName = (Button) rootView.findViewById(R.id.text_emote_name);
		mTextEmoteName.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mName != null) {
					ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
					ClipData clip = ClipData.newPlainText(mName, "[](/" + mName + ")");
					clipboard.setPrimaryClip(clip);
					Toast.makeText(getActivity(), R.string.emotename_copied, Toast.LENGTH_SHORT).show();
				}
			}
		});

		mImageEmote = (ImageView) rootView
				.findViewById(R.id.image_emote_detail);
		mViewLoaded = true;

		loadData();

		return rootView;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		// Activities containing this fragment must implement its callbacks.
		if (!(activity instanceof Callbacks)) {
			throw new IllegalStateException("Activity must implement fragment's callbacks.");
		}

		mCallbacks = (Callbacks) activity;
	}

	@Override
	public void onDetach() {
		super.onDetach();

		// Reset the active callbacks interface to the dummy implementation.
		mCallbacks = sDummyCallbacks;
	}

	private void loadData() {
		if (mViewLoaded && mDataLoaded) {
			mImageEmote.setBackgroundDrawable(mEmote);
			if (mEmote instanceof AnimationDrawable) {
				((AnimationDrawable)mEmote).start();
			}
			mTextEmoteName.setText(mName);
		}
	}

	public void reportEmote() {
		if (mName != null) {
			FragmentManager fm = getFragmentManager();
			ReportDialog dialog = ReportDialog.newInstance(String.format(getString(R.string.report_emote), mName), R.string.reason);

			dialog.setReportDialogListener(new ReportDialog.ReportDialogListener() {
				@Override
				public void onReportDialogFinish(String email, String reason) {
					final Context context = getActivity();

					Report report = new Report();
					report.setEmote(mName);
					report.setSubreddit(mSubreddit);
					report.setMail(email);
					report.setReason(reason);
					report.setInstance(Installation.id(context));

					SendReportTask task = new SendReportTask();
					task.setReportCallback(new SendReportTask.SendReportCallback() {
						@Override
						public void onReportSend() {
							Toast.makeText(context, R.string.report_send, Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onFail(com.trellmor.berrymotes.api.Error error) {
							Toast.makeText(context, R.string.report_error, Toast.LENGTH_SHORT).show();
						}
					});
					task.execute(report);
				}
			});
			dialog.show(fm, ReportDialog.DIALOG_TAG);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(getActivity(),
				EmotesContract.Emote.CONTENT_URI,
				new String[] {
						EmotesContract.Emote.COLUMN_NAME,
						EmotesContract.Emote.COLUMN_APNG,
						EmotesContract.Emote.COLUMN_SUBREDDIT
				},
				EmotesContract.Emote._ID + "=?",
				new String[] { String.valueOf(mEmoteId) },
				EmotesContract.Emote.COLUMN_NAME + " ASC");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (data != null && data.getCount() > 0) {
			data.moveToFirst();
			mName = data.getString(data.getColumnIndex(EmotesContract.Emote.COLUMN_NAME));
			mIsAPNG = data.getInt(data.getColumnIndex(EmotesContract.Emote.COLUMN_APNG)) == 1;
			mSubreddit = data.getString(data.getColumnIndex(EmotesContract.Emote.COLUMN_SUBREDDIT));
			mCallbacks.onEmoteLoaded(mName, mIsAPNG);
			mImageEmote.setBackgroundDrawable(null);
			LoadEmoteTask task = new LoadEmoteTask();
			task.execute(mName);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	private class LoadEmoteTask extends AsyncTask<String, Void, Drawable> {

		@Override
		protected Drawable doInBackground(String... params) {
			EmoteGetter getter = new EmoteGetter(getActivity(), new ScalingEmoteLoader(getActivity()));
			return getter.getDrawable(mName);
		}

		@Override
		protected void onPostExecute(Drawable result) {
			mEmote = result;
			mDataLoaded = true;
			loadData();
		}

	}
}
