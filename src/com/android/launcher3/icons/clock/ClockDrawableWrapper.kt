package com.android.launcher3.icons.clock

import android.content.Context
import android.graphics.Paint
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfoWithIcon
import java.util.function.Consumer

object ClockDrawableWrapper {
    @JvmStatic
    fun create(
        context: Context,
        info: ItemInfoWithIcon,
        layers: ClockLayers,
        registerUpdater: Consumer<AutoUpdateClock>
    ): FastBitmapDrawable {
        val base = info.newIcon(context)
        val state = base.constantState as FastBitmapDrawable.FastBitmapConstantState

        val factory = object : FastBitmapDrawableDelegate.DelegateFactory {
            override fun newDelegate(
                info: BitmapInfo,
                shape: IconShape,
                paint: Paint,
                host: FastBitmapDrawable
            ): FastBitmapDrawableDelegate {
                val delegate = AutoUpdateClock(host, layers)
                registerUpdater.accept(delegate)
                return delegate
            }
        }
        return state.copy(delegateFactory = factory).newDrawable()
    }
}
