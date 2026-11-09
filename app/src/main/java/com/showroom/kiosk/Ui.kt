package com.showroom.kiosk

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.toast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.screenScroll(): Pair<ScrollView, LinearLayout> {
    val scroll = ScrollView(this).apply { isFillViewport = true }
    val frame = FrameLayout(this)
    val available = resources.displayMetrics.widthPixels - dp(32)
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(minOf(dp(720), available), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        setPadding(dp(8), dp(20), dp(8), dp(32))
    }
    frame.addView(column)
    scroll.addView(
        frame,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
    )
    return scroll to column
}

fun LinearLayout.full(view: View, top: Int = 8) {
    addView(
        view,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(top)
        },
    )
}

fun Context.heading(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextColor(getColor(R.color.text))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
}

fun Context.sectionLabel(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextColor(getColor(R.color.gold))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setPadding(0, dp(18), 0, dp(4))
}

fun Context.bodyText(text: CharSequence = ""): TextView = TextView(this).apply {
    this.text = text
    setTextColor(getColor(R.color.text))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    setLineSpacing(dp(2).toFloat(), 1f)
}

fun Context.mutedText(text: CharSequence = ""): TextView = TextView(this).apply {
    this.text = text
    setTextColor(getColor(R.color.muted))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
}

fun Context.field(
    hint: String,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    max: Int = 500,
    password: Boolean = false,
): EditText {
    return EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        filters = arrayOf(InputFilter.LengthFilter(max))
        setTextColor(getColor(R.color.text))
        setHintTextColor(getColor(R.color.muted))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        if (password) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        background = GradientDrawable().apply {
            setColor(getColor(R.color.card))
            setStroke(dp(1), getColor(R.color.gold))
            cornerRadius = dp(10).toFloat()
        }
        backgroundTintList = null
    }
}

fun Context.toggle(label: String): SwitchCompat = SwitchCompat(this).apply {
    text = label
    setTextColor(getColor(R.color.text))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    minHeight = dp(48)
    thumbTintList = ColorStateList.valueOf(getColor(R.color.gold))
    trackTintList = ColorStateList.valueOf(getColor(R.color.muted))
}

fun Context.actionButton(label: String, outlined: Boolean = false, danger: Boolean = false): MaterialButton {
    val button = if (outlined) {
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    } else {
        MaterialButton(this)
    }
    button.text = label
    button.isAllCaps = false
    button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    button.minHeight = dp(52)
    button.cornerRadius = dp(12)
    if (danger) {
        button.backgroundTintList = ColorStateList.valueOf(getColor(R.color.danger))
        button.setTextColor(Color.WHITE)
    } else if (outlined) {
        button.backgroundTintList = ColorStateList.valueOf(getColor(R.color.card))
        button.strokeColor = ColorStateList.valueOf(getColor(R.color.gold))
        button.setTextColor(getColor(R.color.text))
    } else {
        button.backgroundTintList = ColorStateList.valueOf(getColor(R.color.gold))
        button.setTextColor(getColor(R.color.onGold))
    }
    return button
}

fun Context.cardBackground(): GradientDrawable = GradientDrawable().apply {
    setColor(getColor(R.color.card))
    cornerRadius = dp(16).toFloat()
}

fun Context.pad(child: View, margin: Int = 20): FrameLayout {
    return FrameLayout(this).apply {
        addView(
            child,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                val px = dp(margin)
                setMargins(px, px, px, 0)
            },
        )
    }
}
