package com.titpea.papersudoku.page

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.game.model.GameState

val KeyHeight: Dp = 56.dp
val ToolbarHeight: Dp = 64.dp

/** Height of the keypad for a board size: one row for 6×6, two rows for 9×9. */
fun keypadHeight(size: Int): Dp = if (size == 6) KeyHeight else KeyHeight * 2 + 8.dp

/**
 * The four tools in a Material 3 Expressive floating toolbar. Notes is a toggle; the Coach is the
 * one filled action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageToolbar(
    game: GameState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.testTag("toolbar"),
    ) {
        IconButton(
            onClick = { onEvent(PageEvent.Undo) },
            enabled = game.history.isNotEmpty(),
            modifier = Modifier.testTag("tool-undo"),
        ) { Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.undo)) }
        IconButton(
            onClick = { onEvent(PageEvent.Erase) },
            modifier = Modifier.testTag("tool-erase"),
        ) { Icon(painterResource(R.drawable.ic_erase), contentDescription = stringResource(R.string.erase)) }
        FilledIconToggleButton(
            checked = game.notesMode,
            onCheckedChange = { onEvent(PageEvent.ToggleNotes) },
            modifier = Modifier.testTag("tool-notes"),
        ) { Icon(painterResource(R.drawable.ic_pencil), contentDescription = stringResource(R.string.notes)) }
        FilledIconButton(
            onClick = { onEvent(PageEvent.Coach) },
            modifier = Modifier.testTag("coach-action"),
        ) { Icon(painterResource(R.drawable.ic_coach), contentDescription = stringResource(R.string.coach)) }
    }
}

/**
 * Digits as a connected expressive button group. With nothing to write into, a tap picks the
 * digit up (digit-first); finished digits shrink to a check but keep their place.
 */
@Composable
fun Keypad(
    game: GameState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = if (game.size == 6) listOf(1..6) else listOf(1..5, 6..9)
    val remaining = game.remaining
    Column(
        modifier = modifier.fillMaxWidth().testTag("keypad"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            val digits = row.toList()
            val sources = remember(row) { List(digits.size) { MutableInteractionSource() } }
            ButtonGroup(
                // Every key always fits; an overflow button would add an unlabeled node.
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val groupScope = this
                digits.forEachIndexed { i, digit ->
                    customItem(
                        buttonGroupContent = {
                            val done = remaining[digit] <= 0
                            val weight by animateFloatAsState(if (done && game.lockedDigit != digit) 0.55f else 1f, label = "key-weight")
                            DigitKey(
                                digit = digit,
                                left = remaining[digit],
                                held = game.lockedDigit == digit,
                                pencil = game.notesMode,
                                first = i == 0,
                                last = i == digits.lastIndex,
                                interactionSource = sources[i],
                                onClick = { onEvent(PageEvent.Digit(digit)) },
                                modifier = with(groupScope) {
                                    Modifier
                                        .weight(weight)
                                        .animateWidth(sources[i])
                                },
                            )
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(digit.toString()) },
                                onClick = {
                                    onEvent(PageEvent.Digit(digit))
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    left: Int,
    held: Boolean,
    pencil: Boolean,
    first: Boolean,
    last: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pressed by interactionSource.collectIsPressedAsState()
    // Connected group: outer corners round, inner corners tight; pressing rounds a key fully.
    val outer by animateDpAsState(if (pressed || held) 28.dp else 20.dp, label = "key-outer")
    val inner by animateDpAsState(if (pressed || held) 28.dp else 6.dp, label = "key-inner")
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        bottomStart = if (first) outer else inner,
        topEnd = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
    val done = left <= 0
    val container = when {
        held -> colors.primary
        pencil -> colors.surfaceContainerHighest
        else -> colors.secondaryContainer
    }
    val content = when {
        held -> colors.onPrimary
        pencil -> colors.onSurfaceVariant
        else -> colors.onSecondaryContainer
    }
    val description = when {
        held -> stringResource(R.string.keypad_held, digit)
        done -> stringResource(R.string.keypad_done, digit)
        else -> stringResource(R.string.keypad_digit, digit, left)
    }
    Box(
        modifier = modifier
            .height(KeyHeight)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .testTag("keypad-$digit"),
        contentAlignment = Alignment.Center,
    ) {
        if (done && !held) {
            Text("✓", color = content.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(
                text = digit.toString(),
                color = content,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = FrauncesPrinted,
                    fontSize = if (pencil) 20.sp else 26.sp,
                    fontFeatureSettings = "lnum",
                ),
            )
        }
    }
}
