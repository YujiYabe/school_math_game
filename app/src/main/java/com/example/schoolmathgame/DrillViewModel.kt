package com.example.schoolmathgame

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TotalQuestionCount = 100
private const val TimerTickMillis = 50L
private const val ChoiceCount = 4
private const val DefaultTimeLimitSeconds = 3
private const val MinTimeLimitSeconds = 1
private const val MaxTimeLimitSeconds = 30
private const val MinQuestionCount = 10
private const val MaxQuestionCount = 100

enum class DrillScreenState {
    Settings,
    Drill,
    Result,
}

enum class NumberRange(val label: String) {
    OneDigit("1-9"),
    IncludeTwoDigits("1-99"),
}

enum class Operation(val symbol: String, val menuLabel: String) {
    Add("+", "+"),
    Subtract("-", "-"),
    Multiply("×", "×"),
    Divide("÷", "÷"),
}

data class MathProblem(
    val left: Int,
    val right: Int,
    val operation: Operation,
) {
    val expression: String = "$left ${operation.symbol} $right ="

    val answer: Int
        get() = when (operation) {
            Operation.Add -> left + right
            Operation.Subtract -> left - right
            Operation.Multiply -> left * right
            Operation.Divide -> left / right
        }

    val quotient: Int
        get() = left / right

    val remainder: Int
        get() = left % right
}

data class AnswerChoice(
    val label: String,
    val isCorrect: Boolean,
)

data class AnswerReview(
    val questionNumber: Int,
    val expression: String,
    val correctAnswer: String,
    val selectedAnswer: String?,
    val isCorrect: Boolean,
)

data class DrillUiState(
    val screen: DrillScreenState = DrillScreenState.Settings,
    val timeLimitSeconds: Int = DefaultTimeLimitSeconds,
    val questionCount: Int = TotalQuestionCount,
    val leftNumberRange: NumberRange = NumberRange.OneDigit,
    val rightNumberRange: NumberRange = NumberRange.OneDigit,
    val selectedOperation: Operation = Operation.Add,
    val currentQuestionNumber: Int = 0,
    val correctCount: Int = 0,
    val currentProblem: MathProblem? = null,
    val choices: List<AnswerChoice> = emptyList(),
    val reviews: List<AnswerReview> = emptyList(),
    val remainingMillis: Long = 0L,
) {
    val timerProgress: Float
        get() {
            val full = timeLimitSeconds * 1_000L
            return if (full <= 0L) 0f else (remainingMillis.toFloat() / full).coerceIn(0f, 1f)
        }

    val remainingSecondsText: String
        get() = String.format("%.1f", remainingMillis / 1_000f)
}

private data class DrillSettings(
    val timeLimitSeconds: Int = DefaultTimeLimitSeconds,
    val questionCount: Int = TotalQuestionCount,
    val leftNumberRange: NumberRange = NumberRange.OneDigit,
    val rightNumberRange: NumberRange = NumberRange.OneDigit,
    val selectedOperation: Operation = Operation.Add,
)

private val DefaultDrillSettings = DrillSettings()

private class DrillSettingsStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "drill_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): DrillSettings {
        return DrillSettings(
            timeLimitSeconds = preferences
                .getInt(KeyTimeLimitSeconds, DefaultDrillSettings.timeLimitSeconds)
                .coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds),
            questionCount = preferences
                .getInt(KeyQuestionCount, TotalQuestionCount)
                .coerceIn(MinQuestionCount, MaxQuestionCount),
            leftNumberRange = preferences.getEnum(KeyLeftNumberRange, NumberRange.OneDigit),
            rightNumberRange = preferences.getEnum(KeyRightNumberRange, NumberRange.OneDigit),
            selectedOperation = preferences.getEnum(KeySelectedOperation, Operation.Add),
        )
    }

    fun save(settings: DrillSettings) {
        preferences.edit()
            .putInt(KeyTimeLimitSeconds, settings.timeLimitSeconds)
            .putInt(KeyQuestionCount, settings.questionCount)
            .putString(KeyLeftNumberRange, settings.leftNumberRange.name)
            .putString(KeyRightNumberRange, settings.rightNumberRange.name)
            .putString(KeySelectedOperation, settings.selectedOperation.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
        key: String,
        defaultValue: T,
    ): T {
        val name = getString(key, null) ?: return defaultValue
        return runCatching { enumValueOf<T>(name) }.getOrDefault(defaultValue)
    }

    private companion object {
        const val KeyTimeLimitSeconds = "time_limit_seconds"
        const val KeyQuestionCount = "question_count"
        const val KeyLeftNumberRange = "left_number_range"
        const val KeyRightNumberRange = "right_number_range"
        const val KeySelectedOperation = "selected_operation"
    }
}

class DrillViewModel private constructor(
    private val settingsStore: DrillSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(settingsStore.load().toUiState())
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun setTimeLimit(seconds: Int) {
        updateSettings { it.copy(timeLimitSeconds = seconds.coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds)) }
    }

    fun setQuestionCount(count: Int) {
        updateSettings { it.copy(questionCount = count.coerceIn(MinQuestionCount, MaxQuestionCount)) }
    }

    fun setLeftNumberRange(numberRange: NumberRange) {
        updateSettings { it.copy(leftNumberRange = numberRange) }
    }

    fun setRightNumberRange(numberRange: NumberRange) {
        updateSettings { it.copy(rightNumberRange = numberRange) }
    }

    fun setOperation(operation: Operation) {
        updateSettings { it.copy(selectedOperation = operation) }
    }

    fun startDrill() {
        timerJob?.cancel()
        val state = _uiState.value
        val question = generateQuestion(
            leftNumberRange = state.leftNumberRange,
            rightNumberRange = state.rightNumberRange,
            operation = state.selectedOperation,
        )
        _uiState.value = state.copy(
            screen = DrillScreenState.Drill,
            currentQuestionNumber = 1,
            correctCount = 0,
            currentProblem = question.problem,
            choices = question.choices,
            reviews = emptyList(),
            remainingMillis = state.timeLimitSeconds * 1_000L,
        )
        startProblemTimer()
    }

    fun returnToSettings() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                screen = DrillScreenState.Settings,
                currentQuestionNumber = 0,
                correctCount = 0,
                currentProblem = null,
                choices = emptyList(),
                reviews = emptyList(),
                remainingMillis = 0L,
            )
        }
    }

    fun selectChoice(choice: AnswerChoice) {
        completeCurrentProblem(selectedChoice = choice)
    }

    private fun completeCurrentProblem(selectedChoice: AnswerChoice?) {
        timerJob?.cancel()

        val state = _uiState.value
        if (state.screen != DrillScreenState.Drill || state.currentProblem == null) return

        val isCorrect = selectedChoice?.isCorrect == true
        val nextCorrectCount = state.correctCount + if (isCorrect) 1 else 0
        val nextReviews = state.reviews + AnswerReview(
            questionNumber = state.currentQuestionNumber,
            expression = state.currentProblem.expression,
            correctAnswer = state.choices.firstOrNull { it.isCorrect }?.label.orEmpty(),
            selectedAnswer = selectedChoice?.label,
            isCorrect = isCorrect,
        )

        if (state.currentQuestionNumber >= state.questionCount) {
            _uiState.update {
                it.copy(
                    screen = DrillScreenState.Result,
                    correctCount = nextCorrectCount,
                    currentProblem = null,
                    choices = emptyList(),
                    reviews = nextReviews,
                    remainingMillis = 0L,
                )
            }
            return
        }

        _uiState.update {
            val question = generateQuestion(
                leftNumberRange = it.leftNumberRange,
                rightNumberRange = it.rightNumberRange,
                operation = it.selectedOperation,
            )
            it.copy(
                currentQuestionNumber = it.currentQuestionNumber + 1,
                correctCount = nextCorrectCount,
                currentProblem = question.problem,
                choices = question.choices,
                reviews = nextReviews,
                remainingMillis = it.timeLimitSeconds * 1_000L,
            )
        }
        startProblemTimer()
    }

    private fun startProblemTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val durationMillis = _uiState.value.timeLimitSeconds * 1_000L
            val startedAt = System.currentTimeMillis()

            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAt
                val remaining = (durationMillis - elapsed).coerceAtLeast(0L)
                _uiState.update { it.copy(remainingMillis = remaining) }

                if (remaining <= 0L) {
                    completeCurrentProblem(selectedChoice = null)
                    break
                }
                delay(TimerTickMillis)
            }
        }
    }

    private fun generateQuestion(
        leftNumberRange: NumberRange,
        rightNumberRange: NumberRange,
        operation: Operation,
    ): GeneratedQuestion {
        val problem = generateProblem(
            leftNumberRange = leftNumberRange,
            rightNumberRange = rightNumberRange,
            operation = operation,
        )
        val choices = generateIntegerChoices(problem)
        return GeneratedQuestion(problem = problem, choices = choices)
    }

    private fun generateProblem(
        leftNumberRange: NumberRange,
        rightNumberRange: NumberRange,
        operation: Operation,
    ): MathProblem {
        val minimum = when (operation) {
            Operation.Add,
            Operation.Subtract,
                -> 3

            Operation.Multiply,
            Operation.Divide,
                -> 2
        }
        val left = if (operation == Operation.Divide) {
            randomDividend(leftNumberRange, rightNumberRange)
        } else {
            randomNumber(leftNumberRange, minimum = minimum)
        }
        val right = if (operation == Operation.Divide) {
            randomDivisor(left, rightNumberRange)
        } else {
            randomNumber(rightNumberRange, minimum = minimum)
        }
        val nonNegativeLeft = maxOf(left, right)
        val nonNegativeRight = minOf(left, right)

        return MathProblem(
            left = if (operation == Operation.Subtract) nonNegativeLeft else left,
            right = if (operation == Operation.Subtract) nonNegativeRight else right,
            operation = operation,
        )
    }

    private fun randomNumber(numberRange: NumberRange, minimum: Int = 1): Int {
        val max = when (numberRange) {
            NumberRange.OneDigit -> 9
            NumberRange.IncludeTwoDigits -> 99
        }
        return Random.nextInt(minimum.coerceAtMost(max), max + 1)
    }

    private fun randomDividend(leftNumberRange: NumberRange, rightNumberRange: NumberRange): Int {
        val leftMax = maxNumber(leftNumberRange)
        val rightMax = maxNumber(rightNumberRange)
        val candidates = (2..leftMax).filter { dividend ->
            (2..minOf(dividend, rightMax)).any { divisor -> dividend % divisor == 0 }
        }
        return candidates.random()
    }

    private fun randomDivisor(dividend: Int, numberRange: NumberRange): Int {
        val max = maxNumber(numberRange)
        val candidates = (2..minOf(dividend, max)).filter { divisor -> dividend % divisor == 0 }
        return candidates.random()
    }

    private fun maxNumber(numberRange: NumberRange): Int {
        return when (numberRange) {
            NumberRange.OneDigit -> 9
            NumberRange.IncludeTwoDigits -> 99
        }
    }

    private fun generateIntegerChoices(problem: MathProblem): List<AnswerChoice> {
        val correctAnswer = problem.answer
        val values = linkedSetOf(correctAnswer)
        val candidates = plausibleIntegerDistractors(problem).shuffled()

        candidates.forEach { candidate ->
            if (values.size < ChoiceCount && isValidChoiceValue(candidate, problem)) {
                values += candidate
            }
        }

        var distance = 1
        while (values.size < ChoiceCount) {
            listOf(correctAnswer - distance, correctAnswer + distance).shuffled().forEach { candidate ->
                if (values.size < ChoiceCount && isValidChoiceValue(candidate, problem)) {
                    values += candidate
                }
            }
            distance++
        }

        return values
            .map { AnswerChoice(label = it.toString(), isCorrect = it == correctAnswer) }
            .shuffled()
    }

    private fun plausibleIntegerDistractors(problem: MathProblem): List<Int> {
        val nearbyOffsets = listOf(-3, -2, -1, 1, 2, 3)
        return when (problem.operation) {
            Operation.Add -> nearbyOffsets.map { problem.left + problem.right + it } +
                nearbyOffsets.flatMap { offset ->
                    listOf(problem.left + (problem.right + offset), (problem.left + offset) + problem.right)
                }

            Operation.Subtract -> nearbyOffsets.map { problem.left - problem.right + it } +
                nearbyOffsets.flatMap { offset ->
                    listOf(problem.left - (problem.right + offset), (problem.left + offset) - problem.right)
                }

            Operation.Multiply -> nearbyOffsets.flatMap { offset ->
                listOf(
                    problem.left * (problem.right + offset),
                    (problem.left + offset) * problem.right,
                )
            }

            Operation.Divide -> emptyList()
        }.filter { isValidChoiceValue(it, problem) }.distinct()
    }

    private fun isValidChoiceValue(value: Int, problem: MathProblem): Boolean {
        if (value == problem.answer) return false
        return when (problem.operation) {
            Operation.Multiply,
            Operation.Divide,
                -> value > 0

            Operation.Add,
            Operation.Subtract,
                -> value >= 0
        }
    }

    private fun updateSettings(reducer: (DrillUiState) -> DrillUiState) {
        val nextState = reducer(_uiState.value)
        _uiState.value = nextState
        settingsStore.save(nextState.toSettings())
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val settingsStore = DrillSettingsStore(context.applicationContext)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DrillViewModel::class.java)) {
                return DrillViewModel(settingsStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private fun DrillSettings.toUiState(): DrillUiState {
    return DrillUiState(
        timeLimitSeconds = timeLimitSeconds,
        questionCount = questionCount,
        leftNumberRange = leftNumberRange,
        rightNumberRange = rightNumberRange,
        selectedOperation = selectedOperation,
    )
}

private fun DrillUiState.toSettings(): DrillSettings {
    return DrillSettings(
        timeLimitSeconds = timeLimitSeconds,
        questionCount = questionCount,
        leftNumberRange = leftNumberRange,
        rightNumberRange = rightNumberRange,
        selectedOperation = selectedOperation,
    )
}

private data class GeneratedQuestion(
    val problem: MathProblem,
    val choices: List<AnswerChoice>,
)
