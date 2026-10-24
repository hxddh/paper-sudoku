package com.titpea.papersudoku.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.Alpha
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.designsystem.SheetShape
import com.titpea.papersudoku.designsystem.Space
import com.titpea.papersudoku.game.model.GameState

val KeyHeight: Dp = 56.dp

/** The quiet line over the keys: what the pen is doing, and the three tools. A 48dp target. */
val StatusHeight: Dp = 48.dp

/**
 * Room above the status line, at rest, for a one-line hint or the Coach's note. The board is sized
 * with it reserved, so the note never moves the board; a longer note uses the spare height above
 * it, and scrolls when even that is not enough.
 */
val NoteSlotHeight: Dp = 64.dp

/** The solved note and the read-back panel need at least this. */
private val NoteMinHeight: Dp = 176.dp

/** Height of the keypad for a board size: one row for 6×6, two rows for 9×9. */
fun keypadHeight(size: Int): Dp = if (size == 6) KeyHeight else KeyHeight * 2 + Space.S

/**
 * The work area's height under the board (the note's room, the status line, the keys); the solved
 * note and the read-back panel take the same. It grows with large text, up to 130%, rather than
 * squeeze the words; the board takes the rest.
 */
fun workAreaHeight(size: Int, fontScale: Float): Dp {
    val scale = fontScale.coerceIn(1f, 1.3f)
    return maxOf(NoteMinHeight * scale, NoteSlotHeight * scale + StatusHeight + keypadHeight(size))
}

/**
 * The three tools as bare icons at the end of the status line: undo, the pencil (a toggle: pen
 * blue and underlined when on) and the Coach. All three are equally quiet: the page leads, and red
 * stays for the Coach's own marks and the held digit.
 */
@Composable
fun PageTools(
    game: GameState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(modifier.testTag("toolbar"), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onEvent(PageEvent.Undo) },
                enabled = game.history.isNotEmpty(),
                modifier = Modifier.testTag("tool-undo"),
            ) { Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.undo)) }
            PencilButton(
                on = game.notesMode,
                // Pencilling in every candidate is for 9×9 pages; a 6×6 page is quick to mark by hand.
                canFill = game.size == 9 && !game.solved,
                onToggle = { onEvent(PageEvent.ToggleNotes) },
                onFill = { onEvent(PageEvent.FillCandidates) },
            )
            IconButton(
                onClick = { onEvent(PageEvent.Coach) },
                modifier = Modifier.testTag("coach-action"),
            ) { Icon(painterResource(R.drawable.ic_coach), contentDescription = stringResource(R.string.coach)) }
        }
    }
}

/**
 * The pencil: a tap switches it. On a 9×9 page in pencil, "Fill every candidate" is a visible
 * action on the status line; a long press here (and TalkBack's action) does the same.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PencilButton(on: Boolean, canFill: Boolean, onToggle: () -> Unit, onFill: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val fillLabel = stringResource(R.string.fill_candidates)
    val interaction = remember { MutableInteractionSource() }
    val tint = if (on) PaperTheme.ink.pen else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Switch,
                onClickLabel = null,
                onLongClickLabel = if (canFill) fillLabel else null,
                onLongClick = if (canFill) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFill()
                    }
                } else {
                    null
                },
                onClick = onToggle,
            )
            .semantics {
                toggleableState = ToggleableState(on)
                if (canFill) customActions = listOf(CustomAccessibilityAction(fillLabel) { onFill(); true })
            }
            .testTag("tool-notes"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_pencil), contentDescription = stringResource(R.string.notes), tint = tint)
        // On is said by shape as well as colour: a short pen-blue rule under the pencil.
        if (on) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.S)
                    .size(width = 16.dp, height = 2.dp)
                    .background(tint),
            )
        }
    }
}

/**
 * Paper keys in one row (6×6: six digits and Erase) or two (9×9: 5 + 5, Erase last). With no
 * cell chosen, a tap picks the digit up (digit-first); finished digits turn into a check but keep
 * their place. A key keeps its shape: a press is a tint, nothing moves.
 */
@Composable
fun Keypad(
    game: GameState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** False while typing a page in: keys say no count and never turn into a check. */
    counts: Boolean = true,
) {
    // Zero stands for the Erase key.
    val rows = if (game.size == 6) listOf((1..6) + 0) else listOf((1..5).toList(), (6..9) + 0)
    val remaining = game.remaining
    // Digits run 1 → 9 left to right in every language, like the board.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.fillMaxWidth().testTag("keypad"),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
                    for (digit in row) {
                        if (digit == 0) {
                            EraseKey(onClick = { onEvent(PageEvent.Erase) }, modifier = Modifier.weight(1f))
                        } else {
                            DigitKey(
                                digit = digit,
                                left = if (counts) remaining[digit] else null,
                                held = game.lockedDigit == digit,
                                pencil = game.notesMode,
                                onClick = { onEvent(PageEvent.Digit(digit)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A key's paper, a wash of ink deeper while pressed: the press is a tint only. */
@Composable
private fun keyPaper(base: Color, pressed: Boolean): Color =
    if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = Alpha.Wash).compositeOver(base) else base

@Composable
private fun DigitKey(
    digit: Int,
    /** Copies still to place, or null where the keypad does not count. */
    left: Int?,
    held: Boolean,
    pencil: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val done = left != null && left <= 0
    // Paper keys with printed ink. The held digit is terracotta on its paper key, underlined; in
    // pencil mode the digits are smaller and in the pen's blue, so the mode reads as a mode, not
    // as disabled keys.
    val paper = if (pencil && !held) colors.surfaceContainer else colors.surfaceContainerHigh
    val content = when {
        held -> colors.primary
        pencil -> colors.secondary
        else -> colors.onSurface
    }
    val description = when {
        held -> stringResource(R.string.keypad_held, digit)
        done -> stringResource(R.string.keypad_done, digit)
        pencil -> stringResource(R.string.keypad_pencil, digit)
        left == null -> digit.toString()
        else -> stringResource(R.string.keypad_digit, digit, left)
    }
    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(SheetShape)
            .background(keyPaper(paper, pressed))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .testTag("keypad-$digit"),
        contentAlignment = Alignment.Center,
    ) {
        if (done && !held) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            // Sized like the board's digits, by the key rather than the font scale, so they never
            // clip at 200% text: they are glyphs to aim at, and the key says them aloud.
            val glyph = with(LocalDensity.current) { (if (pencil) 20.dp else 26.dp).toSp() }
            Text(
                text = digit.toString(),
                color = content,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = FrauncesPrinted,
                    fontSize = glyph,
                    fontFeatureSettings = "lnum",
                ),
            )
        }
        if (held) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.S)
                    .width(20.dp)
                    .height(2.dp)
                    .background(colors.primary),
            )
        }
    }
}

/** The last key: erases the selected cell. The same paper as the digits: one row of keys. */
@Composable
private fun EraseKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(SheetShape)
            .background(keyPaper(colors.surfaceContainerHigh, pressed))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("tool-erase"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_erase),
            contentDescription = stringResource(R.string.erase),
            tint = colors.onSurfaceVariant,
        )
    }
}
