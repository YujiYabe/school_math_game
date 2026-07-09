package com.example.schoolmathgame

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DrillScreen() {
    val context = LocalContext.current
    val viewModel: DrillViewModel = viewModel(
        factory = remember(context) {
            DrillViewModel.Factory(context)
        },
    )

    DrillScreenContent(viewModel = viewModel)
}

@Composable
private fun DrillScreenContent(viewModel: DrillViewModel) {
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
                    onQuestionCountChanged = viewModel::setQuestionCount,
                    onLeftNumberRangeChanged = viewModel::setLeftNumberRange,
                    onRightNumberRangeChanged = viewModel::setRightNumberRange,
                    onOperationChanged = viewModel::setOperation,
                    onStart = viewModel::startDrill,
                )

                DrillScreenState.Drill -> ActiveDrillScreen(
                    uiState = uiState,
                    onChoiceSelected = viewModel::selectChoice,
                )

                DrillScreenState.RetryResult -> RetryResultScreen(
                    uiState = uiState,
                    onRetry = viewModel::startRetryDrill,
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
    onQuestionCountChanged: (Int) -> Unit,
    onLeftNumberRangeChanged: (NumberRange) -> Unit,
    onRightNumberRangeChanged: (NumberRange) -> Unit,
    onOperationChanged: (Operation) -> Unit,
    onStart: () -> Unit,
) {
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFB9E9FF),
                            Color(0xFF3F9DFF),
                            Color(0xFF1F73E8),
                        ),
                    ),
                )
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xAAE9F7FF),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        text = "メニュー",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF16408F),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NumberRangeDropdown(
                            selectedRange = uiState.leftNumberRange,
                            onSelected = onLeftNumberRangeChanged,
                            modifier = Modifier.weight(1f),
                        )
                        OperationDropdown(
                            selectedOperation = uiState.selectedOperation,
                            onSelected = onOperationChanged,
                            modifier = Modifier.weight(0.9f),
                        )
                        NumberRangeDropdown(
                            selectedRange = uiState.rightNumberRange,
                            onSelected = onRightNumberRangeChanged,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    TimerSlider(
                        seconds = uiState.timeLimitSeconds,
                        onSecondsChanged = onTimeLimitChanged,
                    )

                    QuestionCountSlider(
                        count = uiState.questionCount,
                        onCountChanged = onQuestionCountChanged,
                    )

                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = RoundedCornerShape(31.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF86DC23),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = "start", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberRangeDropdown(
    selectedRange: NumberRange,
    onSelected: (NumberRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = selectedRange.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            NumberRange.values().forEach { range ->
                DropdownMenuItem(
                    text = { Text(text = range.label) },
                    onClick = {
                        onSelected(range)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OperationDropdown(
    selectedOperation: Operation,
    onSelected: (Operation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF86DC23),
                contentColor = Color(0xFF16408F),
            ),
        ) {
            Text(
                text = selectedOperation.symbol,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Operation.values().forEach { operation ->
                DropdownMenuItem(
                    text = { Text(text = operation.menuLabel) },
                    onClick = {
                        onSelected(operation)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TimerSlider(
    seconds: Int,
    onSecondsChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = "${seconds}秒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChanged((it + 0.5f).toInt().coerceIn(1, 30)) },
            valueRange = 1f..30f,
            steps = 28,
        )
    }
}

@Composable
private fun QuestionCountSlider(
    count: Int,
    onCountChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "questions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
            Text(
                text = "${count}問",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF16408F),
            )
        }
        Slider(
            value = count.toFloat(),
            onValueChange = {
                val rounded = ((it + 5f).toInt() / 10) * 10
                onCountChanged(rounded.coerceIn(10, 100))
            },
            valueRange = 10f..100f,
            steps = 8,
        )
    }
}

@Composable
private fun ActiveDrillScreen(
    uiState: DrillUiState,
    onChoiceSelected: (AnswerChoice) -> Unit,
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
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 56.sp,
            )
        }

        ChoicePanel(
            choices = uiState.choices,
            onChoiceSelected = onChoiceSelected,
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
                text = "${uiState.currentQuestionNumber} / ${uiState.activeQuestionCount}問目",
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
            progress = { uiState.timerProgress },
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
private fun ChoicePanel(
    choices: List<AnswerChoice>,
    onChoiceSelected: (AnswerChoice) -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowChoices.forEach { choice ->
                        ChoiceButton(
                            choice = choice,
                            onClick = { onChoiceSelected(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    choice: AnswerChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(93.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = choice.label,
            fontSize = if (choice.label.length > 8) 18.sp else 28.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun RetryResultScreen(
    uiState: DrillUiState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "間違いチェック",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "${uiState.reviews.size}問をもう一度",
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        ReviewList(
            reviews = uiState.reviews,
            showCorrectAnswer = false,
            showRetryCount = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "間違えた問題を解く", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
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
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "結果",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Text(
            text = "${uiState.correctCount} / ${uiState.reviews.size} 点",
            fontSize = 52.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        ReviewList(
            reviews = uiState.reviews,
            showCorrectAnswer = true,
            showRetryCount = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Button(
            onClick = onBackToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = "もう一度", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewList(
    reviews: List<AnswerReview>,
    showCorrectAnswer: Boolean,
    showRetryCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reviews.forEach { review ->
                ReviewRow(
                    review = review,
                    showCorrectAnswer = showCorrectAnswer,
                    showRetryCount = showRetryCount,
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(
    review: AnswerReview,
    showCorrectAnswer: Boolean,
    showRetryCount: Boolean,
) {
    val textColor = if (review.isCorrect) Color.Black else Color(0xFFD00000)
    val selectedText = review.selectedAnswer ?: "未回答"
    val retryText = if (showRetryCount && review.retryCount > 0) {
        "   リトライ: ${review.retryCount}回"
    } else {
        ""
    }
    val answerText = if (showCorrectAnswer) {
        "選択: $selectedText   正解: ${review.correctAnswer}$retryText"
    } else {
        "選択: $selectedText"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${review.questionNumber}問目",
                color = if (review.isCorrect) Color(0xFF666666) else Color(0xFFD00000),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = review.expression,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
            )
        }
        Text(
            text = answerText,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
    }
}
