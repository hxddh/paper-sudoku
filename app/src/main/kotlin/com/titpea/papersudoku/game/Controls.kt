@file:OptIn(ExperimentalFoundationApi::class)

package com.titpea.papersudoku.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.game.model.GameState

/**
 * A floating toolbar of the four tools. Notes is a real switch for accessibility services.
 */
@Composable
fun GameDock(
    game: GameState,
    onEvent: (PlayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        color = colors.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockItem(
                icon = R.drawable.ic_undo,
                label = stringResource(R.string.undo),
                enabled = game.history.isNotEmpty(),
                onClick = { onEvent(PlayEvent.Undo) },
                modifier = Modifier.testTag("dock-undo"),
            )
            DockItem(
                icon = R.drawable.ic_erase,
                label = stringResource(R.string.erase),
                onClick = { onEvent(PlayEvent.Erase) },
                modifier = Modifier.testTag("dock-erase"),
            )
            val notesState = stringResource(if (game.notesMode) R.string.state_on else R.string.state_off)
            DockItem(
                icon = R.drawable.ic_pencil,
                label = stringResource(R.string.notes),
                checked = game.notesMode,
                onClick = { onEvent(PlayEvent.ToggleNotes) },
                modifier = Modifier
                    .testTag("dock-notes")
                    .semantics { stateDescription = notesState },
            )
            DockItem(
                icon = R.drawable.ic_coach,
                label = stringResource(R.string.coach),
                emphasized = true,
                onClick = { onEvent(PlayEvent.Coach) },
                modifier = Modifier.testTag("coach-action"),
            )
        }
    }
}

@Composable
private fun RowScope.DockItem(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean? = null,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val active = checked == true
    val container = when {
        emphasized -> colors.primary
        active -> colors.secondaryContainer
        else -> Color.Transparent
    }
    val content = when {
        emphasized -> colors.onPrimary
        active -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val clickModifier = if (checked != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interaction,
            indication = ripple(),
            onValueChange = { onClick() },
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Column(
        modifier = modifier
            .weight(if (emphasized) 1.25f else 1f)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(container)
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * Digits as an expressive button group. A long press locks a digit (digit-first input): then
 * every tap on the board writes it.
 */
@Composable
fun Keypad(
    game: GameState,
    onEvent: (PlayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = if (game.size == 6) listOf(1..6) else listOf(1..5, 6..9)
    val remaining = game.remaining
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            val digits = row.toList()
            val sources = remember(row) { List(digits.size) { MutableInteractionSource() } }
            ButtonGroup(
                // Every key always fits; an overflow button would add an unlabeled node.
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val groupScope = this
                digits.forEachIndexed { i, digit ->
                    customItem(
                        buttonGroupContent = {
                            DigitKey(
                                digit = digit,
                                left = remaining[digit],
                                locked = game.lockedDigit == digit,
                                notesMode = game.notesMode,
                                interactionSource = sources[i],
                                onClick = { onEvent(PlayEvent.Digit(digit)) },
                                onLongClick = { onEvent(PlayEvent.Lock(digit)) },
                                modifier = with(groupScope) {
                                    Modifier
                                        .weight(1f)
                                        .animateWidth(sources[i])
                                },
                            )
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(digit.toString()) },
                                onClick = {
                                    onEvent(PlayEvent.Digit(digit))
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
    locked: Boolean,
    notesMode: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed || locked) 12.dp else 20.dp, label = "key-corner")
    val shape = RoundedCornerShape(corner)
    val done = left <= 0
    val container = when {
        locked -> colors.primary
        notesMode -> colors.surfaceContainerHighest
        else -> colors.secondaryContainer
    }
    val content = when {
        locked -> colors.onPrimary
        notesMode -> colors.onSurfaceVariant
        else -> colors.onSecondaryContainer
    }
    val description = when {
        locked -> stringResource(R.string.keypad_locked, digit)
        done -> stringResource(R.string.keypad_done, digit)
        else -> stringResource(R.string.keypad_digit, digit, left)
    }
    val lockLabel = stringResource(if (locked) R.string.keypad_unlock else R.string.keypad_lock)
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(shape)
            .background(container)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onLongClickLabel = lockLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .alpha(if (done && !locked) 0.45f else 1f)
            .semantics { contentDescription = description }
            .testTag("keypad-$digit"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                color = content,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = FrauncesPrinted,
                    fontSize = if (notesMode) 20.sp else 26.sp,
                    fontFeatureSettings = "lnum",
                ),
            )
            Text(
                text = if (done) "✓" else left.toString(),
                color = content.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
