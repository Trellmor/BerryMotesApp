package com.trellmor.berrymotes.util;

import java.io.IOException;

public class StorageNotAvailableException extends IOException {
	private static final long serialVersionUID = 1L;
	
	public StorageNotAvailableException(String string) {
		super(string);
	}
}
