/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.launcher3.ext.LauncherLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import android.util.Log;
import android.graphics.BitmapFactory;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import com.mediatek.common.featureoption.FeatureOption;
//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {
    @SuppressWarnings("unused")
    private static final String TAG = "Launcher.IconCache";
    private SharedPreferences mSharedPrefs;//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    private static class CacheEntry {
        public Bitmap icon;
        public String title;
    }

    private final Bitmap mDefaultIcon;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final HashMap<ComponentName, CacheEntry> mCache =
            new HashMap<ComponentName, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private int mIconDpi;

    public IconCache(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        mContext = context;
        mSharedPrefs = context.getSharedPreferences(
				LauncherApplication.SHARED_PREFRENCE, Context.MODE_PRIVATE);//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
        mPackageManager = context.getPackageManager();
        mIconDpi = activityManager.getLauncherLargeIconDensity();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "IconCache, mIconDpi = " + mIconDpi);
        }

        // need to set mIconDpi before getting default icon
        mDefaultIcon = makeDefaultIcon();
    }

    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                android.R.mipmap.sym_def_app_icon);
    }

    public Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }
	// add 20140902 for HOSIN_CUST_LAUNCHER_MESSI
    private String convertToIconResName(String input) {
		return input != null && !input.equals("") ? input.replace('.', '_')
				.toLowerCase() : input;
	}
    public Drawable getFullResIcon(String packageName, int iconId) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ResolveInfo info) {
        return getFullResIcon(info.activityInfo);
    }

    public Drawable getFullResIcon(ActivityInfo info) {
		// add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
        Resources resources = null;
		String iconpath = null;
		String drawableName;
		String[] array;
		List<String> list;
		String themeKeyname = mSharedPrefs.getString(LauncherApplication.NAME_KEY, "default");
		InputStream is = null ;
			// add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
        try {
		if(FeatureOption.HOSIN_CUST_LAUNCHER_MESSI){
			// Hosin_messi add  start
			drawableName = convertToIconResName(info.name) + ".png";
			Log.i(TAG,drawableName);
				array = mContext.getAssets().list(mContext.getString(R.string.theme)+"/" + themeKeyname+"/"
					+mContext.getString(R.string.icon));
				list = Arrays.asList(array);
				drawableName = convertToIconResName(info.name) + ".png";
				if(list.contains(drawableName)){
					iconpath = mContext.getString(R.string.theme)+"/" + themeKeyname +"/"
					+mContext.getString(R.string.icon)+ "/"
							+ drawableName;
					is = mContext.getAssets().open(iconpath);
						return new FastBitmapDrawable(
								BitmapFactory.decodeStream(is ));
				}
					// Hosin_messi add  end
			}
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        
		//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
        }catch(IOException  e){
        	e.printStackTrace();
		// add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end 
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    private Bitmap makeDefaultIcon() {
        Drawable d = getFullResDefaultActivityIcon();
        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1),
                Math.max(d.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        c.setBitmap(null);
        return b;
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public void remove(ComponentName componentName) {
        synchronized (mCache) {
            mCache.remove(componentName);
        }
    }

    /**
     * Empty out the cache.
     */
    public void flush() {
        synchronized (mCache) {
            mCache.clear();

            /// M: Add for smart book feature. Need to update mIconDpi when plug in/out smart book.
            if (FeatureOption.MTK_SMARTBOOK_SUPPORT) {
                ActivityManager activityManager =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                mIconDpi = activityManager.getLauncherLargeIconDensity();
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "flush, mIconDpi = " + mIconDpi);
                }
            }
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Flush icon cache here.");
        }
    }

    /**
     * Empty out the cache that aren't of the correct grid size
     */
    public void flushInvalidIcons(DeviceProfile grid) {
        synchronized (mCache) {
            Iterator<Entry<ComponentName, CacheEntry>> it = mCache.entrySet().iterator();
            while (it.hasNext()) {
                final CacheEntry e = it.next().getValue();
                if (e.icon.getWidth() != grid.iconSizePx || e.icon.getHeight() != grid.iconSizePx) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Fill in "application" with the icon and label for "info."
     */
    public void getTitleAndIcon(AppInfo application, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
            CacheEntry entry = cacheLocked(application.componentName, info, labelCache);

            application.title = entry.title;
            application.iconBitmap = entry.icon;
        }
    }

    public Bitmap getIcon(Intent intent) {
        synchronized (mCache) {
            final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
            ComponentName component = intent.getComponent();

            if (resolveInfo == null || component == null) {
                return mDefaultIcon;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, null);
            return entry.icon;
        }
    }

    public Bitmap getIcon(ComponentName component, ResolveInfo resolveInfo,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
            if (resolveInfo == null || component == null) {
                return null;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, labelCache);
            return entry.icon;
        }
    }

    public boolean isDefaultIcon(Bitmap icon) {
        return mDefaultIcon == icon;
    }

    private CacheEntry cacheLocked(ComponentName componentName, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
        if (LauncherLog.DEBUG_LAYOUT) {
            LauncherLog.d(TAG, "cacheLocked: componentName = " + componentName
                    + ", info = " + info + ", HashMap<Object, CharSequence>:size = "
                    +  ((labelCache == null) ? "null" : labelCache.size()));
        }

        CacheEntry entry = mCache.get(componentName);
        if (entry == null) {
            entry = new CacheEntry();

            mCache.put(componentName, entry);

            ComponentName key = LauncherModel.getComponentNameFromResolveInfo(info);
            if (labelCache != null && labelCache.containsKey(key)) {
                entry.title = labelCache.get(key).toString();
                if (LauncherLog.DEBUG_LOADERS) {
                    LauncherLog.d(TAG, "CacheLocked get title from cache: title = " + entry.title);
                }
            } else {
                entry.title = info.loadLabel(mPackageManager).toString();
                if (LauncherLog.DEBUG_LOADERS) {
                    LauncherLog.d(TAG, "CacheLocked get title from pms: title = " + entry.title);
                }
                if (labelCache != null) {
                    labelCache.put(key, entry.title);
                }
            }
            if (entry.title == null) {
                entry.title = info.activityInfo.name;
                if (LauncherLog.DEBUG_LOADERS) {
                    LauncherLog.d(TAG, "CacheLocked get title from activity information: entry.title = " + entry.title);
                }
            }

            entry.icon = Utilities.createIconBitmap(
                    getFullResIcon(info), mContext);
        }
        return entry;
    }

    public HashMap<ComponentName,Bitmap> getAllIcons() {
        synchronized (mCache) {
            HashMap<ComponentName,Bitmap> set = new HashMap<ComponentName,Bitmap>();
            for (ComponentName cn : mCache.keySet()) {
                final CacheEntry e = mCache.get(cn);
                set.put(cn, e.icon);
            }
            return set;
        }
    }
}
