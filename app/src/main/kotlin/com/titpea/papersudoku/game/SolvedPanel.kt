package com.titpea.papersudoku.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind

@Composable
fun SolvedPanel(
    game: GameState,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("solved-panel")) {
        Text(stringResource(R.string.solved_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.solved_time, formatElapsed(game.elapsedMillis / 1_000)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Text(
            coachUsageLine(game.coach.deepest),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_share))
            }
            FilledTonalButton(onClick = onPrint, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_print), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_print))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("solved-next"),
        ) {
            Text(
                if (game.spec.kind == PuzzleKind.Daily) {
                    stringResource(R.string.solved_open_journal)
                } else {
                    stringResource(R.string.solved_next_classic, difficultyName(game.spec.difficulty))
                },
            )
        }
    }
}

@Composable
fun coachUsageLine(deepest: Int): String = stringResource(
    when (deepest) {
        0 -> R.string.coach_usage_none
        1 -> R.string.coach_usage_look
        2 -> R.string.coach_usage_why
        else -> R.string.coach_usage_answer
    },
)
