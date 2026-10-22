package com.titpea.papersudoku.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.data.ThemeMode
import com.titpea.papersudoku.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rows and headers share one column, at most this wide, centred on large screens. */
private val ColumnWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    versionName: String,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack, tag = "settings") {
        item { Header(stringResource(R.string.settings_appearance)) }
        item {
            Column(Modifier.widthIn(max = ColumnWidth).fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                val options = listOf(
                    ThemeMode.System to R.string.theme_system,
                    ThemeMode.Light to R.string.theme_light,
                    ThemeMode.Dark to R.string.theme_dark,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { onUpdate { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                val context = LocalContext.current
                LinkRow(
                    title = stringResource(R.string.settings_language),
                    body = stringResource(R.string.settings_language_desc),
                    // It opens the system's screen, not one of ours: say so with the icon.
                    external = true,
                    onClick = {
                        // The system's per-app language screen (Android 13+); a device without
                        // it falls back to the app's system settings.
                        val app = Uri.fromParts("package", context.packageName, null)
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(app))
                        }.recoverCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(app))
                        }
                    },
                )
            }
        }
        item { Header(stringResource(R.string.settings_play)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_auto_clear),
                body = stringResource(R.string.settings_auto_clear_desc),
                checked = settings.autoClearNotes,
                onChange = { v -> onUpdate { it.copy(autoClearNotes = v) } },
            )
        }
        item { Header(stringResource(R.string.settings_about)) }
        item { InfoRow(stringResource(R.string.app_name), stringResource(R.string.settings_version, versionName)) }
        item { InfoRow(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_body)) }
        item {
            LinkRow(
                title = stringResource(R.string.settings_licences_title),
                body = stringResource(R.string.settings_licences_body),
                external = false,
                onClick = onOpenLicences,
                modifier = Modifier.testTag("open-licences"),
            )
        }
    }
}

/**
 * The typeface, icons and libraries the app ships, each with its licence in full: the SIL Open
 * Font License travels with Fraunces, and the Apache License with the rest.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val ofl = rawText(R.raw.license_ofl)
    val apache = rawText(R.raw.license_apache)
    SettingsScaffold(title = stringResource(R.string.licences_title), onBack = onBack, tag = "licences") {
        item { Header(stringResource(R.string.licences_fonts)) }
        item { InfoRow(stringResource(R.string.licences_fraunces), stringResource(R.string.licences_fraunces_body)) }
        item { LicenceText(ofl) }
        item { Header(stringResource(R.string.licences_software)) }
        item { InfoRow(stringResource(R.string.licences_symbols), stringResource(R.string.licences_symbols_body)) }
        item { InfoRow(stringResource(R.string.licences_libraries), stringResource(R.string.licences_libraries_body)) }
        item { LicenceText(apache) }
    }
}

@Composable
private fun rawText(@RawRes id: Int): String {
    val resources = LocalContext.current.resources
    val text by produceState("", id) {
        value = withContext(Dispatchers.IO) { resources.openRawResource(id).bufferedReader().use { it.readText() } }
    }
    return text
}

@Composable
private fun LicenceText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = ColumnWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    tag: String,
    content: LazyListScope.() -> Unit,
) {
    // The bar takes a surface once the list scrolls under it, so rows never run under the title.
    val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(tag),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** A section title: the same token as the notebook's, lined up with the rows' text. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun Header(text: String) {
    SectionHeader(
        text,
        Modifier
            .widthIn(max = ColumnWidth)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .widthIn(max = ColumnWidth)
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun LinkRow(
    title: String,
    body: String,
    external: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = if (external) {
            {
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .widthIn(max = ColumnWidth)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun InfoRow(title: String, body: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.widthIn(max = ColumnWidth).fillMaxWidth(),
    )
}
