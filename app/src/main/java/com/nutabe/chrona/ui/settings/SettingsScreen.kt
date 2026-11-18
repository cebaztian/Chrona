package com.nutabe.chrona.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.AppContainer
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.alarms.RowDivider
import com.nutabe.chrona.ui.alarms.SettingRow
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.InfoPill
import com.nutabe.chrona.ui.components.hapticClickable
import com.nutabe.chrona.ui.health.issueCount
import com.nutabe.chrona.ui.health.rememberHealthItems

/** Stateless: the badge count and the version come in as plain values. */
@Composable
fun SettingsScreen(
    issueCount: Int,
    versionName: String,
    onBack: () -> Unit,
    onHealth: () -> Unit,
    onOnboarding: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).hapticClickable(onClick = onBack).testTag("settings-back"), contentAlignment = Alignment.Center) {
                        ChronaIcon(R.drawable.ic_caret_left, contentDescription = stringResource(R.string.back_cd))
                    }
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, modifier = Modifier.padding(start = 8.dp))
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EyebrowTag(stringResource(R.string.settings_general))
            DoubleBezelCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    R.drawable.ic_shield,
                    stringResource(R.string.settings_health),
                    onClick = onHealth,
                    modifier = Modifier.testTag("settings-health"),
                    trailing = {
                        if (issueCount > 0) {
                            InfoPill(
                                pluralStringResource(R.plurals.health_issues, issueCount, issueCount),
                                icon = R.drawable.ic_warning,
                                textColor = scheme.primary,
                                modifier = Modifier.testTag("settings-issues"),
                            )
                        } else {
                            Text(stringResource(R.string.settings_all_good), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        ChronaIcon(R.drawable.ic_caret_right, size = 20.dp)
                    },
                )
                RowDivider()
                SettingRow(R.drawable.ic_book_open, stringResource(R.string.settings_onboarding), onClick = onOnboarding, modifier = Modifier.testTag("settings-onboarding"))
                RowDivider()
                SettingRow(R.drawable.ic_info, stringResource(R.string.settings_version), value = versionName, modifier = Modifier.testTag("settings-version"))
            }
        }
    }
}

@Composable
fun SettingsHost(container: AppContainer, onBack: () -> Unit, onHealth: () -> Unit, onOnboarding: () -> Unit) {
    val context = LocalContext.current
    val items = rememberHealthItems(container)
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    SettingsScreen(issueCount(items), version, onBack, onHealth, onOnboarding)
}
