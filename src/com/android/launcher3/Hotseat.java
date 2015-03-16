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

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.mediatek.launcher3.ext.LauncherLog;
//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
import android.graphics.BitmapFactory;
import android.view.HapticFeedbackConstants;
import java.io.IOException;
import com.mediatek.common.featureoption.FeatureOption;

//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
public class Hotseat extends FrameLayout {
    private static final String TAG = "Hotseat";

    private CellLayout mContent;

    private Launcher mLauncher;

    private int mAllAppsButtonRank;

    private boolean mTransposeLayoutWithOrientation;
    private boolean mIsLandscape;
	//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI start
    private String themeKeyname;
    private Drawable normalD = null;
    private Drawable selectedD = null;
	//add 20140902 for HOSIN_CUST_LAUNCHER_MESSI end
    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources r = context.getResources();
        mTransposeLayoutWithOrientation = 
                r.getBoolean(R.bool.hotseat_transpose_layout_with_orientation);
        mIsLandscape = context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
		if(FeatureOption.HOSIN_CUST_LAUNCHER_MESSI){
		themeKeyname = context.getSharedPreferences(
				LauncherApplication.SHARED_PREFRENCE, Context.MODE_PRIVATE).getString(
				LauncherApplication.NAME_KEY, "default");	//Hosin_messi add
		}
    }

    public void setup(Launcher launcher) {
        mLauncher = launcher;
        setOnKeyListener(new HotseatIconKeyEventListener());
    }

    CellLayout getLayout() {
        return mContent;
    }

    /**
     * Registers the specified listener on the cell layout of the hotseat.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mContent.setOnLongClickListener(l);
    }
  
    private boolean hasVerticalHotseat() {
        return (mIsLandscape && mTransposeLayoutWithOrientation);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return hasVerticalHotseat() ? (mContent.getCountY() - y - 1) : x;
    }
    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return hasVerticalHotseat() ? 0 : rank;
    }
    int getCellYFromOrder(int rank) {
        return hasVerticalHotseat() ? (mContent.getCountY() - (rank + 1)) : 0;
    }
    public boolean isAllAppsButtonRank(int rank) {
        if (AppsCustomizePagedView.DISABLE_ALL_APPS) {
            return false;
        } else {
            return rank == mAllAppsButtonRank;
        }
    }

    /** This returns the coordinates of an app in a given cell, relative to the DragLayer */
    Rect getCellCoordinates(int cellX, int cellY) {
        Rect coords = new Rect();
        mContent.cellToRect(cellX, cellY, 1, 1, coords);
        int[] hotseatInParent = new int[2];
        Utilities.getDescendantCoordRelativeToParent(this, mLauncher.getDragLayer(),
                hotseatInParent, false);
        coords.offset(hotseatInParent[0], hotseatInParent[1]);

        // Center the icon
        int cWidth = mContent.getShortcutsAndWidgets().getCellContentWidth();
        int cHeight = mContent.getShortcutsAndWidgets().getCellContentHeight();
        int cellPaddingX = (int) Math.max(0, ((coords.width() - cWidth) / 2f));
        int cellPaddingY = (int) Math.max(0, ((coords.height() - cHeight) / 2f));
        coords.offset(cellPaddingX, cellPaddingY);

        return coords;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mAllAppsButtonRank = grid.hotseatAllAppsRank;
        mContent = (CellLayout) findViewById(R.id.layout);
        if (grid.isLandscape && !grid.isLargeTablet()) {
            mContent.setGridSize(1, (int) grid.numHotseatIcons);
        } else {
            mContent.setGridSize((int) grid.numHotseatIcons, 1);
        }
        mContent.setIsHotseat(true);

        resetLayout();
    }

    void resetLayout() {
        mContent.removeAllViewsInLayout();

        if (!AppsCustomizePagedView.DISABLE_ALL_APPS) {
            // Add the Apps button
            Context context = getContext();

            LayoutInflater inflater = LayoutInflater.from(context);
            TextView allAppsButton = (TextView)
                    inflater.inflate(R.layout.all_apps_button, mContent, false);
			Drawable d = null;
		if(FeatureOption.HOSIN_CUST_LAUNCHER_MESSI){
			//Hosin_messi modify start 
			
			if (!themeKeyname.equals("default")) {
			
				try { 
					d = new FastBitmapDrawable(BitmapFactory.decodeStream(mContext.getAssets().open(mContext.getString(R.string.theme)+"/"+themeKeyname
					+"/"+mContext.getString(R.string.icon)+"/all_apps_btn.png")));
					String normal = mContext.getString(R.string.theme)+"/" + themeKeyname +"/"
						+mContext.getString(R.string.icon)+ "/"+"all_apps_btn.png";
					String selected = mContext.getString(R.string.theme)+"/" + themeKeyname +"/"
						+mContext.getString(R.string.icon)+ "/"+"all_apps_btn_selected.png";
					normalD = new FastBitmapDrawable(BitmapFactory.decodeStream(mContext.getAssets().open(normal)));
					selectedD = new FastBitmapDrawable(BitmapFactory.decodeStream(mContext.getAssets().open(selected)));
				} catch (IOException e) {
					e.printStackTrace();
				} 
				if(null == d){
					d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
				}
			}
			else{
	          	 	d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
			}
			allAppsButton.setOnTouchListener( new View.OnTouchListener() {
	              		  @Override
	                public boolean onTouch(View v, MotionEvent event) {
	   		
				if(event.getAction() == MotionEvent.ACTION_DOWN){
					if( selectedD!=null){
						Utilities.resizeIconDrawable(selectedD);
						((TextView)v).setCompoundDrawables(null, selectedD, null, null);
					}
				}
				else if(event.getAction() == MotionEvent.ACTION_UP){
					if( normalD!=null){
						Utilities.resizeIconDrawable(normalD);
						((TextView)v).setCompoundDrawables(null, normalD, null, null);
					}
			   	}
			if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
	                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
	                    }
			return false;
	              		  	}
		   	
	            });
			//Hosin_messi modify end 
		}
		else{
           		  d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
		}
            Utilities.resizeIconDrawable(d);
            allAppsButton.setCompoundDrawables(null, d, null, null);

            allAppsButton.setContentDescription(context.getString(R.string.all_apps_button_label));
            if (mLauncher != null && !FeatureOption.HOSIN_CUST_LAUNCHER_MESSI) {
                allAppsButton.setOnTouchListener(mLauncher.getHapticFeedbackTouchListener());
            }
            allAppsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    Trace.traceBegin(Trace.TRACE_TAG_INPUT, "onClick");
                    if (LauncherLog.DEBUG) {
                        LauncherLog.d(TAG, "Click on all apps view on hotseat: mLauncher = " + mLauncher);
                    }
                    if (mLauncher != null) {
                        mLauncher.onClickAllAppsButton(v);
                    }
                    Trace.traceEnd(Trace.TRACE_TAG_INPUT);
                }
            });

            // Note: We do this to ensure that the hotseat is always laid out in the orientation of
            // the hotseat in order regardless of which orientation they were added
            int x = getCellXFromOrder(mAllAppsButtonRank);
            int y = getCellYFromOrder(mAllAppsButtonRank);
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(x,y,1,1);
            lp.canReorder = false;
            mContent.addViewToCellLayout(allAppsButton, -1, 0, lp, true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state.
        if (mLauncher.getWorkspace().isSmall()) {
            return true;
        }
        return false;
    }

    void addAllAppsFolder(IconCache iconCache,
            ArrayList<AppInfo> allApps, ArrayList<ComponentName> onWorkspace,
            Launcher launcher, Workspace workspace) {
        if (AppsCustomizePagedView.DISABLE_ALL_APPS) {
            FolderInfo fi = new FolderInfo();

            fi.cellX = getCellXFromOrder(mAllAppsButtonRank);
            fi.cellY = getCellYFromOrder(mAllAppsButtonRank);
            fi.spanX = 1;
            fi.spanY = 1;
            fi.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
            fi.screenId = mAllAppsButtonRank;
            fi.itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
            fi.title = "More Apps";
            LauncherModel.addItemToDatabase(launcher, fi, fi.container, fi.screenId, fi.cellX,
                    fi.cellY, false);
            FolderIcon folder = FolderIcon.fromXml(R.layout.folder_icon, launcher,
                    getLayout(), fi, iconCache);
            workspace.addInScreen(folder, fi.container, fi.screenId, fi.cellX, fi.cellY,
                    fi.spanX, fi.spanY);

            for (AppInfo info: allApps) {
                ComponentName cn = info.intent.getComponent();
                if (!onWorkspace.contains(cn)) {
                    Log.d(TAG, "Adding to 'more apps': " + info.intent);
                    ShortcutInfo si = info.makeShortcut();
                    fi.add(si);
                }
            }
        }
    }

    void addAppsToAllAppsFolder(ArrayList<AppInfo> apps) {
        if (AppsCustomizePagedView.DISABLE_ALL_APPS) {
            View v = mContent.getChildAt(getCellXFromOrder(mAllAppsButtonRank), getCellYFromOrder(mAllAppsButtonRank));
            FolderIcon fi = null;

            if (v instanceof FolderIcon) {
                fi = (FolderIcon) v;
            } else {
                return;
            }

            FolderInfo info = fi.getFolderInfo();
            for (AppInfo a: apps) {
                ShortcutInfo si = a.makeShortcut();
                info.add(si);
            }
        }
    }
}
