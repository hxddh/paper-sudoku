package com.titpea.papersudoku.settings

import androidx.annotation.RawRes
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.SectionHeader
import com.titpea.papersudoku.designsystem.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rows and headers share one column, at most this wide, centred on large screens. */
private val ColumnWidth = 640.dp

/**
 * What the app is and what it is made with: its version and privacy note, then the typeface, icons
 * and libraries it ships, each with its licence in full (the SIL Open Font License travels with
 * Fraunces, the Apache License with the rest). Reached from the notebook's foot; there are no
 * settings (11.0).
 */
@Composable
fun LicencesScreen(versionName: String, onBack: () -> Unit) {
    val ofl = rawText(R.raw.license_ofl)
    val apache = rawText(R.raw.license_apache)
    LicencesScaffold(title = stringResource(R.string.licences_title), onBack = onBack, tag = "licences") {
        item { Entry(stringResource(R.string.app_name), stringResource(R.string.settings_version, versionName)) }
        item { Entry(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_body)) }
        item { Header(stringResource(R.string.licences_fonts)) }
        item { Entry(stringResource(R.string.licences_fraunces), stringResource(R.string.licences_fraunces_body)) }
        item { LicenceText(ofl) }
        item { Header(stringResource(R.string.licences_software)) }
        item { Entry(stringResource(R.string.licences_symbols), stringResource(R.string.licences_symbols_body)) }
        item { Entry(stringResource(R.string.licences_libraries), stringResource(R.string.licences_libraries_body)) }
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

/** A column on the page's one text edge. */
private fun Modifier.textColumn(): Modifier =
    widthIn(max = ColumnWidth).fillMaxWidth().padding(horizontal = Space.TextEdge)

@Composable
private fun LicenceText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.textColumn().padding(vertical = Space.S),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicencesScaffold(
    title: String,
    onBack: () -> Unit,
    tag: String,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    // The bar takes a surface once the list scrolls under it, so rows never run under the title.
    val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
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
                    containerColor = if (listState.canScrollBackward) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(tag),
            state = listState,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Space.XL,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun Header(text: String) {
    SectionHeader(text, Modifier.textColumn().padding(top = Space.XL, bottom = Space.XS))
}

/** A title and a line under it, on the text edge. */
@Composable
private fun Entry(title: String, body: String) {
    Column(Modifier.textColumn().padding(vertical = Space.S)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
