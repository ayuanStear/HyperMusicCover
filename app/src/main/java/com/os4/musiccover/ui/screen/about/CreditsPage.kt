package com.os4.musiccover.ui.screen.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.os4.musiccover.R
import com.os4.musiccover.ui.util.PageScaffold
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * Who this module borrowed from.
 *
 * Every project here is one whose code, design or data is in the build - not a list of things
 * that were admired from a distance. The summary says what was taken, because "thanks to X" with
 * nothing after it tells a reader nothing and cannot be checked against the source.
 *
 * Names are not translated: they are what their authors call them, in both languages. Only the
 * line saying what was taken is a string resource.
 *
 * Every row opens the project it names, so every entry has to have somewhere to go.
 */
private data class Credit(
    val name: String,
    val summary: String,
)

@Composable
fun CreditsPageContent(
    onBack: () -> Unit,
    isBlurEnabled: Boolean = true,
) {
    val uiCredits = listOf(
        Credit(
            "HyperNavBar",
            stringResource(R.string.credits_hypernavbar),
        ),
        Credit(
            "miuix",
            stringResource(R.string.credits_miuix),
        ),
        Credit(
            "AndroidLiquidGlass",
            stringResource(R.string.credits_liquid_glass),
        ),
    )

    val lyricCredits = listOf(
        Credit(
            "@CialloUM",
            stringResource(R.string.credits_cialloum),
        ),
        Credit(
            "@Leaf-lsgtky",
            stringResource(R.string.credits_leaf),
        ),
        Credit(
            "HyperChanger",
            stringResource(R.string.credits_hyperchanger),
        ),
        Credit(
            "HyperTweak",
            stringResource(R.string.credits_hypertweak),
        ),
        Credit(
            "AMLL",
            stringResource(R.string.credits_amll),
        ),
        Credit(
            "AMLL TTML DB",
            stringResource(R.string.credits_amll_db),
        ),
        Credit(
            "Accompanist Lyrics",
            stringResource(R.string.credits_accompanist),
        ),
        Credit(
            "LyricInfo",
            stringResource(R.string.credits_lyricinfo),
        ),
        Credit(
            "HyperLyrics Enhanced",
            stringResource(R.string.credits_hle),
        ),
    )

    val toolCredits = listOf(
        // The fork, not the original: it is the one this module actually read.
        Credit(
            "InstallerX Revived",
            stringResource(R.string.credits_installerx),
        ),
        Credit(
            "LSPosed",
            stringResource(R.string.credits_lsposed),
        ),
    )

    // This group leads, so the first name on the page is InstallerX Revived.
    val groups = remember(uiCredits, lyricCredits, toolCredits) {
        listOf(
            R.string.credits_group_tools to toolCredits,
            R.string.credits_group_ui to uiCredits,
            R.string.credits_group_lyrics to lyricCredits,
        )
    }

    PageScaffold(
        title = stringResource(R.string.about_credits),
        isBlurEnabled = isBlurEnabled,
        onBack = onBack,
    ) {
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
            ) {
                BasicComponent(summary = stringResource(R.string.credits_intro))
            }
        }

        groups.forEach { (heading, credits) ->
            item {
                SmallTitle(
                    text = stringResource(heading),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    credits.forEach { credit ->
                        BasicComponent(summary = "${credit.name}\n${credit.summary}")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
