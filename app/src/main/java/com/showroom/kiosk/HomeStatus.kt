package com.showroom.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

class HomeStatus(private val context: Context) {
    private val clock = statusText()
    private val wifi = statusIcon(R.drawable.ic_status_wifi, R.string.status_wifi).apply {
        visibility = View.GONE
    }
    private val bolt = statusIcon(R.drawable.ic_status_bolt, R.string.status_charging).apply {
        visibility = View.GONE
    }
    private val batteryIcon = statusIcon(R.drawable.ic_status_battery, R.string.status_battery_icon)
    private val batteryText = statusText()
    private var started = false

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(clock)
        addView(wifi, gap())
        addView(bolt, gap())
        addView(batteryIcon, gap())
        addView(batteryText, tight())
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = renderClock()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = renderBattery(intent)
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = renderWifi()
        override fun onLost(network: Network) = renderWifi()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = renderWifi()
    }

    fun start() {
        if (started) return
        started = true
        renderClock()
        val time = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        register(timeReceiver, time)
        val battery = register(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery != null) renderBattery(battery)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        context.getSystemService(ConnectivityManager::class.java)
            .registerNetworkCallback(request, wifiCallback)
        renderWifi()
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(timeReceiver)
        context.unregisterReceiver(batteryReceiver)
        context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiCallback)
    }

    private fun renderClock() {
        clock.text = DateFormat.getTimeFormat(context).format(Date())
    }

    private fun renderWifi() {
        view.post {
            val connected = wifiConnected()
            wifi.visibility = if (connected) View.VISIBLE else View.GONE
        }
    }

    private fun wifiConnected(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun renderBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level < 0) 0 else (level * 100 / scale).coerceIn(0, 100)
        val state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
            state == BatteryManager.BATTERY_STATUS_FULL
        val low = percent <= 20 && !charging
        val color = context.getColor(if (low) R.color.danger else R.color.text)
        batteryText.text = context.getString(R.string.status_battery_percent, percent)
        batteryText.setTextColor(color)
        batteryIcon.imageTintList = ColorStateList.valueOf(color)
        bolt.visibility = if (charging) View.VISIBLE else View.GONE
        batteryIcon.contentDescription = context.getString(
            if (charging) R.string.status_battery_charging else R.string.status_battery_level,
            percent,
        )
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun statusText(): TextView {
        return TextView(context).apply {
            setTextColor(context.getColor(R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setShadowLayer(12f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }

    private fun statusIcon(drawable: Int, description: Int): ImageView {
        val size = context.dp(22)
        return ImageView(context).apply {
            setImageResource(drawable)
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.text))
            contentDescription = context.getString(description)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun gap(): LinearLayout.LayoutParams {
        val size = context.dp(22)
        return LinearLayout.LayoutParams(size, size).apply { marginStart = context.dp(14) }
    }

    private fun tight(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = context.dp(6) }
    }
}
