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
import com.titpea.papersudoku.designsystem.MarkShape
import com.titpea.papersudoku.designsystem.SectionHeader
import com.titpea.papersudoku.designsystem.Space
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

/**
 * What a handbook page is about: one technique. Using the pen is taught on the page itself, by
 * one-line hints the first time each thing happens (11.0), so the handbook is techniques only.
 */
sealed interface HandbookTopic {
    data class Of(val technique: Technique) : HandbookTopic

    companion object {
        val all: List<HandbookTopic> = Technique.entries.map(::Of)

        /** The page a technique is on, in learning order. */
        fun indexOf(technique: Technique): Int = all.indexOf(Of(technique))
    }
}

/**
 * The handbook: a paper page per technique, turned like the notebook's, each led by its worked
 * example from a real practice page, drawn the way the Coach draws it (the unit, the pattern, the
 * crossed-out candidates, the cell it settles) 16dp under the header, as a page's board is; then
 * how well it is known, in ink (seen, practised, known), and the Coach's own words for it.
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
        // A transparent container names no content colour: text and icons would fall back to black.
        contentColor = MaterialTheme.colorScheme.onBackground,
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
                val topic = topics[index] as HandbookTopic.Of
                TechniquePage(topic.technique, mastery(topic.technique))
            }
            // Turning without a swipe: for TalkBack, a keyboard, or a tap. Ink, at the page's foot.
            // The chevrons' glyphs end on the board's edges.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.XS, vertical = Space.XS),
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
    is HandbookTopic.Of -> techniqueName(topic.technique)
}

/** One handbook page: it scrolls on its own, so a long example never pushes the next page away. */
@Composable
private fun HandbookPage(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Space.BoardTop, bottom = Space.L)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** The words under the board, on the page's one text edge. */
@Composable
private fun TextColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = Space.TextEdge, vertical = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
        content = content,
    )
}

/** Reading text, in ink. */
@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
        // The board first, as on a page: on the page margin, as wide as the window allows.
        Board(
            game = page,
            conflicts = emptySet(),
            marks = marks,
            onTap = {},
            onCellAction = { _, _ -> },
            interactive = false,
            modifier = Modifier
                .padding(horizontal = Space.Margin)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        TextColumn {
            MasteryInk(mastery)
            Body(techniqueSummary(technique))
            SectionHeader(stringResource(R.string.handbook_example), Modifier.padding(top = Space.S))
            workedExample(geometry, hint).forEach { Body(it) }
        }
    }
}

/**
 * How well the technique is known, as ink rather than a score: three strokes, inked as far as it
 * goes (seen, practised, known), and the word. Ink, not the player's pen blue: it is the
 * handbook's note about the player. Nothing at all before it is met.
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
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.testTag("handbook-mastery"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(width = 16.dp, height = 4.dp).background(if (i < inked) ink else faint, MarkShape))
            }
        }
        Spacer(Modifier.width(Space.M))
        Text(stringResource(word), style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}
