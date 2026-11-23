package com.fsck.k9.ui.settings

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.fsck.k9.ui.R
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textview.MaterialTextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import net.thunderbird.core.android.account.LegacyAccount

/** Opacity of the peek row's content at its top; it fades out further towards the bottom (see [peekFade]). */
internal const val PEEK_ACCOUNT_ALPHA = 0.7f

internal class AccountItem(
    val account: LegacyAccount,
    override var isDraggable: Boolean,
    val isSelectionMode: Boolean = false,
    val selected: Boolean = false,
    val peekDescription: String? = null,
) : AbstractItem<AccountItem.ViewHolder>(), IDraggable {
    override var identifier = 200L + account.accountNumber

    override val type = R.id.settings_list_account_item

    override val layoutRes = R.layout.account_list_item

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AccountItem>(view) {
        val name: MaterialTextView = view.findViewById(R.id.name)
        val email: MaterialTextView = view.findViewById(R.id.email)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkBox)

        override fun bindView(item: AccountItem, payloads: List<Any>) {
            val accountName = item.account.name
            if (accountName != null) {
                name.text = item.account.name
                email.text = item.account.email
                email.isVisible = true
            } else {
                name.text = item.account.email
                email.isVisible = false
            }

            dragHandle.isGone = !item.isDraggable
            checkBox.isVisible = item.isSelectionMode
            checkBox.isChecked = item.selected

            val isPeek = item.peekDescription != null
            setContentAlpha(if (isPeek) PEEK_ACCOUNT_ALPHA else 1f)
            setPeekFade(isPeek)
            itemView.contentDescription = item.peekDescription
        }

        override fun unbindView(item: AccountItem) {
            name.text = null
            email.text = null
            checkBox.isChecked = false
            setContentAlpha(1f)
            setPeekFade(false)
            itemView.contentDescription = null
        }

        /**
         * Lets the peek row fade out towards the bottom, like the faded item on the About screen. The row is drawn into
         * its own layer and the gradient masks it (DST_IN), so it works on any background.
         */
        private fun setPeekFade(enabled: Boolean) {
            if (enabled) {
                itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                itemView.foreground = FadeOutMask()
            } else {
                itemView.foreground = null
                itemView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }

        /**
         * Fades the row's content instead of the row itself: RecyclerView's item animator animates the alpha of the
         * row view and resets it to 1 at the end, which would undo the faded peek row.
         */
        private fun setContentAlpha(alpha: Float) {
            val row = itemView as? ViewGroup ?: return
            row.children.forEach { it.alpha = alpha }
        }
    }
}

internal class DragHandleTouchEvent(val action: (position: Int) -> Unit) : TouchEventHook<AccountItem>() {
    override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
        return if (viewHolder is AccountItem.ViewHolder) viewHolder.dragHandle else null
    }

    override fun onTouch(
        v: View,
        event: MotionEvent,
        position: Int,
        fastAdapter: FastAdapter<AccountItem>,
        item: AccountItem,
    ): Boolean {
        return if (event.action == MotionEvent.ACTION_DOWN) {
            action(position)
            true
        } else {
            false
        }
    }
}

/** Mask that keeps the top of whatever is drawn below it and lets it fade out to transparent at the bottom. */
private class FadeOutMask : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    override fun draw(canvas: Canvas) {
        val area = bounds
        paint.shader = LinearGradient(
            0f,
            area.top.toFloat(),
            0f,
            area.bottom.toFloat(),
            Color.BLACK,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(area, paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
