package studio.cluvex.aethery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.VpnService
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.math.roundToInt

object AetherWidget {
    const val ACTION_TOGGLE = "studio.cluvex.aethery.widget.TOGGLE"
    const val ACTION_CYCLE_METHOD = "studio.cluvex.aethery.widget.CYCLE_METHOD"
    const val ACTION_SET_H3 = "studio.cluvex.aethery.widget.SET_H3"
    const val ACTION_SET_H2 = "studio.cluvex.aethery.widget.SET_H2"
    const val ACTION_TOGGLE_FALLBACK = "studio.cluvex.aethery.widget.TOGGLE_FALLBACK"

    private const val SNAPSHOT = "widget_snapshot"
    private const val SPEED_TX = "speed_tx"
    private const val SPEED_RX = "speed_rx"
    private const val TUNNEL_STATUS = "tunnel_status"
    private const val VPN_IP = "vpn_ip"
    private const val VPN_COUNTRY = "vpn_country"
    private const val SETTINGS = "settings"
    const val DEFAULT_PROTOCOL = "default_protocol"
    const val DEFAULT_MASQUE_TRANSPORT = "default_masque_transport"
    const val H3_H2_FALLBACK = "h3_h2_fallback"
    private const val CONNECTION_TYPE = "connection_type"

    enum class Kind { CONNECT, METHOD, CONSOLE }

    class ConnectProvider : Host() {
        override val kind = Kind.CONNECT
    }

    class MethodProvider : Host() {
        override val kind = Kind.METHOD
    }

    class ConsoleProvider : Host() {
        override val kind = Kind.CONSOLE
    }

    abstract class Host : AppWidgetProvider() {
        abstract val kind: Kind

        override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id -> manager.updateAppWidget(id, views(context, kind, id)) }
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE -> toggle(context)
                ACTION_CYCLE_METHOD -> cycleMethod(context)
                ACTION_SET_H3 -> setTransport(context, "h3")
                ACTION_SET_H2 -> setTransport(context, "h2")
                ACTION_TOGGLE_FALLBACK -> toggleFallback(context)
                else -> super.onReceive(context, intent)
            }
            refreshAll(context)
        }
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        Kind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
            ids.forEach { id -> manager.updateAppWidget(id, views(context, kind, id)) }
        }
    }

    fun recordSpeed(context: Context, tx: Long, rx: Long) {
        context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            .edit()
            .putLong(SPEED_TX, tx)
            .putLong(SPEED_RX, rx)
            .apply()
    }

    fun recordStatus(context: Context, status: String) {
        context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            .edit()
            .putString(TUNNEL_STATUS, status)
            .apply()
        if (status == AetherVpnService.STATUS_DISCONNECTED || status == AetherVpnService.STATUS_FAILED) {
            recordSpeed(context, 0, 0)
            recordIp(context, "")
        }
    }

    fun recordIp(context: Context, ip: String, country: String = "") {
        val prefs = context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
        val loc = country.trim().uppercase().ifBlank { prefs.getString(VPN_COUNTRY, "").orEmpty() }
        prefs.edit()
            .putString(VPN_IP, ip.trim())
            .putString(VPN_COUNTRY, if (ip.isBlank()) "" else loc)
            .apply()
        refreshAll(context)
    }

    fun currentIp(context: Context): String =
        context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            .getString(VPN_IP, "")
            ?.trim()
            .orEmpty()

    fun currentCountry(context: Context): String =
        context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            .getString(VPN_COUNTRY, "")
            ?.trim()
            .orEmpty()

    fun countryFlag(country: String): String {
        if (country.length != 2 || country.any { !it.isLetter() }) return ""
        return country.uppercase().map { char ->
            String(Character.toChars(0x1F1E6 + char.code - 'A'.code))
        }.joinToString("")
    }

    fun lookupPublicIp(context: Context) {
        Thread {
            val urls = arrayOf(
                "https://1.1.1.1/cdn-cgi/trace",
                "https://1.0.0.1/cdn-cgi/trace",
            )
            for (url in urls) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 2_000
                    connection.readTimeout = 2_000
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = false
                    try {
                        if (connection.responseCode !in 200..399) continue
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val values = body.lineSequence().mapNotNull { line ->
                            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim() }
                        }.toMap()
                        val ip = values["ip"].orEmpty()
                        if (ip.isNotBlank()) {
                            recordIp(context, ip, values["loc"].orEmpty())
                            return@Thread
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (_: Throwable) {
                }
            }
        }.start()
    }

    fun pin(context: Context, kind: Kind) {
        val manager = AppWidgetManager.getInstance(context)
        val name = ComponentName(context, kind.provider)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(name, null, null)
            Toast.makeText(context, R.string.widget_pinned, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private val Kind.provider: Class<out Host>
        get() = when (this) {
            Kind.CONNECT -> ConnectProvider::class.java
            Kind.METHOD -> MethodProvider::class.java
            Kind.CONSOLE -> ConsoleProvider::class.java
        }

    private val Kind.layout: Int
        get() = when (this) {
            Kind.CONNECT -> R.layout.widget_connect
            Kind.METHOD -> R.layout.widget_method
            Kind.CONSOLE -> R.layout.widget_console
        }

    private fun views(context: Context, kind: Kind, widgetId: Int): RemoteViews {
        val palette = AppAppearance.load(context)
        val state = connectionState(context)
        val protocol = protocol(context)
        val transport = transport(context)
        val fallback = fallback(context)
        val density = context.resources.displayMetrics.density
        val mark = connectMark((72 * density).roundToInt(), state, palette)
        val views = RemoteViews(context.packageName, kind.layout)
        views.setImageViewBitmap(R.id.connect, mark)
        views.setOnClickPendingIntent(R.id.connect, action(context, kind, widgetId, ACTION_TOGGLE))

        if (kind != Kind.CONNECT) {
            views.setTextViewText(R.id.method, protocol.label)
            views.setTextColor(R.id.method, palette.ink)
            views.setTextViewText(R.id.status, statusLabel(state, protocol))
            views.setTextColor(R.id.status, if (state == State.CONNECTED) palette.connected else palette.muted)
            views.setOnClickPendingIntent(R.id.method, action(context, kind, widgetId, ACTION_CYCLE_METHOD))
        }
        if (kind == Kind.CONSOLE) {
            val snapshot = context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            val speed = if (state == State.CONNECTED) {
                "↓ ${formatSpeed(snapshot.getLong(SPEED_RX, 0))}/s   ↑ ${formatSpeed(snapshot.getLong(SPEED_TX, 0))}/s"
            } else {
                context.getString(R.string.widget_speed_idle)
            }
            views.setTextViewText(R.id.speed, speed)
            views.setTextColor(R.id.speed, palette.ink)
            val ip = snapshot.getString(VPN_IP, "")?.trim().orEmpty()
            val flag = countryFlag(snapshot.getString(VPN_COUNTRY, "").orEmpty())
            views.setTextViewText(
                R.id.ip,
                if (state == State.CONNECTED && ip.isNotEmpty()) {
                    listOf("VPN IP", flag, ip).filter { it.isNotBlank() }.joinToString("  ")
                } else {
                    context.getString(R.string.widget_ip_idle)
                },
            )
            views.setTextColor(R.id.ip, if (state == State.CONNECTED && ip.isNotEmpty()) palette.ink else palette.muted)
            bindChip(views, R.id.h3, transport == "h3", context.getString(R.string.widget_h3), palette)
            bindChip(views, R.id.h2, transport == "h2", context.getString(R.string.widget_h2), palette)
            bindChip(
                views,
                R.id.fallback,
                fallback,
                context.getString(if (fallback) R.string.widget_fallback_on else R.string.widget_fallback_off),
                palette,
            )
            views.setOnClickPendingIntent(R.id.h3, action(context, kind, widgetId, ACTION_SET_H3))
            views.setOnClickPendingIntent(R.id.h2, action(context, kind, widgetId, ACTION_SET_H2))
            views.setOnClickPendingIntent(R.id.fallback, action(context, kind, widgetId, ACTION_TOGGLE_FALLBACK))
            val masque = if (protocol.coreName == "masque") View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.masque_heading, masque)
            views.setViewVisibility(R.id.h3, masque)
            views.setViewVisibility(R.id.h2, masque)
            views.setViewVisibility(R.id.fallback, masque)
        }
        return views
    }

    private fun bindChip(
        views: RemoteViews,
        id: Int,
        selected: Boolean,
        text: String,
        palette: AppAppearance.Palette,
    ) {
        views.setTextViewText(id, text)
        views.setTextColor(id, if (selected) palette.primary else palette.ink)
        views.setInt(id, "setBackgroundResource", if (selected) R.drawable.widget_chip_on else R.drawable.widget_chip)
    }

    private fun action(context: Context, kind: Kind, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, kind.provider).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val request = widgetId * 8 + when (action) {
            ACTION_TOGGLE -> 1
            ACTION_CYCLE_METHOD -> 2
            ACTION_SET_H3 -> 3
            ACTION_SET_H2 -> 4
            else -> 5
        }
        return PendingIntent.getBroadcast(context, request, intent, flags)
    }

    private fun toggle(context: Context) {
        if (runCatching { NativeCore.isRunning() }.getOrDefault(false)) {
            recordStatus(context, AetherVpnService.STATUS_DISCONNECTED)
            context.startService(
                Intent(context, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_DISCONNECT),
            )
            return
        }
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val chosen = protocol(context).coreName
        prefs.edit().putString(DEFAULT_PROTOCOL, chosen).commit()
        val proxy = prefs.getString(CONNECTION_TYPE, "VPN") == "PROXY"
        if (!proxy) {
            val permission = if (CoreConfig.hasIdentity(context)) VpnService.prepare(context) else Intent()
            if (permission != null) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_START_CONNECT, true),
                )
                return
            }
        }
        recordStatus(context, AetherVpnService.STATUS_CONNECTING)
        val connect = Intent(context, AetherVpnService::class.java)
            .setAction(AetherVpnService.ACTION_CONNECT)
            .putExtra(AetherVpnService.EXTRA_VPN_MODE, !proxy)
            .putExtra(AetherVpnService.EXTRA_PROTOCOL, chosen)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(connect)
        } else {
            context.startService(connect)
        }
    }

    private fun cycleMethod(context: Context) {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val current = protocol(context)
        val next = when (current) {
            Protocol.MASQUE -> Protocol.WIREGUARD
            Protocol.WIREGUARD -> Protocol.WARP_IN_WARP
            Protocol.WARP_IN_WARP -> Protocol.MASQUE
        }
        prefs.edit().putString(DEFAULT_PROTOCOL, next.coreName).commit()
        if (runCatching { NativeCore.isRunning() }.getOrDefault(false)) {
            context.startService(
                Intent(context, AetherVpnService::class.java)
                    .setAction(AetherVpnService.ACTION_RECONNECT)
                    .putExtra(AetherVpnService.EXTRA_PROTOCOL, next.coreName),
            )
        }
    }

    private fun setTransport(context: Context, value: String) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(DEFAULT_MASQUE_TRANSPORT, value)
            .commit()
    }

    private fun toggleFallback(context: Context) {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(H3_H2_FALLBACK, !prefs.getBoolean(H3_H2_FALLBACK, true)).commit()
    }

    private fun protocol(context: Context): Protocol {
        val name = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .getString(DEFAULT_PROTOCOL, Protocol.MASQUE.coreName)
        return Protocol.entries.firstOrNull { it.coreName == name } ?: Protocol.MASQUE
    }

    private fun transport(context: Context): String =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .getString(DEFAULT_MASQUE_TRANSPORT, "h3") ?: "h3"

    private fun fallback(context: Context): Boolean =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(H3_H2_FALLBACK, true)

    private fun connectionState(context: Context): State {
        val recorded = context.getSharedPreferences(SNAPSHOT, Context.MODE_PRIVATE)
            .getString(TUNNEL_STATUS, null)
        return when (recorded) {
            AetherVpnService.STATUS_CONNECTED -> State.CONNECTED
            AetherVpnService.STATUS_CONNECTING,
            AetherVpnService.STATUS_STARTING,
            AetherVpnService.STATUS_SCANNING,
            -> State.CONNECTING
            AetherVpnService.STATUS_FAILED,
            AetherVpnService.STATUS_DISCONNECTED,
            -> State.DISCONNECTED
            else -> runCatching {
                when {
                    NativeCore.isReady() -> State.CONNECTED
                    NativeCore.isRunning() -> State.CONNECTING
                    else -> State.DISCONNECTED
                }
            }.getOrDefault(State.DISCONNECTED)
        }
    }

    private fun statusLabel(state: State, protocol: Protocol): String = when (state) {
        State.CONNECTED -> "Connected"
        State.CONNECTING -> "Connecting"
        State.DISCONNECTED -> protocol.label
    }

    enum class Protocol(val label: String, val coreName: String) {
        MASQUE("MASQUE", "masque"),
        WIREGUARD("WireGuard", "wireguard"),
        WARP_IN_WARP("WARP-on-WARP", "gool"),
    }

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    fun connectMark(size: Int, state: State, palette: AppAppearance.Palette): Bitmap {
        val bitmap = Bitmap.createBitmap(size.coerceAtLeast(48), size.coerceAtLeast(48), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = min(cx, cy) * 0.78f
        val (container, accent) = when (state) {
            State.DISCONNECTED, State.CONNECTING -> palette.primaryContainer to palette.primary
            State.CONNECTED -> palette.connectedContainer to palette.connected
        }
        paint.style = Paint.Style.FILL
        paint.color = (if (state == State.CONNECTED) 0x33000000 else 0x22000000) or (accent and 0x00FFFFFF)
        canvas.drawCircle(cx, cy, radius + bitmap.width * 0.04f, paint)
        paint.color = container
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = bitmap.width * 0.028f
        paint.color = accent
        canvas.drawCircle(cx, cy, radius, paint)
        if (state == State.CONNECTED) {
            paint.strokeWidth = bitmap.width * 0.022f
            canvas.drawCircle(cx, cy, radius + bitmap.width * 0.035f, paint)
        }
        val icon = radius * 0.34f
        paint.strokeWidth = bitmap.width * 0.038f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(cx - icon, cy - icon, cx + icon, cy + icon), 330f, 240f, false, paint)
        canvas.drawLine(cx, cy - icon * 1.1f, cx, cy - icon * 0.22f, paint)
        return bitmap
    }

    private fun formatSpeed(bytes: Long): String = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        bytes < 1_073_741_824 -> "${bytes / 1_048_576} MB"
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    }
}
