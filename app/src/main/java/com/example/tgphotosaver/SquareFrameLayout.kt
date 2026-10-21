package com.example.tgphotosaver

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** A FrameLayout whose height always equals its width, used for the photo grid cells. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
