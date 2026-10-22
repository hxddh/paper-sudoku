package com.titpea.papersudoku.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.data.ThemeMode
import com.titpea.papersudoku.data.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    versionName: String,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings"),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Header(stringResource(R.string.settings_appearance)) }
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    val options = listOf(
                        ThemeMode.System to R.string.theme_system,
                        ThemeMode.Light to R.string.theme_light,
                        ThemeMode.Dark to R.string.theme_dark,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_language)) },
                        supportingContent = { Text(stringResource(R.string.settings_language_desc)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .clickable {
                                // The system's per-app language screen (Android 13+).
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null)),
                                )
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
            item { InfoRow(stringResource(R.string.settings_fonts_title), stringResource(R.string.settings_fonts_body)) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
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
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun InfoRow(title: String, body: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
    )
}
