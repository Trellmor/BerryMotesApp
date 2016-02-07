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

package com.trellmor.berrymotes.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

public class SubredditProvider extends ContentProvider {
	private SubredditsDatabase mDatabase;

	public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "vnd.berrymotes.subreddits";
	public static final String CONTENT_TYPE_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "vnd.berrymotes.subreddit";
	public static final String CONTENT_AUTHORITY = "com.trellmor.berrymotes.subreddits";
	public static final String PATH_SUBREDDITS = "subreddits";
	private static final Uri CONTENT_URI_BASE = Uri.parse("content://" + CONTENT_AUTHORITY);
	public static final Uri CONTENT_URI_SUBREDDITS = CONTENT_URI_BASE.buildUpon().appendPath(PATH_SUBREDDITS).build();

	private static final int ROUTE_SUBREDDITS = 1;
	private static final int ROUTE_SUBREDDIT = 2;

	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_SUBREDDITS, ROUTE_SUBREDDITS);
		sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_SUBREDDITS + "/*", ROUTE_SUBREDDIT);
	}

	@Override
	public boolean onCreate() {
		mDatabase = new SubredditsDatabase(getContext());

		return true;
	}

	@Override
	public String getType(Uri uri) {
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_SUBREDDITS:
				return CONTENT_TYPE;
			case ROUTE_SUBREDDIT:
				return  CONTENT_TYPE_ITEM;
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		final SQLiteDatabase db = mDatabase.getReadableDatabase();
		final int match = sUriMatcher.match(uri);
		Cursor c;
		switch (match) {
			case ROUTE_SUBREDDITS:
				c = db.query(SubredditColumns.TABLE_SUBREDDITS, projection, selection, selectionArgs, null, null, sortOrder);
				break;
			case ROUTE_SUBREDDIT:
				String id = uri.getLastPathSegment();
				c = db.query(SubredditColumns.TABLE_SUBREDDITS, projection, SubredditColumns._ID + " =?", new String[] {id}, null, null, sortOrder);
				break;
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}

		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		final SQLiteDatabase db = mDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_SUBREDDITS:
				long id = db.insertOrThrow(SubredditColumns.TABLE_SUBREDDITS, null, values);
				Context context = getContext();
				context.getContentResolver().notifyChange(uri, null);
				return Uri.parse(CONTENT_URI_SUBREDDITS + "/" + id);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		final SQLiteDatabase db = mDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_SUBREDDIT:
				String id = uri.getLastPathSegment();
				selection = SubredditColumns._ID + " =?";
				selectionArgs = new String[] {id};
			case ROUTE_SUBREDDITS:
				int rowsAffected = db.delete(SubredditColumns.TABLE_SUBREDDITS, selection, selectionArgs);
				getContext().getContentResolver().notifyChange(uri, null);
				return rowsAffected;
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		final SQLiteDatabase db = mDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_SUBREDDIT:
				String id = uri.getLastPathSegment();
				selection = SubredditColumns._ID + " =?";
				selectionArgs = new String[] {id};
			case ROUTE_SUBREDDITS:
				int rowsAffected = db.update(SubredditColumns.TABLE_SUBREDDITS, values, selection, selectionArgs);
				getContext().getContentResolver().notifyChange(uri, null);
				return rowsAffected;
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	static class SubredditsDatabase extends SQLiteOpenHelper {
		public static final int DATABASE_VERSION = 2;

		private static final String DATABASE_NAME = "subreddits.db";

		private static final String SQL_CREATE_ENTRIES = "CREATE TABLE "
				+ SubredditColumns.TABLE_SUBREDDITS + " ("
				+ SubredditColumns._ID + " INTEGER PRIMARY KEY,"
				+ SubredditColumns.COLUMN_NAME + " TEXT,"
				+ SubredditColumns.COLUMN_LAST_SYNC + " INTEGER,"
				+ SubredditColumns.COLUMN_ENABLED + " INTEGER,"
				+ SubredditColumns.COLUMN_ADDED + " INTEGER,"
				+ SubredditColumns.COLUMN_SIZE + " INTEGER)";

		private static final String SQL_DROP_ENTRIES = "DROP TABLE IF EXISTS "
				+ SubredditColumns.TABLE_SUBREDDITS;

		public SubredditsDatabase(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(SQL_CREATE_ENTRIES);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			switch (oldVersion) {
				case 0:
				case 1:
					db.execSQL(SQL_DROP_ENTRIES);
					onCreate(db);
					break;
			}
		}
	}

	public static final class SubredditColumns implements BaseColumns {
		public static final String TABLE_SUBREDDITS = "subreddits";

		public static final String COLUMN_NAME = "name";
		public static final String COLUMN_LAST_SYNC = "last_sync";
		public static final String COLUMN_ENABLED = "enabled";
		public static final String COLUMN_ADDED = "added";
		public static final String COLUMN_SIZE = "size";
	}
}
