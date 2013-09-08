package com.trellmor.berrymotes;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.widget.ListView;

import com.trellmor.berrymotes.util.CheckListPreference;

/**
 * 
 * @author declanshanaghy http://blog.350nice.com/wp/archives/240 MultiChoice
 *         Preference Widget for Android
 * 
 * @contributor matiboy Added support for check all/none and custom separator
 *              defined in XML. IMPORTANT: The following attributes MUST be
 *              defined (probably inside attr.xml) for the code to even compile
 *              <declare-styleable name="ListPreferenceMultiSelect"> <attr
 *              format="string" name="checkAll" /> <attr format="string"
 *              name="separator" /> </declare-styleable> Whether you decide to
 *              then use those attributes is up to you.
 * 
 */
public class ListPreferenceMultiSelect extends ListPreference {
	private boolean[] mClickedDialogEntryIndices;
	private String mCheckAllKey;
	private int mCheckAllIndex;
	private String mSeperator;
	private CheckListPreference mPreference;
	private CharSequence[] mEntries;
	private CharSequence[] mEntryValues;

	public ListPreferenceMultiSelect(Context context) {
		this(context, null);
	}

	public ListPreferenceMultiSelect(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.ListPreferenceMultiSelect);
		mCheckAllKey = a
				.getString(R.styleable.ListPreferenceMultiSelect_checkAll);
		if (mCheckAllKey == null) {
			mCheckAllKey = CheckListPreference.DEFAULT_ALL_KEY;
		}
		mSeperator = a
				.getString(R.styleable.ListPreferenceMultiSelect_separator);
		if (mSeperator == null) {
			mSeperator = CheckListPreference.DEFAULT_SEPERATOR;
		}
		a.recycle();

		// Initialize the array of boolean to the same size as number of entries
		mClickedDialogEntryIndices = new boolean[getEntries().length];
	}

	@Override
	public void setEntries(CharSequence[] entries) {
		super.setEntries(entries);
		// Initialize the array of boolean to the same size as number of entries
		mClickedDialogEntryIndices = new boolean[entries.length];
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		mEntries = getEntries();
		mEntryValues = getEntryValues();

		if (mEntries == null || mEntryValues == null
				|| mEntries.length != mEntryValues.length) {
			throw new IllegalStateException(
					"ListPreference requires an entries array and an entryValues array which are both the same length");
		}

		restoreCheckedEntries();

		builder.setMultiChoiceItems(mEntries, mClickedDialogEntryIndices,
				new DialogInterface.OnMultiChoiceClickListener() {
					public void onClick(DialogInterface dialog, int which,
							boolean val) {

						if (mCheckAllKey.equals(mEntryValues[which])) {
							checkAll(dialog, val);
						}

						mClickedDialogEntryIndices[which] = val;
						updateCheckAll(dialog);
					}
				});
	}

	private void checkAll(DialogInterface dialog, boolean val) {
		ListView lv = ((AlertDialog) dialog).getListView();
		int size = lv.getCount();
		for (int i = 0; i < size; i++) {
			lv.setItemChecked(i, val);
			mClickedDialogEntryIndices[i] = val;
		}
	}

	private void updateCheckAll(DialogInterface dialog) {
		boolean allChecked = true;
		for (int i = 0; i < mClickedDialogEntryIndices.length; i++) {
			if (i == mCheckAllIndex) {
				continue;
			} else {
				if (!mClickedDialogEntryIndices[i]) {
					allChecked = false;
					break;
				}
			}
		}

		ListView lv = ((AlertDialog) dialog).getListView();
		lv.setItemChecked(mCheckAllIndex, allChecked);
		mClickedDialogEntryIndices[mCheckAllIndex] = allChecked;
	}

	private void restoreCheckedEntries() {
		mPreference = new CheckListPreference(getValue(), mSeperator,
				mCheckAllKey);

		for (int i = 0; i < mEntryValues.length; i++) {
			CharSequence entry = mEntryValues[i];
			if (mCheckAllKey.equals(entry)) {
				mCheckAllIndex = i;
			}
			mClickedDialogEntryIndices[i] = mPreference.isChecked(entry
					.toString());
		}
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult && mEntryValues != null && mPreference != null) {
			for (int i = 0; i < mEntryValues.length; i++) {
				mPreference.setChecked(mEntryValues[i].toString(),
						mClickedDialogEntryIndices[i]);
			}

			if (callChangeListener(mPreference)) {
				setValue(mPreference.toString());
			}
		}
	}
}
