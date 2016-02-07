package ch.qos.logback.classic.android;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import ch.qos.logback.classic.db.names.ColumnName;
import ch.qos.logback.classic.db.names.DBNameResolver;
import ch.qos.logback.classic.db.names.DefaultDBNameResolver;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

/**
 * Created by Daniel on 31.01.2016.
 */
public class ContentProviderAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
	private final Context mContext;
	private Uri mLogsUri;
	private DBNameResolver dbNameResolver;

	public ContentProviderAppender(Context context) {
		mContext = context;
	}

	public void setDbNameResolver(DBNameResolver dbNameResolver) {
		this.dbNameResolver = dbNameResolver;
	}

	public void setLogsUri(Uri logsUri) {
		mLogsUri = logsUri;
	}

	@Override
	public void start() {
		if (mLogsUri == null) {
			addError("Logs URI not set");
			return;
		}

		if (dbNameResolver == null) {
			dbNameResolver = new DefaultDBNameResolver();
		}

		super.start();
		this.started = true;
	}

	@Override
	public void append(ILoggingEvent event) {
		if (isStarted()) {
			subAppend(event);
		}
	}

	private long subAppend(ILoggingEvent event) {
		ContentResolver resolver = mContext.getContentResolver();
		ContentValues values = new ContentValues();
		putEvent(values, event);
		putCallerData(values, event.getCallerData());
		Uri uri = resolver.insert(mLogsUri, values);
		return Long.parseLong(uri.getLastPathSegment());
	}

	private void putEvent(ContentValues values, ILoggingEvent event) {
		values.put(dbNameResolver.getColumnName(ColumnName.TIMESTMP), event.getTimeStamp());
		values.put(dbNameResolver.getColumnName(ColumnName.LEVEL_STRING), event.getLevel().toString());
		values.put(dbNameResolver.getColumnName(ColumnName.LOGGER_NAME), event.getLoggerName());
		values.put(dbNameResolver.getColumnName(ColumnName.FORMATTED_MESSAGE), event.getFormattedMessage());
		values.put(dbNameResolver.getColumnName(ColumnName.THREAD_NAME), event.getThreadName());
	}

	private void putCallerData(ContentValues values, StackTraceElement[] callerDataArray) {
		if (callerDataArray != null && callerDataArray.length > 0) {
			StackTraceElement callerData = callerDataArray[0];
			if (callerData != null) {
				values.put(dbNameResolver.getColumnName(ColumnName.CALLER_FILENAME), callerData.getFileName());
				values.put(dbNameResolver.getColumnName(ColumnName.CALLER_CLASS), callerData.getClassName());
				values.put(dbNameResolver.getColumnName(ColumnName.CALLER_METHOD), callerData.getMethodName());
				values.put(dbNameResolver.getColumnName(ColumnName.CALLER_LINE), callerData.getLineNumber());
			}
		}
	}
}
