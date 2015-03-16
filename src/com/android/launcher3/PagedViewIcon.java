/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends TextView {
    /** A simple callback interface to allow a PagedViewIcon to notify when it has been pressed */
    public static interface PressedCallback {
        void iconPressed(PagedViewIcon icon);
    }

    @SuppressWarnings("unused")
    private static final String TAG = "PagedViewIcon";
    private static final float PRESS_ALPHA = 0.4f;

    private PagedViewIcon.PressedCallback mPressedCallback;
    private boolean mLockDrawableState = false;

    /// M: for OP09 DeleteButton.
    private boolean mSupportEditAndHideApps = false;
    private boolean mDeleteButtonVisiable = false;
    private Drawable mDeleteButtonDrawable = null;
    private int mDeleteMarginleft;

    private Bitmap mIcon;

    /// M: enable shadow
    private static final boolean ENABLE_SHADOW = false;

    public PagedViewIcon(Context context) {
        this(context, null);
    }

    public PagedViewIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        /// M: Add for edit and hide apps for op09.
        mSupportEditAndHideApps = LauncherExtPlugin.getInstance().getOperatorCheckerExt(context).supportEditAndHideApps();
        if (mSupportEditAndHideApps) {
            mDeleteButtonDrawable = context.getResources().getDrawable(R.drawable.ic_launcher_delete_holo);
            mDeleteMarginleft = (int) context.getResources().getDimension(R.dimen.apps_customize_delete_margin_left);
        }
    }

    public void onFinishInflate() {
        super.onFinishInflate();

        // Ensure we are using the right text size
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        /// M: Whether is tablet or use tablet solution, need keep textsize.
        if (grid.isTablet() || getResources().getBoolean(R.bool.allow_rotation)) {
            float fontSize = getContext().getResources().getDimensionPixelSize(
                    R.dimen.normal_text_size);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        } else {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.iconTextSize);
        }
    }

    public void applyFromApplicationInfo(AppInfo info, boolean scaleUp,
            PagedViewIcon.PressedCallback cb) {
        mIcon = info.iconBitmap;
        mPressedCallback = cb;
        setCompoundDrawables(null, Utilities.createIconDrawable(mIcon),
                null, null);
        setText(info.title);
        setTag(info);
    }

    public void lockDrawableState() {
        mLockDrawableState = true;
    }

    public void resetDrawableState() {
        mLockDrawableState = false;
        post(new Runnable() {
            @Override
            public void run() {
                refreshDrawableState();
            }
        });
    }

    protected void drawableStateChanged() {
        super.drawableStateChanged();

        // We keep in the pressed state until resetDrawableState() is called to reset the press
        // feedback
        if (isPressed()) {
            setAlpha(PRESS_ALPHA);
            if (mPressedCallback != null) {
                mPressedCallback.iconPressed(this);
            }
        } else if (!mLockDrawableState) {
            setAlpha(1f);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // If text is transparent, don't draw any shadow
        if (getCurrentTextColor() == getResources().getColor(android.R.color.transparent)) {
            getPaint().clearShadowLayer();
            super.draw(canvas);
            /// M: For feature unread draw.
            MTKUnreadLoader.drawUnreadEventIfNeed(canvas, this);
            return;
        }

        /// M: it may drop performance a lot
        if(ENABLE_SHADOW == true) {
            // We enhance the shadow by drawing the shadow twice
            getPaint().setShadowLayer(BubbleTextView.SHADOW_LARGE_RADIUS, 0.0f,
                    BubbleTextView.SHADOW_Y_OFFSET, BubbleTextView.SHADOW_LARGE_COLOUR);
            super.draw(canvas);
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            canvas.clipRect(getScrollX(), getScrollY() + getExtendedPaddingTop(),
                    getScrollX() + getWidth(),
                    getScrollY() + getHeight(), Region.Op.INTERSECT);
            getPaint().setShadowLayer(BubbleTextView.SHADOW_SMALL_RADIUS, 0.0f, 0.0f,
                    BubbleTextView.SHADOW_SMALL_COLOUR);
            super.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas);
        }

        /// M: For feature unread draw.
        MTKUnreadLoader.drawUnreadEventIfNeed(canvas, this);
        
        /// M: For op09 need draw delete Button.
        if (mSupportEditAndHideApps && mDeleteButtonVisiable) {
            int deleteButtonWidth = mDeleteButtonDrawable.getIntrinsicWidth();
            int deleteButtonHeight = mDeleteButtonDrawable.getIntrinsicHeight();
            int deleteButtonPosX = getScrollX() + mDeleteMarginleft;
            int deleteButtonPosY = getScrollY();

            Rect deleteButtonBounds = new Rect(0, 0, deleteButtonWidth, deleteButtonHeight);
            mDeleteButtonDrawable.setBounds(deleteButtonBounds);

            canvas.save();
            canvas.translate(deleteButtonPosX, deleteButtonPosY);

            mDeleteButtonDrawable.draw(canvas);

            canvas.restore();
        }
    }

    /// M: for OP09 DeleteButton.@{
    public void setDeleteButtonVisibility(boolean visiable) {
        mDeleteButtonVisiable = visiable;
    }

    public boolean getDeleteButtonVisibility() {
        return mDeleteButtonVisiable;
    }
    /// M: for OP09 DeleteButton.}@
}
