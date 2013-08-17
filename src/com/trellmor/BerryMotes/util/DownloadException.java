package com.trellmor.BerryMotes.util;

import java.io.IOException;

public class DownloadException extends IOException {
	private static final long serialVersionUID = 1L;
	
	public DownloadException(String string) {
		super(string);
	}

}
