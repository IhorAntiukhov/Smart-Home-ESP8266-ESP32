package com.arduinoworld.smarthome

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class DeviceItemDecoration: RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val position = parent.getChildAdapterPosition(view)
        val context = parent.context

        if (position > 1) {
            outRect.top = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                -30.0f,
                context.resources.displayMetrics
            ).toInt()
        }
        if (position == 0 || position == 2 || position == 4) {
            outRect.right = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                -9.0f,
                context.resources.displayMetrics
            ).toInt()
        } else {
            outRect.left = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                -9.0f,
                context.resources.displayMetrics
            ).toInt()
        }
    }
}