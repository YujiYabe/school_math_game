package com.example.schoolmathgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DrillScreen(
    viewModel: DrillViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (uiState.screen) {
                DrillScreenState.Settings -> SettingsScreen(
                    uiState = uiState,
                    onTimeLimitChanged = viewModel::setTimeLimit,
                    onNumberRangeChanged = viewModel::setNumberRange,
                    onStart = viewModel::startDrill,
                )

                DrillScreenState.Drill -> ActiveDrillScreen(
                    uiState = uiState,
                    onDigit = viewModel::appendDigit,
                    onMinus = viewModel::appendMinus,
                    onClear = viewModel::clearInput,
                    onConfirm = viewModel::submitAnswer,
                    onToggleDivisionTarget = viewModel::toggleDivisionInputTarget,
                    onDivisionTargetChanged = viewModel::setDivisionInputTarget,
                )

                DrillScreenState.Result -> ResultScreen(
                    uiState = uiState,
                    onBackToSettings = viewModel::returnToSettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: DrillUiState,
    onTimeLimitChanged: (Int) -> Unit,
    onNumberRangeChanged: (NumberRange) -> Unit,
    onStart: () -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "100本ノック算数ドリル",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            SettingLabel(text = "制限時間")
            TimeLimitDropdown(
                selectedSeconds = uiState.timeLimitSeconds,
                onSelected = onTimeLimitChanged,
            )

            Spacer(modifier = Modifier.height(28.dp))

            SettingLabel(text = "出題範囲")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RangeButton(
                    text = "1桁のみ",
                    selected = uiState.numberRange == NumberRange.OneDigit,
                    onClick = { onNumberRangeChanged(NumberRange.OneDigit) },
                    modifier = Modifier.weight(1f),
                )
                RangeButton(
                    text = "2桁含む",
                    selected = uiState.numberRange == NumberRange.IncludeTwoDigits,
                    onClick = { onNumberRangeChanged(NumberRange.IncludeTwoDigits) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(44.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text = "スタート", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimeLimitDropdown(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "${selectedSeconds}秒", fontSize = 18.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (1..10).forEach { seconds ->
                DropdownMenuItem(
                    text = { Text(text = "${seconds}秒") },
                    onClick = {
                        onSelected(seconds)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RangeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ActiveDrillScreen(
    uiState: DrillUiState,
    onDigit: (Int) -> Unit,
    onMinus: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onToggleDivisionTarget: () -> Unit,
    onDivisionTargetChanged: (DivisionInputTarget) -> Unit,
) {
    val problem = uiState.currentProblem

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DrillHeader(uiState = uiState)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = problem?.expression.orEmpty(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 52.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (problem?.operation == Operation.Divide) {
                DivisionAnswerFields(
                    quotient = uiState.quotientInput,
                    remainder = uiState.remainderInput,
                    activeTarget = uiState.divisionInputTarget,
                    onTargetChanged = onDivisionTargetChanged,
                )
            } else {
                AnswerBox(
                    label = "答え",
                    value = uiState.input,
                    selected = true,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        CustomTenKey(
            isDivision = problem?.operation == Operation.Divide,
            onDigit = onDigit,
            onMinus = onMinus,
            onClear = onClear,
            onConfirm = onConfirm,
            onToggleDivisionTarget = onToggleDivisionTarget,
        )
    }
}

@Composable
private fun DrillHeader(uiState: DrillUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${uiState.currentQuestionNumber} / ${uiState.totalQuestions}問目",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${uiState.remainingSecondsText}秒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (uiState.timerProgress < 0.25f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = uiState.timerProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = if (uiState.timerProgress < 0.25f) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DivisionAnswerFields(
    quotient: String,
    remainder: String,
    activeTarget: DivisionInputTarget,
    onTargetChanged: (DivisionInputTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnswerBox(
            label = "商",
            value = quotient,
            selected = activeTarget == DivisionInputTarget.Quotient,
            onClick = { onTargetChanged(DivisionInputTarget.Quotient) },
            modifier = Modifier.weight(1f),
        )
        AnswerBox(
            label = "余り",
            value = remainder,
            selected = activeTarget == DivisionInputTarget.Remainder,
            onClick = { onTargetChanged(DivisionInputTarget.Remainder) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnswerBox(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .height(104.dp)
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value.ifBlank { " " },
            modifier = Modifier.fillMaxWidth(),
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CustomTenKey(
    isDivision: Boolean,
    onDigit: (Int) -> Unit,
    onMinus: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onToggleDivisionTarget: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyRow {
                NumberKey("7") { onDigit(7) }
                NumberKey("8") { onDigit(8) }
                NumberKey("9") { onDigit(9) }
                ActionKey("C", onClick = onClear)
            }
            KeyRow {
                NumberKey("4") { onDigit(4) }
                NumberKey("5") { onDigit(5) }
                NumberKey("6") { onDigit(6) }
                ActionKey("商/余り", enabled = isDivision, onClick = onToggleDivisionTarget)
            }
            KeyRow {
                NumberKey("1") { onDigit(1) }
                NumberKey("2") { onDigit(2) }
                NumberKey("3") { onDigit(3) }
                ActionKey("-", enabled = !isDivision, onClick = onMinus)
            }
            KeyRow {
                Spacer(modifier = Modifier.weight(1f))
                NumberKey("0") { onDigit(0) }
                Spacer(modifier = Modifier.weight(1f))
                ConfirmKey(onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.NumberKey(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.55f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text = text, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RowScope.ActionKey(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.55f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            fontSize = if (text.length > 2) 14.sp else 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun RowScope.ConfirmKey(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.55f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "確定",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultScreen(
    uiState: DrillUiState,
    onBackToSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "結果",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "${uiState.correctCount} / ${uiState.totalQuestions} 点",
            fontSize = 52.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onBackToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "設定に戻る", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
