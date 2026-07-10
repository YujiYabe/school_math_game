package com.example.schoolmathgame

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TotalQuestionCount = 100
private const val TimerTickMillis = 50L
private const val ChoiceCount = 4
private const val DefaultTimeLimitSeconds = 3
private const val MinTimeLimitSeconds = 1
private const val MaxTimeLimitSeconds = 30
private const val MinQuestionCount = 5
private const val MaxQuestionCount = 100
private const val MaxHistoryCount = 50
private const val MaxRetryCount = 2

enum class DrillScreenState {
    Settings,
    Drill,
    RetryResult,
    Result,
    History,
    HistoryDetail,
}

enum class NumberRange(val label: String) {
    OneDigit("1〜9"),
    IncludeTwoDigits("1〜99"),
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
    val problem: MathProblem,
    val expression: String,
    val correctAnswer: String,
    val selectedAnswer: String?,
    val isCorrect: Boolean,
    val retryCount: Int = 0,
    val isUnrecoverable: Boolean = false,
    val correctChoiceIndex: Int? = null,
)

data class RetryProblem(
    val questionNumber: Int,
    val problem: MathProblem,
    val retryCount: Int = 0,
    val lastCorrectChoiceIndex: Int? = null,
)

data class DrillHistory(
    val id: Long,
    val completedAtMillis: Long,
    val leftNumberRange: NumberRange,
    val rightNumberRange: NumberRange,
    val operation: Operation,
    val questionCount: Int,
    val timeLimitSeconds: Int,
    val correctCount: Int,
    val reviews: List<AnswerReview>,
) {
    val completedAtText: String
        get() = HistoryDateFormat.format(Date(completedAtMillis))
}

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
    val allReviews: List<AnswerReview> = emptyList(),
    val retryProblems: List<RetryProblem> = emptyList(),
    val history: List<DrillHistory> = emptyList(),
    val selectedHistory: DrillHistory? = null,
    val isRetryMode: Boolean = false,
    val remainingMillis: Long = 0L,
) {
    val activeQuestionCount: Int
        get() = if (isRetryMode) retryProblems.size else questionCount

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

private class DrillHistoryStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "drill_history",
        Context.MODE_PRIVATE,
    )

    fun load(): List<DrillHistory> {
        val rawJson = preferences.getString(KeyHistory, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            List(array.length()) { index ->
                array.getJSONObject(index).toDrillHistory()
            }
        }.getOrDefault(emptyList())
    }

    fun add(history: DrillHistory): List<DrillHistory> {
        val nextHistory = (listOf(history) + load()).take(MaxHistoryCount)
        save(nextHistory)
        return nextHistory
    }

    fun delete(id: Long): List<DrillHistory> {
        val nextHistory = load().filterNot { it.id == id }
        save(nextHistory)
        return nextHistory
    }

    private fun save(history: List<DrillHistory>) {
        val array = JSONArray()
        history.forEach { array.put(it.toJson()) }
        preferences.edit()
            .putString(KeyHistory, array.toString())
            .apply()
    }

    private fun JSONObject.toDrillHistory(): DrillHistory {
        val reviewsArray = getJSONArray("reviews")
        return DrillHistory(
            id = getLong("id"),
            completedAtMillis = getLong("completedAtMillis"),
            leftNumberRange = enumValueOrDefault(optString("leftNumberRange"), NumberRange.OneDigit),
            rightNumberRange = enumValueOrDefault(optString("rightNumberRange"), NumberRange.OneDigit),
            operation = enumValueOrDefault(optString("operation"), Operation.Add),
            questionCount = getInt("questionCount"),
            timeLimitSeconds = getInt("timeLimitSeconds"),
            correctCount = getInt("correctCount"),
            reviews = List(reviewsArray.length()) { index ->
                reviewsArray.getJSONObject(index).toAnswerReview()
            },
        )
    }

    private fun DrillHistory.toJson(): JSONObject {
        val reviewsArray = JSONArray()
        reviews.forEach { reviewsArray.put(it.toJson()) }
        return JSONObject()
            .put("id", id)
            .put("completedAtMillis", completedAtMillis)
            .put("leftNumberRange", leftNumberRange.name)
            .put("rightNumberRange", rightNumberRange.name)
            .put("operation", operation.name)
            .put("questionCount", questionCount)
            .put("timeLimitSeconds", timeLimitSeconds)
            .put("correctCount", correctCount)
            .put("reviews", reviewsArray)
    }

    private fun JSONObject.toAnswerReview(): AnswerReview {
        return AnswerReview(
            questionNumber = getInt("questionNumber"),
            problem = getJSONObject("problem").toMathProblem(),
            expression = getString("expression"),
            correctAnswer = getString("correctAnswer"),
            selectedAnswer = optString("selectedAnswer").ifBlank { null },
            isCorrect = getBoolean("isCorrect"),
            retryCount = optInt("retryCount", 0),
            isUnrecoverable = optBoolean("isUnrecoverable", false),
            correctChoiceIndex = optInt("correctChoiceIndex", -1).takeIf { it >= 0 },
        )
    }

    private fun AnswerReview.toJson(): JSONObject {
        return JSONObject()
            .put("questionNumber", questionNumber)
            .put("problem", problem.toJson())
            .put("expression", expression)
            .put("correctAnswer", correctAnswer)
            .put("selectedAnswer", selectedAnswer.orEmpty())
            .put("isCorrect", isCorrect)
            .put("retryCount", retryCount)
            .put("isUnrecoverable", isUnrecoverable)
            .put("correctChoiceIndex", correctChoiceIndex ?: -1)
    }

    private fun JSONObject.toMathProblem(): MathProblem {
        return MathProblem(
            left = getInt("left"),
            right = getInt("right"),
            operation = enumValueOrDefault(getString("operation"), Operation.Add),
        )
    }

    private fun MathProblem.toJson(): JSONObject {
        return JSONObject()
            .put("left", left)
            .put("right", right)
            .put("operation", operation.name)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, defaultValue: T): T {
        return runCatching { enumValueOf<T>(name.orEmpty()) }.getOrDefault(defaultValue)
    }

    private companion object {
        const val KeyHistory = "history"
    }
}

class DrillViewModel private constructor(
    private val settingsStore: DrillSettingsStore,
    private val historyStore: DrillHistoryStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(settingsStore.load().toUiState(historyStore.load()))
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun setTimeLimit(seconds: Int) {
        updateSettings { it.copy(timeLimitSeconds = seconds.coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds)) }
    }

    fun setQuestionCount(count: Int) {
        updateSettings { it.copy(questionCount = count.coerceIn(MinQuestionCount, MaxQuestionCount)) }
    }

    fun setLeftNumberRange(numberRange: NumberRange) {
        updateSettings { state ->
            state.copy(leftNumberRange = numberRange)
                .withValidRangesForOperation()
        }
    }

    fun setRightNumberRange(numberRange: NumberRange) {
        updateSettings { state ->
            state.copy(rightNumberRange = numberRange)
                .withValidRangesForOperation()
        }
    }

    fun setOperation(operation: Operation) {
        updateSettings { state ->
            state.copy(selectedOperation = operation)
                .withValidRangesForOperation()
        }
    }

    fun startDrill() {
        timerJob?.cancel()
        val state = _uiState.value.withValidRangesForOperation()
        if (state != _uiState.value) {
            _uiState.value = state
            settingsStore.save(state.toSettings())
        }
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
            allReviews = emptyList(),
            retryProblems = emptyList(),
            isRetryMode = false,
            remainingMillis = state.timeLimitSeconds * 1_000L,
        )
        startProblemTimer()
    }

    fun startRetryDrill() {
        timerJob?.cancel()
        val state = _uiState.value
        val firstProblem = state.retryProblems.firstOrNull() ?: return
        _uiState.value = state.copy(
            screen = DrillScreenState.Drill,
            currentQuestionNumber = 1,
            correctCount = 0,
            currentProblem = firstProblem.problem,
            choices = generateIntegerChoices(
                problem = firstProblem.problem,
                avoidedCorrectIndex = firstProblem.lastCorrectChoiceIndex,
            ),
            reviews = emptyList(),
            isRetryMode = true,
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
                allReviews = emptyList(),
                retryProblems = emptyList(),
                selectedHistory = null,
                isRetryMode = false,
                remainingMillis = 0L,
            )
        }
    }

    fun showHistory() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                screen = DrillScreenState.History,
                history = historyStore.load(),
                selectedHistory = null,
            )
        }
    }

    fun showHistoryDetail(history: DrillHistory) {
        _uiState.update {
            it.copy(
                screen = DrillScreenState.HistoryDetail,
                selectedHistory = history,
            )
        }
    }

    fun returnToHistory() {
        _uiState.update {
            it.copy(
                screen = DrillScreenState.History,
                selectedHistory = null,
            )
        }
    }

    fun deleteHistory(history: DrillHistory) {
        val nextHistory = historyStore.delete(history.id)
        _uiState.update {
            it.copy(
                history = nextHistory,
                selectedHistory = it.selectedHistory?.takeIf { selectedHistory ->
                    selectedHistory.id != history.id
                },
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
        val reviewQuestionNumber = state.retryProblems
            .getOrNull(state.currentQuestionNumber - 1)
            ?.questionNumber
            ?: state.currentQuestionNumber
        val nextReviews = state.reviews + AnswerReview(
            questionNumber = reviewQuestionNumber,
            problem = state.currentProblem,
            expression = state.currentProblem.expression,
            correctAnswer = state.choices.firstOrNull { it.isCorrect }?.label.orEmpty(),
            selectedAnswer = selectedChoice?.label,
            isCorrect = isCorrect,
            correctChoiceIndex = state.choices.indexOfFirst { it.isCorrect }.takeIf { it >= 0 },
        )

        if (state.currentQuestionNumber >= state.activeQuestionCount) {
            finishAttempt(
                correctCount = nextCorrectCount,
                reviews = nextReviews,
            )
            return
        }

        _uiState.update {
            val nextProblem = nextProblem(state = it)
            it.copy(
                currentQuestionNumber = it.currentQuestionNumber + 1,
                correctCount = nextCorrectCount,
                currentProblem = nextProblem.problem,
                choices = nextProblem.choices,
                reviews = nextReviews,
                remainingMillis = it.timeLimitSeconds * 1_000L,
            )
        }
        startProblemTimer()
    }

    private fun finishAttempt(
        correctCount: Int,
        reviews: List<AnswerReview>,
    ) {
        _uiState.update {
            val allReviews = if (it.isRetryMode) {
                reviewsAfterRetryAttempt(
                    allReviews = it.allReviews,
                    retryProblems = it.retryProblems,
                    attemptReviews = reviews,
                )
            } else {
                reviews
            }
            val retryableIncorrectReviews = allReviews.filter { review ->
                !review.isCorrect && !review.isUnrecoverable
            }

            if (retryableIncorrectReviews.isEmpty()) {
                val completedAtMillis = System.currentTimeMillis()
                val finalCorrectCount = allReviews.count { review -> review.isCorrect }
                val savedHistory = DrillHistory(
                    id = completedAtMillis,
                    completedAtMillis = completedAtMillis,
                    leftNumberRange = it.leftNumberRange,
                    rightNumberRange = it.rightNumberRange,
                    operation = it.selectedOperation,
                    questionCount = it.questionCount,
                    timeLimitSeconds = it.timeLimitSeconds,
                    correctCount = finalCorrectCount,
                    reviews = allReviews,
                )
                val nextHistory = historyStore.add(savedHistory)
                it.copy(
                    screen = DrillScreenState.Result,
                    correctCount = finalCorrectCount,
                    currentProblem = null,
                    choices = emptyList(),
                    reviews = allReviews,
                    allReviews = allReviews,
                    retryProblems = emptyList(),
                    history = nextHistory,
                    isRetryMode = false,
                    remainingMillis = 0L,
                )
            } else {
                it.copy(
                    screen = DrillScreenState.RetryResult,
                    correctCount = correctCount,
                    currentProblem = null,
                    choices = emptyList(),
                    reviews = retryableIncorrectReviews,
                    allReviews = allReviews,
                    retryProblems = retryableIncorrectReviews.map { review ->
                        RetryProblem(
                            questionNumber = review.questionNumber,
                            problem = review.problem,
                            retryCount = review.retryCount,
                            lastCorrectChoiceIndex = review.correctChoiceIndex,
                        )
                    },
                    isRetryMode = false,
                    remainingMillis = 0L,
                )
            }
        }
    }

    private fun reviewsAfterRetryAttempt(
        allReviews: List<AnswerReview>,
        retryProblems: List<RetryProblem>,
        attemptReviews: List<AnswerReview>,
    ): List<AnswerReview> {
        val retryProblemsByQuestion = retryProblems.associateBy { it.questionNumber }
        val attemptReviewsByQuestion = attemptReviews.associateBy { it.questionNumber }
        return allReviews.map { review ->
            val attemptReview = attemptReviewsByQuestion[review.questionNumber]
                ?: return@map review
            val nextRetryCount = (retryProblemsByQuestion[review.questionNumber]?.retryCount ?: 0) + 1
            if (attemptReview.isCorrect) {
                review.copy(
                    selectedAnswer = attemptReview.selectedAnswer,
                    isCorrect = true,
                    retryCount = nextRetryCount,
                    isUnrecoverable = false,
                    correctChoiceIndex = attemptReview.correctChoiceIndex,
                )
            } else {
                review.copy(
                    selectedAnswer = attemptReview.selectedAnswer,
                    retryCount = nextRetryCount,
                    isUnrecoverable = nextRetryCount >= MaxRetryCount,
                    correctChoiceIndex = attemptReview.correctChoiceIndex,
                )
            }
        }
    }

    private fun nextProblem(state: DrillUiState): GeneratedQuestion {
        val retryProblem = state.retryProblems.getOrNull(state.currentQuestionNumber)
        if (state.isRetryMode && retryProblem != null) {
            return GeneratedQuestion(
                problem = retryProblem.problem,
                choices = generateIntegerChoices(
                    problem = retryProblem.problem,
                    avoidedCorrectIndex = retryProblem.lastCorrectChoiceIndex,
                ),
            )
        }

        return generateQuestion(
            leftNumberRange = state.leftNumberRange,
            rightNumberRange = state.rightNumberRange,
            operation = state.selectedOperation,
        )
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
        if (operation == Operation.Divide) {
            return generateMultiplicationTableDivisionProblem()
        }

        val minimum = when (operation) {
            Operation.Add,
            Operation.Subtract,
                -> 3

            Operation.Multiply -> 3
            Operation.Divide -> 2
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

    private fun generateMultiplicationTableDivisionProblem(): MathProblem {
        val divisor = Random.nextInt(2, 10)
        val quotients = (2..9).filter { quotient -> divisor * quotient >= 10 }
        val quotient = quotients.random()
        return MathProblem(
            left = divisor * quotient,
            right = divisor,
            operation = Operation.Divide,
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

    private fun generateIntegerChoices(
        problem: MathProblem,
        avoidedCorrectIndex: Int? = null,
    ): List<AnswerChoice> {
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

        val choices = values.map { AnswerChoice(label = it.toString(), isCorrect = it == correctAnswer) }
        if (avoidedCorrectIndex == null || choices.size < 2) {
            return choices.shuffled()
        }

        repeat(12) {
            val shuffledChoices = choices.shuffled()
            val correctIndex = shuffledChoices.indexOfFirst { it.isCorrect }
            if (correctIndex != avoidedCorrectIndex) {
                return shuffledChoices
            }
        }

        return choices
            .sortedBy { it.isCorrect }
            .let { sortedChoices ->
                if (sortedChoices.indexOfFirst { it.isCorrect } == avoidedCorrectIndex) {
                    sortedChoices.asReversed()
                } else {
                    sortedChoices
                }
            }
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
        private val historyStore = DrillHistoryStore(context.applicationContext)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DrillViewModel::class.java)) {
                return DrillViewModel(settingsStore, historyStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private val HistoryDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)

private fun DrillSettings.toUiState(history: List<DrillHistory>): DrillUiState {
    return DrillUiState(
        timeLimitSeconds = timeLimitSeconds,
        questionCount = questionCount,
        leftNumberRange = leftNumberRange,
        rightNumberRange = rightNumberRange,
        selectedOperation = selectedOperation,
        history = history,
    ).withValidRangesForOperation()
}

private fun DrillUiState.withValidRangesForOperation(): DrillUiState {
    return when (selectedOperation) {
        Operation.Multiply -> copy(
            leftNumberRange = NumberRange.OneDigit,
            rightNumberRange = NumberRange.OneDigit,
        )

        Operation.Divide -> copy(
            leftNumberRange = NumberRange.IncludeTwoDigits,
            rightNumberRange = NumberRange.OneDigit,
        )

        Operation.Add,
        Operation.Subtract,
            -> this
    }
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
