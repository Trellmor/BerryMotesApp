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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.trellmor.berrymotes.R;

public class ReportDialog extends DialogFragment {
	private static final String ARG_TITLE = "title";
	private static final String ARG_TEXT = "text";
	public static final String DIALOG_TAG = "fragment_report";

	private EditText mEditEmail;
	private EditText mEditText;
	private ReportDialogListener mListener = null;

	interface ReportDialogListener {
		void onReportDialogFinish(String email, String text);
	}

	public ReportDialog() {

	}

	public static ReportDialog newInstance(String title, int text) {
		ReportDialog dialog = new ReportDialog();
		Bundle args = new Bundle();
		args.putString(ARG_TITLE, title);
		args.putInt(ARG_TEXT, text);
		dialog.setArguments(args);
		return dialog;
	}

	public void setReportDialogListener(ReportDialogListener listener) {
		this.mListener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();

		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_report, null);
		mEditEmail = (EditText) view.findViewById(R.id.edit_email);
		mEditText = (EditText) view.findViewById(R.id.edit_text);
		TextView textText = (TextView) view.findViewById(R.id.text_text);
		textText.setText(args.getInt(ARG_TEXT));

		return new AlertDialog.Builder(getActivity())
				.setView(view)
				.setTitle(args.getString(ARG_TITLE))
				.setPositiveButton(R.string.send,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (mListener != null)
									mListener.onReportDialogFinish(mEditEmail.getText().toString(), mEditText.getText().toString());
							}
						})
				.setNegativeButton(R.string.cancel, null)
				.create();
	}
}
