package studio.cluvex.aethery

import android.content.Context
import org.json.JSONObject
import java.io.File

object CoreConfig {
    fun json(context: Context, protocol: String? = null): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        fun text(key: String, fallback: String = "") =
            prefs.getString(key, fallback)?.trim().orEmpty()
        val manualObfuscation = JSONObject().apply {
            text("obfuscation_jc").toIntOrNull()?.let { put("jc", it) }
            text("obfuscation_jmin").toIntOrNull()?.let { put("jmin", it) }
            text("obfuscation_jmax").toIntOrNull()?.let { put("jmax", it) }
            putOpt("i1", text("obfuscation_i1").ifBlank { null })
            putOpt("i2", text("obfuscation_i2").ifBlank { null })
        }

        val proxy = text("connection_type", "VPN") == "PROXY"
        val lan = prefs.getBoolean("lan_sharing", false)
        val listenHost = if (proxy && lan) "0.0.0.0" else "127.0.0.1"
        return JSONObject().apply {
            put("config_path", File(context.filesDir, "aether.toml").absolutePath)
            val method = protocol ?: text("default_protocol", "masque")
            // "gool-masque" is the app's name for upstream's default gool: the core knows it as gool
            // with its inner WireGuard carried through MASQUE.
            put("protocol", if (method == "gool-masque") "gool" else method)
            if (method == "gool-masque") put("gool_mode", "masque")
            put("listen", "$listenHost:${prefs.getInt("default_socks_port", 1819)}")
            put("http_proxy", "$listenHost:${prefs.getInt("default_http_proxy_port", 1820)}")
            if (proxy && lan) {
                put("proxy_user", "aethery")
                put("proxy_pass", lanProxyPassword(prefs))
            }
            put("scan_mode", text("default_scan_mode", "balanced"))
            put("ip_scan", text("default_scan", "v4"))
            put("endpoint_cache_path", File(context.filesDir, "masque-gateway-cache.json").absolutePath)
            put("endpoint_discovery", text("endpoint_discovery", "cache"))
            put("masque_transport", text("default_masque_transport", "h3"))
            putOpt("forced_peer", text("manual_endpoint").ifBlank { null })
            put("obfuscation_profile", text("obfuscation_profile", "balanced"))
            putOpt("obfuscation_parameters", manualObfuscation.takeIf { it.length() > 0 }?.toString())
            put("retry_obfuscation_profiles", prefs.getBoolean("retry_obfuscation_profiles", true))
            put("tls_curve_preset", text("tls_curve_preset", "chrome"))
            put("wireguard_data_check", prefs.getBoolean("wireguard_data_check", true))
            put("log_level", text("log_level", "info"))
            put("perf_profile", text("perf_profile", "auto"))
            put("h2_fragmentation", text("h2_fragmentation", "on") == "on")
            put("h2_fallback", prefs.getBoolean("h3_h2_fallback", true))
            putOpt("dns_servers", text("dns_servers").ifBlank { null })
            putOpt("route_block", text("route_block").ifBlank { null })
            putOpt("route_direct", text("route_direct").ifBlank { null })
            putOpt("team", SecureStore.getSecret(context, "zero_trust_team").ifBlank { null })
            putOpt("access_client_id", SecureStore.getSecret(context, "zero_trust_client_id").ifBlank { null })
            putOpt("access_client_secret", SecureStore.getSecret(context, "zero_trust_client_secret").ifBlank { null })
            putOpt("access_token", SecureStore.getSecret(context, "zero_trust_token").ifBlank { null })
            putOpt("access_email", SecureStore.getSecret(context, "zero_trust_email").ifBlank { null })
            put("gateway", prefs.getBoolean("zero_trust_gateway", false))
            putOpt("exit_location", text("exit_location").ifBlank { null })
            putOpt("ech", text("ech", "off").takeIf { it != "off" && it.isNotBlank() })
            if (!prefs.getBoolean("quic_v2", true)) put("quic_v2", false)
            if (prefs.getBoolean("quick_reconnect", false)) put("quick_reconnect", true)
            if (prefs.getBoolean("disable_grease", false)) put("disable_grease", true)
            putOpt("fragment_size", text("fragment_size").ifBlank { null })
            putOpt("fragment_delay", text("fragment_delay").ifBlank { null })
            text("wg_keepalive").toIntOrNull()?.takeIf { it > 0 }?.let { put("keepalive", it) }
        }.toString()
    }

    /**
     * True once any identity has been saved. Callers that would ask Android whether VPN permission
     * is held must register first when this is false, because that question drops other VPNs.
     */
    fun hasIdentity(context: Context): Boolean =
        context.filesDir.listFiles().orEmpty().any { it.name.startsWith("aether") && it.name.endsWith(".toml") }

    fun lanProxyPassword(prefs: android.content.SharedPreferences): String {
        val existing = prefs.getString("lan_proxy_password", "")?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val generated = (1..12).map { alphabet.random() }.joinToString("")
        prefs.edit().putString("lan_proxy_password", generated).apply()
        return generated
    }
}
