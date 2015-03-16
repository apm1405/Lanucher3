/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import com.android.launcher3.R;
//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import java.io.IOException;
import com.mediatek.common.featureoption.FeatureOption;
//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
public class PageIndicatorMarker extends FrameLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "PageIndicator";

    private static final int MARKER_FADE_DURATION = 175;

    private ImageView mActiveMarker;
    private ImageView mInactiveMarker;
    private boolean mIsActive = false;
	private String themekeyName;//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI  
	private Context mContext;//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
    public PageIndicatorMarker(Context context) {
        this(context, null);
		mContext = context;//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
    }

    public PageIndicatorMarker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
		mContext = context;
    }

    public PageIndicatorMarker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
		if(FeatureOption.HOSIN_CUST_LAUNCHER_MESSI){
			mContext = context;
			themekeyName = context.getSharedPreferences(LauncherApplication.SHARED_PREFRENCE,
				Context.MODE_PRIVATE).getString(LauncherApplication.NAME_KEY,"default");
		}
		//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI  end
	
    }

    protected void onFinishInflate() {
        mActiveMarker = (ImageView) findViewById(R.id.active);
        mInactiveMarker = (ImageView) findViewById(R.id.inactive);
    }

    void setMarkerDrawables(int activeResId, int inactiveResId) {
        Resources r = getResources();
		if(FeatureOption.HOSIN_CUST_LAUNCHER_MESSI){	
			// Hosin_messi modify  start
	Drawable active = null;
	Drawable inactive = null;
		if(!themekeyName.equals("default"))
		{
			try{
			active = new FastBitmapDrawable(BitmapFactory.decodeStream(mContext.getAssets().
				open(mContext.getString(R.string.theme)+"/"+themekeyName+"/icon"+
				"/ic_pageindicator_current.png")));
			inactive = new FastBitmapDrawable(BitmapFactory.decodeStream(mContext.getAssets().
				open(mContext.getString(R.string.theme)+"/"+themekeyName+"/icon"+
				"/ic_pageindicator_default.png")));
			}catch(IOException e){
				e.printStackTrace();
				}
			if(null==active || null ==inactive ){
			active = r.getDrawable(activeResId);
            		inactive =r.getDrawable(inactiveResId);
			}
		}
        else {
			active = r.getDrawable(activeResId);
           	inactive =r.getDrawable(inactiveResId);
        }
	 	mActiveMarker.setImageDrawable(active);
       	 mInactiveMarker.setImageDrawable(inactive);
		 // Hosin_messi modify end
		}
		else{
        mActiveMarker.setImageDrawable(r.getDrawable(activeResId));
        mInactiveMarker.setImageDrawable(r.getDrawable(inactiveResId));
		}
    }

    void activate(boolean immediate) {
        if (immediate) {
            mActiveMarker.animate().cancel();
            mActiveMarker.setAlpha(1f);
            mActiveMarker.setScaleX(1f);
            mActiveMarker.setScaleY(1f);
            mInactiveMarker.animate().cancel();
            mInactiveMarker.setAlpha(0f);
        } else {
            mActiveMarker.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(MARKER_FADE_DURATION).start();
            mInactiveMarker.animate()
                    .alpha(0f)
                    .setDuration(MARKER_FADE_DURATION).start();
        }
        mIsActive = true;
    }

    void inactivate(boolean immediate) {
        if (immediate) {
            mInactiveMarker.animate().cancel();
            mInactiveMarker.setAlpha(1f);
            mActiveMarker.animate().cancel();
            mActiveMarker.setAlpha(0f);
            mActiveMarker.setScaleX(0.5f);
            mActiveMarker.setScaleY(0.5f);
        } else {
            mInactiveMarker.animate().alpha(1f)
                    .setDuration(MARKER_FADE_DURATION).start();
            mActiveMarker.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(MARKER_FADE_DURATION).start();
        }
        mIsActive = false;
    }

    boolean isActive() {
        return mIsActive;
    }
}
