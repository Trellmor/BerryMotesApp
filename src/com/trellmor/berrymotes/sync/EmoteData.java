package com.trellmor.berrymotes.sync;

import java.util.List;

public class EmoteData {
	private boolean apng = false;
	private boolean nsfw = false;
	private List<Image> images;

	public boolean isApng() {
		return apng;
	}

	public boolean isNsfw() {
		return nsfw;
	}

	public List<Image> getImages() {
		return images;
	}
	
	public static class Image {
		private int delay = 0;
		private int index = 0;
		private String image;
		
		public int getDelay() {
			return delay;
		}
		public int getIndex() {
			return index;
		}
		public String getImage() {
			return image;
		}
	}
}

