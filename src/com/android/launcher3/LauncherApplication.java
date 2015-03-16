/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Application;

import com.mediatek.launcher3.ext.LauncherLog;
import com.mediatek.common.featureoption.FeatureOption;

public class LauncherApplication extends Application {
    private static final String TAG = "LauncherApplication";
	//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
	public static final String NAME_KEY = "theme_key";
	public static final String BITMAP_KEY = "bitmap_key";
	public static final String SHARED_PREFRENCE = "com.android.mlauncher.prefs";
	public static boolean  themeChanged = false;//judge wheather themeswitch or not
	// add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
    /// M: flag for starting Launcher from application
    private boolean mTotallyStart = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "LauncherApplication onCreate");
        }

        LauncherAppState.setApplicationContext(this);
        LauncherAppState.getInstance();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        LauncherAppState.getInstance().onTerminate();
    }

    /// M: LauncherApplication start flag @{
    public void setTotalStartFlag() {
        mTotallyStart = true;
    }

    public void resetTotalStartFlag() {
        mTotallyStart = false;
    }

    public boolean isTotalStart() {
        return mTotallyStart;
    }
    /// M: }@
}