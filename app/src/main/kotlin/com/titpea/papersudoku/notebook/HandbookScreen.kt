package com.titpea.papersudoku.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.HandbookExamples
import com.titpea.papersudoku.page.Board
import com.titpea.papersudoku.page.CoachUi
import com.titpea.papersudoku.page.coachMarks
import com.titpea.papersudoku.page.techniqueName
import com.titpea.papersudoku.page.techniqueSummary
import com.titpea.papersudoku.page.workedExample
import com.titpea.papersudoku.settings.SettingsScaffold

/** What a handbook page is about: using the pen, or one technique. */
sealed interface HandbookTopic {
    data object Pen : HandbookTopic
    data class Of(val technique: Technique) : HandbookTopic

    companion object {
        val all: List<HandbookTopic> = listOf(Pen) + Technique.entries.map(::Of)
    }
}

/**
 * The handbook: how to use the pen, then each technique with a worked example from a real
 * practice page, drawn the way the Coach draws it (the unit, the pattern, the crossed-out
 * candidates, the cell it settles) and explained in the Coach's own words. It opens at the topic
 * tapped in the notebook; the rest are a scroll away.
 */
@Composable
fun HandbookScreen(start: Int, techniquesMet: Set<Technique>, onBack: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, HandbookTopic.all.lastIndex))
    SettingsScaffold(
        title = stringResource(R.string.notebook_techniques),
        onBack = onBack,
        tag = "handbook-screen",
        listState = listState,
    ) {
        HandbookTopic.all.forEach { topic ->
            item(key = topic.toString()) {
                when (topic) {
                    HandbookTopic.Pen -> PenSection()
                    is HandbookTopic.Of -> TechniqueSection(topic.technique, topic.technique in techniquesMet)
                }
            }
        }
    }
}

@Composable
private fun SectionColumn(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Title(text: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PenSection() {
    SectionColumn("handbook-pen") {
        Title(stringResource(R.string.handbook_pen))
        listOf(
            R.string.handbook_pen_tap,
            R.string.handbook_pen_aim,
            R.string.handbook_pen_hold,
            R.string.handbook_pen_pencil,
            R.string.handbook_pen_fill,
            R.string.handbook_pen_keyboard,
        ).forEach { Body(stringResource(it)) }
    }
}

@Composable
private fun TechniqueSection(technique: Technique, met: Boolean) {
    val page = remember(technique) { HandbookExamples.page(technique) }
    val hint = remember(technique) { HandbookExamples.of(technique).hint }
    val geometry = page.spec.geometry
    // The board as the Coach's Why shows it, on the step that teaches this technique.
    val coach = CoachUi(hint, level = 2, step = hint.groundwork.lastIndex.coerceAtLeast(0))
    val marks = remember(technique) { coachMarks(geometry, page.values, coach) }
    SectionColumn("handbook-${technique.name}") {
        Title(techniqueName(technique), if (met) stringResource(R.string.technique_seen) else null)
        Body(techniqueSummary(technique))
        Board(
            game = page,
            conflicts = emptySet(),
            marks = marks,
            onTap = {},
            onCellAction = { _, _ -> },
            interactive = false,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally),
        )
        Text(
            stringResource(R.string.handbook_example),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        workedExample(geometry, hint).forEach { Body(it) }
    }
}
