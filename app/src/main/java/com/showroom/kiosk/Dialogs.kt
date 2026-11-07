package com.showroom.kiosk

import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.showroom.kiosk.core.PinGuard
import com.showroom.kiosk.core.PinResult

object Dialogs {
    fun askPin(activity: AppCompatActivity, store: KioskStore, onAccepted: () -> Unit) {
        val state = store.pinState()
        if (state == null) {
            if (store.kioskEnabled) {
                activity.toast(activity.getString(R.string.need_pin))
                return
            }
            onAccepted()
            return
        }
        val now = System.currentTimeMillis()
        if (now < state.lockoutUntilEpochMs) {
            activity.toast(activity.getString(R.string.pin_locked, secondsLeft(state.lockoutUntilEpochMs)))
            return
        }
        val input = pinField(activity)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.enter_pin)
            .setView(activity.pad(input))
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            secure(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val current = store.pinState() ?: return@setOnClickListener
                val (result, next) = PinGuard.check(current, input.text.toString(), System.currentTimeMillis())
                store.savePinState(next)
                when (result) {
                    is PinResult.Accepted -> {
                        dialog.dismiss()
                        onAccepted()
                    }
                    is PinResult.Waiting -> activity.toast(
                        activity.getString(R.string.pin_locked, secondsLeft(result.lockoutUntilEpochMs)),
                    )
                    is PinResult.Rejected -> activity.toast(
                        if (result.lockoutUntilEpochMs > 0L) {
                            activity.getString(R.string.pin_locked, secondsLeft(result.lockoutUntilEpochMs))
                        } else {
                            activity.getString(R.string.pin_wrong)
                        },
                    )
                }
            }
        }
        dialog.show()
    }

    fun changePin(activity: AppCompatActivity, store: KioskStore, onChanged: () -> Unit) {
        val hasPin = store.hasPin()
        val old = if (hasPin) pinField(activity, activity.getString(R.string.old_pin)) else null
        val fresh = pinField(activity, activity.getString(R.string.new_pin))
        val again = pinField(activity, activity.getString(R.string.confirm_pin))
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(12), activity.dp(20), 0)
            if (old != null) full(old, 0)
            full(fresh, if (old == null) 0 else 8)
            full(again)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (hasPin) R.string.change_pin else R.string.set_pin_title)
            .setView(form)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            secure(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hasPin) {
                    val current = store.pinState() ?: return@setOnClickListener
                    val (result, next) = PinGuard.check(current, old?.text?.toString().orEmpty(), System.currentTimeMillis())
                    if (result !is PinResult.Accepted) {
                        store.savePinState(next)
                        activity.toast(activity.getString(R.string.pin_wrong))
                        return@setOnClickListener
                    }
                    store.savePinState(next)
                }
                val pin = fresh.text.toString()
                if (!PinGuard.isFormatValid(pin)) {
                    activity.toast(activity.getString(R.string.pin_format))
                    return@setOnClickListener
                }
                if (pin != again.text.toString()) {
                    activity.toast(activity.getString(R.string.pin_mismatch))
                    return@setOnClickListener
                }
                store.setPin(pin)
                dialog.dismiss()
                activity.toast(activity.getString(R.string.pin_changed))
                onChanged()
            }
        }
        dialog.show()
    }

    private fun pinField(activity: AppCompatActivity, hint: String = activity.getString(R.string.enter_pin)): EditText {
        return activity.field(
            hint,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            PinGuard.MAX_LENGTH,
            password = true,
        )
    }

    private fun secure(dialog: AlertDialog) {
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun secondsLeft(untilEpochMs: Long): Int {
        return (((untilEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)).toInt()
    }
}
