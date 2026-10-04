package com.netrik.feature.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netrik.BuildConfig
import com.netrik.R
import com.netrik.core.designsystem.component.IconAvatar
import com.netrik.core.designsystem.component.NetrikTopAppBar
import com.netrik.core.designsystem.component.SectionHeader
import com.netrik.core.designsystem.component.groupedItemShape
import com.netrik.core.designsystem.theme.NetrikTheme
import com.netrik.core.settings.AppLanguage
import com.netrik.core.settings.AppLanguages
import com.netrik.core.settings.ThemeMode
import java.util.Locale

/** Where "Report a bug" leads. */
private const val ISSUES_URL = "https://github.com/henriquesebastiao/netrik/issues"

/** Dynamic color (Material You) exists from Android 12 on. */
private val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val language = remember(showLanguage) { AppLanguages.current(context) }

    Scaffold(topBar = { NetrikTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "appearance") {
                SettingsSection(stringResource(R.string.settings_section_appearance)) {
                    ThemeRow(appearance.themeMode, viewModel::setThemeMode, index = 0, count = 3)
                    SwitchRow(
                        icon = R.drawable.ic_palette,
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(
                            when {
                                !dynamicColorSupported -> R.string.settings_dynamic_color_unsupported
                                appearance.dynamicColor -> R.string.settings_dynamic_color_on
                                else -> R.string.settings_dynamic_color_off
                            },
                        ),
                        checked = appearance.dynamicColor && dynamicColorSupported,
                        enabled = dynamicColorSupported,
                        onCheckedChange = viewModel::setDynamicColor,
                        index = 1,
                        count = 3,
                    )
                    SwitchRow(
                        icon = R.drawable.ic_contrast,
                        title = stringResource(R.string.settings_amoled),
                        subtitle = stringResource(R.string.settings_amoled_sub),
                        checked = appearance.amoled,
                        onCheckedChange = viewModel::setAmoled,
                        index = 2,
                        count = 3,
                    )
                }
            }
            item(key = "language") {
                SettingsSection(stringResource(R.string.settings_section_language)) {
                    SettingsRow(
                        icon = R.drawable.ic_language,
                        title = stringResource(R.string.settings_language),
                        subtitle = languageLabel(language),
                        index = 0,
                        count = 1,
                        onClick = { showLanguage = true },
                    )
                }
            }
            item(key = "about") {
                SettingsSection(stringResource(R.string.settings_section_about)) {
                    SettingsRow(
                        icon = R.drawable.ic_info,
                        title = stringResource(R.string.settings_version),
                        subtitle = BuildConfig.VERSION_NAME,
                        subtitleMono = true,
                        index = 0,
                        count = 3,
                    )
                    SettingsRow(
                        icon = R.drawable.ic_bug_report,
                        title = stringResource(R.string.settings_report_bug),
                        subtitle = stringResource(R.string.settings_report_bug_sub),
                        trailing = R.drawable.ic_open_in_new,
                        index = 1,
                        count = 3,
                        onClick = { openLink(context, ISSUES_URL) },
                    )
                    SettingsRow(
                        icon = R.drawable.ic_description,
                        title = stringResource(R.string.settings_licenses),
                        index = 2,
                        count = 3,
                        onClick = { showLicenses = true },
                    )
                }
            }
        }
    }

    if (showLanguage) {
        LanguageDialog(
            current = language,
            onSelect = { choice ->
                showLanguage = false
                if (choice != language) {
                    AppLanguages.set(context, choice)
                    // Android 13+ recreates the activity on its own; older versions need a nudge.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) (context as? Activity)?.recreate()
                }
            },
            onDismiss = { showLanguage = false },
        )
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/** Grouped list item, same look as the hub tool list. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    index: Int,
    count: Int,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    @DrawableRes trailing: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    end: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconAvatar(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = if (subtitleMono) NetrikTheme.dataTypography.dataSmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            end?.invoke()
            if (trailing != null) {
                Icon(painterResource(trailing), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    enabled: Boolean = true,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        index = index,
        count = count,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        end = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** Theme: system / light / dark, as segmented buttons under the title. */
@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, index: Int, count: Int) {
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconAvatar(icon = R.drawable.ic_dark_mode)
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            }
            val options = listOf(
                ThemeMode.System to R.string.settings_theme_system,
                ThemeMode.Light to R.string.settings_theme_light,
                ThemeMode.Dark to R.string.settings_theme_dark,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_language), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = option == current, onClick = { onSelect(option) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(languageLabel(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Language names are written in their own language ("English", "Português (Brasil)"), so anyone can
 * find theirs. "System default" shows which language that means, or that it isn't available.
 */
@Composable
private fun languageLabel(language: AppLanguage): String {
    val context = LocalContext.current
    return when (language) {
        AppLanguage.System -> {
            val system = AppLanguages.systemLocale(context)
            val name = system.getDisplayLanguage(system).replaceFirstChar { it.titlecase(system) }
            if (AppLanguages.isSupported(system)) {
                stringResource(R.string.settings_language_system_current, name)
            } else {
                stringResource(R.string.settings_language_system_fallback, name)
            }
        }
        AppLanguage.English -> "English"
        AppLanguage.PortugueseBrazil -> Locale.forLanguageTag("pt-BR").let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase() }
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_description), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Text(
                text = stringResource(R.string.about_licenses),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.settings_no_browser), Toast.LENGTH_SHORT).show()
    }
}
