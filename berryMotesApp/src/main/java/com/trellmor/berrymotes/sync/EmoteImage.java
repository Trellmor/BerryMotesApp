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

package com.trellmor.berrymotes.sync;

import java.util.List;

public class EmoteImage {
	private boolean apng = false;
	private boolean nsfw = false;
	private int delay = 0;
	private int index = 0;
	private String hash;
	private String image;
	private String sr;
	private List<String> names;

	public boolean isApng() {
		return apng;
	}

	public boolean isNsfw() {
		return nsfw;
	}

	public List<String> getNames() {
		return names;
	}

	public int getDelay() {
		return delay;
	}

	public int getIndex() {
		return index;
	}

	public String getImage() {
		return image;
	}
	
	public String getHash() {
		return hash;
	}
	
	public String getSubreddit() {
		return sr;
	}
}
