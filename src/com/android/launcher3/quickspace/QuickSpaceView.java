/*
 * Copyright (C) 2018-2025 crDroid Android Project
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
package com.android.launcher3.quickspace;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.quickspace.views.AccentedTextClock;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    public final ColorStateList mColorStateList;
    public BubbleTextView mBubbleTextView;
    public final int mQuickspaceBackgroundRes;

    public ViewGroup mQuickspaceContent;
    public ImageView mEventSubIcon;
    public ImageView mNowPlayingIcon;
    public TextView mEventTitleSub;

    public TextView mQuickspaceDayOfWeek;
    public AccentedTextClock mQuickspaceClock;
    public TextView mQuickspaceDate;
    public TextView mPSAMessage;
    public ViewGroup mNowPlayingContent;
    public TextView mNowPlayingText;
    public ViewGroup mContextualInfoRow;

    public TextView mEventTitleSubColored;
    public TextView mGreetingsExt;
    public TextView mGreetingsExtClock;
    public ViewGroup mWeatherContentSub;
    public ImageView mWeatherIconSub;
    public TextView mWeatherTempSub;
    public TextView mEventTitle;

    public boolean mIsQuickEvent;
    public boolean mFinishedInflate;
    public boolean mWeatherAvailable;
    public boolean mAttached;

    private boolean mIsAlternateStyle = false;
    private boolean mLastAccentState;
    private boolean mViewsLoaded = false;
    private String mLastEventTitle = "";
    private String mLastWeatherTemp = "";
    private boolean mLastNowPlayingState = false;
    private String mLastPSAMessage = "";
    private String mLastActionTitle = "";

    private QuickSpaceActionReceiver mActionReceiver;
    public QuickspaceController mController;

    private int mCurrentStyle = -1;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mController = new QuickspaceController(context);
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
        int style = Integer.parseInt(LauncherPrefs.QUICKSPACE_UI_STYLE.get(getContext()));
        boolean styleChanged = mCurrentStyle != style;
        if (!mViewsLoaded || styleChanged) {
            prepareLayout(style);
            mViewsLoaded = true;
        }
        mIsQuickEvent = mController.isQuickEvent();
        mWeatherAvailable = mController.isWeatherAvailable();

        if (styleChanged || !mViewsLoaded || hasDataChanged()) {
            updateView(style);
        }
    }

    private void updateView(int style) {
        switch (style) {
            case 2:
                post(() -> loadLargeStyle());
                break;
            case 1: // Extended
            case 0: // Default
            default:
                loadDoubleLine(style == 1);
                break;
        }

    }

    private boolean hasDataChanged() {
        if (mController == null || mController.getEventController() == null) {
            return false;
        }

        String currentEventTitle = mController.getEventController().getTitle();
        String currentWeatherTemp = mController.getWeatherTemp();
        boolean currentNowPlayingState = mController.getEventController().isNowPlaying();
        String currentActionTitle = mController.getEventController().getActionTitle();
        
        String currentPSAMessage = "";
        if (mIsQuickEvent && LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) && !currentNowPlayingState) {
            currentPSAMessage = currentActionTitle != null ? currentActionTitle : "";
        }

        boolean changed = !mLastEventTitle.equals(currentEventTitle != null ? currentEventTitle : "") ||
                         !mLastWeatherTemp.equals(currentWeatherTemp != null ? currentWeatherTemp : "") ||
                         mLastNowPlayingState != currentNowPlayingState ||
                         !mLastActionTitle.equals(currentActionTitle != null ? currentActionTitle : "") ||
                         !mLastPSAMessage.equals(currentPSAMessage);

        if (changed) {
            mLastEventTitle = currentEventTitle != null ? currentEventTitle : "";
            mLastWeatherTemp = currentWeatherTemp != null ? currentWeatherTemp : "";
            mLastNowPlayingState = currentNowPlayingState;
            mLastActionTitle = currentActionTitle != null ? currentActionTitle : "";
            mLastPSAMessage = currentPSAMessage;
        }

        return changed;
    }

    private final void loadDoubleLine(boolean useAlternativeQuickspaceUI) {
        if (mController == null || mController.getEventController() == null) {
            return;
        }

        if (getBackground() == null) {
            setBackgroundResource(mQuickspaceBackgroundRes);
        }

        String eventTitle = mController.getEventController().getTitle();
        if (!mEventTitle.getText().toString().equals(eventTitle)) {
            mEventTitle.setText(eventTitle);
        }

        if (useAlternativeQuickspaceUI) {
            String greetingsExt = mController.getEventController().getGreetings();
            updateTextViewIfNeeded(mGreetingsExt, greetingsExt, true);
            if (mGreetingsExt.getVisibility() == View.VISIBLE) {
                mGreetingsExt.setEllipsize(TruncateAt.END);
                mGreetingsExt.setOnClickListener(mController.getEventController().getAction());
            }
            String greetingsExtClock = mController.getEventController().getClockExt();
            updateTextViewIfNeeded(mGreetingsExtClock, greetingsExtClock, true);
            if (mGreetingsExtClock.getVisibility() == View.VISIBLE) {
                mGreetingsExtClock.setOnClickListener(mController.getEventController().getAction());
            }
        }
        boolean shouldShowPsa = mIsQuickEvent && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) ||
                        mController.getEventController().isNowPlaying());

        if (shouldShowPsa) {
            post(() -> {
                if (mController == null || mController.getEventController() == null) {
                    return;
                }

                maybeSetMarquee(mEventTitle);
                mEventTitle.setOnClickListener(mController.getEventController().getAction());
                
                String actionTitle = mController.getEventController().getActionTitle();
                if (!mEventTitleSub.getText().toString().equals(actionTitle)) {
                    mEventTitleSub.setText(actionTitle);
                }
                maybeSetMarquee(mEventTitleSub);
                mEventTitleSub.setOnClickListener(mController.getEventController().getAction());

                if (mEventTitleSub.getVisibility() != View.VISIBLE) {
                    animateIn(mEventTitleSub);
                }
            });

            if (useAlternativeQuickspaceUI) {
                if (mController.getEventController().isNowPlaying()) {

                    post(() -> {

                    if (mController == null || mController.getEventController() == null) {
                        return;
                    }

                        animateOut(mEventSubIcon);
                        animateIn(mEventTitleSubColored);
                        animateIn(mNowPlayingIcon);
                        mNowPlayingIcon.setOnClickListener(mController.getEventController().getAction());

                        String nowPlayingText = getContext().getString(R.string.qe_now_playing_by);
                        if (!mEventTitleSubColored.getText().toString().equals(nowPlayingText)) {
                            mEventTitleSubColored.setText(nowPlayingText);
                        }
                        mEventTitleSubColored.setOnClickListener(mController.getEventController().getAction());
                    });
                } else {
                    post(() -> {
                        setEventSubIcon();
                        animateOut(mEventTitleSubColored);
                        animateOut(mNowPlayingIcon);
                    });
                }
            } else {
                setEventSubIcon();
            }
        } else {
            post(() -> {
                if (mEventTitleSub != null && mEventSubIcon != null) {
                animateOut(mEventTitleSub);
                animateOut(mEventSubIcon);
                }
            });
            if (useAlternativeQuickspaceUI) {
                post(() -> {
                    animateOut(mEventTitleSubColored);
                    animateOut(mNowPlayingIcon);
                    if (mEventTitleSubColored != null && mNowPlayingIcon != null) {
                    }
                });
            }
        }

        post(() -> {
            bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
            boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
            if (mWeatherContentSub != null) {
                mWeatherContentSub.setOnClickListener(hasGoogleApp ? getActionReceiver().getWeatherAction() : null);
            }
        });
    }
    
    private void updateTextViewIfNeeded(TextView textView, String newText, boolean setVisibility) {
        if (textView == null) return;
        
        boolean hasText = newText != null && !newText.isEmpty();
        boolean currentlyVisible = textView.getVisibility() == View.VISIBLE;
        String currentText = textView.getText().toString();
        
        if (setVisibility && hasText != currentlyVisible) {
            textView.setVisibility(hasText ? View.VISIBLE : View.GONE);
        }
        
        if (hasText && !currentText.equals(newText)) {
            textView.setText(newText);
        }
    }

    private void maybeSetMarquee(TextView tv) {
        if (tv == null) return;
        tv.setSelected(false);
        tv.setEllipsize(TruncateAt.END);
        final float textWidth = tv.getPaint().measureText(tv.getText().toString());
        tv.post(() -> {
            android.text.Layout layout = tv.getLayout();
            if (layout != null && layout.getEllipsizedWidth() < textWidth) {
                tv.setEllipsize(TruncateAt.MARQUEE);
                tv.setMarqueeRepeatLimit(1);
                tv.setSelected(true);
            }
        });
    }

    private void setEventSubIcon() {
        if (mController == null || mController.getEventController() == null) {
            return;
        }

        Drawable icon = mController.getEventController().getActionIcon();
        if (icon != null) {
            if (mEventSubIcon.getVisibility() != View.VISIBLE) {
                animateIn(mEventSubIcon);
            }
            mEventSubIcon.setImageTintList(mController.getEventController().isNowPlaying() ? null : mColorStateList);
            mEventSubIcon.setImageDrawable(icon);
            mEventSubIcon.setOnClickListener(mController.getEventController().getAction());
        } else {
            animateOut(mEventSubIcon);
        }
    }

    private final void bindWeather(View container, TextView title, ImageView icon) {
        if (container == null || title == null || icon == null) return;

        if (!mWeatherAvailable || mController.getEventController().isNowPlaying()) {
            if (container.getVisibility() != View.GONE) {
                container.setVisibility(View.GONE);
            }
            return;
        }
        String weatherTemp = mController.getWeatherTemp();
        if (weatherTemp == null || weatherTemp.isEmpty()) {
            if (container.getVisibility() != View.GONE) {
                container.setVisibility(View.GONE);
            }
            return;
        }
        if (container.getVisibility() != View.VISIBLE) {
            animateIn(container);
        }

        if (!title.getText().toString().equals(weatherTemp)) {
            title.setText(weatherTemp);
        }

        Drawable weatherIcon = mController.getWeatherIcon();
        if (icon.getDrawable() != weatherIcon) {
            icon.setImageDrawable(weatherIcon);
        }
    }

    private QuickSpaceActionReceiver getActionReceiver() {
        if (mActionReceiver == null) {
            mActionReceiver = new QuickSpaceActionReceiver(getContext());
        }
        return mActionReceiver;
    }

    private void loadLargeStyle() {
        if (mQuickspaceDayOfWeek == null) return; // Views not inflated for this style

        if (mController == null || mController.getEventController() == null) {
            return;
        }

        beginBatchEdit();

        boolean accentEnabled = LauncherPrefs.QUICKSPACE_VOLTAGE_ACCENT.get(getContext());
        if (mQuickspaceClock != null) { 
            if (mLastAccentState != accentEnabled) {
                mQuickspaceClock.setAccentEnabled(accentEnabled);
                mLastAccentState = accentEnabled;
            }
        }

        String dayOfWeek = QuickEventsController.getDayOfWeek(getContext());
        updateTextViewIfNeeded(mQuickspaceDayOfWeek, dayOfWeek, false);

        String shortDate = mController.getEventController().getShortDate(getContext());
        updateTextViewIfNeeded(mQuickspaceDate, shortDate, false);

        if (mWeatherContentSub.getVisibility() != View.VISIBLE) {
            mWeatherContentSub.setVisibility(View.VISIBLE);
        }

        View.OnClickListener openClockListener = v -> {
            try {
                getContext().startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        };

        View.OnClickListener openCalendarListener = v -> {
            try {
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, System.currentTimeMillis());
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        };

        mQuickspaceClock.setOnClickListener(openClockListener);
        mQuickspaceDayOfWeek.setOnClickListener(openClockListener); // Both clock and day open the Clock app
        mQuickspaceDate.setOnClickListener(openCalendarListener);

        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        mWeatherContentSub.setOnClickListener(hasGoogleApp ? getActionReceiver().getWeatherAction() : null);

        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);

        boolean isNowPlaying = mController.getEventController().isNowPlaying();
        if (isNowPlaying) {
            if (mContextualInfoRow.getVisibility() != View.VISIBLE) {
                mContextualInfoRow.setVisibility(View.VISIBLE);
            }
            if (mPSAMessage.getVisibility() != View.GONE) {
                mPSAMessage.setVisibility(View.GONE);
            }
            if (mNowPlayingContent.getVisibility() != View.VISIBLE) {
                mNowPlayingContent.setVisibility(View.VISIBLE);
            }

            if (mController == null || mController.getEventController() == null) {
                endBatchEdit();
                return;
            }

            String nowPlaying = mController.getEventController().getTitle() + " - " + mController.getEventController().getActionTitle();
            if (!mNowPlayingText.getText().toString().equals(nowPlaying)) {
                mNowPlayingText.setText(nowPlaying);
            }
            mNowPlayingContent.setOnClickListener(mController.getEventController().getAction());
            post(() -> maybeSetMarquee(mNowPlayingText));
        } else {
            if (mNowPlayingContent.getVisibility() != View.GONE) {
                mNowPlayingContent.setVisibility(View.GONE);
            }
            if (mIsQuickEvent && LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext())) {
                if (mContextualInfoRow.getVisibility() != View.VISIBLE) {
                    mContextualInfoRow.setVisibility(View.VISIBLE);
                }
                if (mPSAMessage.getVisibility() != View.VISIBLE) {
                    mPSAMessage.setVisibility(View.VISIBLE);
                }

                if (mController == null || mController.getEventController() == null) {
                    endBatchEdit();
                    return;
                }

                String actionTitle = mController.getEventController().getActionTitle();
                if (!mPSAMessage.getText().toString().equals(actionTitle)) {
                    mPSAMessage.setText(actionTitle);
                }
                mPSAMessage.setOnClickListener(mController.getEventController().getAction());
                post(() -> maybeSetMarquee(mPSAMessage));
            } else {
                if (mContextualInfoRow.getVisibility() != View.GONE) {
                    mContextualInfoRow.setVisibility(View.GONE);
                }
            }
        }

        endBatchEdit();
    }


    private void beginBatchEdit() {
       if (mQuickspaceContent != null) {
            mQuickspaceContent.suppressLayout(true);
        }
    }
    
    private void endBatchEdit() {
        if (mQuickspaceContent != null) {
            mQuickspaceContent.suppressLayout(false);
        }
    }

    private final void loadViews() {
        mEventTitle = (TextView) findViewById(R.id.quick_event_title);
        mEventTitleSub = (TextView) findViewById(R.id.quick_event_title_sub);
        mEventTitleSubColored = (TextView) findViewById(R.id.quick_event_title_sub_colored);
        mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon_sub);
        mEventSubIcon = (ImageView) findViewById(R.id.quick_event_icon_sub);
        mWeatherIconSub = (ImageView) findViewById(R.id.quick_event_weather_icon);
        mQuickspaceContent = (ViewGroup) findViewById(R.id.quickspace_content);
        mWeatherContentSub = (ViewGroup) findViewById(R.id.quick_event_weather_content);
        mWeatherTempSub = (TextView) findViewById(R.id.quick_event_weather_temp);
        if (mCurrentStyle == 1) { // Extended style
            mGreetingsExtClock = (TextView) findViewById(R.id.extended_greetings_clock);
            mGreetingsExt = (TextView) findViewById(R.id.extended_greetings);
        }

        if (mCurrentStyle == 2) { // Large style
            mQuickspaceDayOfWeek = findViewById(R.id.quickspace_day_of_week);
            mQuickspaceClock = (AccentedTextClock) findViewById(R.id.quickspace_clock);
            mQuickspaceDate = findViewById(R.id.quickspace_date);
            mPSAMessage = findViewById(R.id.quickspace_psa_message);
            mNowPlayingContent = findViewById(R.id.now_playing_content);
            mNowPlayingText = findViewById(R.id.now_playing_text);
            mContextualInfoRow = findViewById(R.id.contextual_info_row);
        }
    }

    private void prepareLayout(int style) {
        if (mCurrentStyle == style && mViewsLoaded) {
            return; // Avoid unnecessary layout inflation
        }

        mCurrentStyle = style;
        int indexOfChild = indexOfChild(mQuickspaceContent);
        if (mQuickspaceContent != null) {
            removeView(mQuickspaceContent);
        }
        int layoutId;
        switch (style) {
            case 1:
                layoutId = R.layout.quickspace_alternate_double;
                break;
            case 2:
                layoutId = R.layout.quickspace_large_style;
                break;
            default:
                layoutId = R.layout.quickspace_doubleline;
        }
        addView(LayoutInflater.from(getContext()).inflate(layoutId, this, false), indexOfChild);

        loadViews();
        getQuickSpaceView();
    }

    private void getQuickSpaceView() {
         if (mQuickspaceContent.getVisibility() != View.VISIBLE) {
            mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0.0f);
            mQuickspaceContent.animate().setDuration(150).alpha(1.0f);
        }
    }

    private void animateIn(View view) {
        if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
            return; // Already visible
        }
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(view.getHeight() / 2f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void animateOut(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return; // Already hidden
        }
        view.animate()
            .alpha(0f)
            .translationY(view.getHeight() / 2f)
            .setDuration(250)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> view.setVisibility(View.GONE))
            .start();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

        mAttached = false;
    }

    public boolean isPackageEnabled(String pkgName, Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        mFinishedInflate = true;
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
        if (isAttachedToWindow()) {
            if (mController != null) {
                mController.addListener(this);
            }
        }
    }

    @Override
    public void onLayout(boolean b, int n, int n2, int n3, int n4) {
        super.onLayout(b, n, n2, n3, n4);
    }

    public void onPause() {
        mController.onPause();
    }

    public void onResume() {
        if (mController != null && mFinishedInflate) {
            mController.addListener(this);
        }
        mController.onResume();
    }

    public void onDestroy() {
        mController.onDestroy();
        mActionReceiver = null;
        mController = null;
        mBubbleTextView = null;
        mQuickspaceContent = null;
        mEventSubIcon = null;
        mNowPlayingIcon = null;
        mEventTitleSub = null;
        mEventTitleSubColored = null;
        mGreetingsExt = null;
        mGreetingsExtClock = null;
        mWeatherContentSub = null;
        mWeatherIconSub = null;
        mWeatherTempSub = null;
        mEventTitle = null;
        
        // Nullify Voltage style views
        mQuickspaceDayOfWeek = null;
        mQuickspaceClock = null;
        mQuickspaceDate = null;
        mPSAMessage = null;
        mNowPlayingContent = null;
        mNowPlayingText = null;
        mContextualInfoRow = null;
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
