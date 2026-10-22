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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.game.model.GameState

val KeyHeight: Dp = 56.dp
val ToolbarHeight: Dp = 64.dp

/** Height of the keypad for a board size: one row for 6×6, two rows for 9×9. */
fun keypadHeight(size: Int): Dp = if (size == 6) KeyHeight else KeyHeight * 2 + 8.dp

/**
 * The three tools in a Material 3 Expressive floating toolbar: undo, the pencil (a toggle, in ink
 * blue when on) and the Coach (tonal). Red stays for the Coach's own marks and the held digit.
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
        val colors = MaterialTheme.colorScheme
        FilledIconToggleButton(
            checked = game.notesMode,
            onCheckedChange = { onEvent(PageEvent.ToggleNotes) },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = colors.onSurfaceVariant,
                checkedContainerColor = colors.secondaryContainer,
                checkedContentColor = colors.onSecondaryContainer,
            ),
            modifier = Modifier.testTag("tool-notes"),
        ) { Icon(painterResource(R.drawable.ic_pencil), contentDescription = stringResource(R.string.notes)) }
        FilledTonalIconButton(
            onClick = { onEvent(PageEvent.Coach) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
            modifier = Modifier.testTag("coach-action"),
        ) { Icon(painterResource(R.drawable.ic_coach), contentDescription = stringResource(R.string.coach)) }
    }
}

/**
 * Digits as a connected expressive button group, with Erase as the last key: 5 + 5 for 9×9, one
 * row of seven for 6×6. With nothing to write into, a tap picks the digit up (digit-first);
 * finished digits shrink to a check but keep their place.
 */
@Composable
fun Keypad(
    game: GameState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Zero stands for the Erase key.
    val rows = if (game.size == 6) listOf((1..6) + 0) else listOf((1..5).toList(), (6..9) + 0)
    val remaining = game.remaining
    Column(
        modifier = modifier.fillMaxWidth().testTag("keypad"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            val digits = row
            val sources = remember(row) { List(digits.size) { MutableInteractionSource() } }
            ButtonGroup(
                // Every key always fits; an overflow button would add an unlabeled node.
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val groupScope = this
                digits.forEachIndexed { i, digit ->
                    if (digit == 0) {
                        customItem(
                            buttonGroupContent = {
                                EraseKey(
                                    interactionSource = sources[i],
                                    onClick = { onEvent(PageEvent.Erase) },
                                    modifier = with(groupScope) { Modifier.weight(1f).animateWidth(sources[i]) },
                                )
                            },
                            menuContent = { menuState ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.erase)) },
                                    onClick = {
                                        onEvent(PageEvent.Erase)
                                        menuState.dismiss()
                                    },
                                )
                            },
                        )
                        return@forEachIndexed
                    }
                    customItem(
                        buttonGroupContent = {
                            val done = remaining[digit] <= 0
                            val weight by animateFloatAsState(if (done && game.lockedDigit != digit) 0.55f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "key-weight")
                            DigitKey(
                                digit = digit,
                                left = remaining[digit],
                                held = game.lockedDigit == digit,
                                pencil = game.notesMode,
                                first = i == 0,
                                // In a row that ends with Erase, the digits' outer edge is inside.
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
    val outer by animateDpAsState(if (pressed || held) 28.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "key-outer")
    val inner by animateDpAsState(if (pressed || held) 28.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "key-inner")
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        bottomStart = if (first) outer else inner,
        topEnd = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
    val done = left <= 0
    // Paper keys with printed ink; the held digit is the one red key. Pencil mode greys the ink.
    val container = when {
        held -> colors.primary
        pencil -> colors.surfaceContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        held -> colors.onPrimary
        pencil -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val description = when {
        held -> stringResource(R.string.keypad_held, digit)
        done -> stringResource(R.string.keypad_done, digit)
        else -> stringResource(R.string.keypad_digit, digit, left)
    }
    Box(
        modifier = modifier
            .height(KeyHeight)
            // 44dp keeps all seven 6×6 keys on a 360dp phone (they are 56dp tall).
            .widthIn(min = 44.dp)
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
    }
}

/** The last key: erases the selected cell. Rounded on its outer edge like the group's end. */
@Composable
private fun EraseKey(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pressed by interactionSource.collectIsPressedAsState()
    val outer by animateDpAsState(if (pressed) 28.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "erase-outer")
    val inner by animateDpAsState(if (pressed) 28.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "erase-inner")
    val shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
    Box(
        modifier = modifier
            .height(KeyHeight)
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
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
