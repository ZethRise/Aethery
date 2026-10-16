package studio.cluvex.aethery

import android.content.Context
import android.content.res.Configuration
import android.os.Build

object AppAppearance {
    const val PREFERENCE = "appearance_theme"

    enum class Theme(
        val label: String,
        val description: String,
        val followsSystem: Boolean = false,
        val canvas: Long = 0L,
        val surface: Long = 0L,
        val surfaceVariant: Long = 0L,
        val ink: Long = 0L,
        val muted: Long = 0L,
        val divider: Long = 0L,
        val primary: Long = 0L,
        val primaryContainer: Long = 0L,
        val connected: Long = 0L,
        val connectedContainer: Long = 0L,
        val skyTop: Long = 0L,
        val skyBottom: Long = 0L,
    ) {
        HALO(
            "Halo",
            "Default. Dusk sky that warms to dawn when you connect",
            canvas = 0xFF0B0D24,
            surface = 0xFF1B1A45,
            surfaceVariant = 0xFF26245C,
            ink = 0xFFF2EEFF,
            muted = 0xFFA9A7C9,
            divider = 0xFF3A3870,
            primary = 0xFF9DB0FF,
            primaryContainer = 0xFF2B3470,
            connected = 0xFFFFC58A,
            connectedContainer = 0xFF5A3A1E,
            skyTop = 0xFF0B0D24,
            skyBottom = 0xFF1B1A45,
        ),
        DYNAMIC(
            "Dynamic",
            "Follows the phone's light or dark theme",
            followsSystem = true,
            connected = 0xFF8FFFB5,
            connectedContainer = 0xFF176B3B,
        ),
        FOREST(
            "Forest",
            "Current green on charcoal",
            canvas = 0xFF101411,
            surface = 0xFF171C18,
            surfaceVariant = 0xFF222A24,
            ink = 0xFFE8F1EA,
            muted = 0xFFB9C6BB,
            divider = 0xFF3B473E,
            primary = 0xFFA4D8BB,
            primaryContainer = 0xFF1F4030,
            connected = 0xFF8FFFB5,
            connectedContainer = 0xFF176B3B,
        ),
        BLUE_BLACK(
            "Blue / black",
            "Ice blue on near-black",
            canvas = 0xFF0B1016,
            surface = 0xFF121821,
            surfaceVariant = 0xFF1B2430,
            ink = 0xFFE6EEF8,
            muted = 0xFFA9B7C9,
            divider = 0xFF334155,
            primary = 0xFF7EB6FF,
            primaryContainer = 0xFF16324F,
            connected = 0xFF9CD0FF,
            connectedContainer = 0xFF0F3A66,
        ),
        BLUE_GOLD(
            "Blue / gold",
            "Gold accents on deep navy",
            canvas = 0xFF0C1424,
            surface = 0xFF142038,
            surfaceVariant = 0xFF1C2C48,
            ink = 0xFFF4EFE3,
            muted = 0xFFC4B79A,
            divider = 0xFF3D4E6B,
            primary = 0xFFE6C36A,
            primaryContainer = 0xFF3A3118,
            connected = 0xFFFFD78A,
            connectedContainer = 0xFF5A4716,
        ),
        GOLD_BLACK(
            "Gold / black",
            "Warm gold on black",
            canvas = 0xFF0E0C08,
            surface = 0xFF17140E,
            surfaceVariant = 0xFF241F16,
            ink = 0xFFF6F0E4,
            muted = 0xFFC9BFA8,
            divider = 0xFF3E372A,
            primary = 0xFFE8C36A,
            primaryContainer = 0xFF3A2F14,
            connected = 0xFFFFE08A,
            connectedContainer = 0xFF5C4714,
        ),
    }

    data class Palette(
        val canvas: Int,
        val surface: Int,
        val surfaceVariant: Int,
        val ink: Int,
        val muted: Int,
        val divider: Int,
        val primary: Int,
        val primaryContainer: Int,
        val connected: Int,
        val connectedContainer: Int,
        val theme: Theme,
        val skyTop: Int = canvas,
        val skyBottom: Int = surface,
        val dawn: Int = connected,
        val warning: Int = 0xFFFFB24A.toInt(),
        val error: Int = 0xFFFF8FA3.toInt(),
    )

    fun current(context: Context): Theme {
        val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(PREFERENCE, null)
        return Theme.entries.firstOrNull { it.name == name } ?: Theme.HALO
    }

    fun save(context: Context, theme: Theme) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFERENCE, theme.name)
            .apply()
    }

    fun load(context: Context): Palette {
        val theme = current(context)
        if (theme.followsSystem) return systemPalette(context)
        return Palette(
            canvas = theme.canvas.toInt(),
            surface = theme.surface.toInt(),
            surfaceVariant = theme.surfaceVariant.toInt(),
            ink = theme.ink.toInt(),
            muted = theme.muted.toInt(),
            divider = theme.divider.toInt(),
            primary = theme.primary.toInt(),
            primaryContainer = theme.primaryContainer.toInt(),
            connected = theme.connected.toInt(),
            connectedContainer = theme.connectedContainer.toInt(),
            theme = theme,
            skyTop = (if (theme.skyTop != 0L) theme.skyTop else theme.canvas).toInt(),
            skyBottom = (if (theme.skyBottom != 0L) theme.skyBottom else theme.surface).toInt(),
        )
    }

    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun systemPalette(context: Context): Palette {
        val night = isNight(context)
        fun color(resource: Int, fallback: Long): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(resource)
            } else {
                fallback.toInt()
            }
        return if (night) {
            Palette(
                canvas = color(android.R.color.system_neutral1_900, 0xFF101411),
                surface = color(android.R.color.system_neutral1_800, 0xFF171C18),
                surfaceVariant = color(android.R.color.system_neutral2_800, 0xFF222A24),
                ink = color(android.R.color.system_neutral1_50, 0xFFE8F1EA),
                muted = color(android.R.color.system_neutral2_300, 0xFFB9C6BB),
                divider = color(android.R.color.system_neutral2_600, 0xFF3B473E),
                primary = color(android.R.color.system_accent1_200, 0xFFA4D8BB),
                primaryContainer = color(android.R.color.system_accent1_800, 0xFF1F4030),
                connected = 0xFF8FFFB5.toInt(),
                connectedContainer = 0xFF176B3B.toInt(),
                theme = Theme.DYNAMIC,
            )
        } else {
            Palette(
                canvas = color(android.R.color.system_neutral1_10, 0xFFF6F8F6),
                surface = color(android.R.color.system_neutral1_50, 0xFFEEF2EE),
                surfaceVariant = color(android.R.color.system_neutral2_100, 0xFFE3E8E3),
                ink = color(android.R.color.system_neutral1_900, 0xFF151916),
                muted = color(android.R.color.system_neutral2_600, 0xFF5B655D),
                divider = color(android.R.color.system_neutral2_200, 0xFFC5CDC6),
                primary = color(android.R.color.system_accent1_600, 0xFF2F6B4A),
                primaryContainer = color(android.R.color.system_accent1_100, 0xFFD4EEDF),
                connected = 0xFF176B3B.toInt(),
                connectedContainer = 0xFFD7F5E3.toInt(),
                theme = Theme.DYNAMIC,
            )
        }
    }
}
