package com.trellmor.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.trellmor.berrymotes.R;

public class SquareImageView extends ImageView {
	public static enum FixedAlong {
		width, height
	}

	private FixedAlong fixedAlong = FixedAlong.width;

	public SquareImageView(Context context) {
		super(context);
	}

	public SquareImageView(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray array = context.obtainStyledAttributes(attrs,
				R.styleable.SquareImageView, 0, 0);

		fixedAlong = FixedAlong.valueOf(array
				.getString(R.styleable.SquareImageView_fixedAlong));
		if (fixedAlong == null)
			fixedAlong = FixedAlong.width;

		array.recycle();
	}

	public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	int squareDimen = 1;

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int square = (fixedAlong == FixedAlong.width) ? getMeasuredWidth()
				: getMeasuredHeight();

		if (square > squareDimen) {
			squareDimen = square;
		}

		setMeasuredDimension(squareDimen, squareDimen);
	}

}
