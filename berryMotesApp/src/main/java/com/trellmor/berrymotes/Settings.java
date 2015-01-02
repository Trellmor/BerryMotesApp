/*
 * BerryMotes android
 * Copyright (C) 2015 Daniel Triendl <trellmor@trellmor.com>
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
package com.trellmor.berrymotes;

public class Settings {
	public final static String KEY_SHOW_NSFW = "show_nsfw";
	public final static String KEY_BACKGROUND = "background";
	public final static String KEY_SYNC_CONNECTION = "sync_connection";
	public final static String KEY_SYNC_NSFW = "sync_nsfw";
	public final static String KEY_SYNC_FREQUENCY = "sync_frequency";
	public final static String KEY_SYNC_LAST_MODIFIED = "sync_last_modified";
	public final static String KEY_SYNC_SUBREDDITS = "sync_subreddits";
	public final static String KEY_LOG = "log";
	public final static String KEY_LOG_DELETE = "log_delete";
	public final static String KEY_LOG_SEND = "log_send";

	public final static String VALUE_SYNC_CONNECTION_WIFI = "wifi";
	public final static String VALUE_SYNC_CONNECTION_ALL = "all";

	public final static String DEFAULT_SYNC_SUBREDDITS = "#ALL#";
	public final static String ALL_KEY_SYNC_SUBREDDITS = "#ALL#";
	public final static String SEPARATOR_SYNC_SUBREDDITS = ";";
}
