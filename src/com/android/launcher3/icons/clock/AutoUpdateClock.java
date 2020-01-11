package com.android.launcher3.icons.clock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;

import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.FastBitmapDrawableDelegate;
import com.android.launcher3.icons.IconShape;

import java.util.TimeZone;

public class AutoUpdateClock implements FastBitmapDrawableDelegate, Runnable {

    private final FastBitmapDrawable mDrawable;
    private ClockLayers mLayers;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public AutoUpdateClock(FastBitmapDrawable drawable, ClockLayers layers) {
        mDrawable = drawable;
        mLayers = layers;
    }

    @Override
    public void drawContent(BitmapInfo info, IconShape iconShape, Canvas canvas, Rect bounds, Paint paint) {
        if (mLayers == null) {
            if (info != null && info.icon != null) {
                canvas.drawBitmap(info.icon, null, bounds, paint);
            }
        } else {
            canvas.drawBitmap(mLayers.bitmap, null, bounds, mPaint);
            mLayers.updateAngles();
            canvas.scale(mLayers.scale, mLayers.scale,
                    bounds.exactCenterX() + mLayers.offset,
                    bounds.exactCenterY() + mLayers.offset);
            canvas.clipPath(mLayers.mDrawable.getIconMask());
            mLayers.mDrawable.getForeground().draw(canvas);
            rescheduleUpdate();
        }
    }

    @Override
    public void onBoundsChange(Rect bounds) {
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(bounds);
        }
    }

    @Override
    public boolean onLevelChange(int level) {
        return false;
    }

    @Override
    public void run() {
        if (mLayers.updateAngles()) {
            mDrawable.invalidateSelf();
        } else {
            rescheduleUpdate();
        }
    }

    private void rescheduleUpdate() {
        long millisInSecond = 1000L;
        mDrawable.unscheduleSelf(this);
        long uptimeMillis = SystemClock.uptimeMillis();
        mDrawable.scheduleSelf(this, uptimeMillis - uptimeMillis % millisInSecond + millisInSecond);
    }

    // Used only by Google Clock
    void updateLayers(ClockLayers layers) {
        mLayers = layers;
        if (mLayers != null) {
            mLayers.mDrawable.setBounds(mDrawable.getBounds());
        }
        mDrawable.invalidateSelf();
    }

    void setTimeZone(TimeZone timeZone) {
        if (mLayers != null) {
            mLayers.setTimeZone(timeZone);
            mDrawable.invalidateSelf();
        }
    }
}
