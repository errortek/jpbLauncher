/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.apppairs;

import static com.android.launcher3.BubbleTextView.DISPLAY_FOLDER;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.views.ActivityContext;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * A {@link android.widget.FrameLayout} used to represent an app pair icon on the workspace.
 * <br>
 * The app pair icon is two parallel background rectangles with rounded corners. Icons of the two
 * member apps are set into these rectangles.
 */
public class AppPairIcon extends FrameLayout implements DraggableView, Reorderable {
    private static final String TAG = "AppPairIcon";

    /**
     * Indicates that the app pair is currently launchable on the current screen.
     */
    private boolean mIsLaunchableAtScreenSize = true;

    // A view that holds the app pair icon graphic.
    private AppPairIconGraphic mIconGraphic;
    // A view that holds the app pair's title.
    private BubbleTextView mAppPairName;
    // The underlying ItemInfo that stores info about the app pair members, etc.
    private FolderInfo mInfo;
    // The containing element that holds this icon: workspace, taskbar, folder, etc. Affects certain
    // aspects of how the icon is drawn.
    private int mContainer;

    // Required for Reorderable -- handles translation and bouncing movements
    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;

    public AppPairIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppPairIcon(Context context) {
        super(context);
    }

    /**
     * Builds an AppPairIcon to be added to the Launcher.
     */
    public static AppPairIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo appPairInfo, int container) {
        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        AppPairIcon icon = (AppPairIcon) inflater.inflate(resId, group, false);

        // Sort contents, so that left-hand app comes first
        appPairInfo.contents.sort(Comparator.comparingInt(a -> a.rank));

        icon.setTag(appPairInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = appPairInfo;
        icon.mContainer = container;

        icon.checkDisabledState();

        // Set up icon drawable area
        icon.mIconGraphic = icon.findViewById(R.id.app_pair_icon_graphic);
        icon.mIconGraphic.init(icon, container);

        // Set up app pair title
        icon.mAppPairName = icon.findViewById(R.id.app_pair_icon_name);
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) icon.mAppPairName.getLayoutParams();
        // Shift the title text down to leave room for the icon graphic. Since the icon graphic is
        // a separate element (and not set as a CompoundDrawable on the BubbleTextView), we need to
        // shift the text down manually.
        lp.topMargin = container == DISPLAY_FOLDER
                ? grid.folderChildIconSizePx + grid.folderChildDrawablePaddingPx
                : grid.iconSizePx + grid.iconDrawablePaddingPx;
        // For some reason, app icons have setIncludeFontPadding(false) inside folders, so we set it
        // here to match that.
        icon.mAppPairName.setIncludeFontPadding(container != DISPLAY_FOLDER);
        icon.mAppPairName.setText(appPairInfo.title);

        // Set up accessibility
        icon.setContentDescription(icon.getAccessibilityTitle(appPairInfo));
        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        return icon;
    }

    /**
     * Returns a formatted accessibility title for app pairs.
     */
    public String getAccessibilityTitle(FolderInfo appPairInfo) {
        CharSequence app1 = appPairInfo.contents.get(0).title;
        CharSequence app2 = appPairInfo.contents.get(1).title;
        return getContext().getString(R.string.app_pair_name_format, app1, app2);
    }

    // Required for DraggableView
    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    // Required for DraggableView
    @Override
    public void getWorkspaceVisualDragBounds(Rect outBounds) {
        mIconGraphic.getIconBounds(outBounds);
    }

    /** Sets the visibility of the icon's title text */
    public void setTextVisible(boolean visible) {
        if (visible) {
            mAppPairName.setVisibility(VISIBLE);
        } else {
            mAppPairName.setVisibility(INVISIBLE);
        }
    }

    // Required for Reorderable
    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    // Required for Reorderable
    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    // Required for Reorderable
    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    public FolderInfo getInfo() {
        return mInfo;
    }

    public BubbleTextView getTitleTextView() {
        return mAppPairName;
    }

    public AppPairIconGraphic getIconDrawableArea() {
        return mIconGraphic;
    }

    public boolean isLaunchableAtScreenSize() {
        return mIsLaunchableAtScreenSize;
    }

    /**
     * Checks if the app pair is launchable in the current device configuration.
     *
     * App pairs can be "disabled" in two ways:
     * 1) One of the member WorkspaceItemInfos is disabled (i.e. the app software itself is paused
     * by the user or can't be launched).
     * 2) This specific instance of an app pair can't be launched due to screen size requirements.
     *
     * This method checks and updates #2. Both #1 and #2 are checked when app pairs are drawn
     * {@link AppPairIconGraphic#dispatchDraw(Canvas)} or clicked on
     * {@link com.android.launcher3.touch.ItemClickHandler#onClickAppPairIcon(View)}
     */
    public void checkDisabledState() {
        DeviceProfile dp = ActivityContext.lookupContext(getContext()).getDeviceProfile();
        // If user is on a small screen, we can't launch if either of the apps is non-resizeable
        mIsLaunchableAtScreenSize =
                dp.isTablet || getInfo().contents.stream().noneMatch(
                        wii -> wii.hasStatusFlag(WorkspaceItemInfo.FLAG_NON_RESIZEABLE));
        // Invalidate to update icons
        mIconGraphic.redraw();
    }

    /**
     * Called when WorkspaceItemInfos get updated, and the app pair icon may need to be redrawn.
     */
    public void maybeRedrawForWorkspaceUpdate(Predicate<WorkspaceItemInfo> itemCheck) {
        // If either of the app pair icons return true on the predicate (i.e. in the list of
        // updated apps), redraw the icon graphic (icon background and both icons).
        if (getInfo().contents.stream().anyMatch(itemCheck)) {
            checkDisabledState();
        }
    }

    /**
     * Inside folders, icons are vertically centered in their rows. See
     * {@link BubbleTextView#onMeasure(int, int)} for comparison.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mContainer == DISPLAY_FOLDER) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            ActivityContext activity = ActivityContext.lookupContext(getContext());
            Paint.FontMetrics fm = mAppPairName.getPaint().getFontMetrics();
            int cellHeightPx = activity.getDeviceProfile().folderChildIconSizePx
                    + activity.getDeviceProfile().folderChildDrawablePaddingPx
                    + (int) Math.ceil(fm.bottom - fm.top);
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
