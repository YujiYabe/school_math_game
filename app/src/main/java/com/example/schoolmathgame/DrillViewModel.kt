package com.example.schoolmathgame

import androidx.lifecycle.ViewModel
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

enum class DrillScreenState {
    Settings,
    Drill,
    Result,
}

enum class NumberRange {
    OneDigit,
    IncludeTwoDigits,
}

enum class Operation(val symbol: String) {
    Add("+"),
    Subtract("-"),
    Multiply("×"),
    Divide("÷"),
}

enum class DivisionInputTarget {
    Quotient,
    Remainder,
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

data class DrillUiState(
    val screen: DrillScreenState = DrillScreenState.Settings,
    val timeLimitSeconds: Int = 3,
    val numberRange: NumberRange = NumberRange.OneDigit,
    val totalQuestions: Int = TotalQuestionCount,
    val currentQuestionNumber: Int = 0,
    val correctCount: Int = 0,
    val currentProblem: MathProblem? = null,
    val input: String = "",
    val quotientInput: String = "",
    val remainderInput: String = "",
    val divisionInputTarget: DivisionInputTarget = DivisionInputTarget.Quotient,
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

class DrillViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DrillUiState())
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun setTimeLimit(seconds: Int) {
        _uiState.update { it.copy(timeLimitSeconds = seconds.coerceIn(1, 10)) }
    }

    fun setNumberRange(numberRange: NumberRange) {
        _uiState.update { it.copy(numberRange = numberRange) }
    }

    fun startDrill() {
        timerJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(
            screen = DrillScreenState.Drill,
            currentQuestionNumber = 1,
            correctCount = 0,
            currentProblem = generateProblem(state.numberRange),
            input = "",
            quotientInput = "",
            remainderInput = "",
            divisionInputTarget = DivisionInputTarget.Quotient,
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
                input = "",
                quotientInput = "",
                remainderInput = "",
                divisionInputTarget = DivisionInputTarget.Quotient,
                remainingMillis = 0L,
            )
        }
    }

    fun appendDigit(digit: Int) {
        if (digit !in 0..9) return
        updateActiveInput { current ->
            if (current == "0") digit.toString() else (current + digit).take(5)
        }
    }

    fun appendMinus() {
        val problem = _uiState.value.currentProblem ?: return
        if (problem.operation == Operation.Divide) return

        updateActiveInput { current ->
            when {
                current.startsWith("-") -> current.drop(1)
                current.isBlank() -> "-"
                else -> "-$current"
            }.take(6)
        }
    }

    fun clearInput() {
        updateActiveInput { "" }
    }

    fun setDivisionInputTarget(target: DivisionInputTarget) {
        _uiState.update { it.copy(divisionInputTarget = target) }
    }

    fun toggleDivisionInputTarget() {
        _uiState.update {
            it.copy(
                divisionInputTarget = when (it.divisionInputTarget) {
                    DivisionInputTarget.Quotient -> DivisionInputTarget.Remainder
                    DivisionInputTarget.Remainder -> DivisionInputTarget.Quotient
                },
            )
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val problem = state.currentProblem ?: return
        val isCorrect = when (problem.operation) {
            Operation.Divide -> {
                state.quotientInput.toIntOrNull() == problem.quotient &&
                    state.remainderInput.toIntOrNull() == problem.remainder
            }

            else -> state.input.toIntOrNull() == problem.answer
        }
        completeCurrentProblem(isCorrect)
    }

    private fun updateActiveInput(transform: (String) -> String) {
        val state = _uiState.value
        if (state.screen != DrillScreenState.Drill) return

        val problem = state.currentProblem ?: return
        _uiState.update {
            when {
                problem.operation != Operation.Divide -> it.copy(input = transform(it.input))
                it.divisionInputTarget == DivisionInputTarget.Quotient -> {
                    it.copy(quotientInput = transform(it.quotientInput))
                }

                else -> it.copy(remainderInput = transform(it.remainderInput))
            }
        }
    }

    private fun completeCurrentProblem(isCorrect: Boolean) {
        timerJob?.cancel()

        val state = _uiState.value
        if (state.screen != DrillScreenState.Drill || state.currentProblem == null) return

        val nextCorrectCount = state.correctCount + if (isCorrect) 1 else 0
        if (state.currentQuestionNumber >= state.totalQuestions) {
            _uiState.update {
                it.copy(
                    screen = DrillScreenState.Result,
                    correctCount = nextCorrectCount,
                    currentProblem = null,
                    input = "",
                    quotientInput = "",
                    remainderInput = "",
                    remainingMillis = 0L,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentQuestionNumber = it.currentQuestionNumber + 1,
                correctCount = nextCorrectCount,
                currentProblem = generateProblem(it.numberRange),
                input = "",
                quotientInput = "",
                remainderInput = "",
                divisionInputTarget = DivisionInputTarget.Quotient,
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
                    completeCurrentProblem(isCorrect = false)
                    break
                }
                delay(TimerTickMillis)
            }
        }
    }

    private fun generateProblem(numberRange: NumberRange): MathProblem {
        val operation = Operation.values().random()
        val max = when (numberRange) {
            NumberRange.OneDigit -> 9
            NumberRange.IncludeTwoDigits -> 99
        }

        return when (operation) {
            Operation.Add -> MathProblem(
                left = Random.nextInt(1, max + 1),
                right = Random.nextInt(1, max + 1),
                operation = operation,
            )

            Operation.Subtract -> MathProblem(
                left = Random.nextInt(1, max + 1),
                right = Random.nextInt(1, max + 1),
                operation = operation,
            )

            Operation.Multiply -> MathProblem(
                left = Random.nextInt(1, max + 1),
                right = Random.nextInt(1, max + 1),
                operation = operation,
            )

            Operation.Divide -> MathProblem(
                left = Random.nextInt(1, max + 1),
                right = Random.nextInt(1, max.coerceAtLeast(2) + 1),
                operation = operation,
            )
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
