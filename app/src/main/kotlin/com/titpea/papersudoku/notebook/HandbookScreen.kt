package com.titpea.papersudoku.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.HandbookExamples
import com.titpea.papersudoku.game.model.Mastery
import com.titpea.papersudoku.page.Board
import com.titpea.papersudoku.page.CoachUi
import com.titpea.papersudoku.page.DOT
import com.titpea.papersudoku.page.coachMarks
import com.titpea.papersudoku.page.techniqueName
import com.titpea.papersudoku.page.techniqueSummary
import com.titpea.papersudoku.page.workedExample
import kotlinx.coroutines.launch

/** What a handbook page is about: using the pen, or one technique. */
sealed interface HandbookTopic {
    data object Pen : HandbookTopic
    data class Of(val technique: Technique) : HandbookTopic

    companion object {
        val all: List<HandbookTopic> = listOf(Pen) + Technique.entries.map(::Of)

        /** The page a technique is on: after using the pen, in learning order. */
        fun indexOf(technique: Technique): Int = all.indexOf(Of(technique))
    }
}

/**
 * The handbook: a paper page per topic, turned like the notebook's. First how to use the pen,
 * then one technique a page, led by its worked example from a real practice page, drawn the way
 * the Coach draws it (the unit, the pattern, the crossed-out candidates, the cell it settles),
 * then how well it is known, in ink (seen, practised, known), and the Coach's own words for it.
 * It opens at the topic tapped in the notebook; the header names the page and where it is.
 *
 * [mastery] says how well each technique is known; [techniquesMet] only feeds its default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandbookScreen(
    start: Int,
    techniquesMet: Set<Technique>,
    onBack: () -> Unit,
    mastery: (Technique) -> Mastery = { Mastery.of(it in techniquesMet, 0) },
) {
    val topics = HandbookTopic.all
    val pager = rememberPagerState(initialPage = start.coerceIn(0, topics.lastIndex)) { topics.size }
    val scope = rememberCoroutineScope()
    val current = topics[pager.currentPage.coerceIn(0, topics.lastIndex)]
    Scaffold(
        modifier = Modifier.testTag("handbook-screen"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // A page header, as on a page: back, the overline, the title.
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("handbook-back")) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(
                            stringResource(R.string.notebook_techniques) + DOT +
                                stringResource(R.string.handbook_position, pager.currentPage + 1, topics.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.testTag("handbook-position"),
                        )
                        Text(
                            topicTitle(current),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.testTag("handbook-title").semantics { heading() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                // Two lines of title: the bar grows at large text instead of clipping them.
                expandedHeight = LocalDensity.current.fontScale.let { scale ->
                    maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, (24 * 2 * scale + 12).dp)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state = pager,
                key = { topics[it].toString() },
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("handbook-pager"),
            ) { index ->
                when (val topic = topics[index]) {
                    HandbookTopic.Pen -> PenPage()
                    is HandbookTopic.Of -> TechniquePage(topic.technique, mastery(topic.technique))
                }
            }
            // Turning without a swipe: for TalkBack, a keyboard, or a tap. Ink, at the page's foot.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    enabled = pager.currentPage > 0,
                    modifier = Modifier.testTag("handbook-previous"),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.handbook_previous))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    enabled = pager.currentPage < topics.lastIndex,
                    modifier = Modifier.testTag("handbook-next"),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.handbook_next))
                }
            }
        }
    }
}

@Composable
private fun topicTitle(topic: HandbookTopic): String = when (topic) {
    HandbookTopic.Pen -> stringResource(R.string.handbook_pen)
    is HandbookTopic.Of -> techniqueName(topic.technique)
}

/** One handbook page: it scrolls on its own, so a long example never pushes the next page away. */
@Composable
private fun HandbookPage(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** The words under the board, on the notebook's one 20dp text edge. */
@Composable
private fun TextColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PenPage() {
    HandbookPage("handbook-pen") {
        TextColumn {
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
}

@Composable
private fun TechniquePage(technique: Technique, mastery: Mastery) {
    val page = remember(technique) { HandbookExamples.page(technique) }
    val hint = remember(technique) { HandbookExamples.of(technique).hint }
    val geometry = page.spec.geometry
    // The board as the Coach's Why shows it, on the step that teaches this technique.
    val coach = CoachUi(hint, level = 2, step = hint.groundwork.lastIndex.coerceAtLeast(0))
    val marks = remember(technique) { coachMarks(geometry, page.values, coach) }
    HandbookPage("handbook-${technique.name}") {
        // The board first, as on a page: 8dp margins, as wide as the window allows.
        Board(
            game = page,
            conflicts = emptySet(),
            marks = marks,
            onTap = {},
            onCellAction = { _, _ -> },
            interactive = false,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        TextColumn {
            MasteryInk(mastery)
            Body(techniqueSummary(technique))
            Text(
                stringResource(R.string.handbook_example),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            workedExample(geometry, hint).forEach { Body(it) }
        }
    }
}

/**
 * How well the technique is known, as ink rather than a score: three pen strokes, inked as far as
 * it goes (seen, practised, known), and the word. Nothing at all before it is met.
 */
@Composable
private fun MasteryInk(mastery: Mastery) {
    val inked = when (mastery) {
        Mastery.Unmet -> 0
        Mastery.Met -> 1
        Mastery.Practised -> 2
        Mastery.Mastered -> 3
    }
    val word = when (mastery) {
        Mastery.Unmet -> null
        Mastery.Met -> R.string.mastery_met
        Mastery.Practised -> R.string.mastery_practised
        Mastery.Mastered -> R.string.mastery_mastered
    } ?: return
    val pen = PaperTheme.ink.pen
    val faint = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.testTag("handbook-mastery"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(width = 16.dp, height = 3.dp).background(if (i < inked) pen else faint, RoundedCornerShape(1.5.dp)))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(word), style = MaterialTheme.typography.labelLarge, color = pen)
    }
}
