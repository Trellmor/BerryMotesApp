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

import ch.qos.logback.classic.db.names.ColumnName;
import ch.qos.logback.classic.db.names.DBNameResolver;
import ch.qos.logback.classic.db.names.DefaultDBNameResolver;

public class LogProvider extends ContentProvider {
	private LogsDatabase mDatabase;

	public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "vnd.berrymotes.subreddits";
	public static final String CONTENT_TYPE_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "vnd.berrymotes.subreddit";
	public static final String CONTENT_AUTHORITY = "com.trellmor.berrymotes.logs";
	public static final String PATH_LOGS = "logs";
	private static final Uri CONTENT_URI_BASE = Uri.parse("content://" + CONTENT_AUTHORITY);
	public static final Uri CONTENT_URI_LOGS = CONTENT_URI_BASE.buildUpon().appendPath(PATH_LOGS).build();

	private static final int ROUTE_LOGS = 1;
	private static final int ROUTE_LOG = 2;

	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_LOGS, ROUTE_LOGS);
		sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_LOGS + "/*", ROUTE_LOG);
	}

	@Override
	public boolean onCreate() {
		mDatabase = new LogsDatabase(getContext());
		return true;
	}

	@Override
	public String getType(Uri uri) {
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_LOGS:
				return CONTENT_TYPE;
			case ROUTE_LOG:
				return CONTENT_TYPE_ITEM;
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
			case ROUTE_LOGS:
				c = db.query(LogsColumns.TABLE_LOGS, projection, selection, selectionArgs, null, null, sortOrder);
				break;
			case ROUTE_LOG:
				String id = uri.getLastPathSegment();
				c = db.query(LogsColumns.TABLE_LOGS, projection, LogsColumns._ID + " =?", new String[] {id}, null, null, sortOrder);
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
			case ROUTE_LOGS:
				long id = db.insertOrThrow(LogsColumns.TABLE_LOGS, null, values);
				getContext().getContentResolver().notifyChange(uri, null);
				return Uri.parse(CONTENT_URI_LOGS + "/" + id);
			default:
				throw new UnsupportedOperationException("Unknown uri: " + uri);
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		final SQLiteDatabase db = mDatabase.getWritableDatabase();
		final int match = sUriMatcher.match(uri);
		switch (match) {
			case ROUTE_LOG:
				String id = uri.getLastPathSegment();
				selection = LogsColumns._ID + " =?";
				selectionArgs = new String[] {id};
			case ROUTE_LOGS:
				int rowsAffected = db.delete(LogsColumns.TABLE_LOGS, selection, selectionArgs);
				getContext().getContentResolver().notifyChange(uri, null);
				return rowsAffected;
			default:
				throw new UnsupportedOperationException("Unknown uri:" + uri);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO: Implement this to handle requests to update one or more rows.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	static class LogsDatabase extends SQLiteOpenHelper {
		public static final int DATABASE_VERSION = 1;

		private static final String DATABASE_NAME = "logs.db";

		public LogsDatabase(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		private static final String SQL_CREATE_LOGS = "CREATE TABLE " + LogsColumns.TABLE_LOGS + "("
				+ LogsColumns._ID + " INTEGER PRIMARY KEY,"
				+ LogsColumns.COLUMN_TIMESTAMP + " INTEGER,"
				+ LogsColumns.COLUMN_LEVEL + " TEXT,"
				+ LogsColumns.COLUMN_MESSAGE + " TEXT,"
				+ LogsColumns.COLUMN_LOGGER + " TEXT,"
				+ LogsColumns.COLUMN_THREAD_NAME + " TEXT,"
				+ LogsColumns.COLUMN_CALLER_FILENAME + " TEXT,"
				+ LogsColumns.COLUMN_CALLER_CLASS + " TEXT,"
				+ LogsColumns.COLUMN_CALLER_METHOD + " TEXT,"
				+ LogsColumns.COLUMN_CALLER_LINE + " INT)";

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(SQL_CREATE_LOGS);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		}
	}

	public static final class LogsColumns implements BaseColumns {
		public static final String TABLE_LOGS = "logs";

		public static final DBNameResolver DB_NAME_RESOLVER = new DefaultDBNameResolver();

		public static final String COLUMN_TIMESTAMP = DB_NAME_RESOLVER.getColumnName(ColumnName.TIMESTMP);
		public static final String COLUMN_LEVEL = DB_NAME_RESOLVER.getColumnName(ColumnName.LEVEL_STRING);
		public static final String COLUMN_MESSAGE = DB_NAME_RESOLVER.getColumnName(ColumnName.FORMATTED_MESSAGE);
		public static final String COLUMN_LOGGER = DB_NAME_RESOLVER.getColumnName(ColumnName.LOGGER_NAME);
		public static final String COLUMN_THREAD_NAME = DB_NAME_RESOLVER.getColumnName(ColumnName.THREAD_NAME);
		public static final String COLUMN_CALLER_FILENAME = DB_NAME_RESOLVER.getColumnName(ColumnName.CALLER_FILENAME);
		public static final String COLUMN_CALLER_CLASS = DB_NAME_RESOLVER.getColumnName(ColumnName.CALLER_CLASS);
		public static final String COLUMN_CALLER_METHOD = DB_NAME_RESOLVER.getColumnName(ColumnName.CALLER_METHOD);
		public static final String COLUMN_CALLER_LINE = DB_NAME_RESOLVER.getColumnName(ColumnName.CALLER_LINE);
	}
}
