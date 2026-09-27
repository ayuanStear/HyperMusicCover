// SPDX-License-Identifier: Apache-2.0
package com.os4.musiccover

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.os4.musiccover.ui.screen.features.ValueSlider
import com.os4.musiccover.ui.theme.AppTheme
import com.os4.musiccover.ui.util.PageScaffold
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * The Xiaomi super island's length - the pill the system puts at the top of the screen while
 * something is playing, not the module's own lock screen pill.
 *
 * A screen of its own rather than a row on the lock screen island's page, because the two have
 * nothing to do with each other: that one is drawn by the module inside the keyguard's shortcut
 * row, this one by the control centre plugin as a window of its own, and the numbers do not
 * travel between them. Anything set here is left untouched on the other page and the other way
 * round (2026-09-27).
 */
class IslandActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, LocaleHelper.getSavedLanguage(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings.load(this)
        val theme = runCatching { ColorSchemeMode.valueOf(settings.themeMode) }
            .getOrDefault(ColorSchemeMode.System)
        setContent { AppTheme(themeMode = theme) { IslandPage(settings.isBlurEnabled, ::finish) } }
    }
}

@Composable
private fun IslandPage(blur: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val metrics = LocalDensity.current
    // The display's own width in pixels: the user asked for the longest length the screen can
    // hold, and that is this number - not the dp-rounded Configuration.screenWidthDp.
    val screenWidthPx = remember(context) { context.resources.displayMetrics.widthPixels }
    val fallbackMinPx = with(metrics) { DEFAULT_MIN_DP.dp.roundToPx() }

    var alive by remember { mutableStateOf(false) }
    // The floor the plugin gives the island. 108dp is what it is on this build; the measured
    // answer takes over as soon as the island has been drawn once.
    var minPx by remember { mutableStateOf(fallbackMinPx) }
    var enabled by remember { mutableStateOf(false) }
    var lengthPx by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val reply = ModuleBridge.queryAlive(context)
        alive = reply.alive
        if (!reply.alive) return@LaunchedEffect
        if (reply.islandMinPx > 0) minPx = reply.islandMinPx
        // Where the slider starts: the length the island is already being drawn at, so turning
        // the switch on changes nothing until the thumb is moved. Until the island has been seen
        // once - or when nothing has played yet - it starts halfway, which is at least a length
        // the user can see and drag away from.
        val settled = when {
            reply.islandLengthPx > 0 -> reply.islandLengthPx
            reply.islandSystemPx > 0 -> reply.islandSystemPx
            else -> (minPx + screenWidthPx) / 2
        }
        lengthPx = settled.coerceIn(minPx, screenWidthPx)
        enabled = reply.islandLengthPx > 0
    }

    fun push(px: Int) {
        lengthPx = px
        ModuleBridge.setIslandLength(context, px)
    }

    PageScaffold(title = stringResource(R.string.features_island_title), isBlurEnabled = blur, onBack = onBack) {
        item {
            Card(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.island_custom),
                        summary = if (alive) {
                            stringResource(R.string.island_custom_summary)
                        } else {
                            stringResource(R.string.island_waiting)
                        },
                        checked = enabled,
                        enabled = alive,
                        onCheckedChange = { on ->
                            enabled = on
                            // On with nothing chosen yet sends the length the slider is already
                            // showing, rather than 0, which would mean "the setting is off" and
                            // leave the switch lying about what the island is doing.
                            push(if (on) lengthPx.coerceIn(minPx, screenWidthPx) else 0)
                        },
                    )
                    AnimatedVisibility(
                        visible = enabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ValueSlider(
                            title = stringResource(R.string.island_length),
                            summary = stringResource(R.string.island_length_summary, screenWidthPx),
                            value = lengthPx.toFloat().coerceIn(minPx.toFloat(), screenWidthPx.toFloat()),
                            valueRange = minPx.toFloat()..screenWidthPx.toFloat(),
                            enabled = alive,
                            label = { "${it.roundToInt()} px" },
                            onValueChange = { v -> push(v.roundToInt().coerceIn(minPx, screenWidthPx)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where the length slider starts before the plugin has said what its own floor is: the same
 * 108dp the island's minimum width resource is on this build.
 */
private const val DEFAULT_MIN_DP = 108f
