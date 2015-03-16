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

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.launcher3.DropTarget.DragObject;

import com.mediatek.launcher3.ext.AllApps;
import com.mediatek.launcher3.ext.LauncherLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import com.mediatek.common.featureoption.FeatureOption;//by qinjian add 20140730
import android.media.AudioManager;//by qinjian add 20140730 begin

/**
 * A simple callback interface which also provides the results of the task.
 */
interface AsyncTaskCallback {
    void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data);
}

/**
 * The data needed to perform either of the custom AsyncTasks.
 */
class AsyncTaskPageData {
    enum Type {
        LoadWidgetPreviewData
    }

    AsyncTaskPageData(int p, ArrayList<Object> l, int cw, int ch, AsyncTaskCallback bgR,
            AsyncTaskCallback postR, WidgetPreviewLoader w) {
        page = p;
        items = l;
        generatedImages = new ArrayList<Bitmap>();
        maxImageWidth = cw;
        maxImageHeight = ch;
        doInBackgroundCallback = bgR;
        postExecuteCallback = postR;
        widgetPreviewLoader = w;
    }
    void cleanup(boolean cancelled) {
        // Clean up any references to source/generated bitmaps
        if (generatedImages != null) {
            if (cancelled) {
                for (int i = 0; i < generatedImages.size(); i++) {
                    widgetPreviewLoader.recycleBitmap(items.get(i), generatedImages.get(i));
                }
            }
            generatedImages.clear();
        }
    }
    int page;
    ArrayList<Object> items;
    ArrayList<Bitmap> sourceImages;
    ArrayList<Bitmap> generatedImages;
    int maxImageWidth;
    int maxImageHeight;
    AsyncTaskCallback doInBackgroundCallback;
    AsyncTaskCallback postExecuteCallback;
    WidgetPreviewLoader widgetPreviewLoader;
}

/**
 * A generic template for an async task used in AppsCustomize.
 */
class AppsCustomizeAsyncTask extends AsyncTask<AsyncTaskPageData, Void, AsyncTaskPageData> {
    AppsCustomizeAsyncTask(int p, AsyncTaskPageData.Type ty) {
        page = p;
        threadPriority = Process.THREAD_PRIORITY_DEFAULT;
        dataType = ty;
    }
    @Override
    protected AsyncTaskPageData doInBackground(AsyncTaskPageData... params) {
        if (params.length != 1) return null;
        // Load each of the widget previews in the background
        params[0].doInBackgroundCallback.run(this, params[0]);
        return params[0];
    }
    @Override
    protected void onPostExecute(AsyncTaskPageData result) {
        // All the widget previews are loaded, so we can just callback to inflate the page
        result.postExecuteCallback.run(this, result);
    }

    void setThreadPriority(int p) {
        threadPriority = p;
    }
    void syncThreadPriority() {
        Process.setThreadPriority(threadPriority);
    }

    // The page that this async task is associated with
    AsyncTaskPageData.Type dataType;
    int page;
    int threadPriority;
}

/**
 * The Apps/Customize page that displays all the applications, widgets, and shortcuts.
 */
public class AppsCustomizePagedView extends PagedViewWithDraggableItems implements
        View.OnClickListener, View.OnKeyListener, DropTarget, DragSource, DragScroller,
        PagedViewIcon.PressedCallback, PagedViewWidget.ShortPressListener, DragController.DragListener,
        LauncherTransitionable {
    static final String TAG = "AppsCustomizePagedView";

    /**
     * The different content types that this paged view can show.
     */
    public enum ContentType {
        Applications,
        Widgets
    }
    private ContentType mContentType = ContentType.Applications;

    // Refs
    private Launcher mLauncher;
    private DragController mDragController;
    private final LayoutInflater mLayoutInflater;
    private final PackageManager mPackageManager;

    // Save and Restore
    private int mSaveInstanceStateItemIndex = -1;
    private PagedViewIcon mPressedIcon;

    // Content
    /// M: Modify for CT launcher.
    public static ArrayList<AppInfo> mApps;
    private ArrayList<Object> mWidgets;

    /// M: Flag to record whether the app list data has been set to AppsCustomizePagedView.
    private boolean mAppsHasSet = false;

    // Cling
    private boolean mHasShownAllAppsCling;
    private int mClingFocusedX;
    private int mClingFocusedY;

    // Caching
    private Canvas mCanvas;
    private IconCache mIconCache;

    // Dimens
    private int mContentWidth, mContentHeight;
    private int mWidgetCountX, mWidgetCountY;
    private int mWidgetWidthGap, mWidgetHeightGap;
    private PagedViewCellLayout mWidgetSpacingLayout;
    private int mNumAppsPages;
    private int mNumWidgetPages;

    // Relating to the scroll and overscroll effects
    Workspace.ZInterpolator mZInterpolator = new Workspace.ZInterpolator(0.5f);
    private static float CAMERA_DISTANCE = 6500;
    private static float TRANSITION_SCALE_FACTOR = 0.74f;
    private static float TRANSITION_PIVOT = 0.65f;
    private static float TRANSITION_MAX_ROTATION = 22;
    private static boolean PERFORM_OVERSCROLL_ROTATION = true;
    private AccelerateInterpolator mAlphaInterpolator = new AccelerateInterpolator(0.9f);
    private DecelerateInterpolator mLeftScreenAlphaInterpolator = new DecelerateInterpolator(4);

    public static boolean DISABLE_ALL_APPS = false;

    // Previews & outlines
    ArrayList<AppsCustomizeAsyncTask> mRunningTasks;
    private static final int sPageSleepDelay = 200;

    private Runnable mInflateWidgetRunnable = null;
    private Runnable mBindWidgetRunnable = null;
    static final int WIDGET_NO_CLEANUP_REQUIRED = -1;
    static final int WIDGET_PRELOAD_PENDING = 0;
    static final int WIDGET_BOUND = 1;
    static final int WIDGET_INFLATED = 2;
    int mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
    int mWidgetLoadingId = -1;
    PendingAddWidgetInfo mCreateWidgetInfo = null;
    private boolean mDraggingWidget = false;

    private Toast mWidgetInstructionToast;

    // Deferral of loading widget previews during launcher transitions
    private boolean mInTransition;
    private ArrayList<AsyncTaskPageData> mDeferredSyncWidgetPageItems =
        new ArrayList<AsyncTaskPageData>();
    private ArrayList<Runnable> mDeferredPrepareLoadWidgetPreviewsTasks =
        new ArrayList<Runnable>();

    private Rect mTmpRect = new Rect();

    // Used for drawing shortcut previews
    BitmapCache mCachedShortcutPreviewBitmap = new BitmapCache();
    PaintCache mCachedShortcutPreviewPaint = new PaintCache();
    CanvasCache mCachedShortcutPreviewCanvas = new CanvasCache();

    // Used for drawing widget previews
    CanvasCache mCachedAppWidgetPreviewCanvas = new CanvasCache();
    RectCache mCachedAppWidgetPreviewSrcRect = new RectCache();
    RectCache mCachedAppWidgetPreviewDestRect = new RectCache();
    PaintCache mCachedAppWidgetPreviewPaint = new PaintCache();

    WidgetPreviewLoader mWidgetPreviewLoader;

    private boolean mInBulkBind;
    private boolean mNeedToUpdatePageCountsAndInvalidateData;

    /// M: For OP09 Start. @{
    /**
     * M: Is the user is dragging an item near the edge of a page?
     */
    private boolean mInScrollArea = false;
    private final int mTouchDelta = 8;

    /**
     * The CellLayout that is currently being dragged over
     */
    private CellLayout mDragTargetLayout = null;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    private float[] mDragViewVisualCenter = new float[2];

    private DropTarget.DragEnforcer mDragEnforcer;

    private boolean mAnimatingViewIntoPlace = false;

    private Matrix mTempInverseMatrix = new Matrix();

    private Alarm mReorderAlarm = new Alarm();

    private static final int REORDER_ANIMATION_DURATION = 230;

    private CellLayout mCurrentDropTarget = null;

    private CellLayout mPrevDropTarget = null;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    private int[] mTargetCell = new int[2];
    private int[] mPreviousTargetCell = new int[2];
    private int[] mEmptyCell = new int[2];
    private int[] mPrevTargetCell = new int[2];
    private int[] mPrevEmptyCell = new int[2];
    private Point mDisplaySize = new Point();
    private boolean mIsDragOccuring = false;

    /**
     * M: Record the last cell info in the full page.
     */
    private PagedViewIcon mPrevLastCell = null;
    private CellLayout.CellInfo mPrevLastCellInfo = new CellLayout.CellInfo();
    private PagedViewIcon mLastCell = null;
    private CellLayout.CellInfo mLastCellInfo = new CellLayout.CellInfo();

    /**
     * M: Recorder all apps info for each page.
     */
    public static ArrayList<PageInfo> sAllAppsPage = new ArrayList<PageInfo>();
    /**
     * M: Recorder what apps will be hidden, what apps will be shown.
     */
    static ArrayList<AppInfo> sShowAndHideApps = new ArrayList<AppInfo>();

    private static ArrayList<AppInfo> sRemovedApps = new ArrayList<AppInfo>();

    /**
     * M: Real time reorder do or not.
     */
    private boolean mOccuredReorder = false;

    /**
     * M: whether to support edit and hide apps, only support for OP09 projects.
     */
    private boolean mSupportEditAndHideApps = false;
    private boolean mSupportCycleSliding = false;

    private Drawable mDeleteButtonDrawable = null;
    private int mDeleteMarginleft;

    /// M: STK package name and class name.
    private static final String STK_PACKAGE_NAME = "com.android.stk";
    private static final String STK_CLASS_NAME = "com.android.stk.StkLauncherActivity";
    private static final String STK2_CLASS_NAME = "com.android.stk.StkLauncherActivityII";

    /// M: For OP09 END.}@

    public AppsCustomizePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
        mPackageManager = context.getPackageManager();
        mApps = new ArrayList<AppInfo>();
        mWidgets = new ArrayList<Object>();
        mIconCache = (LauncherAppState.getInstance()).getIconCache();
        mCanvas = new Canvas();
        mRunningTasks = new ArrayList<AppsCustomizeAsyncTask>();

        // Save the default widget preview background
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppsCustomizePagedView, 0, 0);
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        mWidgetWidthGap = mWidgetHeightGap = grid.edgeMarginPx;
        mWidgetCountX = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountX, 2);
        mWidgetCountY = a.getInt(R.styleable.AppsCustomizePagedView_widgetCountY, 2);
        mClingFocusedX = a.getInt(R.styleable.AppsCustomizePagedView_clingFocusedX, 0);
        mClingFocusedY = a.getInt(R.styleable.AppsCustomizePagedView_clingFocusedY, 0);
        a.recycle();
        mWidgetSpacingLayout = new PagedViewCellLayout(getContext());

        // The padding on the non-matched dimension for the default widget preview icons
        // (top + bottom)
        mFadeInAdjacentScreens = false;

        // Unless otherwise specified this view is important for accessibility.
        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        /// M: whether to support eidt and add apps, add for OP09.
        mSupportEditAndHideApps = LauncherExtPlugin.getInstance().getOperatorCheckerExt(context).supportEditAndHideApps();
        mDragEnforcer = new DropTarget.DragEnforcer(context);

        if (mSupportEditAndHideApps) {
            mCellCountX = AllApps.sAppsCellCountX;
            mCellCountY = AllApps.sAppsCellCountY;
            mDeleteButtonDrawable = context.getResources().getDrawable(R.drawable.ic_launcher_delete_holo);
            mDeleteMarginleft = (int) context.getResources().getDimension(R.dimen.apps_customize_delete_margin_left);
        }
        mSupportCycleSliding = LauncherExtPlugin.getInstance().getOperatorCheckerExt(context).supportAppListCycleSliding();
        if (isSupportCycleSlidingScreen()) {
            PERFORM_OVERSCROLL_ROTATION = false;
        }
    }

    @Override
    protected void init() {
        super.init();
        mCenterPagesVertically = false;

        Context context = getContext();
        Resources r = context.getResources();
        setDragSlopeThreshold(r.getInteger(R.integer.config_appsCustomizeDragSlopeThreshold)/100f);
    }

    public void onFinishInflate() {
        super.onFinishInflate();

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        setPadding(grid.edgeMarginPx, 2 * grid.edgeMarginPx,
                grid.edgeMarginPx, 2 * grid.edgeMarginPx);
    }

    /** Returns the item index of the center item on this page so that we can restore to this
     *  item index when we rotate. */
    private int getMiddleComponentIndexOnCurrentPage() {
        int i = -1;
        if (getPageCount() > 0) {
            int currentPage = getCurrentPage();
            if (mContentType == ContentType.Applications) {
                AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(currentPage);
                ShortcutAndWidgetContainer childrenLayout = layout.getShortcutsAndWidgets();
                int numItemsPerPage = mCellCountX * mCellCountY;
                int childCount = childrenLayout.getChildCount();
                if (childCount > 0) {
                    i = (currentPage * numItemsPerPage) + (childCount / 2);
                }
            } else if (mContentType == ContentType.Widgets) {
                int numApps = mApps.size();
                PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(currentPage);
                int numItemsPerPage = mWidgetCountX * mWidgetCountY;
                int childCount = layout.getChildCount();
                if (childCount > 0) {
                    i = numApps +
                        (currentPage * numItemsPerPage) + (childCount / 2);
                }
            } else {
                throw new RuntimeException("Invalid ContentType");
            }
        }
        return i;
    }

    /** Get the index of the item to restore to if we need to restore the current page. */
    int getSaveInstanceStateIndex() {
        if (mSaveInstanceStateItemIndex == -1) {
            mSaveInstanceStateItemIndex = getMiddleComponentIndexOnCurrentPage();
        }
        return mSaveInstanceStateItemIndex;
    }

    /** Returns the page in the current orientation which is expected to contain the specified
     *  item index. */
    int getPageForComponent(int index) {
        if (index < 0) return 0;

        if (index < mApps.size()) {
            int numItemsPerPage = mCellCountX * mCellCountY;
            return (index / numItemsPerPage);
        } else {
            int numItemsPerPage = mWidgetCountX * mWidgetCountY;
            return (index - mApps.size()) / numItemsPerPage;
        }
    }

    /** Restores the page for an item at the specified index */
    void restorePageForIndex(int index) {
        if (index < 0) return;
        mSaveInstanceStateItemIndex = index;
    }

    private void updatePageCounts() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePageCounts: mSupportCycleSliding = " + mSupportCycleSliding + ", mNumAppsPages = "
                    + mNumAppsPages + ", mNumWidgetPages = " + mNumWidgetPages);
        }

        if (mSupportCycleSliding) {
            /// M: only update related tab when cycle sliding is supported.
            AppsCustomizeTabHost tabHost = getTabHost();
            if (tabHost != null) {
                String tag = tabHost.getCurrentTabTag();
                if (tag != null) {
                    if (tag.equals(tabHost.getTabTagForContentType(ContentType.Widgets))) {
                        mNumAppsPages = 0;
                        updateWidgetsPageCounts(true);
                    } else {
                        mNumWidgetPages = 0;
                        updateAppsPageCounts();
                    }
                    return;
                }
            }
        }

        updateWidgetsPageCounts(false);
        updateAppsPageCounts();
    }

    void updateAppsPageCounts() {
        /// M: use apps pages size as number of apps page if edit mode is supported.
        if (!mSupportEditAndHideApps) {
            mNumAppsPages = (int) Math.ceil((float) mApps.size() / (mCellCountX * mCellCountY));
        } else {
            mNumAppsPages = sAllAppsPage.size();
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateAppsPageCounts end: mNumWidgetPages = " + mNumWidgetPages + ", mNumAppsPages = "
                    + mNumAppsPages + ", mCellCountX = " + mCellCountX + ", mCellCountY = " + mCellCountY
                    + ", mApps.size() = " + mApps.size());
        }
    }

    void updateWidgetsPageCounts(boolean isWidgetTab) {
        /// M: hide all widget pages when cycle sliding is supported.
        if (mSupportCycleSliding && !isWidgetTab) {
            mNumWidgetPages = 0;
        } else {
            mNumWidgetPages = (int) Math.ceil(mWidgets.size() / (float) (mWidgetCountX * mWidgetCountY));
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateWidgetsPageCounts end: mNumWidgetPages = " + mNumWidgetPages + ", mWidgets.size() = "
                    + mWidgets.size());
        }
    }

    protected void onDataReady(int width, int height) {
        if (mWidgetPreviewLoader == null) {
            mWidgetPreviewLoader = new WidgetPreviewLoader(mLauncher);
        }

        // Now that the data is ready, we can calculate the content width, the number of cells to
        // use for each page
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        mWidgetSpacingLayout.setPadding(mPageLayoutPaddingLeft, mPageLayoutPaddingTop,
                mPageLayoutPaddingRight, mPageLayoutPaddingBottom);

        /// M: Add for op09 Edit and Hide app icons.
        if (!mSupportEditAndHideApps) {
            mCellCountX = (int) grid.allAppsNumCols;
            mCellCountY = (int) grid.allAppsNumRows;
        }
        updatePageCounts();

        // Force a measure to update recalculate the gaps
        mContentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        mContentHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        mWidgetSpacingLayout.measure(widthSpec, heightSpec);

        AppsCustomizeTabHost host = (AppsCustomizeTabHost) getTabHost();
        final boolean hostIsTransitioning = host.isTransitioning();

        // Restore the page
        int page = getPageForComponent(mSaveInstanceStateItemIndex);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDataReady: height = " + height + ", width = " + width + ", page = " + page
                    + ", hostIsTransitioning = " + hostIsTransitioning + ", mContentWidth = "
                    + mContentWidth + ", mNumAppsPages = " + mNumAppsPages + ", mNumWidgetPages = "
                    + mNumWidgetPages + ", this = " + this);
        }
        invalidatePageData(Math.max(0, page), hostIsTransitioning);

        // Show All Apps cling if we are finished transitioning, otherwise, we will try again when
        // the transition completes in AppsCustomizeTabHost (otherwise the wrong offsets will be
        // returned while animating)
        if (!hostIsTransitioning) {
            post(new Runnable() {
                @Override
                public void run() {
                    showAllAppsCling();
                }
            });
        }

        /// M: flush the pending apps queue which are pended during loading data
        if (mSupportEditAndHideApps) {
            Launcher.flushPendingAppsQueue(this);
        }
    }

    void showAllAppsCling() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "showAllAppsCling: mHasShownAllAppsCling = " + mHasShownAllAppsCling);
        }

        if (!mHasShownAllAppsCling && isDataReady()) {
            mHasShownAllAppsCling = true;
            // Calculate the position for the cling punch through
            int[] offset = new int[2];
            int[] pos = mWidgetSpacingLayout.estimateCellPosition(mClingFocusedX, mClingFocusedY);
            mLauncher.getDragLayer().getLocationInDragLayer(this, offset);
            // PagedViews are centered horizontally but top aligned
            // Note we have to shift the items up now that Launcher sits under the status bar
            pos[0] += (getMeasuredWidth() - mWidgetSpacingLayout.getMeasuredWidth()) / 2 +
                    offset[0];
            pos[1] += offset[1] - mLauncher.getDragLayer().getPaddingTop();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        LauncherLog.d(TAG, "onMeasure mIsInEditMode = " + mIsInEditMode + widthMeasureSpec + "  " + heightMeasureSpec
                + "  " + width + "  " + height);
        if (!isDataReady()) {
            LauncherLog.d(TAG, "onMeasure DISABLE_ALL_APPS is " + DISABLE_ALL_APPS
                    + " !mApps.isEmpty() is " + !mApps.isEmpty()
                    + " !mWidgets.isEmpty()" + !mWidgets.isEmpty());
            if ((DISABLE_ALL_APPS || !mApps.isEmpty()) && !mWidgets.isEmpty() && mAppsHasSet) {
                setDataIsReady();
                setMeasuredDimension(width, height);
                onDataReady(width, height);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void onPackagesUpdated(ArrayList<Object> widgetsAndShortcuts) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        // Get the list of widgets and shortcuts
        mWidgets.clear();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updatePackages: widgetsAndShortcuts size = " + widgetsAndShortcuts.size());
        }
        for (Object o : widgetsAndShortcuts) {
            if (o instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo widget = (AppWidgetProviderInfo) o;
                if (!app.shouldShowAppOrWidgetProvider(widget.provider)) {
                    continue;
                }
                widget.label = widget.label.trim();
                if (widget.minWidth > 0 && widget.minHeight > 0) {
                    // Ensure that all widgets we show can be added on a workspace of this size
                    int[] spanXY = Launcher.getSpanForWidget(mLauncher, widget);
                    int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, widget);
                    int minSpanX = Math.min(spanXY[0], minSpanXY[0]);
                    int minSpanY = Math.min(spanXY[1], minSpanXY[1]);
                    if (minSpanX <= (int) grid.numColumns &&
                        minSpanY <= (int) grid.numRows) {
                        mWidgets.add(widget);
                    } else {
                        Log.e(TAG, "Widget " + widget.provider + " can not fit on this device (" +
                              widget.minWidth + ", " + widget.minHeight + "), min span is (" + minSpanX + ", " + minSpanY + ")"
                              + "), span is (" + spanXY[0] + ", " + spanXY[1] + ")");
                    }
                } else {
                    LauncherLog.e(TAG, "Widget " + widget.provider + " has invalid dimensions (" +
                                  widget.minWidth + ", " + widget.minHeight);
                }
            } else {
                // just add shortcuts
                mWidgets.add(o);
            }
        }
        updatePageCountsAndInvalidateData();
    }

    public void setBulkBind(boolean bulkBind) {
        if (bulkBind) {
            mInBulkBind = true;
        } else {
            mInBulkBind = false;
            if (mNeedToUpdatePageCountsAndInvalidateData) {
                updatePageCountsAndInvalidateData();
            }
        }
    }

    private void updatePageCountsAndInvalidateData() {
        if (mInBulkBind) {
            mNeedToUpdatePageCountsAndInvalidateData = true;
        } else {
            updatePageCounts();
            invalidateOnDataChange();
            mNeedToUpdatePageCountsAndInvalidateData = false;
        }
    }

    @Override
    public void onClick(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onClick: v = " + v + ", v.getTag() = " + v.getTag());
        }

        /// M: add systrace to analyze application launch time.
        Trace.traceBegin(Trace.TRACE_TAG_INPUT, "AppsCustomizePagedView.onClick");

        // When we have exited all apps or are in transition, disregard clicks
        if (!mLauncher.isAllAppsVisible() || mLauncher.getWorkspace().isSwitchingState() || Launcher.isInEditMode()) {
            return; // / M: Add for op09 Edit and Hide app icons.
        }

        if (v instanceof PagedViewIcon) {
            // Animate some feedback to the click
            final AppInfo appInfo = (AppInfo) v.getTag();

            // Lock the drawable state to pressed until we return to Launcher
            if (mPressedIcon != null) {
                mPressedIcon.lockDrawableState();
            }


          //by qinjian add start for FMRadio show toast 20140730
          if(FeatureOption.HOSIN_QMOBILE_FM_RADIO_TOAST){
             if(appInfo.componentName.getPackageName().equals("com.mediatek.FMRadio")){
                 AudioManager am=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
				 if(am.isWiredHeadsetOn()==false){   
				 	String text="Please insert the earphone and then re-open FM. The earphone is used as antenna of FM radio";
                    CustomToast.makeText(getContext(),text,3000).show();
					return;
				 }
			   }
          	}
          //by qinjian add  for FMRadio show toast  end



			
            mLauncher.startActivitySafely(v, appInfo.intent, appInfo);
            mLauncher.getStats().recordLaunch(appInfo.intent);
        } else if (v instanceof PagedViewWidget) {
            // Let the user know that they have to long press to add a widget
            if (mWidgetInstructionToast != null) {
                mWidgetInstructionToast.cancel();
            }
            mWidgetInstructionToast = Toast.makeText(getContext(),R.string.long_press_widget_to_add,
                Toast.LENGTH_SHORT);
            mWidgetInstructionToast.show();

            // Create a little animation to show that the widget can move
            float offsetY = getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);
            final ImageView p = (ImageView) v.findViewById(R.id.widget_preview);
            AnimatorSet bounce = LauncherAnimUtils.createAnimatorSet();
            ValueAnimator tyuAnim = LauncherAnimUtils.ofFloat(p, "translationY", offsetY);
            tyuAnim.setDuration(125);
            ValueAnimator tydAnim = LauncherAnimUtils.ofFloat(p, "translationY", 0f);
            tydAnim.setDuration(100);
            bounce.play(tyuAnim).before(tydAnim);
            bounce.setInterpolator(new AccelerateInterpolator());
            bounce.start();
        }

        /// M: add systrace to analyze application launch time.
        Trace.traceEnd(Trace.TRACE_TAG_INPUT);
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeKeyEvent(v,  keyCode, event);
    }

    /*
     * PagedViewWithDraggableItems implementation
     */
    @Override
    protected void determineDraggingStart(android.view.MotionEvent ev) {
        // Disable dragging by pulling an app down for now.
    }

    private void beginDraggingApplication(View v) {
        mLauncher.getWorkspace().onDragStartedWithItem(v);
        mLauncher.getWorkspace().beginDragShared(v, this);

        /// M: Add for Edit AllAppsList for op09.
        if (Launcher.isInEditMode()) {
            View cellLayout = v;
            while (!(cellLayout instanceof CellLayout)) {
                cellLayout = (View) cellLayout.getParent();
            }

            CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) cellLayout.getTag();
            // This happens when long clicking an item with the dpad/trackball
            if (cellInfo == null) {
                LauncherLog.i(TAG, "cellInfo is null during dragging: cellLayout = " + cellLayout);
                return;
            }

            // Make sure the drag was started by a long press as opposed to a
            // long click.
            if (!cellInfo.cell.isInTouchMode()) {
                LauncherLog.i(TAG, "The child " + cellInfo.cell + " is not in touch mode.");
                return;
            }

            mDragInfo = cellInfo;
            mPrevEmptyCell[0] = cellInfo.cellX;
            mPrevEmptyCell[1] = cellInfo.cellY;

            mEmptyCell[0] = cellInfo.cellX;
            mEmptyCell[1] = cellInfo.cellY;

            ((CellLayout) cellLayout).removeChildView(cellInfo.cell);
            AppInfo appInfo = (AppInfo) v.getTag();
            sAllAppsPage.get((int) appInfo.screenId).remove(appInfo);
            mOccuredReorder = true;

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "beginDraggingApplication: mEmptyCell[0] = " + mEmptyCell[0]
                        + ", mEmptyCell[1] = " + mEmptyCell[1] + ", mDragInfo = " + mDragInfo
                        + ",appInfo = " + appInfo + ",cellLayout = " + cellLayout + ",v = " + v);
            }

            cellLayout.clearFocus();
            cellLayout.setPressed(false);
        }
    }

    Bundle getDefaultOptionsForWidget(Launcher launcher, PendingAddWidgetInfo info) {
        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AppWidgetResizeFrame.getWidgetSizeRanges(mLauncher, info.spanX, info.spanY, mTmpRect);
            Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(mLauncher,
                    info.componentName, null);

            float density = getResources().getDisplayMetrics().density;
            int xPaddingDips = (int) ((padding.left + padding.right) / density);
            int yPaddingDips = (int) ((padding.top + padding.bottom) / density);

            options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    mTmpRect.left - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    mTmpRect.top - yPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    mTmpRect.right - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    mTmpRect.bottom - yPaddingDips);
        }
        return options;
    }

    private void preloadWidget(final PendingAddWidgetInfo info) {
        final AppWidgetProviderInfo pInfo = info.info;
        final Bundle options = getDefaultOptionsForWidget(mLauncher, info);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "preloadWidget info = " + info + ", pInfo = " + pInfo +
                    ", pInfo.configure = " + pInfo.configure);
        }

        if (pInfo.configure != null) {
            info.bindOptions = options;
            return;
        }

        mWidgetCleanupState = WIDGET_PRELOAD_PENDING;
        mBindWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                mWidgetLoadingId = mLauncher.getAppWidgetHost().allocateAppWidgetId();
                // Options will be null for platforms with JB or lower, so this serves as an
                // SDK level check.
                if (options == null) {
                    if (AppWidgetManager.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                            mWidgetLoadingId, info.componentName)) {
                        mWidgetCleanupState = WIDGET_BOUND;
                    }
                } else {
                    if (AppWidgetManager.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                            mWidgetLoadingId, info.componentName, options)) {
                        mWidgetCleanupState = WIDGET_BOUND;
                    }
                }
            }
        };
        post(mBindWidgetRunnable);

        mInflateWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWidgetCleanupState != WIDGET_BOUND) {
                    return;
                }
                AppWidgetHostView hostView = mLauncher.
                        getAppWidgetHost().createView(getContext(), mWidgetLoadingId, pInfo);
                info.boundWidget = hostView;
                mWidgetCleanupState = WIDGET_INFLATED;
                hostView.setVisibility(INVISIBLE);
                int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(info.spanX,
                        info.spanY, info, false);

                // We want the first widget layout to be the correct size. This will be important
                // for width size reporting to the AppWidgetManager.
                DragLayer.LayoutParams lp = new DragLayer.LayoutParams(unScaledSize[0],
                        unScaledSize[1]);
                lp.x = lp.y = 0;
                lp.customPosition = true;
                hostView.setLayoutParams(lp);
                mLauncher.getDragLayer().addView(hostView);
            }
        };
        post(mInflateWidgetRunnable);
    }

    @Override
    public void onShortPress(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onShortcutPress v = " + v + ", v.getTag() = " + v.getTag());
        }

        // We are anticipating a long press, and we use this time to load bind and instantiate
        // the widget. This will need to be cleaned up if it turns out no long press occurs.
        if (mCreateWidgetInfo != null) {
            // Just in case the cleanup process wasn't properly executed. This shouldn't happen.
            cleanupWidgetPreloading(false);
        }
        mCreateWidgetInfo = new PendingAddWidgetInfo((PendingAddWidgetInfo) v.getTag());
        preloadWidget(mCreateWidgetInfo);
    }

    private void cleanupWidgetPreloading(boolean widgetWasAdded) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cleanupWidgetPreloading widgetWasAdded = " + widgetWasAdded
                    + ", mCreateWidgetInfo = " + mCreateWidgetInfo + ", mWidgetLoadingId = "
                    + mWidgetLoadingId);
        }

        if (!widgetWasAdded) {
            // If the widget was not added, we may need to do further cleanup.
            PendingAddWidgetInfo info = mCreateWidgetInfo;
            mCreateWidgetInfo = null;

            if (mWidgetCleanupState == WIDGET_PRELOAD_PENDING) {
                // We never did any preloading, so just remove pending callbacks to do so
                removeCallbacks(mBindWidgetRunnable);
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_BOUND) {
                 // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // We never got around to inflating the widget, so remove the callback to do so.
                removeCallbacks(mInflateWidgetRunnable);
            } else if (mWidgetCleanupState == WIDGET_INFLATED) {
                // Delete the widget id which was allocated
                if (mWidgetLoadingId != -1) {
                    mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
                }

                // The widget was inflated and added to the DragLayer -- remove it.
                AppWidgetHostView widget = info.boundWidget;
                mLauncher.getDragLayer().removeView(widget);
            }
        }
        mWidgetCleanupState = WIDGET_NO_CLEANUP_REQUIRED;
        mWidgetLoadingId = -1;
        mCreateWidgetInfo = null;
        PagedViewWidget.resetShortPressTarget();
    }

    @Override
    public void cleanUpShortPress(View v) {
        if (!mDraggingWidget) {
            cleanupWidgetPreloading(false);
        }
    }

    private boolean beginDraggingWidget(View v) {
        mDraggingWidget = true;
        // Get the widget preview as the drag representation
        ImageView image = (ImageView) v.findViewById(R.id.widget_preview);
        PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v.getTag();

        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "beginDraggingWidget: createItemInfo = " + createItemInfo
                    + ", v = " + v + ", image = " + image + ", this = " + this);
        }

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getDrawable() == null) {
            mDraggingWidget = false;
            return false;
        }

        // Compose the drag image
        Bitmap preview;
        Bitmap outline;
        float scale = 1f;
        Point previewPadding = null;

        if (createItemInfo instanceof PendingAddWidgetInfo) {
            // This can happen in some weird cases involving multi-touch. We can't start dragging
            // the widget if this is null, so we break out.
            if (mCreateWidgetInfo == null) {
                return false;
            }

            PendingAddWidgetInfo createWidgetInfo = mCreateWidgetInfo;
            createItemInfo = createWidgetInfo;
            int spanX = createItemInfo.spanX;
            int spanY = createItemInfo.spanY;
            int[] size = mLauncher.getWorkspace().estimateItemSize(spanX, spanY,
                    createWidgetInfo, true);

            FastBitmapDrawable previewDrawable = (FastBitmapDrawable) image.getDrawable();
            float minScale = 1.25f;
            int maxWidth, maxHeight;
            maxWidth = Math.min((int) (previewDrawable.getIntrinsicWidth() * minScale), size[0]);
            maxHeight = Math.min((int) (previewDrawable.getIntrinsicHeight() * minScale), size[1]);

            int[] previewSizeBeforeScale = new int[1];

            preview = mWidgetPreviewLoader.generateWidgetPreview(createWidgetInfo.componentName,
                    createWidgetInfo.previewImage, createWidgetInfo.icon, spanX, spanY,
                    maxWidth, maxHeight, null, previewSizeBeforeScale);

            // Compare the size of the drag preview to the preview in the AppsCustomize tray
            int previewWidthInAppsCustomize = Math.min(previewSizeBeforeScale[0],
                    mWidgetPreviewLoader.maxWidthForWidgetPreview(spanX));
            scale = previewWidthInAppsCustomize / (float) preview.getWidth();

            // The bitmap in the AppsCustomize tray is always the the same size, so there
            // might be extra pixels around the preview itself - this accounts for that
            if (previewWidthInAppsCustomize < previewDrawable.getIntrinsicWidth()) {
                int padding =
                        (previewDrawable.getIntrinsicWidth() - previewWidthInAppsCustomize) / 2;
                previewPadding = new Point(padding, 0);
            }
        } else {
            PendingAddShortcutInfo createShortcutInfo = (PendingAddShortcutInfo) v.getTag();
            Drawable icon = mIconCache.getFullResIcon(createShortcutInfo.shortcutActivityInfo);
            preview = Bitmap.createBitmap(icon.getIntrinsicWidth(),
                    icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(preview);
            mCanvas.save();
            WidgetPreviewLoader.renderDrawableToBitmap(icon, preview, 0, 0,
                    icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            mCanvas.restore();
            mCanvas.setBitmap(null);
            createItemInfo.spanX = createItemInfo.spanY = 1;
        }

        // Don't clip alpha values for the drag outline if we're using the default widget preview
        boolean clipAlpha = !(createItemInfo instanceof PendingAddWidgetInfo &&
                (((PendingAddWidgetInfo) createItemInfo).previewImage == 0));

        // Save the preview for the outline generation, then dim the preview
        outline = Bitmap.createScaledBitmap(preview, preview.getWidth(), preview.getHeight(),
                false);

        // Start the drag
        mLauncher.lockScreenOrientation();
        mLauncher.getWorkspace().onDragStartedWithItem(createItemInfo, outline, clipAlpha);
        mDragController.startDrag(image, preview, this, createItemInfo,
                DragController.DRAG_ACTION_COPY, previewPadding, scale);
        outline.recycle();
        preview.recycle();
        return true;
    }

    @Override
    protected boolean beginDragging(final View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "beginDragging: v = " + v + ", this = " + this);
        }

        if (!super.beginDragging(v)) return false;

        if (v instanceof PagedViewIcon) {
            beginDraggingApplication(v);
            /// M: return directly if in edit mode for op09.
            if (Launcher.isInEditMode()) {
                return true;
            }
        } else if (v instanceof PagedViewWidget) {
            if (!beginDraggingWidget(v)) {
                return false;
            }
        }

        // We delay entering spring-loaded mode slightly to make sure the UI
        // thready is free of any work.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                // We don't enter spring-loaded mode if the drag has been cancelled
                if (mLauncher.getDragController().isDragging()) {
                    // Reset the alpha on the dragged icon before we drag
                    resetDrawableState();

                    // Go into spring loaded mode (must happen before we startDrag())
                    mLauncher.enterSpringLoadedDragMode();
                }
            }
        }, 150);

        return true;
    }

    /**
     * Clean up after dragging.
     *
     * @param target where the item was dragged to (can be null if the item was flung)
     */
    private void endDragging(View target, boolean isFlingToDelete, boolean success) {
        if (LauncherLog.DEBUG_DRAG) {
            LauncherLog.d(TAG, "endDragging: target = " + target + ", isFlingToDelete = " + isFlingToDelete + ", success = " + success);
        }

        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragMode();
        }
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public View getContent() {
        return null;
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onLauncherTransitionPrepare l = " + l + ", animated = " + animated +
                    ", toWorkspace = " + toWorkspace);
        }

        mInTransition = true;
        if (toWorkspace) {
            cancelAllTasks();
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onLauncherTransitionEnd l = " + l + ", animated = " + animated +
                    ", toWorkspace = " + toWorkspace);
        }

        mInTransition = false;
        for (AsyncTaskPageData d : mDeferredSyncWidgetPageItems) {
            onSyncWidgetPageItems(d);
        }
        mDeferredSyncWidgetPageItems.clear();
        for (Runnable r : mDeferredPrepareLoadWidgetPreviewsTasks) {
            r.run();
        }
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
        mForceDrawAllChildrenNextFrame = !toWorkspace;
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDropCompleted: target = " + target + ", d = " + d
                    + ", isFlingToDelete = " + isFlingToDelete + ", success = " + success
                    + ", mEmptyCell = (" + mEmptyCell[0] + ", " + mEmptyCell[1]
                    + "), mTargetCell = (" + mTargetCell[0] + ", " + mTargetCell[1] + ")."
                    + ", mDragInfo = " + mDragInfo + ",mCurrentPage = " + mCurrentPage);
        }

        // Return early and wait for onFlingToDeleteCompleted if this was the result of a fling
        if (isFlingToDelete) return;

        endDragging(target, false, success);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    layout.calculateSpans(itemInfo);
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
                /// M: Display an error message if the drag failed due to exist one IMtkWidget
                /// which providerName equals the providerName of the dragInfo.
                if (d.dragInfo instanceof PendingAddWidgetInfo) {
                    PendingAddWidgetInfo info = (PendingAddWidgetInfo) d.dragInfo;
                    if (workspace.searchIMTKWidget(workspace, info.componentName.getClassName()) != null) {
                        mLauncher.showOnlyOneWidgetMessage(info);
                    }
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            /// M: The drag failed, we need to return the item to all apps list, add for OP09.
            if (Launcher.isInEditMode()) {
                onDrop(d);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
        cleanupWidgetPreloading(success);
        mDraggingWidget = false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onFlingToDeleteCompleted.");
        }

        // We just dismiss the drag when we fling, so cleanup here
        endDragging(null, true, true);
        cleanupWidgetPreloading(false);
        mDraggingWidget = false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDetachedFromWindow.");
        }
        cancelAllTasks();
    }

    public void clearAllWidgetPages() {
        cancelAllTasks();
        int count = getChildCount();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "clearAllPages: count = " + count);
        }

        for (int i = 0; i < count; i++) {
            View v = getPageAt(i);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
                mDirtyPageContent.set(i, true);
            }
        }
    }

    private void cancelAllTasks() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "cancelAllTasks: mRunningTasks size = " + mRunningTasks.size());
        }

        // Clean up all the async tasks
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            task.cancel(false);
            iter.remove();
            mDirtyPageContent.set(task.page, true);

            // We've already preallocated the views for the data to load into, so clear them as well
            View v = getPageAt(task.page);
            if (v instanceof PagedViewGridLayout) {
                ((PagedViewGridLayout) v).removeAllViewsOnPage();
            }
        }
        mDeferredSyncWidgetPageItems.clear();
        mDeferredPrepareLoadWidgetPreviewsTasks.clear();
    }

    public void setContentType(ContentType type) {
        int page = getCurrentPage();
        if (mContentType != type) {
            page = 0;
        }
        mContentType = type;
        /// M: Add for op09 Edit and Hide app icons.
        updatePageCounts();
        invalidatePageData(page, true);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "setContentType: type = " + type + ", mNumAppsPages = " + mNumAppsPages
                    + ", mNumWidgetPages = " + mNumWidgetPages + ", this = " + this);
        }
    }

    public ContentType getContentType() {
        return mContentType;
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        super.snapToPage(whichPage, delta, duration);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "snapToPage: whichPage = " + whichPage + ", delta = "
                    + delta + ", duration = " + duration + ", this = " + this);
        }

        // Update the thread priorities given the direction lookahead
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int pageIndex = task.page;
            if ((mNextPage > mCurrentPage && pageIndex >= mCurrentPage) ||
                (mNextPage < mCurrentPage && pageIndex <= mCurrentPage)) {
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            } else {
                task.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            }
        }

        /// M: set current drop target for dragging, add for OP09.
        if (whichPage < mNumAppsPages && isSupportCycleSlidingScreen()) {
            mCurrentDropTarget = (CellLayout) getPageAt(whichPage);
        }
    }

    /*
     * Apps PagedView implementation
     */
    private void setVisibilityOnChildren(ViewGroup layout, int visibility) {
        int childCount = layout.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            layout.getChildAt(i).setVisibility(visibility);
        }
    }

    private void setupPage(AppsCustomizeCellLayout layout) {
        layout.setGridSize(mCellCountX, mCellCountY);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.  That said, we already know the
        // expected page width, so we can actually optimize by hiding all the TextView-based
        // children that are expensive to measure, and let that happen naturally later.
        setVisibilityOnChildren(layout, View.GONE);
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
        setVisibilityOnChildren(layout, View.VISIBLE);
    }

    public void syncAppsPageItems(int page, boolean immediate) {
        // ensure that we have the right number of items on the pages
        final boolean isRtl = isLayoutRtl();
        int numCells = mCellCountX * mCellCountY;
        int startIndex = page * numCells;
        int endIndex = Math.min(startIndex + numCells, mApps.size());
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncAppsPageItems: page = " + page + ", immediate = " + immediate
                    + ", numCells = " + numCells + ", startIndex = " + startIndex + ", endIndex = "
                    + endIndex + ", app size = " + mApps.size() + ", child count = "
                    + getChildCount() + ", this = " + this);
        }

        AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);

        layout.removeAllViewsOnPage();
        ArrayList<Object> items = new ArrayList<Object>();
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();
        for (int i = startIndex; i < endIndex; ++i) {
            AppInfo info = mApps.get(i);
            PagedViewIcon icon = (PagedViewIcon) mLayoutInflater.inflate(
                    R.layout.apps_customize_application, layout, false);
            icon.applyFromApplicationInfo(info, true, this);
            icon.setOnClickListener(this);
            icon.setOnLongClickListener(this);
            icon.setOnTouchListener(this);
            icon.setOnKeyListener(this);

            int index = i - startIndex;
            int x = index % mCellCountX;
            int y = index / mCellCountX;
            if (isRtl) {
                x = mCellCountX - x - 1;
            }
            layout.addViewToCellLayout(icon, -1, i, new CellLayout.LayoutParams(x,y, 1,1), false);

            items.add(info);
            images.add(info.iconBitmap);
        }

        enableHwLayersOnVisiblePages();
    }

    /**
     * A helper to return the priority for loading of the specified widget page.
     */
    private int getWidgetPageLoadPriority(int page) {
        // If we are snapping to another page, use that index as the target page index
        int toPage = mCurrentPage;
        if (mNextPage > -1) {
            toPage = mNextPage;
        }

        // We use the distance from the target page as an initial guess of priority, but if there
        // are no pages of higher priority than the page specified, then bump up the priority of
        // the specified page.
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        int minPageDiff = Integer.MAX_VALUE;
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            minPageDiff = Math.abs(task.page - toPage);
        }

        int rawPageDiff = Math.abs(page - toPage);
        return rawPageDiff - Math.min(rawPageDiff, minPageDiff);
    }
    /**
     * Return the appropriate thread priority for loading for a given page (we give the current
     * page much higher priority)
     */
    private int getThreadPriorityForPage(int page) {
        // TODO-APPS_CUSTOMIZE: detect number of cores and set thread priorities accordingly below
        int pageDiff = getWidgetPageLoadPriority(page);
        if (pageDiff <= 0) {
            return Process.THREAD_PRIORITY_LESS_FAVORABLE;
        } else if (pageDiff <= 1) {
            return Process.THREAD_PRIORITY_LOWEST;
        } else {
            return Process.THREAD_PRIORITY_LOWEST;
        }
    }
    private int getSleepForPage(int page) {
        int pageDiff = getWidgetPageLoadPriority(page);
        return Math.max(0, pageDiff * sPageSleepDelay);
    }
    /**
     * Creates and executes a new AsyncTask to load a page of widget previews.
     */
    private void prepareLoadWidgetPreviewsTask(int page, ArrayList<Object> widgets,
            int cellWidth, int cellHeight, int cellCountX) {

        // Prune all tasks that are no longer needed
        Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
        while (iter.hasNext()) {
            AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
            int taskPage = task.page;
            if (taskPage < getAssociatedLowerPageBound(mCurrentPage) ||
                    taskPage > getAssociatedUpperPageBound(mCurrentPage)) {
                task.cancel(false);
                iter.remove();
            } else {
                task.setThreadPriority(getThreadPriorityForPage(taskPage));
            }
        }

        // We introduce a slight delay to order the loading of side pages so that we don't thrash
        final int sleepMs = getSleepForPage(page);
        AsyncTaskPageData pageData = new AsyncTaskPageData(page, widgets, cellWidth, cellHeight,
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    try {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (Exception e) {}
                        loadWidgetPreviewsInBackground(task, data);
                    } finally {
                        if (task.isCancelled()) {
                            data.cleanup(true);
                        }
                    }
                }
            },
            new AsyncTaskCallback() {
                @Override
                public void run(AppsCustomizeAsyncTask task, AsyncTaskPageData data) {
                    mRunningTasks.remove(task);
                    if (task.isCancelled()) return;
                    // do cleanup inside onSyncWidgetPageItems
                    onSyncWidgetPageItems(data);
                }
            }, mWidgetPreviewLoader);

        // Ensure that the task is appropriately prioritized and runs in parallel
        AppsCustomizeAsyncTask t = new AppsCustomizeAsyncTask(page,
                AsyncTaskPageData.Type.LoadWidgetPreviewData);
        t.setThreadPriority(getThreadPriorityForPage(page));
        t.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, pageData);
        mRunningTasks.add(t);
    }

    /*
     * Widgets PagedView implementation
     */
    private void setupPage(PagedViewGridLayout layout) {
        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        layout.setMinimumWidth(getPageContentWidth());
        layout.measure(widthSpec, heightSpec);
    }

    public void syncWidgetPageItems(final int page, final boolean immediate) {
        int numItemsPerPage = mWidgetCountX * mWidgetCountY;

        // Calculate the dimensions of each cell we are giving to each widget
        final ArrayList<Object> items = new ArrayList<Object>();
        int contentWidth = mContentWidth;
        final int cellWidth = ((contentWidth - mPageLayoutPaddingLeft - mPageLayoutPaddingRight
                - ((mWidgetCountX - 1) * mWidgetWidthGap)) / mWidgetCountX);
        int contentHeight = mContentHeight;
        final int cellHeight = ((contentHeight - mPageLayoutPaddingTop - mPageLayoutPaddingBottom
                - ((mWidgetCountY - 1) * mWidgetHeightGap)) / mWidgetCountY);

        // Prepare the set of widgets to load previews for in the background
        int offset = page * numItemsPerPage;
        for (int i = offset; i < Math.min(offset + numItemsPerPage, mWidgets.size()); ++i) {
            items.add(mWidgets.get(i));
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncWidgetPageItems: page = " + page + ", immediate = " + immediate
                    + ", numItemsPerPage = " + numItemsPerPage
                    + ", contentWidth = " + contentWidth + ", cellWidth = " + cellWidth
                    + ", contentHeight = " + contentHeight + ", cellHeight = " + cellHeight
                    + ", offset = " + offset + ", this = " + this);
        }

        // Prepopulate the pages with the other widget info, and fill in the previews later
        final PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(page);
        layout.setColumnCount(layout.getCellCountX());
        LauncherLog.d(TAG, "syncWidgetPageItems: cell count x = " + layout.getCellCountX() 
                    + ", layout column count = " + layout.getColumnCount());
        for (int i = 0; i < items.size(); ++i) {
            Object rawInfo = items.get(i);
            PendingAddItemInfo createItemInfo = null;
            PagedViewWidget widget = (PagedViewWidget) mLayoutInflater.inflate(
                    R.layout.apps_customize_widget, layout, false);
            if (rawInfo instanceof AppWidgetProviderInfo) {
                // Fill in the widget information
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) rawInfo;
                createItemInfo = new PendingAddWidgetInfo(info, null, null);

                // Determine the widget spans and min resize spans.
                int[] spanXY = Launcher.getSpanForWidget(mLauncher, info);
                createItemInfo.spanX = spanXY[0];
                createItemInfo.spanY = spanXY[1];
                int[] minSpanXY = Launcher.getMinSpanForWidget(mLauncher, info);
                createItemInfo.minSpanX = minSpanXY[0];
                createItemInfo.minSpanY = minSpanXY[1];

                widget.applyFromAppWidgetProviderInfo(info, -1, spanXY, mWidgetPreviewLoader);
                widget.setTag(createItemInfo);
                widget.setShortPressListener(this);
            } else if (rawInfo instanceof ResolveInfo) {
                // Fill in the shortcuts information
                ResolveInfo info = (ResolveInfo) rawInfo;
                createItemInfo = new PendingAddShortcutInfo(info.activityInfo);
                createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
                createItemInfo.componentName = new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name);
                widget.applyFromResolveInfo(mPackageManager, info, mWidgetPreviewLoader);
                widget.setTag(createItemInfo);
            }
            widget.setOnClickListener(this);
            widget.setOnLongClickListener(this);
            widget.setOnTouchListener(this);
            widget.setOnKeyListener(this);

            // Layout each widget
            int ix = i % mWidgetCountX;
            int iy = i / mWidgetCountX;
            LauncherLog.d(TAG, "syncWidgetPageItems: i = " + i + 
                        ", ix = " + ix + ", iy = " + iy + ", mWidgetCountX = " + mWidgetCountX);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(iy, GridLayout.START),
                    GridLayout.spec(ix, GridLayout.TOP));
            lp.width = cellWidth;
            lp.height = cellHeight;
            lp.setGravity(Gravity.TOP | Gravity.START);
            if (ix > 0) lp.leftMargin = mWidgetWidthGap;
            if (iy > 0) lp.topMargin = mWidgetHeightGap;
            layout.addView(widget, lp);
        }

        // wait until a call on onLayout to start loading, because
        // PagedViewWidget.getPreviewSize() will return 0 if it hasn't been laid out
        // TODO: can we do a measure/layout immediately?
        layout.setOnLayoutListener(new Runnable() {
            public void run() {
                // Load the widget previews
                int maxPreviewWidth = cellWidth;
                int maxPreviewHeight = cellHeight;
                if (layout.getChildCount() > 0) {
                    PagedViewWidget w = (PagedViewWidget) layout.getChildAt(0);
                    int[] maxSize = w.getPreviewSize();
                    maxPreviewWidth = maxSize[0];
                    maxPreviewHeight = maxSize[1];
                    if ((maxPreviewWidth <= 0) || (maxPreviewHeight <= 0)) {
                        if (LauncherLog.DEBUG) {
                            LauncherLog.d(TAG, "syncWidgetPageItems: maxPreviewWidth = " + maxPreviewWidth
                                + ", maxPreviewHeight = " + maxPreviewHeight);
                        }
                    }
                }

                mWidgetPreviewLoader.setPreviewSize(
                        maxPreviewWidth, maxPreviewHeight, mWidgetSpacingLayout);
                if (immediate) {
                    AsyncTaskPageData data = new AsyncTaskPageData(page, items,
                            maxPreviewWidth, maxPreviewHeight, null, null, mWidgetPreviewLoader);
                    loadWidgetPreviewsInBackground(null, data);
                    onSyncWidgetPageItems(data);
                } else {
                    if (mInTransition) {
                        mDeferredPrepareLoadWidgetPreviewsTasks.add(this);
                    } else {
                        prepareLoadWidgetPreviewsTask(page, items,
                                maxPreviewWidth, maxPreviewHeight, mWidgetCountX);
                    }
                }
                layout.setOnLayoutListener(null);
            }
        });
    }
    private void loadWidgetPreviewsInBackground(AppsCustomizeAsyncTask task,
            AsyncTaskPageData data) {
        // loadWidgetPreviewsInBackground can be called without a task to load a set of widget
        // previews synchronously
        if (task != null) {
            // Ensure that this task starts running at the correct priority
            task.syncThreadPriority();
        }

        // Load each of the widget/shortcut previews
        ArrayList<Object> items = data.items;
        ArrayList<Bitmap> images = data.generatedImages;
        int count = items.size();
        for (int i = 0; i < count; ++i) {
            if (task != null) {
                // Ensure we haven't been cancelled yet
                if (task.isCancelled()) break;
                // Before work on each item, ensure that this task is running at the correct
                // priority
                task.syncThreadPriority();
            }

            images.add(mWidgetPreviewLoader.getPreview(items.get(i)));
        }
    }

    private void onSyncWidgetPageItems(AsyncTaskPageData data) {
        if (mInTransition) {
            mDeferredSyncWidgetPageItems.add(data);
            return;
        }
        try {
            int page = data.page;
            PagedViewGridLayout layout = (PagedViewGridLayout) getPageAt(page);

            ArrayList<Object> items = data.items;
            int count = items.size();
            for (int i = 0; i < count; ++i) {
                PagedViewWidget widget = (PagedViewWidget) layout.getChildAt(i);
                if (widget != null) {
                    Bitmap preview = data.generatedImages.get(i);
                    widget.applyPreview(new FastBitmapDrawable(preview), i);
                }
            }

            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "onSyncWidgetPageItems: page = " + page + ", layout = " + layout
                    + ", count = " + count + ", this = " + this);
            }
            enableHwLayersOnVisiblePages();

            // Update all thread priorities
            Iterator<AppsCustomizeAsyncTask> iter = mRunningTasks.iterator();
            while (iter.hasNext()) {
                AppsCustomizeAsyncTask task = (AppsCustomizeAsyncTask) iter.next();
                int pageIndex = task.page;
                task.setThreadPriority(getThreadPriorityForPage(pageIndex));
            }
        } finally {
            data.cleanup(false);
        }
    }

    @Override
    public void syncPages() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPages: mNumWidgetPages = " + mNumWidgetPages + ", mNumAppsPages = "
                    + mNumAppsPages + ", this = " + this);
        }

        disablePagedViewAnimations();

        removeAllViews();
        cancelAllTasks();

        Context context = getContext();
        if (mContentType == ContentType.Applications) {
            for (int i = 0; i < mNumAppsPages; ++i) {
                AppsCustomizeCellLayout layout = new AppsCustomizeCellLayout(context);
                setupPage(layout);
                addView(layout, new PagedView.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
        } else if (mContentType == ContentType.Widgets) {
            for (int j = 0; j < mNumWidgetPages; ++j) {
                PagedViewGridLayout layout = new PagedViewGridLayout(context, mWidgetCountX,
                        mWidgetCountY);
                setupPage(layout);
                addView(layout, new PagedView.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
            }
        } else {
            throw new RuntimeException("Invalid ContentType");
        }

        enablePagedViewAnimations();
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "syncPageItems: page = " + page + ", immediate = " + immediate
                    + ", mNumAppsPages = " + mNumAppsPages + ",mSupportEditAndHideApps = "
                    + mSupportEditAndHideApps + ",page size = " + sAllAppsPage.size());
        }

        if (mContentType == ContentType.Widgets) {
            syncWidgetPageItems(page, immediate);
        } else {
            if (!mSupportEditAndHideApps) {
                syncAppsPageItems(page, immediate);
            } else {
                /// M: Sync apps page items according to page info, add for OP09.
                syncAppsPageItems(sAllAppsPage.get(page), page, immediate);
            }
        }
    }

    // We want our pages to be z-ordered such that the further a page is to the left, the higher
    // it is in the z-order. This is important to insure touch events are handled correctly.
    View getPageAt(int index) {
        return getChildAt(indexToPage(index));
    }

    @Override
    protected int indexToPage(int index) {
        return getChildCount() - index - 1;
    }

    // In apps customize, we have a scrolling effect which emulates pulling cards off of a stack.
    @Override
    protected void screenScrolled(int screenCenter) {
        final boolean isRtl = isLayoutRtl();
        super.screenScrolled(screenCenter);

        if (LauncherLog.DEBUG_DRAW) {
            LauncherLog.d(TAG, "screenScrolled: screenCenter = " + screenCenter + ", mOverScrollX = " + mOverScrollX
                    + ", mMaxScrollX = " + mMaxScrollX + ", mScrollX = " + mScrollX + ",getScrollX() = " + getScrollX()
                    + ", this = " + this);
        }

        for (int i = 0; i < getChildCount(); i++) {
            View v = getPageAt(i);
            if (v != null) {
                float scrollProgress = getScrollProgress(screenCenter, v, i);

                float interpolatedProgress;
                float translationX;
                float maxScrollProgress = Math.max(0, scrollProgress);
                float minScrollProgress = Math.min(0, scrollProgress);

                if (isRtl) {
                    translationX = maxScrollProgress * v.getMeasuredWidth();
                    interpolatedProgress = mZInterpolator.getInterpolation(Math.abs(maxScrollProgress));
                } else {
                    translationX = minScrollProgress * v.getMeasuredWidth();
                    interpolatedProgress = mZInterpolator.getInterpolation(Math.abs(minScrollProgress));
                }
                float scale = (1 - interpolatedProgress) +
                        interpolatedProgress * TRANSITION_SCALE_FACTOR;

                float alpha;
                if (isRtl && (scrollProgress > 0)) {
                    alpha = mAlphaInterpolator.getInterpolation(1 - Math.abs(maxScrollProgress));
                } else if (!isRtl && (scrollProgress < 0)) {
                    alpha = mAlphaInterpolator.getInterpolation(1 - Math.abs(scrollProgress));
                } else {
                    //  On large screens we need to fade the page as it nears its leftmost position
                    alpha = mLeftScreenAlphaInterpolator.getInterpolation(1 - scrollProgress);
                }

                if (LauncherLog.DEBUG_DRAW) {
                    LauncherLog.d(TAG, "screenScrolled: i = " + i + ", scrollProgress = " + scrollProgress + ", alpha = "
                            + alpha + ", v = " + v + ", this = " + this);
                }

                v.setCameraDistance(mDensity * CAMERA_DISTANCE);
                int pageWidth = v.getMeasuredWidth();
                int pageHeight = v.getMeasuredHeight();

                if (PERFORM_OVERSCROLL_ROTATION) {
                    float xPivot = isRtl ? 1f - TRANSITION_PIVOT : TRANSITION_PIVOT;
                    boolean isOverscrollingFirstPage = isRtl ? scrollProgress > 0 : scrollProgress < 0;
                    boolean isOverscrollingLastPage = isRtl ? scrollProgress < 0 : scrollProgress > 0;

                    if (i == 0 && isOverscrollingFirstPage) {
                        // Overscroll to the left
                        v.setPivotX(xPivot * pageWidth);
                        v.setRotationY(-TRANSITION_MAX_ROTATION * scrollProgress);
                        scale = 1.0f;
                        alpha = 1.0f;
                        // On the first page, we don't want the page to have any lateral motion
                        translationX = 0;
                    } else if (i == getChildCount() - 1 && isOverscrollingLastPage) {
                        // Overscroll to the right
                        v.setPivotX((1 - xPivot) * pageWidth);
                        v.setRotationY(-TRANSITION_MAX_ROTATION * scrollProgress);
                        scale = 1.0f;
                        alpha = 1.0f;
                        // On the last page, we don't want the page to have any lateral motion.
                        translationX = 0;
                    } else {
                        v.setPivotY(pageHeight / 2.0f);
                        v.setPivotX(pageWidth / 2.0f);
                        v.setRotationY(0f);
                    }
                }

                v.setTranslationX(translationX);
                v.setScaleX(scale);
                v.setScaleY(scale);
                v.setAlpha(alpha);

                // If the view has 0 alpha, we set it to be invisible so as to prevent
                // it from accepting touches
                if (alpha == 0) {
                    v.setVisibility(INVISIBLE);
                } else if (v.getVisibility() != VISIBLE) {
                    v.setVisibility(VISIBLE);
                }
            }
        }

        enableHwLayersOnVisiblePages();
    }

    private void enableHwLayersOnVisiblePages() {
        final int screenCount = getChildCount();

        getVisiblePages(mTempVisiblePagesRange);
        int leftScreen = mTempVisiblePagesRange[0];
        int rightScreen = mTempVisiblePagesRange[1];
        int forceDrawScreen = -1;
        if (leftScreen == rightScreen) {
            // make sure we're caching at least two pages always
            if (rightScreen < screenCount - 1) {
                rightScreen++;
                forceDrawScreen = rightScreen;
            } else if (leftScreen > 0) {
                leftScreen--;
                forceDrawScreen = leftScreen;
            }
        } else {
            forceDrawScreen = leftScreen + 1;
        }

        for (int i = 0; i < screenCount; i++) {
            final View layout = (View) getPageAt(i);
            if (!(leftScreen <= i && i <= rightScreen &&
                    (i == forceDrawScreen || shouldDrawChild(layout)))) {
                layout.setLayerType(LAYER_TYPE_NONE, null);
            }
        }

        for (int i = 0; i < screenCount; i++) {
            final View layout = (View) getPageAt(i);

            if (leftScreen <= i && i <= rightScreen &&
                    (i == forceDrawScreen || shouldDrawChild(layout))) {
                if (layout.getLayerType() != LAYER_TYPE_HARDWARE) {
                    layout.setLayerType(LAYER_TYPE_HARDWARE, null);
                }
            }
        }
    }

    protected void overScroll(float amount) {
        acceleratedOverScroll(amount);
    }

    /**
     * Used by the parent to get the content width to set the tab bar to
     * @return
     */
    public int getPageContentWidth() {
        return mContentWidth;
    }

    @Override
    protected void onPageEndMoving() {
        super.onPageEndMoving();
        mForceDrawAllChildrenNextFrame = true;
        // We reset the save index when we change pages so that it will be recalculated on next
        // rotation
        mSaveInstanceStateItemIndex = -1;
    }

    /*
     * AllAppsView implementation
     */
    public void setup(Launcher launcher, DragController dragController) {
        mLauncher = launcher;
        mDragController = dragController;
    }

    /**
     * We should call thise method whenever the core data changes (mApps, mWidgets) so that we can
     * appropriately determine when to invalidate the PagedView page data.  In cases where the data
     * has yet to be set, we can requestLayout() and wait for onDataReady() to be called in the
     * next onMeasure() pass, which will trigger an invalidatePageData() itself.
     */
    private void invalidateOnDataChange() {
        if (!isDataReady()) {
            // The next layout pass will trigger data-ready if both widgets and apps are set, so
            // request a layout to trigger the page data when ready.
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "invalidateOnDataChange : Data is not ready");
            }
            requestLayout();
        } else {
            cancelAllTasks();
            invalidatePageData();
        }
    }

    public void setApps(ArrayList<AppInfo> list) {
        if (!DISABLE_ALL_APPS) {
            mApps = list;
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "setApps : mApps = " + mApps.size() + ", mAppsHasSet = "
                        + mAppsHasSet + ", this = " + this);
            }
            /// M: Init all apps for all apps pages for op09.
            mAppsHasSet = true;
            if (!mSupportEditAndHideApps) {
                /// HOSIN: sort apps by installation time
                if (com.mediatek.common.featureoption.FeatureOption.HOSIN_SORT_ALL_APPS_BY_INSTALLATION_TIME) {
                	Collections.sort(mApps, LauncherModel.getInstallTimeComparator());
            	} else {  /// HOSIN:
                	Collections.sort(mApps, LauncherModel.getAppNameComparator());
            	}  /// HOSIN:
                reorderApps();
            } else {
                initAllAppsPage();
            }
            updatePageCountsAndInvalidateData();
        }
    }
    private void addAppsWithoutInvalidate(ArrayList<AppInfo> list) {
        // We add it in place, in alphabetical order
        int count = list.size();
        for (int i = 0; i < count; ++i) {
            AppInfo info = list.get(i);
            int index = Collections.binarySearch(mApps, info, LauncherModel.getAppNameComparator());
            if (index < 0) {
                mApps.add(-(index + 1), info);
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "addAppsWithoutInvalidate: mApps size = " + mApps.size()
                            + ", index = " + index + ", info = " + info + ", this = " + this);
                }
            }
        }
    }
    public void addApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addApps: mSupportEditAndHideApps = " + mSupportEditAndHideApps + ", list = " + list + ", this = " + this);
        }

        if (!DISABLE_ALL_APPS) {
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                addAppsWithoutInvalidate(list);
                reorderApps();
            } else {
                addAddedApps(list);
                notifyPageListChanged();
            }
            updatePageCountsAndInvalidateData();
        }
    }
    private int findAppByComponent(List<AppInfo> list, AppInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = list.get(i);
            if (info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }
    private void removeAppsWithoutInvalidate(ArrayList<AppInfo> list) {
        // loop through all the apps and remove apps that have the same component
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "removeAppsWithoutInvalidate: removeIndex = " + removeIndex
                            + ", ApplicationInfo info = " + info + ", this = " + this);
                }
            }
        }
    }
    public void removeApps(ArrayList<AppInfo> appInfos) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeApps: appInfos = " + appInfos + ",size = " + mApps.size() + ",sRemovedApps = "
                    + sRemovedApps + ", this = " + this);
        }

        if (!DISABLE_ALL_APPS) {
            removeAppsWithoutInvalidate(appInfos);
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                reorderApps();
            } else {
                removeDisabledApps(sRemovedApps);
                notifyPageListChanged();
                sRemovedApps.clear();
            }
            updatePageCountsAndInvalidateData();
        }
    }
    public void updateApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "updateApps: list = " + list + ", this = " + this);
        }

        // We remove and re-add the updated applications list because it's properties may have
        // changed (ie. the title), and this will ensure that the items will be in their proper
        // place in the list.
        if (!DISABLE_ALL_APPS) {
            /// M: Add for op09 Edit and Hide app icons.
            if (!mSupportEditAndHideApps) {
                // We remove and re-add the updated applications list because it's
                // properties may have changed (ie. the title), and this will ensure
                // that the items will be in their proper place in the list.
                removeAppsWithoutInvalidate(list);
                addAppsWithoutInvalidate(list);
                reorderApps();
            }
            updatePageCountsAndInvalidateData();
        }
    }

    public void reset() {
        // If we have reset, then we should not continue to restore the previous state
        mSaveInstanceStateItemIndex = -1;

        AppsCustomizeTabHost tabHost = getTabHost();
        String tag = tabHost.getCurrentTabTag();
        /// M: whether we need to invalidate page data when tab changes, we need to do this if we support circle
        /// sliding, or else the data and tab will be inconsistent.
        boolean needInvalidateForTabChanges = false;
        if (tag != null) {
            if (!tag.equals(tabHost.getTabTagForContentType(ContentType.Applications))) {
                tabHost.setCurrentTabFromContent(ContentType.Applications);
                needInvalidateForTabChanges = isSupportCycleSlidingScreen();
            }
        }

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "reset: tag = " + tag + ",mCurrentPage = " + mCurrentPage + ", mNumWidgetPages = "
                    + mNumWidgetPages + ", mNumAppsPages = " + mNumAppsPages + ", needInvalidateForTabChanges = "
                    + needInvalidateForTabChanges + ",this = " + this);
        }

        /// M: we need to update page counts if the tab changes from widgets to apps.
        if (needInvalidateForTabChanges) {
            updatePageCounts();
        }

        if (mCurrentPage != 0 || needInvalidateForTabChanges) {
            invalidatePageData(0);
        }
    }

    private AppsCustomizeTabHost getTabHost() {
        return (AppsCustomizeTabHost) mLauncher.findViewById(R.id.apps_customize_pane);
    }

    public void dumpState() {
        // TODO: Dump information related to current list of Applications, Widgets, etc.
        AppInfo.dumpApplicationInfoList(TAG, "mApps", mApps);
        dumpAppWidgetProviderInfoList(TAG, "mWidgets", mWidgets);
    }

    private void dumpAppWidgetProviderInfoList(String tag, String label,
            ArrayList<Object> list) {
        Log.d(tag, label + " size=" + list.size());
        for (Object i: list) {
            if (i instanceof AppWidgetProviderInfo) {
                AppWidgetProviderInfo info = (AppWidgetProviderInfo) i;
                Log.d(tag, "   label=\"" + info.label + "\" previewImage=" + info.previewImage
                        + " resizeMode=" + info.resizeMode + " configure=" + info.configure
                        + " initialLayout=" + info.initialLayout
                        + " minWidth=" + info.minWidth + " minHeight=" + info.minHeight);
            } else if (i instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) i;
                Log.d(tag, "   label=\"" + info.loadLabel(mPackageManager) + "\" icon="
                        + info.icon);
            }
        }
    }

    public void surrender() {
        // TODO: If we are in the middle of any process (ie. for holographic outlines, etc) we
        // should stop this now.

        // Stop all background tasks
        cancelAllTasks();
    }

    @Override
    public void iconPressed(PagedViewIcon icon) {
        // Reset the previously pressed icon and store a reference to the pressed icon so that
        // we can reset it on return to Launcher (in Launcher.onResume())
        if (mPressedIcon != null) {
            mPressedIcon.resetDrawableState();
        }
        mPressedIcon = icon;
    }

    public void resetDrawableState() {
        if (mPressedIcon != null) {
            mPressedIcon.resetDrawableState();
            mPressedIcon = null;
        }
    }

    /*
     * We load an extra page on each side to prevent flashes from scrolling and loading of the
     * widget previews in the background with the AsyncTasks.
     */
    final static int sLookBehindPageCount = 2;
    final static int sLookAheadPageCount = 2;
    protected int getAssociatedLowerPageBound(int page) {
        final int count = getChildCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        int windowMinIndex = Math.max(Math.min(page - sLookBehindPageCount, count - windowSize),
                isSupportCycleSlidingScreen() ? -1 : 0);/// M: Add for op09 SysleSlidingScreen.
        return windowMinIndex;
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getChildCount();
        int windowSize = Math.min(count, sLookBehindPageCount + sLookAheadPageCount + 1);
        int windowMaxIndex = Math.min(Math.max(page + sLookAheadPageCount, windowSize - 1),
                isSupportCycleSlidingScreen() ? count : count - 1);/// M: Add for op09 CycleSlidingScreen.
        return windowMaxIndex;
    }

    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        int stringId = R.string.default_scroll_format;
        int count = 0;

        if (mContentType == ContentType.Applications) {
            stringId = R.string.apps_customize_apps_scroll_format;
            count = mNumAppsPages;
        } else if (mContentType == ContentType.Widgets) {
            stringId = R.string.apps_customize_widgets_scroll_format;
            count = mNumWidgetPages;
        } else {
            throw new RuntimeException("Invalid ContentType");
        }

        return String.format(getContext().getString(stringId), page + 1, count);
    }

    /**
     * M: Reorder apps in applist.
     */
    public void reorderApps() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "reorderApps: mApps = " + mApps + ", this = " + this);
        }
        if (AllAppsList.sTopPackages == null || mApps == null || mApps.isEmpty()
                || AllAppsList.sTopPackages.isEmpty()) {
            return;
        }

        ArrayList<AppInfo> dataReorder = new ArrayList<AppInfo>(
                AllAppsList.DEFAULT_APPLICATIONS_NUMBER);

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            for (AppInfo ai : mApps) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    mApps.remove(ai);
                    dataReorder.add(ai);
                    break;
                }
            }
        }

        for (AllAppsList.TopPackage tp : AllAppsList.sTopPackages) {
            int newIndex = 0;
            for (AppInfo ai : dataReorder) {
                if (ai.componentName.getPackageName().equals(tp.packageName)
                        && ai.componentName.getClassName().equals(tp.className)) {
                    newIndex = Math.min(Math.max(tp.order, 0), mApps.size());
                    mApps.add(newIndex, ai);
                    break;
                }
            }
        }
    }

    /// M: Add for Edit AllAppsList for op09 start.@{

    /**
     * M: Remove apps with package name but not invalidate.
     */
    private void removeAppsWithPackageNameWithoutInvalidate(ArrayList<String> packageNames) {
        // loop through all the package names and remove apps that have the same package name
        for (String pn : packageNames) {
            int removeIndex = findAppByPackage(mApps, pn);
            while (removeIndex > -1) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "removeAppsWithPackageNameWithoutInvalidate: removeIndex = " + removeIndex
                            + ", pn = " + pn + ", this = " + this);
                }
                /// M: store the remove apps in list for op09.
                sRemovedApps.add(mApps.remove(removeIndex));
                removeIndex = findAppByPackage(mApps, pn);
            }
        }
    }

    /**
     * M: Enter edit mode, allow user to rearrange application icons.
     */
    public void enterEditMode() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "enterEditMode: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = " + mNumAppsPages + ",mCurrentPage = " + mCurrentPage);
        }
        mIsInEditMode = true;
        resetDataIsReady();

        // A trick, we make the widget page number as 0, so we can not scroll to
        // widget page in edit mode.
        mNumWidgetPages = 0;
        // Add a new empty page at the tail.
        addNewEmptyAppsPage();
        invalidatePageData(mCurrentPage);

        // Make apps customized pane can receive drag and drop event.
        mDragController.setDragScoller(this);
        mDragController.setMoveTarget(this);
        mDragController.addDropTarget(this);
        mDragController.addDragListener(this);

        Launcher.enablePendingAppsQueue();

    }

    private boolean mIsInEditMode;

    /**
     * M: Exit edit mode.
     */
    public void exitEditMode() {
        mIsInEditMode = false;
        mNumWidgetPages = (int) Math
                .ceil(mWidgets.size() / (float) (mWidgetCountX * mWidgetCountY));
        removeEmptyPages();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "exitEditMode: mNumWidgetPages = " + mNumWidgetPages
                    + ", mNumAppsPages = " + mNumAppsPages + ", mCurrentPage = " + mCurrentPage);
        }

        invalidatePageData(mCurrentPage);

        // Make apps customized pane can't receive drag and drop event when exit edit mode.
        mDragController.setDragScoller(mLauncher.getWorkspace());
        mDragController.setMoveTarget(mLauncher.getWorkspace());
        mDragController.removeDropTarget(this);
        mDragController.removeDragListener(this);

        Launcher.disableAndFlushPendingAppsQueue(this);
    }

    /**
     * M: Whether need to show the update and hide icon.
     *
     * @return True if in apps page, else false.
     */
    public boolean needShowEditAndHideIcon() {
        return (mNumAppsPages <= 0 || mCurrentPage < mNumAppsPages);
    }

    /**
     * M: When drag enter, it will be called.
     */
    @Override
    public void onDragEnter(DragObject d) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragEnter: d = " + d + ", mDragTargetLayout = "
                    + mDragTargetLayout);
        }

        mDragEnforcer.onDragEnter();

        mDropToLayout = null;
        AppsCustomizeCellLayout layout = getCurrentDropLayout();
        setCurrentDropLayout(layout);

        // Clear previous target cell.
        mPreviousTargetCell[0] = -1;
        mPreviousTargetCell[1] = -1;
    }

    /**
     * M: When drag an icon, it will be called.
     */
    @Override
    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (mInScrollArea) {
            return;
        }

        float[] r = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView, null);
        AppsCustomizeCellLayout layout = getCurrentDropLayout();
        mTargetCell = layout.findNearestArea((int) r[0], (int) r[1], 1, 1, mTargetCell);

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragOver: d = " + d + ", mTargetCell = (" + mTargetCell[0] + ", "
                    + mTargetCell[1] + ").");
        }
        // If drag icon from another page.
        if (mPrevDropTarget != null && mPrevDropTarget != mCurrentDropTarget) {
            mPreviousTargetCell[0] = -1;
            mPreviousTargetCell[1] = -1;
            mPrevDropTarget = null;

            mEmptyCell[0] = -1;
            mEmptyCell[1] = -1;

            // If the page is full, process last cell in the page.
            saveLastCellInFullPage(layout, true);

            setCurrentDropLayout(layout);
        }

        if (mTargetCell[0] != mPreviousTargetCell[0] || mTargetCell[1] != mPreviousTargetCell[1]) {
            mReorderAlarm.cancelAlarm();
            mReorderAlarm.setOnAlarmListener(mReorderAlarmListener);
            mReorderAlarm.setAlarm(150);
            mPreviousTargetCell[0] = mTargetCell[0];
            mPreviousTargetCell[1] = mTargetCell[1];
        }
    }

    /**
     * M: When drag exit, it will be called.
     */
    @Override
    public void onDragExit(DragObject d) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragExit: d = " + d);
        }

        mDragEnforcer.onDragExit();

        // Here we store the final page that will be dropped to, if the
        // workspace in fact receives the drop.
        mDropToLayout = mDragTargetLayout;

        // Reset the scroll area and previous drag target
        mInScrollArea = false;

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDragExit: drag source = " + (d != null ? d.dragSource : null)
                    + ", drag info = " + (d != null ? d.dragInfo : null) + ", mDragTargetLayout = "
                    + mDragTargetLayout + ", mIsPageMoving = " + mIsPageMoving);
        }
        setCurrentDropLayout(null);
    }

    @Override
    public void onDrop(DragObject d) {
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        AppsCustomizeTabHost host = (AppsCustomizeTabHost) getTabHost();
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onDrop 1: drag view = " + d.dragView + ", dragInfo = " + d.dragInfo
                    + ", dragSource  = " + d.dragSource + ", dropTargetLayout = "
                    + dropTargetLayout + ", mDragInfo = " + mDragInfo + ", mInScrollArea = "
                    + mInScrollArea + ", this = " + this);
        }

        if (mDragInfo != null) {
            final View cell = mDragInfo.cell;

            // If drag an icon into the scroll area, or the top layout, its
            // dropTargetLayout will be null, and find the nearest cell the
            // dragObject will be placed.
            if (dropTargetLayout == null) {
                dropTargetLayout = mCurrentDropTarget;
                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, dropTargetLayout, mTargetCell);
            } else {
                mTargetCell[0] = mEmptyCell[0];
                mTargetCell[1] = mEmptyCell[1];
            }

            if (dropTargetLayout.isExceedLastPosition(mTargetCell)) {
                mTargetCell = dropTargetLayout.getLastPosition(mTargetCell);
            }

            if (dropTargetLayout != null) {
                int screen = (mTargetCell[0] < 0) ? (int) mDragInfo.screenId
                        : indexToPage(indexOfChild(dropTargetLayout));

                if (mCurrentPage != screen) {
                    snapToPage(screen);
                }

                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "onDrop 2: mCurrentPage = " + mCurrentPage + ",screen = "
                            + screen + ", mTargetCell[0] = " + mTargetCell[0]
                            + ", mTargetCell[1]  = " + mTargetCell[1] + ",child count = "
                            + getChildCount() + ", this = " + this);
                }

                final AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(screen);
                View child = layout.getChildAt(mTargetCell[0], mTargetCell[1]);

                // If has the view in the target cell
                if (child != null) {
                    if ((int) mDragInfo.screenId != screen) {
                        // If the page is full, process the last cell.
                        saveLastCellInFullPage(layout, (int) mDragInfo.screenId != screen);
                    }
                    // Realtime reorder all icons position.
                    realTimeReorder(mEmptyCell, mTargetCell, layout);
                }
                // Update item position after drop.
                updateItemPositionAfterDrop(layout, cell, screen);
                mOccuredReorder = false;
            }

            final AppsCustomizeCellLayout parent = (AppsCustomizeCellLayout) cell.getParent().getParent();
            d.deferDragViewCleanupPostAnimation = false;
            parent.onDropChild(cell);
            mPrevDropTarget = null;
        }
    }

    @Override
    public boolean acceptDrop(DragObject dragObject) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "acceptDrop: edit mode = " + Launcher.isInEditMode()
                    + ",dragObject = " + dragObject);
        }
        return Launcher.isInEditMode();
    }

    @Override
    public void getHitRect(Rect outRect) {
        outRect.set(0, 0, mDisplaySize.x, mDisplaySize.y);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "getHitRect: outRect = " + outRect);
        }
    }

    @Override
    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(getTabHost(), loc);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "getLocationInDragLayer: loc = (" + loc[0] + "," + loc[1] + ")");
        }
    }

    @Override
    public boolean isDropEnabled() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "isDropEnabled: edit mode = " + Launcher.isInEditMode());
        }
        return Launcher.isInEditMode();
    }

    @Override
    public void onFlingToDelete(DragObject d, int x, int y, PointF vec) {
        // We don't need to fling delete items in apps customize pane.
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragStart: source = " + source + ", info = " + info
                    + ", dragAction = " + dragAction);
        }
        mIsDragOccuring = true;
        mLauncher.lockScreenOrientation();
    }

    @Override
    public void onDragEnd() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDragEnd: mIsDragOccuring = " + mIsDragOccuring);
        }
        mIsDragOccuring = false;
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public void scrollLeft() {
        if (mSupportCycleSliding) {
            final boolean scrollFinished = mScroller.isFinished();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "scrollLeft: mCurrentPage = " + mCurrentPage + ", mNextPage = " + mNextPage
                        + ", scrollFinished = " + scrollFinished);
            }
            if (scrollFinished) {
                if (mCurrentPage > 0) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage - 1);
                } else if (isSupportCycleSlidingScreen() && mCurrentPage == 0) {
                    // We need also to roll back the reorder record of the first page when cycle sliding is supported.
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(getChildCount() - 1);
                }
                // Rollback the prev screen realtime reorder.
                rollbackLastScreenItemsPosition(mCurrentPage);
            } else {
                // TODO: in what condition will enter this statement.
                if (mNextPage > 0) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage - 1);
                    // Rollback the prev screen realtime reorder.
                    rollbackLastScreenItemsPosition(mNextPage);
                }
            }
        }

        super.scrollLeft();
    }

    @Override
    public void scrollRight() {
        if (mSupportCycleSliding) {
            final boolean scrollFinished = mScroller.isFinished();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "scrollRight: mCurrentPage = " + mCurrentPage + ", mNextPage = " + mNextPage
                        + ", scrollFinished = " + scrollFinished);
            }
            if (scrollFinished) {
                if (mCurrentPage < getChildCount() - 1) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage + 1);
                } else if (isSupportCycleSlidingScreen() && mCurrentPage == getChildCount() - 1) {
                    // We need also to roll back the reorder record of the last page when cycle sliding is supported.
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mCurrentPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(0);
                }
                // Rollback the prev screen realtime reorder.
                rollbackLastScreenItemsPosition(mCurrentPage);
            } else {
                if (mNextPage < getChildCount() - 1) {
                    mPrevDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage);
                    mCurrentDropTarget = (AppsCustomizeCellLayout) getPageAt(mNextPage + 1);
                    // Rollback the prev screen realtime reorder.
                    rollbackLastScreenItemsPosition(mNextPage);
                }
            }
        }

        super.scrollRight();
    }

    /**
     * M: In edit mode, drag an icon into scroll area, will snap to next page.
     */
    @Override
    public boolean onEnterScrollArea(int x, int y, int direction) {
        boolean result = false;
        mInScrollArea = true;
        int page = getNextPage() + (direction == DragController.SCROLL_LEFT ? -1 : 1);
        final int childCount = getChildCount();

        // Make it can scroll circle when dragging app icon in all apps list.
        if (isSupportCycleSlidingScreen()) {
            if (direction == DragController.SCROLL_RIGHT && page == childCount) {
                page = 0;
            } else if (direction == DragController.SCROLL_LEFT && page == -1) {
                page = childCount - 1;
            }
        }

        // We always want to exit the current layout to ensure parity of enter / exit
        setCurrentDropLayout(null);
        if (0 <= page && page < childCount) {
            result = true;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onEnterScrollArea: page = " + page + ", result = " + result);
        }

        return result;
    }

    /**
     * M: In edit mode, when drag an icon leave scroll area.
     */
    @Override
    public boolean onExitScrollArea() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "onExitScrollArea: mInScrollArea = " + mInScrollArea);
        }
        boolean result = false;
        if (mInScrollArea) {
            invalidate();
            AppsCustomizeCellLayout layout = getCurrentDropLayout();
            setCurrentDropLayout(layout);
            result = true;
            mInScrollArea = false;
        }
        return result;
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * M: Set current drop layout.
     *
     * @param layout
     */
    void setCurrentDropLayout(AppsCustomizeCellLayout layout) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "setCurrentDropLayout layout = " + layout + ", mDragTargetLayout = "
                    + mDragTargetLayout);
        }
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragExit();
        }

        mDragTargetLayout = layout;

        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
    }

    /**
     * M: Return the current {@link CellLayout}, correctly picking the
     * destination screen while a scroll is in progress.
     */
    public AppsCustomizeCellLayout getCurrentDropLayout() {
        return (AppsCustomizeCellLayout) getPageAt(getNextPage());
    }

    /**
     * M: Returns a list of all the CellLayouts in the
     * AppsCustomizePagedView.
     */
    private ArrayList<AppsCustomizeCellLayout> getAppsCustomizeCellLayouts() {
        ArrayList<AppsCustomizeCellLayout> layouts = new ArrayList<AppsCustomizeCellLayout>();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            layouts.add(((AppsCustomizeCellLayout) getChildAt(i)));
        }
        return layouts;
    }

    /**
     * M: Returns a specific CellLayout.
     */
    private AppsCustomizeCellLayout getParentAppsCustomizeCellLayoutForView(final View v) {
        // TODO: shall we use tag to record the parent info(such as screen
        // index) of MTKAppIcon instead of search.
        ArrayList<AppsCustomizeCellLayout> layouts = getAppsCustomizeCellLayouts();
        for (AppsCustomizeCellLayout layout : layouts) {
            int index = layout.indexOfChildOnPage(v);
            if (index > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * M: Add a view in the screen.
     *
     * @param child: The view will be added.
     * @param screen: The screen which the view will be added to.
     * @param x: The x position the view will be placed.
     * @param y: Tye y position the view will be placed.
     * @param spanX
     * @param spanY
     * @param insert
     */
    private void addInScreen(View child, int screen, int x, int y, int spanX, int spanY, boolean insert) {
        final AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(screen);
        child.setOnKeyListener(new IconKeyEventListener());

        ViewGroup.LayoutParams genericLp = child.getLayoutParams();
        CellLayout.LayoutParams lp;
        if (genericLp == null || !(genericLp instanceof CellLayout.LayoutParams)) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp = (CellLayout.LayoutParams) genericLp;
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        int childId = lp.cellY * mCellCountX + lp.cellX;

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addInScreen: child = " + child + ", childInfo = " + child.getTag()
                    + ", screen = " + screen + ", x = " + x + ", y = " + y + ", childId = "
                    + childId);
        }

        if (!layout.addViewToCellLayout(child, insert ? 0 : -1, childId, lp, true)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.w(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY
                    + ") to AppsCustomizeCellLayout");
        }
    }

    /**
     * M: This is used to compute the visual center of the dragView. This point
     * is then used to visualize drop locations and determine where to drop an
     * item. The idea is that the visual center represents the user's
     * interpretation of where the item is, and hence is the appropriate point
     * to use when determining drop location, merge from Workspace.
     *
     * @param x
     * @param y
     * @param xOffset
     * @param yOffset
     * @param dragView
     * @param recycle
     * @return
     */
    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
            DragView dragView, float[] recycle) {
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        // First off, the drag view has been shifted in a way that is not
        // represented in the x and y values or the x/yOffsets. Here we account
        // for that shift.
        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // These represent the visual top and left of drag view if a dragRect
        // was provided.
        // If a dragRect was not provided, then they correspond to the actual
        // view left and top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    /**
     * M:Convert the 2D coordinate xy from the parent View's coordinate space to
     * this CellLayout's coordinate space. The argument xy is modified with the
     * return result, merge from Workspace.
     *
     * @param v
     * @param xy
     * @param cachedInverseMatrix
     */
    private void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
        if (cachedInverseMatrix == null) {
            v.getMatrix().invert(mTempInverseMatrix);
            cachedInverseMatrix = mTempInverseMatrix;
        }
        int scrollX = getScrollX();
        if (mNextPage != INVALID_PAGE) {
            scrollX = mScroller.getFinalX();
        }
        xy[0] = xy[0] + scrollX - v.getLeft();
        xy[1] = xy[1] + getScrollY() - v.getTop();
        cachedInverseMatrix.mapPoints(xy);
    }

    /**
     * M: Calculate the nearest cell where the given object would be dropped.
     * pixelX and pixelY should be in the coordinate system of layout.
     */
    private int[] findNearestArea(int pixelX, int pixelY, int spanX, int spanY,
            CellLayout layout, int[] recycle) {
        return layout.findNearestArea(pixelX, pixelY, spanX, spanY, recycle);
    }

    /**
     * M: Update item position after drop.
     *
     * @param layout
     * @param cell
     * @param screen
     */
    private void updateItemPositionAfterDrop(final CellLayout layout, final View cell,
            final int screen) {
        // update the item's position after drop.
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell
                .getLayoutParams();
        lp.cellX = mTargetCell[0];
        lp.cellY = mTargetCell[1];
        lp.cellHSpan = 1;
        lp.cellVSpan = 1;

        final int childId = lp.cellY * mCellCountX + lp.cellX;
        cell.setId(childId);

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemPositionAfterDrop: layout = " + layout + ", cell = "
                    + cell + ", screen = " + screen + ", childId = " + childId);
        }

        // add the view in the screen.
        addInScreen(cell, screen, mTargetCell[0], mTargetCell[1], 1, 1, false);

        // modify app position.
        modifyAppPosition(cell, screen, lp.cellX, lp.cellY);

        // If the page is full, drop the icon from another page, the last cell
        // in the page will be placed the first position in the next page.
        if (mLastCell != null) {
            processLastCellInFullPage(screen);
        }

        // Update all apps position in the page after realTimeReorder.
        updateItemLocationsInDatabase(layout);

        // If the page is the last empty page, automatically add new empty page.
        if (screen == sAllAppsPage.size() - 1
                && sAllAppsPage.get(screen).getAppsCount() - 1 == 0) {
            addNewAppsPageLayout();
            invalidatePageData(screen);
        }
    }

    /**
     * M: Recorder the last cell in full page.
     *
     * @param layout
     * @param dragFromOtherScreen
     */
    private void saveLastCellInFullPage(final CellLayout layout,
            final boolean dragFromOtherScreen) {
        if (mLastCell != null) {
            LauncherLog.i(TAG, "saveLastCellInFullPage mLastCell = " + mLastCell);
            return;
        }

        final int page = indexToPage(indexOfChild(layout));
        final PageInfo pageInfo = sAllAppsPage.get(page);
        final int childCount = layout.getChildrenLayout().getChildCount();
        int index = childCount;

        // If the page is full, recorder the last cell info.
        if (pageInfo.isFull()) {
            index = childCount - 1;
            mLastCell = (PagedViewIcon) layout.getChildAt(mCellCountX - 1, mCellCountY - 1);
            final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mLastCell
                    .getLayoutParams();
            mLastCellInfo.cell = mLastCell;
            mLastCellInfo.cellX = lp.cellX;
            mLastCellInfo.cellY = lp.cellY;
            mLastCellInfo.pos = mLastCellInfo.cellY * mCellCountX + mLastCellInfo.cellX;
            // If the page is full, the last cell will be invisible temporarily.
            mLastCell.setVisibility(INVISIBLE);

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "saveLastCellInFullpage page = " + page + ", mLastCell = "
                        + mLastCell + ", mLastCellInfo = " + mLastCellInfo);
            }
        }

        // If drag icon from another page, the last cell in the page will be the
        // empty cell.
        if (dragFromOtherScreen && (mEmptyCell[0] == -1 && mEmptyCell[1] == -1)) {
            mEmptyCell[0] = index % mCellCountX;
            mEmptyCell[1] = index / mCellCountX;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "saveLastCellInFullpage mEmptyCell[0] = " + mEmptyCell[0]
                    + ", mEmptyCell[1] = " + mEmptyCell[1]);
        }
    }

    /**
     * M: The last view in full page will be placed in the first position in the
     * next page.
     *
     * @param screen
     */
    private void processLastCellInFullPage(final int screen) {
        final int childCount = getChildCount();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processLastCellInFullPage: screen = " + screen + ",childCount = "
                    + childCount);
        }

        final int[] firstCell = new int[2];
        final int[] emptyCell = new int[2];
        AppsCustomizeCellLayout layout = null;

        for (int i = screen + 1; i < childCount; i++) {
            mPrevLastCell = mLastCell;
            firstCell[0] = 0;
            firstCell[1] = 0;

            layout = (AppsCustomizeCellLayout) getPageAt(i);
            layout.getLastPosition(emptyCell);

            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "processLastCellInFullPage: mPrevLastCell = " + mPrevLastCell
                        + ",emptyCell = (" + emptyCell[0] + ", " + emptyCell[1] + ").");
            }

            final boolean isFull = sAllAppsPage.get(i).isFull();
            if (isFull) {
                mLastCell = (PagedViewIcon) layout.getChildAt(mCellCountX - 1, mCellCountY - 1);
                final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) mLastCell
                        .getLayoutParams();
                mLastCellInfo.cell = mLastCell;
                mLastCellInfo.cellX = lp.cellX;
                mLastCellInfo.cellY = lp.cellY;
                mLastCellInfo.pos = mLastCellInfo.cellY * mCellCountX + mLastCellInfo.cellX;
                mLastCell.setVisibility(INVISIBLE);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "processLastCellInFullPage: mLastCell = " + mLastCell
                            + ", mLastCellInfo = " + mLastCellInfo);
                }
            }

            realTimeReorder(emptyCell, firstCell, layout);

            // Remove the last cell from the prev page.
            getParentAppsCustomizeCellLayoutForView(mPrevLastCell).removeChildView(mPrevLastCell);
            AppInfo info = (AppInfo) mPrevLastCell.getTag();

            // Display the last cell.
            mPrevLastCell.setVisibility(VISIBLE);

            // Add the last cell in the cur page.
            addInScreen(mPrevLastCell, i, firstCell[0], firstCell[1], 1, 1, false);
            modifyAppPosition(mPrevLastCell, i, firstCell[0], firstCell[1]);
            updateItemLocationsInDatabase(layout);

            if (!isFull) {
                // Clear the last cell info.
                mPrevLastCell = null;
                mLastCell = null;
                break;
            }
        }

        // If no empty position, add an new empty page to place the last view.
        if (mPrevLastCell != null) {
            int newPage = addNewAppsPageLayout();
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "processLastCellInFullPage: newPage = "
                        + newPage + ", mPrevLastCell = " + mPrevLastCell);
            }

            // Place the app in the new page.
            addAppToNewPage((AppInfo) mPrevLastCell.getTag(), newPage);
        }
    }

    /**
     * M: Modify app position.
     *
     * @param v: The app which position has changed.
     * @param screen: The screen that the app will be added to.
     * @param cellX: The cellX that the app will be placed.
     * @param cellY: The cellY that the app will be placed.
     */
    private void modifyAppPosition(final View v, final int screen, final int cellX, final int cellY) {
        AppInfo info = (AppInfo) v.getTag();
        info.cellX = cellX;
        info.cellY = cellY;
        long prevScreen = info.screenId;
        int prevPos = info.pos;
        int pos = cellY * mCellCountX + cellX;

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "modifyAppPositioin v = " + v + ", info = " + info + ", screen = "
                    + screen + ", cellX = " + cellX + ", cellY = " + cellY + ", prevScreen = "
                    + prevScreen + ", prevPos = " + prevPos + ", pos = " + pos);
        }

        if (prevScreen != screen) {
            PageInfo prevPage = sAllAppsPage.get((int)prevScreen);
            prevPage.remove(info);
        }

        // Add the app in the current page.
        PageInfo curPage = sAllAppsPage.get((int)screen);
        info.screenId = screen;
        info.pos = pos;
        curPage.add(info);
    }

    /**
     * M: Update all items info in database.
     *
     * @param cl
     * @param useLayoutParams
     */
    void updateItemLocationsInDatabase(final CellLayout cl) {
        if (cl == null) {
            LauncherLog.e(TAG, "updateItemLocationsInDatabase cl == null!");
            return;
        }
        final LauncherModel model = mLauncher.getModel();
        final int count = cl.getChildrenLayout().getChildCount();

        int screen = indexToPage(indexOfChild(cl));
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase cl = " + cl + ", screen = " + screen);
        }

        AppInfo info = null;
        View v = null;
        for (int i = 0; i < count; i++) {
            v = cl.getChildOnPageAt(i);
            info = (AppInfo) v.getTag();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
            // Null check required as the AllApps button doesn't have an item info.
            if (info != null) {
                model.moveAllAppsItemInDatabase(mLauncher, info, screen, lp.cellX, lp.cellY);
            }
        }
    }

    /**
     * M: Update all items info in database.
     *
     * @param page
     * @param pageInfo
     */
    void updateItemLocationsInDatabase(final int page, final PageInfo pageInfo) {
        final LauncherModel model = mLauncher.getModel();
        final int count = pageInfo.getAppsCount();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase: page = " + page + ",pageInfo = " + pageInfo);
        }

        AppInfo info = null;
        for (int i = 0; i < count; i++) {
            info = pageInfo.get(i);
            // Null check required as the AllApps button doesn't have an item info.
            if (info != null) {
                model.moveAllAppsItemInDatabase(mLauncher, info, page, info.cellX, info.cellY);
            }
        }
    }

    /**
     * M: Update the app info in database.
     *
     * @param info: The app info which will be updated in database.
     */
    private void updateItemInDatabase(final AppInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "updateItemLocationsInDatabase: info = " + info);
        }

        final LauncherModel model = mLauncher.getModel();
        if (info != null) {
            model.moveAllAppsItemInDatabase(mLauncher, info, (int) info.screenId, info.cellX, info.cellY);
        }
    }

    /**
     * M: Add an item in database.
     *
     * @param info: The app info which will be added.
     */
    private void addItemInDatabase(final AppInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addItemInDatabase: info = " + info);
        }
        final LauncherModel model = mLauncher.getModel();
        if (info != null) {
            model.addAllAppsItemToDatabase(mLauncher, info, (int) info.screenId, info.cellX, info.cellY,
                    false);
        }
    }

    /**
     * M: Delete an item from database.
     *
     * @param info: The app info which will be removed.
     */
    private void deleteItemInDatabase(final AppInfo info) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "deleteItemInDatabase: info = " + info);
        }
        LauncherModel model = mLauncher.getModel();
        if (info != null) {
            model.deleteAllAppsItemFromDatabase(mLauncher, info);
        }
    }

    /**
     * M: The app is system app or not.
     *
     * @param info
     * @return
     */
    public boolean isSystemApp(final AppInfo info) {
        final Intent intent = info.intent;
        final PackageManager pm = mLauncher.getPackageManager();
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        if (resolveInfo != null
                && (resolveInfo.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }
        return false;
    }

    /**
     * M: Rollback the last screen realtime reorder.
     *
     * @param page
     */
    private void rollbackLastScreenItemsPosition(final int page) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "rollbackLastScreenItemsPosition: page = " + page
                    + ", occuredReorder = " + mOccuredReorder);
        }
        // If the last screen doesn't realtime reorder, do not rollback.
        if (!mOccuredReorder) {
            return;
        }

        if (page != -1) {
            AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);
            PageInfo pageInfo = sAllAppsPage.get(page);
            mPrevEmptyCell[0] = mTargetCell[0];
            mPrevEmptyCell[1] = mTargetCell[1];
            int childCount = layout.getChildrenLayout().getChildCount();
            int index = childCount;

            if (pageInfo.isFull() && mLastCell != null) {
                index = childCount - 1;
                mPrevLastCell = mLastCell;
                mPrevLastCell.setVisibility(VISIBLE);
                mPrevLastCell = null;
                mLastCell = null;
            }
            mPrevTargetCell[0] = index % mCellCountX;
            mPrevTargetCell[1] = index / mCellCountX;

            realTimeReorder(mPrevEmptyCell, mPrevTargetCell, layout);

            mPrevEmptyCell[0] = -1;
            mPrevEmptyCell[1] = -1;

            mPrevTargetCell[0] = -1;
            mPrevTargetCell[1] = -1;

            updateItemLocationsInDatabase(layout);

            mOccuredReorder = false;
        }
    }

    /**
     * M: When receive PACKAGE_CHANGED, PACKAGE_ADDED, PACKAGE_REMOVED, notify
     * HideAppsActivity, all apps data has changed.
     */
    private void notifyPageListChanged() {
        final Intent intent = new Intent(HideAppsActivity.ACTION_PACKAGE_CHANGED);
        mLauncher.sendBroadcast(intent);
    }

    /**
     * M: Init all apps for each page.
     */
    private void initAllAppsPage() {
        if (sAllAppsPage.size() > 0) {
            sAllAppsPage.clear();
        }

        //Get the number of apps page.
        if (mNumAppsPages <= 0) {
            mNumAppsPages = LauncherModel.sMaxAppsPageIndex + 1;
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "initAllAppsPage mNumAppsPages = " + mNumAppsPages);
        }

        // Create new pages.
        for (int i = 0; i < mNumAppsPages; i++) {
            final PageInfo pageInfo = new PageInfo();
            sAllAppsPage.add(pageInfo);
        }

        /// M: Init all apps in all apps pages.
        for (int i = 0; i < mApps.size(); i++) {
            AppInfo info = mApps.get(i);
            long screen = info.screenId;
            if (info.screenId != -1 && info.isVisible) {
                PageInfo pageInfo = sAllAppsPage.get((int)screen);
                pageInfo.add(info);
            }
        }

        HashSet<Integer> hidePages = new HashSet<Integer>();

        final int pageCount = sAllAppsPage.size();
        for (int i = 0; i < pageCount; i++) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                hidePages.add(i);
            }
        }

        //Some pages will be removed, update other pages location in the all apps page.
        if (hidePages.size() > 0) {
            updatePagesWhenNeedToRemove(hidePages);
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "initAllAppsPage end: mNumAppsPages = " + mNumAppsPages
                    + ",sAllAppsPage size = " + sAllAppsPage.size() + ",hidePages = " + hidePages);
        }
    }

    /**
     * M: Find first empty position to place the app.
     *
     * @param info the application info.
     * @return The page index with first empty cell, -1 if no empty cell exists.
     */
    private int findFirstEmptyPosition(final AppInfo info) {
        final int pageCount = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "findFirstEmptyPosition: info = " + info + ",pageCount = "
                    + pageCount);
        }

        for (int i = 0; i < pageCount; i++) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            // If the page does not full, it means the page has empty cell.
            if (!pageInfo.isFull()) {
                // Decide where the app icon should be present here.
                fillPageInfoIntoAppInfo(info, pageInfo, i);
                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "Find empty cell for " + info);
                }
                return i;
            }
        }

        return -1;
    }

    /**
     * M: Find first empty position to place the app.
     *
     * @param info the application info.
     * @return The page index with first empty cell, -1 if no empty cell exists.
     */
    private int findFirstEmptyPositionFromLastPage(final AppInfo info) {
        final int pageCount = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "findFirstEmptyPosition: info = " + info + ",pageCount = "
                    + pageCount);
        }

        final PageInfo pageInfo = sAllAppsPage.get(pageCount - 1);
        // If the page does not full, it means the page has empty cell.
        if (!pageInfo.isFull()) {
            fillPageInfoIntoAppInfo(info, pageInfo, pageCount - 1);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "Find empty cell for " + info);
            }
            return (pageCount - 1);
        }
        return -1;
    }

    /**
     * M: Fill page info into app info.
     */
    private void fillPageInfoIntoAppInfo(final AppInfo appInfo, final PageInfo pageInfo, int screen) {
        // Calculate the position the apps will be placed.
        appInfo.pos = pageInfo.allApps.size();
        // Update the app info, cellX, cellY, screen, state.
        appInfo.cellX = appInfo.pos % mCellCountX;
        appInfo.cellY = appInfo.pos / mCellCountX;
        appInfo.screenId = screen;
    }

    /**
     * M: According to the page info, sync all apps icon in the page.
     *
     * @param pageInfo
     * @param page
     * @param immediate
     */
    public void syncAppsPageItems(final PageInfo pageInfo, final int page, final boolean immediate) {
        ArrayList<AppInfo> allApps = pageInfo.allApps;

        AppsCustomizeCellLayout layout = (AppsCustomizeCellLayout) getPageAt(page);

        layout.removeAllViewsOnPage();
        ArrayList<Object> items = new ArrayList<Object>();
        ArrayList<Bitmap> images = new ArrayList<Bitmap>();

        for (int i = 0; i < allApps.size(); ++i) {
            AppInfo info = allApps.get(i);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "syncAppsPageItems: i = " + i + ", info = " + info + ", page = "
                        + page);
            }
            if (info != null && info.isVisible) {
                PagedViewIcon icon = (PagedViewIcon) mLayoutInflater.inflate(
                        R.layout.apps_customize_application, layout, false);
                icon.applyFromApplicationInfo(info, true, this);
                icon.setOnClickListener(this);
                icon.setOnLongClickListener(this);
                icon.setOnTouchListener(this);
                icon.setOnKeyListener(this);
                // If in the edit mode, and the app is not system app,
                // display the "Delete" button in the left-top corner of the app
                // icon.
                if (Launcher.isInEditMode() && !isSystemApp(info)) {
                    icon.setDeleteButtonVisibility(true);
                } else {
                    icon.setDeleteButtonVisibility(false);
                }

                int x = info.pos % mCellCountX;
                int y = info.pos / mCellCountX;
                layout.addViewToCellLayout(icon, -1, info.pos,
                        new CellLayout.LayoutParams(x, y, 1, 1), false);

                if (LauncherLog.DEBUG_EDIT) {
                    LauncherLog.d(TAG, "syncAppsPageItems info = " + info + ", page = " + page);
                }

                items.add(info);
                images.add(info.iconBitmap);
            }
        }

        enableHwLayersOnVisiblePages();
    }

    /**
     * M: Add a new empty page layout.
     *
     * @return
     */
    private int addNewAppsPageLayout() {
        Context context = getContext();
        AppsCustomizeCellLayout layout = new AppsCustomizeCellLayout(context);
        setupPage(layout);
        addView(layout);

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addNewAppsPageLayout: mNumAppsPages = " + mNumAppsPages);
        }
        mDirtyPageContent.add(true);
        return addNewEmptyAppsPage();
    }

    /**
     * M: Add new page info in all apps tab.
     *
     * @return
     */
    private int addNewEmptyAppsPage() {
        PageInfo newPage = new PageInfo();
        newPage.allApps = new ArrayList<AppInfo>();
        sAllAppsPage.add(newPage);
        mNumAppsPages++;
        LauncherLog.d(TAG, "addNewEmptyAppsPage mNumAppsPages = " + mNumAppsPages, new Throwable());
        return sAllAppsPage.size() - 1;
    }

    /**
     * M: Remove all empty pages in apps customize pane, empty page means there
     * is no item in the page.
     */
    private void removeEmptyPages() {
        final int pageCount = sAllAppsPage.size();

        for (int i = pageCount - 1; i >= 0; i--) {
            final PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                sAllAppsPage.remove(i);
                if (i < mCurrentPage) {
                    mCurrentPage--;
                }
                mNumAppsPages--;
            }
        }

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "removeEmptyPages: mNumAppsPages = " + mNumAppsPages);
        }
        updateAllAppsPage();
    }

    /**
     * M: Add an app to new page.
     *
     * @param appInfo: The app will be added.
     * @param page: The page that the app will be added to.
     */
    private void addAppToNewPage(final AppInfo appInfo, final int page) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "addAppToNewPage: appInfo = " + appInfo + ", page = " + page);
        }
        ArrayList<AppInfo> newPage = sAllAppsPage.get(page).allApps;
        appInfo.screenId = page;
        appInfo.pos = newPage.size();
        appInfo.cellX = appInfo.pos % mCellCountX;
        appInfo.cellY = appInfo.pos / mCellCountX;
        newPage.add(appInfo);
    }

    /**
     * M: Remove uninstall, disabled apps.
     *
     * @param list: The apps list which will be removed.
     */
    private void removeDisabledApps(ArrayList<AppInfo> list) {
        hideOrRemoveApps(list, true);
    }

    /**
     * M: Find the app in the specified application info arraylist.
     *
     * @param allAppsInfo: The array list used to found the app.
     * @param appInfo: The specified app info which will be found.
     * @return
     */
    private AppInfo findApp(final ArrayList<AppInfo> allAppsInfo,
            final AppInfo appInfo) {
        final int appsCount = allAppsInfo.size();
        final ComponentName componentName = appInfo.componentName;
        for (int i = 0; i < appsCount; i++) {
            AppInfo info = allAppsInfo.get(i);
            if (info.componentName.equals(componentName)) {
                return appInfo;
            }
        }
        return null;
    }

    /**
     * M: When receive PACKAGE_ADDED.
     *
     * @param list: The list of apps added.
     */
    private void addAddedApps(final ArrayList<AppInfo> list) {
        final int length = list.size();
        for (int i = 0; i < length; i++) {
            AppInfo info = list.get(i);

            final int appIndex = findActivity(mApps, info.componentName);
            if (LauncherLog.DEBUG_EDIT) {
                LauncherLog.d(TAG, "addAddedApps: info = " + info + ",appIndex = " + appIndex + ",componentName = "
                        + info.componentName);
            }

            // Avoid adding the same application to all apps list repeatly.
            if (appIndex >= 0) {
                AppInfo origApp = mApps.get(appIndex);
                if (origApp.screenId >= 0) {
                    LauncherLog.i(TAG, "App " + origApp + " already exists in all apps list!");
                    return;
                } else {
                    LauncherLog.i(TAG, "App " + origApp + " is in the pending added list!");
                    mApps.remove(origApp);
                }
            }

            info.isVisible = true;
            mApps.add(mApps.size(), info);

            addApp(info);
            addItemInDatabase(info);
        }
    }

    /**
     * M: Find app in apps, return the position in the list.
     *
     * @param apps
     * @param component
     * @return
     */
    private int findActivity(ArrayList<AppInfo> apps, ComponentName component) {
        final int N = apps.size();
        for (int i = 0; i < N; i++) {
            final AppInfo info = apps.get(i);
            if (info.componentName.equals(component)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * M: Show apps which state changed from hide to show.
     *
     * @param showAppsList The apps list which are to be shown.
     */
    private void showApps(final ArrayList<AppInfo> showAppsList) {
        final int appsCount = showAppsList.size();
        for (int i = 0; i < appsCount; i++) {
            final AppInfo appInfo = showAppsList.get(i);
            showApp(appInfo);
            // If the app is in HideAndDiabled arraylist, add an item in
            // database
            updateItemInDatabase(appInfo);
        }
    }

    /**
     * M: Show an app.
     *
     * @param info: The app info which will be shown.
     */
    private void showApp(final AppInfo info) {
        final long prevScreen = info.screenId;

        // Find first empty position to place the app.
        int page = findFirstEmptyPosition(info);
        // If do not find an empty position, add new empty page.
        if (page == -1) {
            int newPage = addNewAppsPageLayout();
            // Place the app in the new page.
            addAppToNewPage(info, newPage);
        } else {
            addApp(info, page);
        }
    }

    /**
     * M: Add an app.
     *
     * @param info: The app info which will be added.
     */
    private void addApp(final AppInfo info) {
        // Find first empty position to place the app.
        int page = -1;
        final ComponentName component = info.intent.getComponent();
        if (component.getPackageName().equals(STK_PACKAGE_NAME)
                && (component.getClassName().equals(STK_CLASS_NAME) || component.equals(STK2_CLASS_NAME))) {
            page = findFirstEmptyPositionFromLastPage(info);
        } else {
            page = findFirstEmptyPosition(info);
        }

        // If do not find an empty position, add new empty page.
        if (page == -1) {
            int newPage = addNewAppsPageLayout();
            // Place the app in the new page.
            addAppToNewPage(info, newPage);
        } else {
            addApp(info, page);
        }
    }

    /**
     * M: Add an app.
     *
     * @param info: The app which will be added.
     * @param page: The page which the app will be added to.
     */
    private void addApp(AppInfo info, long page) {
        sAllAppsPage.get((int)page).add(info);
    }

    /**
     * M: Back from HideAppsActivity, process the state change of apps.
     *
     * @param pages: All pages which state has changed.
     */
    public void processAppsStateChanged() {
        final int count = sShowAndHideApps.size();
        if (count == 0) {
            LauncherLog.i(TAG, "processAppsStateChanged with no state changed apps.");
            return;
        }

        // Used to recorder all apps which will be hidden.
        ArrayList<AppInfo> hideApps = new ArrayList<AppInfo>();
        // Used to recorder app apps which will be shown.
        ArrayList<AppInfo> showApps = new ArrayList<AppInfo>();

        for (int i = 0; i < count; i++) {
            AppInfo appInfo = sShowAndHideApps.get(i);
            if (appInfo.isVisible) {
                showApps.add(appInfo);
            } else {
                hideApps.add(appInfo);
            }
        }

        sShowAndHideApps.clear();

        // Hide apps.
        if (hideApps.size() > 0) {
            hideOrRemoveApps(hideApps, false);
        }

        // Show apps.
        if (showApps.size() > 0) {
            showApps(showApps);
        }

        // Remove all hide pages.
        removeHidePages();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processAppsStateChanged end: mNumAppsPages = "
                    + mNumAppsPages + ",mCurrentPage = " + mCurrentPage + ",showApps = " + showApps
                    + ",hideApps = " + hideApps);
        }

        invalidatePageData(mCurrentPage);

        // If the apps are hidden, the corresponding shortcuts in the homescreen
        // will be removed.
        if (hideApps.size() > 0) {
            mLauncher.getWorkspace().removeItemsByAppInfo(hideApps);
        }
    }

    /**
     * M: Process pending add apps.
     *
     * @param list
     */
    public void processPendingAddApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingAddApps: list = " + list + ", this = " + this);
        }

        if (!mSupportEditAndHideApps) {
            addAppsWithoutInvalidate(list);
            reorderApps();
        } else {
            addAddedApps(list);
            notifyPageListChanged();
        }
    }

    /**
     * M: Process pending updated apps.
     *
     * @param list
     */
    public void processPendingUpdateApps(ArrayList<AppInfo> list) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingUpdateApps: list = " + list + ", this = " + this);
        }

        if (!mSupportEditAndHideApps) {
            removeAppsWithoutInvalidate(list);
            addAppsWithoutInvalidate(list);
            reorderApps();
        }
    }

    /**
     * M: Process pending removed apps.
     *
     * @param packageNames
     */
    public void processPendingRemoveApps(ArrayList<String> packageNames) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingRemoveApps: packageNames = " + packageNames + ", this = " + this);
        }

        removeAppsWithPackageNameWithoutInvalidate(packageNames);

        if (!mSupportEditAndHideApps) {
            reorderApps();
        } else {
            removeDisabledApps(sRemovedApps);
            notifyPageListChanged();
            sRemovedApps.clear();
        }
    }

    /**
     * M: Update page count and invalidate data after handle pending add/update/remove apps.
     */
    public void processPendingPost() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingPost: this = " + this);
        }

        updatePageCounts();
        invalidateOnDataChange();
    }

    /**
     * M: Hide or remove some apps.
     *
     * @param apps: The apps will be removed or hidden.
     * @param isRemoved: True removed, false hidden.
     */
    private void hideOrRemoveApps(ArrayList<AppInfo> apps, boolean isRemoved) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "hideOrRemoveApps: apps = " + apps + ",isRemoved = " + isRemoved);
        }

        // Used to recorder all pages which apps state changed.
        ArrayList<Integer> pageAppsStateChanged = new ArrayList<Integer>();
        final int hideAppsCount = apps.size();

        for (int i = 0; i < hideAppsCount; i++) {
            AppInfo appInfo = apps.get(i);
            long page = appInfo.screenId;
            PageInfo pageInfo = sAllAppsPage.get((int)page);
            pageInfo.remove(appInfo);
            if (pageAppsStateChanged.indexOf(page) == -1) {
                pageAppsStateChanged.add((int)page);
            }
            if (isRemoved) {
                deleteItemInDatabase(appInfo);
            } else {
                updateItemInDatabase(appInfo);
            }
        }

        final int count = pageAppsStateChanged.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "hideOrRemoveApps middle: pageAppsStateChanged = " + pageAppsStateChanged);
        }

        for (int i = 0; i < count; i++) {
            final int page = pageAppsStateChanged.get(i);
            final PageInfo pageInfo = sAllAppsPage.get(page);
            final int appsCount = pageInfo.getAppsCount();
            for (int j = 0; j < appsCount; j++) {
                AppInfo info = pageInfo.get(j);
                info.pos = j;
                info.cellX = j % mCellCountX;
                info.cellY = j / mCellCountX;
            }
            if (appsCount > 0) {
                updateItemLocationsInDatabase(page, pageInfo);
            }
        }
    }

    /**
     * M: Find which apps will be hidden, which apps will be shown.
     *
     * @param allApps
     * @param hideApps
     * @param showApps
     * @param page
     */
    private void findHideAndShowApps(final ArrayList<AppInfo> allApps,
            final ArrayList<AppInfo> hideApps, final ArrayList<AppInfo> showApps,
            int page) {
        final int count = allApps.size();
        for (int i = 0; i < count; i++) {
            AppInfo info = allApps.get(i);
            if (page == info.screenId && info.stateChanged) {
                if (info.isVisible) {
                    info.isVisible = false;
                    hideApps.add(info);
                    // Update the other apps position if some apps will be hidden.
                    moveAppsPositionAfterHideApps(info);
                } else {
                    info.isVisible = true;
                    showApps.add(info);
                }
            }
        }
    }

    /**
     * M: Find which apps will be shown, which apps will be hidden.
     *
     * @param pageInfo
     * @param hideApps
     * @param showApps
     */
    private void findHideAndShowApps(final PageInfo pageInfo,
            final ArrayList<AppInfo> hideApps, final ArrayList<AppInfo> showApps) {
        final ArrayList<AppInfo> allApps = pageInfo.allApps;
        final int appSize = allApps.size();
        for (int i = 0; i < appSize; i++) {
            final AppInfo info = allApps.get(i);
            if (info.stateChanged) {
                // If state has changed and the current application icon is
                // visible, then change it to invisible.
                if (info.isVisible) {
                    info.isVisible = false;
                    hideApps.add(info);
                    // Update the other apps position if some apps will be hidden.
                    moveAppsPositionAfterHideApps(info);
                } else {
                    info.isVisible = true;
                    showApps.add(info);
                }
            }
        }
    }

    /**
     * M: All apps behind the hidden apps will move forward one position.
     *
     * @param hideAppInfo
     */
    private void moveAppsPositionAfterHideApps(final AppInfo hideAppInfo) {
        final long page = hideAppInfo.screenId;
        final int pos = hideAppInfo.pos;

        final PageInfo pageInfo = sAllAppsPage.get((int)page);
        final ArrayList<AppInfo> allApps = pageInfo.allApps;
        final int childCount = allApps.size();

        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "moveAppsPositionAfterHideApps: page = " + page + ",pos = " + pos
                    + ",hideAppInfo = " + hideAppInfo + ",childCount = " + childCount);
        }

        for (int i = 0; i < childCount; i++) {
            final AppInfo appInfo = allApps.get(i);
            if (appInfo.pos > pos) {
                appInfo.pos--;
                appInfo.cellX = appInfo.pos % mCellCountX;
                appInfo.cellY = appInfo.pos / mCellCountX;
                // Update app info in database which position has changed.
                updateItemInDatabase(appInfo);
            }
        }
        // Update the hideAppInfo in database.
        updateItemInDatabase(hideAppInfo);
    }

    /**
     * M: If all apps in the page are be removed or hidden, the page will be
     * removed.
     *
     * @param pages
     */
    private void updatePagesWhenNeedToRemove(final HashSet<Integer> pages) {
        removeHidePage(pages);
        updateAllAppsPage();
    }

    /**
     * M: Get all hidden pages and remove them in all apps list.
     */
    private void removeHidePages() {
        // Used to recorder all pages which will be removed.
        HashSet<Integer> pageNeedToRemove = new HashSet<Integer>();
        final int count = sAllAppsPage.size();
        for (int i = 0; i < count; i++) {
            PageInfo pageInfo = sAllAppsPage.get(i);
            if (pageInfo.isEmpty()) {
                pageNeedToRemove.add(i);
            }
        }

        // If some pages will be removed, adjust other pages position in all
        // apps pages.
        if (pageNeedToRemove.size() > 0) {
            updatePagesWhenNeedToRemove(pageNeedToRemove);
        }

        // If the current page becomes widget page because we hide some pages,
        // set the current page to the last apps page instead.
        if (mCurrentPage >= mNumAppsPages) {
            setCurrentPage(mNumAppsPages - 1);
        }
    }

    /**
     * M: Remove all hidden pages from all apps pages.
     */
    private void removeHidePage(final HashSet<Integer> hidePages) {
        final int hidePageSize = hidePages.size();
        final int allAppsPageSize = sAllAppsPage.size();
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "removeHidePage: hidePages = " + hidePages + ",allAppsPageSize = "
                    + allAppsPageSize + ",mNumAppsPages = " + mNumAppsPages + ",sAllAppsPage = "
                    + sAllAppsPage);
        }

        for (int i = allAppsPageSize - 1; i >= 0; i--) {
            PageInfo pageInfo = sAllAppsPage.get(i);
            AppInfo firstInfo = null;
            if (!pageInfo.isEmpty()) {
                firstInfo = pageInfo.allApps.get(0);
            }

            if (pageInfo.isEmpty() || hidePages.contains(firstInfo.screenId)) {
                sAllAppsPage.remove(i);
                // Descrease the number of apps pages.
                mNumAppsPages--;
            }
        }
        LauncherLog.d(TAG, "removeHidePage mNumAppsPages = " + mNumAppsPages, new Throwable());
    }

    /**
     * M: Update other pages position in all apps apges after remove the hidden
     * pages.
     */
    private void updateAllAppsPage() {
        final int pageSize = sAllAppsPage.size();
        // If all pages are removed, add an new empty page.
        if (pageSize == 0) {
            addNewAppsPageLayout();
        }

        // Update the screen of application info since some pages may be removed.
        for (int i = 0; i < pageSize; i++) {
            PageInfo pageInfo = sAllAppsPage.get(i);
            ArrayList<AppInfo> allApps = pageInfo.allApps;
            final int appCount = allApps.size();
            for (int j = 0; j < appCount; j++) {
                AppInfo info = allApps.get(j);
                info.screenId = i;
                updateItemInDatabase(info);
            }
        }
    }
    
    /**
     * M: Update unread number of the given component in app customize paged view
     * with the given value, first find the icon, and then update the number.
     * NOTES: since maybe not all applications are added in the customize paged
     * view, we should update the apps info at the same time.
     *
     * @param component
     * @param unreadNum
     */
    public void updateAppsUnreadChanged(ComponentName component, int unreadNum) {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: component = " + component
                    + ",unreadNum = " + unreadNum + ",mNumAppsPages = " + mNumAppsPages);
        }
        updateUnreadNumInAppInfo(component, unreadNum);
        if((isDataReady())&& (mContentType == ContentType.Applications)){
            for (int i = 0; i < mNumAppsPages; i++) {
                ShortcutAndWidgetContainer cl = ((AppsCustomizeCellLayout) getPageAt(i)).getShortcutsAndWidgets();
                if (cl == null) {
                    return;
                }
                final int count = cl.getChildCount();
                PagedViewIcon appIcon = null;
                AppInfo appInfo = null;
                for (int j = 0; j < count; j++) {
                    appIcon = (PagedViewIcon) cl.getChildAt(j);
                    appInfo = (AppInfo) appIcon.getTag();
                    if (LauncherLog.DEBUG_UNREAD) {
                        LauncherLog.d(TAG, "updateAppsUnreadChanged: component = " + component
                                + ", appInfo = " + appInfo.componentName + ", appIcon = " + appIcon);
                    }
                    if (appInfo != null && appInfo.componentName.equals(component)) {
                        appInfo.unreadNum = unreadNum;
                        appIcon.invalidate();
                    }
                }
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     */
    public void updateAppsUnread() {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "updateAppsUnreadChanged: mNumAppsPages = " + mNumAppsPages);
        }

        updateUnreadNumInAppInfo(mApps);
        if(isDataReady()){
            // Update apps which already shown in the customized pane.
            for (int i = 0; i < mNumAppsPages; i++) {
                ShortcutAndWidgetContainer cl = ((AppsCustomizeCellLayout) getPageAt(i)).getShortcutsAndWidgets();
                if (cl == null) {
                    return;
                }
                final int count = cl.getChildCount();
                PagedViewIcon appIcon = null;
                AppInfo appInfo = null;
                for (int j = 0; j < count; j++) {
                    appIcon = (PagedViewIcon) cl.getChildAt(j);
                    appInfo = (AppInfo) appIcon.getTag();
                    appInfo.unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(appInfo.componentName);
                    appIcon.invalidate();
                    if (LauncherLog.DEBUG_UNREAD) {
                        LauncherLog.d(TAG, "updateAppsUnreadChanged: i = " + i + ", appInfo = "
                                + appInfo.componentName + ", unreadNum = " + appInfo.unreadNum);
                    }
                }
            }
        }
    }

    /**
     * M: Update the unread number of the app info with given component.
     *
     * @param component
     * @param unreadNum
     */
    private void updateUnreadNumInAppInfo(ComponentName component, int unreadNum) {
        final int size = mApps.size();
        AppInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = mApps.get(i);
            if (appInfo.intent.getComponent().equals(component)) {
                appInfo.unreadNum = unreadNum;
            }
        }
    }

    /**
     * M: Update unread number of all application info with data in MTKUnreadLoader.
     *
     * @param apps
     */
    public static void updateUnreadNumInAppInfo(final ArrayList<AppInfo> apps) {
        final int size = apps.size();
        AppInfo appInfo = null;
        for (int i = 0; i < size; i++) {
            appInfo = apps.get(i);
            appInfo.unreadNum = MTKUnreadLoader.getUnreadNumberOfComponent(appInfo.componentName);
        }
    }

    /**
     * M: invalidate app page items.
     */
    void invalidateAppPages(int currentPage, boolean immediateAndOnly) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "invalidateAppPages: currentPage = " + currentPage + ", immediateAndOnly = " + immediateAndOnly);
        }
        invalidatePageData(currentPage, immediateAndOnly);
    }

    @Override
    protected float getScrollProgress(int screenCenter, View v, int page) {
        if (isSupportCycleSlidingScreen()) {
            final int halfScreenSize = getMeasuredWidth() / 2;
            final int childCount = getChildCount();
            final int firstCenter = halfScreenSize;
            final int lastCenter = getChildOffset(childCount - 1) + halfScreenSize;
            final int totalWidth = lastCenter + halfScreenSize;

            // Reset the screen center to make it in (-halfScreenSize, halfScreenSize) when the page is 0,
            // and in ((totalWidth - halfScreenSize), (totalWidth + halfScreenSize)) when the page is the last.
            if (page == 0) {
                if (screenCenter >= lastCenter) {
                    screenCenter -= totalWidth;
                }
            } else if (page == childCount - 1) {
                if (screenCenter <= firstCenter) {
                    screenCenter += totalWidth;
                }
            }
        }

        return super.getScrollProgress(screenCenter, v, page);
    }

    /**
     * M: Support cycle sliding screen or not.
     * @return true: support cycle sliding screen.
     */
    public boolean isSupportCycleSlidingScreen() {
        return mSupportCycleSliding;
    }

    /**
     * M: The class used to describe the information of each page in the all
     * apps list.
     */
    class PageInfo {
        /**
         * The arraylist used to recorder all apps info in the page.
         */
        ArrayList<AppInfo> allApps = new ArrayList<AppInfo>();

        PageInfo() {
        }

        /**
         * M: get an app info.
         */
        public AppInfo get(final int pos) {
            return allApps.get(pos);
        }

        /**
         * M: add an app info.
         *
         * @param appInfo: The app info will be added.
         */
        public void add(final AppInfo appInfo) {
            allApps.add(appInfo);
        }

        /**
         * M: remove an app from the specified pos.
         *
         * @param pos: The app in the pos will be removed.
         */
        public void remove(final int pos) {
            allApps.remove(pos);
        }

        /**
         * M: remove an app.
         *
         * @param appInfo
         */
        public void remove(final AppInfo appInfo) {
            final int pos = find(appInfo);
            if (pos != -1) {
                allApps.remove(pos);
            }
        }

        /**
         * M: find the specified app info.
         *
         * @param info: The app info will be found.
         * @return: If find, return the app info, if not found, return null.
         */
        public int find(final AppInfo info) {
            final ComponentName componentName = info.componentName;
            for (int i = 0; i < allApps.size(); i++) {
                AppInfo appInfo = allApps.get(i);
                if (appInfo.componentName.equals(componentName)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * M: if find, modify the value of the app info, if not found, add the app
         * info.
         *
         * @param info: The app info which value will be modified.
         */
        public void modify(final AppInfo info) {
            final int pos = find(info);
            if (pos != -1) {
                AppInfo appInfo = allApps.get(pos);
                appInfo = info;
            } else {
                allApps.add(info);
            }
        }

        /**
         * M: whether the page is full or not.
         *
         * @return True if the page is full, else false.
         */
        public boolean isFull() {
            return allApps.size() == mCellCountX * mCellCountY;
        }

        /**
         * M: whether the page is empty or not.
         *
         * @return True if the page is empty, else false.
         */
        public boolean isEmpty() {
            return allApps.size() == 0;
        }

        /**
         * M: the count of all apps in the page.
         *
         * @return
         */
        public int getAppsCount() {
            return allApps.size();
        }

        @Override
        public String toString() {
            if (isEmpty()) {
                return "Empty PageInfo";
            } else {
                return "PageInfo{ page = " + allApps.get(0).screenId + ", appSize = "
                        + allApps.size() + "}";
            }
        }
    }

    /**
     * M: Realtime reorder.
     *
     * @param empty
     * @param target
     * @param layout
     */
    private void realTimeReorder(int[] empty, int[] target, AppsCustomizeCellLayout layout) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "realTimeReorder: empty = (" + empty[0] + ", " + empty[1]
                    + "),target = (" + target[0] + ", " + target[1] + ").");
        }
        boolean wrap;
        int startX;
        int endX;
        int startY;
        int delay = 0;
        float delayAmount = 30;

        if (readingOrderGreaterThan(target, empty)) {
            wrap = empty[0] >= layout.getCountX() - 1;
            startY = wrap ? empty[1] + 1 : empty[1];
            for (int y = startY; y <= target[1]; y++) {
                startX = y == empty[1] ? empty[0] + 1 : 0;
                endX = y < target[1] ? layout.getCountX() - 1 : target[0];
                for (int x = startX; x <= endX; x++) {
                    View v = layout.getChildAt(x, y);
                    if (v != null
                            && layout.animateChildToPosition(v, empty[0], empty[1],
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                        empty[0] = x;
                        empty[1] = y;
                        delay += delayAmount;
                        delayAmount *= 0.9;
                        mOccuredReorder = true;
                    }
                }
            }
        } else {
            wrap = empty[0] == 0;
            startY = wrap ? empty[1] - 1 : empty[1];
            for (int y = startY; y >= target[1]; y--) {
                startX = y == empty[1] ? empty[0] - 1 : layout.getCountX() - 1;
                endX = y > target[1] ? 0 : target[0];
                for (int x = startX; x >= endX; x--) {
                    View v = layout.getChildAt(x, y);
                    if (v != null
                            && layout.animateChildToPosition(v, empty[0], empty[1],
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                        empty[0] = x;
                        empty[1] = y;
                        delay += delayAmount;
                        delayAmount *= 0.9;
                        mOccuredReorder = true;
                    }
                }
            }
        }
    }

    /**
     * M: Calculate the order, merge from Folder.
     *
     * @param v1
     * @param v2
     * @return
     */
    private boolean readingOrderGreaterThan(int[] v1, int[] v2) {
        if (v1[1] > v2[1] || (v1[1] == v2[1] && v1[0] > v2[0])) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * M: Set reorder alarm.
     */
    private final OnAlarmListener mReorderAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            realTimeReorder(mEmptyCell, mTargetCell, getCurrentDropLayout());
        }
    };
    /**
     * M: Support OP09 UnInstall App When click delete button In EditMode.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (LauncherLog.DEBUG_MOTION) {
            LauncherLog.d(TAG, "onTouch: v = " + v + ", event = " + event + ",this = " + this);
        }

        if (mSupportEditAndHideApps    /// M: Make sure is OP09.
                && v instanceof PagedViewIcon
                && ((PagedViewIcon) v).getDeleteButtonVisibility()    /// M: Make Sure deleteButton is shown.
                && event.getAction() == MotionEvent.ACTION_DOWN
                && touchInDeleteArea(event)) {
                mLauncher.onClickDeleteButton(v);
            return true;
        } else {
            return super.onTouch(v, event);
        }
    }

    /**
     * M: Support OP09 Check touch in Delete Area.
     */
    private boolean touchInDeleteArea(MotionEvent event) {
        int deleteButtonWidth = mDeleteButtonDrawable.getIntrinsicWidth();
        int deleteButtonHeight = mDeleteButtonDrawable.getIntrinsicHeight();
        int eventx = (int) event.getX();
        int eventy = (int) event.getY();

        if (eventx >= (mDeleteMarginleft - mTouchDelta)
                && eventx <= (mDeleteMarginleft + deleteButtonWidth + mTouchDelta)
                && eventy + mTouchDelta >= 0
                && eventy <= (deleteButtonHeight + mTouchDelta)) {
            return true;
        }
        return false;
    }

    /**
     * M: Find apps by package name.
     */
    private int findAppByPackage(List<AppInfo> list, String packageName) {
        int length = list.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = list.get(i);
            if (info.getPackageName().equals(packageName)) {
                /// M: we only remove items whose component is in disable state,
                /// this is add to deal the case that there are more than one
                /// activities with LAUNCHER category, and one of them is
                /// disabled may cause all activities removed from app list.
                final boolean isComponentEnabled = Utilities.isComponentEnabled(getContext(),
                        info.intent.getComponent());
                LauncherLog.d(TAG, "findAppByPackage: i = " + i + ",name = " + info.intent.getComponent() + ", info = "
                        + info + ",isComponentEnabled = " + isComponentEnabled);
                if (!isComponentEnabled) {
                    return i;
                } else {
                    /// M: we need to make restore the app info in data list in all apps list to make information sync.
                    mLauncher.getModel().restoreAppInAllAppsList(info);
                }
            }
        }
        return -1;
    }
    /// M: Add for Edit AllAppsList for op09 end.}@
}
