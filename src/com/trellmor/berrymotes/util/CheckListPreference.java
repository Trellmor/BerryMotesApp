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

package com.trellmor.berrymotes.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CheckListPreference {
	public static String DEFAULT_ALL_KEY = "#ALL#";
	public static String DEFAULT_SEPERATOR = ";";

	private String mAllKey;
	private String mSeperator = ";";
	private List<String> mValues;

	public CheckListPreference(String value) {
		this(value, DEFAULT_SEPERATOR, DEFAULT_ALL_KEY);
	}

	public CheckListPreference(String value, String seperator) {
		this(value, seperator, DEFAULT_ALL_KEY);
	}

	public CheckListPreference(String value, String seperator, String allKey) {
		mAllKey = allKey;
		mSeperator = seperator;

		mValues = new ArrayList<String>();
		if (!"".equals(value)) {
			mValues.addAll(Arrays.asList(value.split(seperator)));			
		}
	}
	
	public boolean isChecked(String value) {
		return mValues.contains(mAllKey) || mValues.contains(value);
	}
	
	public void setChecked(String value, boolean checked) {
		if (checked) {
			if (!mValues.contains(value)) {
				mValues.add(value);
			}
		} else {
			while (mValues.contains(value)) {
				mValues.remove(value);
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (String value : mValues) {
			if (!"".equals(value)) {
				sb.append(value).append(mSeperator);
			}
		}
		return sb.toString();
	}
}
