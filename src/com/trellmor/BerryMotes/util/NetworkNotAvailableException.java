package com.trellmor.BerryMotes.util;

import java.io.IOException;

public class NetworkNotAvailableException extends IOException {
	private static final long serialVersionUID = 1L;

	public NetworkNotAvailableException(String string) {
		super(string);
	}

}
