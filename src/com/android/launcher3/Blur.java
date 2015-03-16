package com.android.launcher3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build.VERSION;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
/*
	HOSIN_CUST_LAUNCHER_MESSI
*/
public class Blur {

	private static final String TAG = "Blur";

	@SuppressLint("NewApi")
	public static Bitmap fastblur(Context context, Bitmap sentBitmap, int radius) {
			Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
		if (VERSION.SDK_INT > 16) {
			

			final RenderScript rs = RenderScript.create(context);
			final Allocation input = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE,
					Allocation.USAGE_SCRIPT);
			final Allocation output = Allocation.createTyped(rs, input.getType());
			final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
			script.setRadius(radius);
			script.setInput(input);
			script.forEach(output);
			output.copyTo(bitmap);
			
		}
		return bitmap;
		}

}
