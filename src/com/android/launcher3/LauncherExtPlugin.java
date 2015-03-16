/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.android.launcher3;

import android.content.Context;

import com.mediatek.launcher3.ext.DefaultDataLoader;
import com.mediatek.launcher3.ext.DefaultOperatorChecker;
import com.mediatek.launcher3.ext.DefaultSearchButton;
import com.mediatek.launcher3.ext.IDataLoader;
import com.mediatek.launcher3.ext.IOperatorChecker;
import com.mediatek.launcher3.ext.ISearchButtonExt;
import com.mediatek.launcher3.ext.LauncherLog;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.PluginManager;

/**
 * M: LauncherExtPlugin class used to Get AllAppsListExt.
 */
public class LauncherExtPlugin {
    private ISearchButtonExt mSearchButtonExt = null;
    private IOperatorChecker mOperatorCheckerExt = null;
    private IDataLoader mDataLoadExt = null;
    private static LauncherExtPlugin sLauncherExtPluginInstance = new LauncherExtPlugin();

    public static LauncherExtPlugin getInstance() {
        return sLauncherExtPluginInstance;
    }

    public synchronized ISearchButtonExt getSearchButtonExt(final Context context) {
        if (mSearchButtonExt == null) {
            try {
                mSearchButtonExt = (ISearchButtonExt) PluginManager.createPluginObject(
                        context, ISearchButtonExt.class.getName());
            } catch (Plugin.ObjectCreationException e) {
                mSearchButtonExt = new DefaultSearchButton();
            }
        }
        LauncherLog.d("SearchButtonExt", "getSearchButtonExt: context = " + context
                + ", mSearchButtonExt = " + mSearchButtonExt);
        return mSearchButtonExt;
    }

    public synchronized IOperatorChecker getOperatorCheckerExt(final Context context) {
        if (mOperatorCheckerExt == null) {
            try {
                mOperatorCheckerExt = (IOperatorChecker) PluginManager.createPluginObject(context,
                        IOperatorChecker.class.getName());
            } catch (Plugin.ObjectCreationException e) {
                mOperatorCheckerExt = new DefaultOperatorChecker();
            }
        }
        LauncherLog.d("OperatorChecker", "getOperatorCheckerExt: context = " + context
                + ", mOperatorCheckerExt = " + mOperatorCheckerExt);
        return mOperatorCheckerExt;
    }

    public synchronized IDataLoader getLoadDataExt(final Context context) {
        if (mDataLoadExt == null) {
            try {
                mDataLoadExt = (IDataLoader) PluginManager.createPluginObject(context,
                        IDataLoader.class.getName());
            } catch (Plugin.ObjectCreationException e) {
                mDataLoadExt = new DefaultDataLoader(context);
            }
        }
        LauncherLog.d("DataLoadExt", "getLoadDataExt: context = " + context
                + ", mDataLoadExt = " + mDataLoadExt);
        return mDataLoadExt;
    }
}
