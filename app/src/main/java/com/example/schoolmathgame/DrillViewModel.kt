package com.example.schoolmathgame

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
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

private const val DefaultQuestionCount = 5
private const val TimerTickMillis = 50L
private const val ChoiceCount = 4
private const val DefaultTimeLimitSeconds = 30
private const val MinTimeLimitSeconds = 1
private const val MaxTimeLimitSeconds = 30
private const val MinQuestionCount = 5
private const val MaxQuestionCount = 100
private const val MaxHistoryCount = 50
private const val MaxRetryCount = 2
private const val DefaultYoutubeMinutesPer100Correct = 30
private const val MaxYoutubeRewardAvailableSeconds = 120 * 60
private const val DefaultYoutubeRewardUnlimited = false
private const val DefaultInAppYoutubeEnabled = true
private const val MinParentPasswordLength = 4
private const val DefaultParentPassword = ""

enum class DrillScreenState {
    Settings,
    Drill,
    RetryResult,
    Result,
    History,
    HistoryDetail,
    Admin,
    YoutubeReward,
}

enum class NumberRange(val label: String) {
    OneDigit("1〜9"),
    IncludeTwoDigits("1〜99"),
}

enum class Operation(val symbol: String) {
    Add("+"),
    Subtract("-"),
    Multiply("×"),
    Divide("÷"),
}

private val DefaultEnabledOperations = Operation.values().toSet()

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

data class OperationProgress(
    val operation: Operation,
    val solvedCount: Int,
    val percentage: Int,
)

data class SavedWifiNetwork(
    val ssid: String,
    val password: String,
)

data class DrillUiState(
    val screen: DrillScreenState = DrillScreenState.Settings,
    val timeLimitSeconds: Int = DefaultTimeLimitSeconds,
    val questionCount: Int = DefaultQuestionCount,
    val leftNumberRange: NumberRange = NumberRange.OneDigit,
    val rightNumberRange: NumberRange = NumberRange.OneDigit,
    val selectedOperation: Operation = Operation.Add,
    val enabledOperations: Set<Operation> = DefaultEnabledOperations,
    val operationProgress: List<OperationProgress> = emptyList(),
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
    val isAdminAuthenticated: Boolean = false,
    val adminAuthError: String? = null,
    val parentPasswordMessage: String? = null,
    val youtubeMinutesPer100Correct: Int = DefaultYoutubeMinutesPer100Correct,
    val youtubeRewardTotalScore: Int = 0,
    val youtubeRewardAvailableSeconds: Int = 0,
    val youtubeRewardUsedSeconds: Int = 0,
    val youtubeRewardAdjustmentSeconds: Int = 0,
    val isYoutubeRewardUnlimited: Boolean = DefaultYoutubeRewardUnlimited,
    val isInAppYoutubeEnabled: Boolean = DefaultInAppYoutubeEnabled,
    val youtubeWifiSsid: String = "",
    val youtubeWifiPassword: String = "",
    val savedYoutubeWifiNetworks: List<SavedWifiNetwork> = emptyList(),
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
    val questionCount: Int = DefaultQuestionCount,
    val leftNumberRange: NumberRange = NumberRange.OneDigit,
    val rightNumberRange: NumberRange = NumberRange.OneDigit,
    val selectedOperation: Operation = Operation.Add,
    val enabledOperations: Set<Operation> = DefaultEnabledOperations,
    val operationSolvedCounts: Map<Operation, Int> = emptyMap(),
    val parentPasswordSalt: String = "",
    val parentPasswordHash: String = "",
    val youtubeMinutesPer100Correct: Int = DefaultYoutubeMinutesPer100Correct,
    val youtubeRewardCorrectCount: Int = 0,
    val youtubeRewardUsedSeconds: Int = 0,
    val youtubeRewardAdjustmentSeconds: Int = 0,
    val isYoutubeRewardUnlimited: Boolean = DefaultYoutubeRewardUnlimited,
    val isInAppYoutubeEnabled: Boolean = DefaultInAppYoutubeEnabled,
    val youtubeWifiSsid: String = "",
    val youtubeWifiPassword: String = "",
    val savedYoutubeWifiNetworks: List<SavedWifiNetwork> = emptyList(),
)

private val DefaultDrillSettings = DrillSettings()

private class DrillSettingsStore(context: Context) {
    private val settingsFile = File(context.filesDir, SettingsFileName)
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "drill_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): DrillSettings {
        val fileSettings = readSettingsFile()
        if (fileSettings != null) return fileSettings

        val migratedSettings = readPreferences()
        writeSettingsFile(migratedSettings)
        return migratedSettings
    }

    fun save(settings: DrillSettings) {
        val current = load()
        writeSettingsFile(
            current.copy(
                timeLimitSeconds = settings.timeLimitSeconds,
                questionCount = settings.questionCount,
                leftNumberRange = settings.leftNumberRange,
                rightNumberRange = settings.rightNumberRange,
                selectedOperation = settings.selectedOperation,
                enabledOperations = settings.enabledOperations.sanitizedEnabledOperations(),
            ),
        )
    }

    fun saveEnabledOperations(enabledOperations: Set<Operation>) {
        writeSettingsFile(
            load().copy(enabledOperations = enabledOperations.sanitizedEnabledOperations()),
        )
    }

    fun addOperationSolvedCount(operation: Operation, solvedCount: Int) {
        if (solvedCount <= 0) return

        val current = load()
        writeSettingsFile(
            current.copy(
                operationSolvedCounts = current.operationSolvedCounts +
                    (operation to ((current.operationSolvedCounts[operation] ?: 0) + solvedCount)),
            ),
        )
    }

    fun saveParentPasswordHash(salt: String, hash: String) {
        writeSettingsFile(load().copy(parentPasswordSalt = salt, parentPasswordHash = hash))
    }

    fun saveYoutubeMinutesPer100Correct(minutes: Int) {
        writeSettingsFile(
            load().copy(
                youtubeMinutesPer100Correct = minutes.coerceIn(0, 120),
                isYoutubeRewardUnlimited = false,
            ),
        )
    }

    fun addYoutubeRewardCorrectCount(correctCount: Int) {
        if (correctCount <= 0) return

        val current = load()
        writeSettingsFile(
            current.copy(youtubeRewardCorrectCount = current.youtubeRewardCorrectCount + correctCount),
        )
    }

    fun saveYoutubeRewardUsedSeconds(seconds: Int) {
        writeSettingsFile(load().copy(youtubeRewardUsedSeconds = seconds.coerceAtLeast(0)))
    }

    fun saveYoutubeRewardAvailableSeconds(seconds: Int) {
        val current = load()
        val adjustedSeconds = seconds.coerceIn(0, MaxYoutubeRewardAvailableSeconds)
        writeSettingsFile(
            current.copy(
                youtubeRewardAdjustmentSeconds = adjustedSeconds - current.youtubeRewardRawAvailableSeconds(),
                isYoutubeRewardUnlimited = false,
            ),
        )
    }

    fun saveYoutubeWifiSettings(ssid: String, password: String) {
        val trimmedSsid = ssid.trim()
        val current = load()
        val savedNetworks = if (trimmedSsid.isBlank()) {
            current.savedYoutubeWifiNetworks
        } else {
            (listOf(SavedWifiNetwork(trimmedSsid, password)) +
                current.savedYoutubeWifiNetworks.filterNot { network -> network.ssid == trimmedSsid })
        }
        writeSettingsFile(
            current.copy(
                youtubeWifiSsid = trimmedSsid,
                youtubeWifiPassword = password,
                savedYoutubeWifiNetworks = savedNetworks,
            ),
        )
    }

    fun selectYoutubeWifiSettings(ssid: String) {
        val current = load()
        val network = current.savedYoutubeWifiNetworks.firstOrNull { it.ssid == ssid.trim() } ?: return
        writeSettingsFile(
            current.copy(
                youtubeWifiSsid = network.ssid,
                youtubeWifiPassword = network.password,
            ),
        )
    }

    fun deleteYoutubeWifiSettings(ssid: String) {
        val trimmedSsid = ssid.trim()
        val current = load()
        val savedNetworks = current.savedYoutubeWifiNetworks.filterNot { network -> network.ssid == trimmedSsid }
        val activeWasDeleted = current.youtubeWifiSsid == trimmedSsid
        writeSettingsFile(
            current.copy(
                youtubeWifiSsid = if (activeWasDeleted) "" else current.youtubeWifiSsid,
                youtubeWifiPassword = if (activeWasDeleted) "" else current.youtubeWifiPassword,
                savedYoutubeWifiNetworks = savedNetworks,
            ),
        )
    }

    fun saveInAppYoutubeEnabled(enabled: Boolean) {
        writeSettingsFile(load().copy(isInAppYoutubeEnabled = enabled))
    }

    private fun readSettingsFile(): DrillSettings? {
        if (!settingsFile.exists()) return null
        return runCatching {
            JSONObject(settingsFile.readText()).toDrillSettings()
        }.getOrNull()
    }

    private fun writeSettingsFile(settings: DrillSettings) {
        val tempFile = File(settingsFile.parentFile, "${settingsFile.name}.tmp")
        tempFile.writeText(settings.toJson().toString())
        if (!tempFile.renameTo(settingsFile)) {
            settingsFile.writeText(settings.toJson().toString())
            tempFile.delete()
        }
    }

    private fun readPreferences(): DrillSettings {
        return DrillSettings(
            timeLimitSeconds = preferences
                .getInt(KeyTimeLimitSeconds, DefaultDrillSettings.timeLimitSeconds)
                .coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds),
            questionCount = preferences
                .getInt(KeyQuestionCount, DefaultDrillSettings.questionCount)
                .coerceIn(MinQuestionCount, MaxQuestionCount),
            leftNumberRange = preferences.getEnum(KeyLeftNumberRange, NumberRange.OneDigit),
            rightNumberRange = preferences.getEnum(KeyRightNumberRange, NumberRange.OneDigit),
            selectedOperation = preferences.getEnum(KeySelectedOperation, Operation.Add),
            enabledOperations = preferences.getString(KeyEnabledOperations, null).toEnabledOperations(),
            operationSolvedCounts = Operation.values().associateWith { operation ->
                preferences.getInt(operationSolvedCountKey(operation), 0).coerceAtLeast(0)
            },
            parentPasswordSalt = preferences.getString(KeyParentPasswordSalt, null).orEmpty(),
            parentPasswordHash = preferences.getString(KeyParentPasswordHash, null).orEmpty(),
            youtubeMinutesPer100Correct = preferences
                .getInt(KeyYoutubeMinutesPer100Correct, DefaultYoutubeMinutesPer100Correct)
                .coerceIn(0, 120),
            youtubeRewardCorrectCount = preferences.getInt(KeyYoutubeRewardCorrectCount, 0).coerceAtLeast(0),
            youtubeRewardUsedSeconds = preferences.getInt(KeyYoutubeRewardUsedSeconds, 0).coerceAtLeast(0),
            youtubeRewardAdjustmentSeconds = preferences.getInt(KeyYoutubeRewardAdjustmentSeconds, 0),
            isYoutubeRewardUnlimited = preferences.getBoolean(
                KeyYoutubeRewardUnlimited,
                DefaultYoutubeRewardUnlimited,
            ),
            isInAppYoutubeEnabled = preferences.getBoolean(KeyInAppYoutubeEnabled, DefaultInAppYoutubeEnabled),
            youtubeWifiSsid = preferences.getString(KeyYoutubeWifiSsid, null).orEmpty().trim(),
            youtubeWifiPassword = preferences.getString(KeyYoutubeWifiPassword, null).orEmpty(),
        ).withMigratedWifiNetworks()
    }

    private fun DrillSettings.withMigratedWifiNetworks(): DrillSettings {
        if (youtubeWifiSsid.isBlank()) return copy(savedYoutubeWifiNetworks = savedYoutubeWifiNetworks.sanitizedWifiNetworks())
        val savedNetworks = (
            listOf(SavedWifiNetwork(youtubeWifiSsid.trim(), youtubeWifiPassword)) + savedYoutubeWifiNetworks
            ).sanitizedWifiNetworks()
        return copy(
            youtubeWifiSsid = youtubeWifiSsid.trim(),
            savedYoutubeWifiNetworks = savedNetworks,
        )
    }

    private fun List<SavedWifiNetwork>.sanitizedWifiNetworks(): List<SavedWifiNetwork> {
        return map { network -> network.copy(ssid = network.ssid.trim()) }
            .filter { network -> network.ssid.isNotBlank() }
            .distinctBy { network -> network.ssid }
    }

    private fun JSONObject.toDrillSettings(): DrillSettings {
        val countsJson = optJSONObject(KeyOperationSolvedCounts)
        return DrillSettings(
            timeLimitSeconds = optInt(
                KeyTimeLimitSeconds,
                DefaultDrillSettings.timeLimitSeconds,
            ).coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds),
            questionCount = optInt(
                KeyQuestionCount,
                DefaultDrillSettings.questionCount,
            ).coerceIn(MinQuestionCount, MaxQuestionCount),
            leftNumberRange = enumValueOrDefault(optString(KeyLeftNumberRange), NumberRange.OneDigit),
            rightNumberRange = enumValueOrDefault(optString(KeyRightNumberRange), NumberRange.OneDigit),
            selectedOperation = enumValueOrDefault(optString(KeySelectedOperation), Operation.Add),
            enabledOperations = if (has(KeyEnabledOperations)) {
                optString(KeyEnabledOperations).toEnabledOperations()
            } else {
                DefaultEnabledOperations
            },
            operationSolvedCounts = Operation.values().associateWith { operation ->
                countsJson?.optInt(operation.name, 0)?.coerceAtLeast(0) ?: 0
            },
            parentPasswordSalt = optString(KeyParentPasswordSalt, ""),
            parentPasswordHash = optString(KeyParentPasswordHash, ""),
            youtubeMinutesPer100Correct = optInt(
                KeyYoutubeMinutesPer100Correct,
                DefaultYoutubeMinutesPer100Correct,
            ).coerceIn(0, 120),
            youtubeRewardCorrectCount = optInt(KeyYoutubeRewardCorrectCount, 0).coerceAtLeast(0),
            youtubeRewardUsedSeconds = optInt(KeyYoutubeRewardUsedSeconds, 0).coerceAtLeast(0),
            youtubeRewardAdjustmentSeconds = optInt(KeyYoutubeRewardAdjustmentSeconds, 0),
            isYoutubeRewardUnlimited = optBoolean(
                KeyYoutubeRewardUnlimited,
                DefaultYoutubeRewardUnlimited,
            ),
            isInAppYoutubeEnabled = optBoolean(KeyInAppYoutubeEnabled, DefaultInAppYoutubeEnabled),
            youtubeWifiSsid = optString(KeyYoutubeWifiSsid, "").trim(),
            youtubeWifiPassword = optString(KeyYoutubeWifiPassword, ""),
            savedYoutubeWifiNetworks = optJSONArray(KeyYoutubeWifiNetworks).toSavedWifiNetworks(),
        ).withMigratedWifiNetworks()
    }

    private fun DrillSettings.toJson(): JSONObject {
        val countsJson = JSONObject()
        Operation.values().forEach { operation ->
            countsJson.put(operation.name, operationSolvedCounts[operation]?.coerceAtLeast(0) ?: 0)
        }
        return JSONObject()
            .put(KeyTimeLimitSeconds, timeLimitSeconds.coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds))
            .put(KeyQuestionCount, questionCount.coerceIn(MinQuestionCount, MaxQuestionCount))
            .put(KeyLeftNumberRange, leftNumberRange.name)
            .put(KeyRightNumberRange, rightNumberRange.name)
            .put(KeySelectedOperation, selectedOperation.name)
            .put(KeyEnabledOperations, enabledOperations.sanitizedEnabledOperations().toPersistedString())
            .put(KeyOperationSolvedCounts, countsJson)
            .put(KeyParentPasswordSalt, parentPasswordSalt)
            .put(KeyParentPasswordHash, parentPasswordHash)
            .put(KeyYoutubeMinutesPer100Correct, youtubeMinutesPer100Correct.coerceIn(0, 120))
            .put(KeyYoutubeRewardCorrectCount, youtubeRewardCorrectCount.coerceAtLeast(0))
            .put(KeyYoutubeRewardUsedSeconds, youtubeRewardUsedSeconds.coerceAtLeast(0))
            .put(KeyYoutubeRewardAdjustmentSeconds, youtubeRewardAdjustmentSeconds)
            .put(KeyYoutubeRewardUnlimited, isYoutubeRewardUnlimited)
            .put(KeyInAppYoutubeEnabled, isInAppYoutubeEnabled)
            .put(KeyYoutubeWifiSsid, youtubeWifiSsid)
            .put(KeyYoutubeWifiPassword, youtubeWifiPassword)
            .put(KeyYoutubeWifiNetworks, savedYoutubeWifiNetworks.sanitizedWifiNetworks().toJsonArray())
    }

    private fun JSONArray?.toSavedWifiNetworks(): List<SavedWifiNetwork> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = optJSONObject(index)
            SavedWifiNetwork(
                ssid = item?.optString(KeyWifiNetworkSsid, "").orEmpty(),
                password = item?.optString(KeyWifiNetworkPassword, "").orEmpty(),
            )
        }.sanitizedWifiNetworks()
    }

    private fun List<SavedWifiNetwork>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { network ->
            array.put(
                JSONObject()
                    .put(KeyWifiNetworkSsid, network.ssid)
                    .put(KeyWifiNetworkPassword, network.password),
            )
        }
        return array
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
        key: String,
        defaultValue: T,
    ): T {
        val name = getString(key, null) ?: return defaultValue
        return runCatching { enumValueOf<T>(name) }.getOrDefault(defaultValue)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, defaultValue: T): T {
        return runCatching { enumValueOf<T>(name.orEmpty()) }.getOrDefault(defaultValue)
    }

    private companion object {
        const val SettingsFileName = "drill_settings.json"
        const val KeyTimeLimitSeconds = "time_limit_seconds"
        const val KeyQuestionCount = "question_count"
        const val KeyLeftNumberRange = "left_number_range"
        const val KeyRightNumberRange = "right_number_range"
        const val KeySelectedOperation = "selected_operation"
        const val KeyEnabledOperations = "enabled_operations"
        const val KeyOperationSolvedCounts = "operation_solved_counts"
        const val KeyParentPasswordSalt = "parent_password_salt"
        const val KeyParentPasswordHash = "parent_password_hash"
        const val KeyYoutubeMinutesPer100Correct = "youtube_minutes_per_100_correct"
        const val KeyYoutubeRewardCorrectCount = "youtube_reward_correct_count"
        const val KeyYoutubeRewardUsedSeconds = "youtube_reward_used_seconds"
        const val KeyYoutubeRewardAdjustmentSeconds = "youtube_reward_adjustment_seconds"
        const val KeyYoutubeRewardUnlimited = "youtube_reward_unlimited"
        const val KeyInAppYoutubeEnabled = "in_app_youtube_enabled"
        const val KeyYoutubeWifiSsid = "youtube_wifi_ssid"
        const val KeyYoutubeWifiPassword = "youtube_wifi_password"
        const val KeyYoutubeWifiNetworks = "youtube_wifi_networks"
        const val KeyWifiNetworkSsid = "ssid"
        const val KeyWifiNetworkPassword = "password"

        fun operationSolvedCountKey(operation: Operation): String = "operation_solved_count_${operation.name}"
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
    private var youtubeRewardTimerJob: Job? = null

    fun setTimeLimit(seconds: Int) {
        updateSettings { it.copy(timeLimitSeconds = seconds.coerceIn(MinTimeLimitSeconds, MaxTimeLimitSeconds)) }
    }

    fun setQuestionCount(count: Int) {
        updateSettings { it.copy(questionCount = count.coerceIn(MinQuestionCount, MaxQuestionCount)) }
    }

    fun setOperation(operation: Operation) {
        updateSettings { state ->
            if (operation !in state.enabledOperations) {
                state
            } else {
                state.copy(selectedOperation = operation)
                    .withValidRangesForOperation()
            }
        }
    }

    fun setOperationEnabled(operation: Operation) {
        val current = _uiState.value.enabledOperations.sanitizedEnabledOperations()
        val nextEnabledOperations = if (operation in current) {
            if (current.size == 1) current else current - operation
        } else {
            current + operation
        }.sanitizedEnabledOperations()

        settingsStore.saveEnabledOperations(nextEnabledOperations)
        _uiState.update { state ->
            state.copy(enabledOperations = nextEnabledOperations)
                .withValidRangesForOperation()
        }
        settingsStore.save(_uiState.value.toSettings())
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
        youtubeRewardTimerJob?.cancel()
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
                isAdminAuthenticated = false,
            )
        }
    }

    fun showAdmin() {
        timerJob?.cancel()
        youtubeRewardTimerJob?.cancel()
        _uiState.update {
            it.copy(
                screen = DrillScreenState.Admin,
                isAdminAuthenticated = false,
                adminAuthError = null,
                parentPasswordMessage = null,
            )
        }
    }

    fun unlockAdmin(password: String) {
        val settings = settingsStore.load()
        val authenticated = verifyParentPassword(password, settings)
        _uiState.update {
            it.copy(
                isAdminAuthenticated = authenticated,
                adminAuthError = if (authenticated) null else "パスワードが違います。",
                parentPasswordMessage = null,
            )
        }
    }

    fun saveParentPassword(currentPassword: String, newPassword: String): Boolean {
        val settings = settingsStore.load()
        if (newPassword.length < MinParentPasswordLength) {
            _uiState.update {
                it.copy(parentPasswordMessage = "${MinParentPasswordLength}文字以上で設定してください。")
            }
            return false
        }
        if (!verifyParentPassword(currentPassword, settings)) {
            _uiState.update {
                it.copy(parentPasswordMessage = "現在のパスワードが違います。")
            }
            return false
        }

        val passwordHash = ParentPasswordHash.create(newPassword)
        settingsStore.saveParentPasswordHash(passwordHash.salt, passwordHash.hash)
        _uiState.update {
            it.copy(parentPasswordMessage = "パスワードを保存しました。")
        }
        return true
    }

    fun setYoutubeMinutesPer100Correct(minutes: Int) {
        settingsStore.saveYoutubeMinutesPer100Correct(minutes.coerceIn(0, 120))
        refreshSettingsUiState()
    }

    fun setYoutubeRewardAvailableSeconds(seconds: Int) {
        settingsStore.saveYoutubeRewardAvailableSeconds(seconds)
        refreshSettingsUiState()
    }

    fun setInAppYoutubeEnabled(enabled: Boolean) {
        settingsStore.saveInAppYoutubeEnabled(enabled)
        if (!enabled && _uiState.value.screen == DrillScreenState.YoutubeReward) {
            hideYoutubeReward()
            return
        }
        refreshSettingsUiState()
    }

    fun saveYoutubeWifiSettings(ssid: String, password: String) {
        settingsStore.saveYoutubeWifiSettings(ssid, password)
        refreshSettingsUiState()
    }

    fun selectYoutubeWifiSettings(ssid: String) {
        settingsStore.selectYoutubeWifiSettings(ssid)
        refreshSettingsUiState()
    }

    fun deleteYoutubeWifiSettings(ssid: String) {
        settingsStore.deleteYoutubeWifiSettings(ssid)
        refreshSettingsUiState()
    }

    fun startYoutubeRewardSession() {
        val state = _uiState.value
        if (!state.isInAppYoutubeEnabled) return
        if (!state.isYoutubeRewardUnlimited && state.youtubeRewardAvailableSeconds <= 0) return
        timerJob?.cancel()
        _uiState.update { it.copy(screen = DrillScreenState.YoutubeReward) }
    }

    fun hideYoutubeReward() {
        youtubeRewardTimerJob?.cancel()
        youtubeRewardTimerJob = null
        _uiState.update { it.copy(screen = DrillScreenState.Settings) }
    }

    fun setYoutubeRewardTimerRunning(running: Boolean) {
        if (_uiState.value.screen != DrillScreenState.YoutubeReward) return
        if (running) {
            if (youtubeRewardTimerJob?.isActive == true) return
            startYoutubeRewardTimer()
        } else {
            youtubeRewardTimerJob?.cancel()
            youtubeRewardTimerJob = null
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

    private fun startYoutubeRewardTimer() {
        youtubeRewardTimerJob?.cancel()
        youtubeRewardTimerJob = viewModelScope.launch {
            while (_uiState.value.screen == DrillScreenState.YoutubeReward) {
                delay(1_000)
                val settings = settingsStore.load()
                if (settings.isYoutubeRewardUnlimited) {
                    refreshSettingsUiState(screen = DrillScreenState.YoutubeReward)
                    continue
                }
                val availableSeconds = settings.youtubeRewardAvailableSeconds()
                if (availableSeconds <= 1) {
                    settingsStore.saveYoutubeRewardUsedSeconds(
                        settings.youtubeRewardUsedSeconds + availableSeconds.coerceAtLeast(0),
                    )
                    refreshSettingsUiState(screen = DrillScreenState.Settings)
                    break
                }

                settingsStore.saveYoutubeRewardUsedSeconds(settings.youtubeRewardUsedSeconds + 1)
                refreshSettingsUiState(screen = DrillScreenState.YoutubeReward)
            }
        }
    }

    private fun refreshSettingsUiState(screen: DrillScreenState = _uiState.value.screen) {
        val settings = settingsStore.load()
        _uiState.update {
            it.copy(
                screen = screen,
                enabledOperations = settings.enabledOperations,
                operationProgress = settings.operationProgress(),
                youtubeMinutesPer100Correct = settings.youtubeMinutesPer100Correct,
                youtubeRewardTotalScore = settings.youtubeRewardCorrectCount,
                youtubeRewardAvailableSeconds = settings.youtubeRewardAvailableSeconds(),
                youtubeRewardUsedSeconds = settings.youtubeRewardUsedSeconds,
                youtubeRewardAdjustmentSeconds = settings.youtubeRewardAdjustmentSeconds,
                isYoutubeRewardUnlimited = settings.isYoutubeRewardUnlimited,
                isInAppYoutubeEnabled = settings.isInAppYoutubeEnabled,
                youtubeWifiSsid = settings.youtubeWifiSsid,
                youtubeWifiPassword = settings.youtubeWifiPassword,
                savedYoutubeWifiNetworks = settings.savedYoutubeWifiNetworks,
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
                val solvedCount = allReviews.size.takeIf { count -> count > 0 } ?: it.questionCount
                settingsStore.addOperationSolvedCount(it.selectedOperation, solvedCount)
                settingsStore.addYoutubeRewardCorrectCount(finalCorrectCount)
                val latestSettings = settingsStore.load()
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
                    operationProgress = latestSettings.operationProgress(),
                    youtubeRewardTotalScore = latestSettings.youtubeRewardCorrectCount,
                    youtubeRewardAvailableSeconds = latestSettings.youtubeRewardAvailableSeconds(),
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
                -> 10

            Operation.Multiply -> 1
            Operation.Divide -> 2
        }
        val left = randomNumber(leftNumberRange, minimum = minimum)
        val right = randomNumber(rightNumberRange, minimum = minimum)
        val nonNegativeLeft = maxOf(left, right)
        val nonNegativeRight = minOf(left, right)

        return MathProblem(
            left = if (operation == Operation.Subtract) nonNegativeLeft else left,
            right = if (operation == Operation.Subtract) nonNegativeRight else right,
            operation = operation,
        )
    }

    private fun generateMultiplicationTableDivisionProblem(): MathProblem {
        val divisor = Random.nextInt(1, 10)
        val quotient = Random.nextInt(1, 10)
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

        val fallbackStep = if (shouldKeepChoiceOnesDigitFixed(problem)) 10 else 1
        var distance = 1
        while (values.size < ChoiceCount) {
            val offset = distance * fallbackStep
            listOf(correctAnswer - offset, correctAnswer + offset).shuffled().forEach { candidate ->
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
        val sameOnesDigitOffsets = listOf(-30, -20, -10, 10, 20, 30)
        return when (problem.operation) {
            Operation.Add -> if (shouldKeepChoiceOnesDigitFixed(problem)) {
                sameOnesDigitOffsets.map { problem.answer + it }
            } else {
                nearbyOffsets.map { problem.left + problem.right + it } +
                    nearbyOffsets.flatMap { offset ->
                        listOf(problem.left + (problem.right + offset), (problem.left + offset) + problem.right)
                    }
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
        if (shouldKeepChoiceOnesDigitFixed(problem) && value % 10 != problem.answer % 10) return false
        return when (problem.operation) {
            Operation.Multiply,
            Operation.Divide,
                -> value > 0

            Operation.Add,
            Operation.Subtract,
                -> value >= 0
        }
    }

    private fun shouldKeepChoiceOnesDigitFixed(problem: MathProblem): Boolean {
        return problem.operation == Operation.Add && problem.left >= 10 && problem.right >= 10
    }

    private fun updateSettings(reducer: (DrillUiState) -> DrillUiState) {
        val nextState = reducer(_uiState.value)
        _uiState.value = nextState
        settingsStore.save(nextState.toSettings())
    }

    override fun onCleared() {
        timerJob?.cancel()
        youtubeRewardTimerJob?.cancel()
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
        enabledOperations = enabledOperations,
        operationProgress = operationProgress(),
        history = history,
        youtubeMinutesPer100Correct = youtubeMinutesPer100Correct,
        youtubeRewardTotalScore = youtubeRewardCorrectCount,
        youtubeRewardAvailableSeconds = youtubeRewardAvailableSeconds(),
        youtubeRewardUsedSeconds = youtubeRewardUsedSeconds,
        youtubeRewardAdjustmentSeconds = youtubeRewardAdjustmentSeconds,
        isYoutubeRewardUnlimited = isYoutubeRewardUnlimited,
        isInAppYoutubeEnabled = isInAppYoutubeEnabled,
        youtubeWifiSsid = youtubeWifiSsid,
        youtubeWifiPassword = youtubeWifiPassword,
        savedYoutubeWifiNetworks = savedYoutubeWifiNetworks,
    ).withValidRangesForOperation()
}

private fun DrillUiState.withValidRangesForOperation(): DrillUiState {
    val safeEnabledOperations = enabledOperations.sanitizedEnabledOperations()
    val safeOperation = selectedOperation.takeIf { it in safeEnabledOperations }
        ?: safeEnabledOperations.first()
    val state = copy(
        selectedOperation = safeOperation,
        enabledOperations = safeEnabledOperations,
    )
    return when (safeOperation) {
        Operation.Multiply -> state.copy(
            leftNumberRange = NumberRange.OneDigit,
            rightNumberRange = NumberRange.OneDigit,
        )

        Operation.Divide -> state.copy(
            leftNumberRange = NumberRange.IncludeTwoDigits,
            rightNumberRange = NumberRange.OneDigit,
        )

        Operation.Add,
        Operation.Subtract,
            -> state.copy(
                leftNumberRange = NumberRange.IncludeTwoDigits,
                rightNumberRange = NumberRange.IncludeTwoDigits,
            )
    }
}

private fun DrillUiState.toSettings(): DrillSettings {
    return DrillSettings(
        timeLimitSeconds = timeLimitSeconds,
        questionCount = questionCount,
        leftNumberRange = leftNumberRange,
        rightNumberRange = rightNumberRange,
        selectedOperation = selectedOperation,
        enabledOperations = enabledOperations,
        youtubeMinutesPer100Correct = youtubeMinutesPer100Correct,
        isYoutubeRewardUnlimited = isYoutubeRewardUnlimited,
        isInAppYoutubeEnabled = isInAppYoutubeEnabled,
        youtubeRewardCorrectCount = youtubeRewardTotalScore,
        youtubeRewardUsedSeconds = youtubeRewardUsedSeconds,
        youtubeRewardAdjustmentSeconds = youtubeRewardAdjustmentSeconds,
        youtubeWifiSsid = youtubeWifiSsid,
        youtubeWifiPassword = youtubeWifiPassword,
        savedYoutubeWifiNetworks = savedYoutubeWifiNetworks,
    )
}

private fun DrillSettings.youtubeRewardRawAvailableSeconds(): Int {
    return youtubeRewardEarnedSeconds() - youtubeRewardUsedSeconds
}

private fun DrillSettings.youtubeRewardAvailableSeconds(): Int {
    return (youtubeRewardRawAvailableSeconds() + youtubeRewardAdjustmentSeconds).coerceAtLeast(0)
}

private fun DrillSettings.youtubeRewardEarnedSeconds(): Int {
    return youtubeRewardCorrectCount * youtubeMinutesPer100Correct * 60 / 100
}

private fun DrillSettings.operationProgress(): List<OperationProgress> {
    val totalSolvedCount = operationSolvedCounts.values.sum().coerceAtLeast(0)
    return Operation.values().map { operation ->
        val solvedCount = operationSolvedCounts[operation] ?: 0
        OperationProgress(
            operation = operation,
            solvedCount = solvedCount,
            percentage = if (totalSolvedCount == 0) 0 else solvedCount * 100 / totalSolvedCount,
        )
    }
}

private fun String?.toEnabledOperations(): Set<Operation> {
    if (isNullOrBlank()) return DefaultEnabledOperations
    return split(",")
        .mapNotNull { name -> runCatching { enumValueOf<Operation>(name) }.getOrNull() }
        .toSet()
        .sanitizedEnabledOperations()
}

private fun Set<Operation>.sanitizedEnabledOperations(): Set<Operation> {
    val ordered = Operation.values().filter { operation -> operation in this }.toSet()
    return if (ordered.isEmpty()) DefaultEnabledOperations else ordered
}

private fun Set<Operation>.toPersistedString(): String =
    sanitizedEnabledOperations().joinToString(",") { operation -> operation.name }

private data class ParentPasswordHash(
    val salt: String,
    val hash: String,
) {
    companion object {
        fun create(password: String): ParentPasswordHash {
            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val salt = saltBytes.toBase64()
            return ParentPasswordHash(
                salt = salt,
                hash = hashParentPassword(password, salt),
            )
        }
    }
}

private fun verifyParentPassword(password: String, settings: DrillSettings): Boolean {
    if (settings.parentPasswordSalt.isBlank() || settings.parentPasswordHash.isBlank()) {
        return password == DefaultParentPassword
    }
    return hashParentPassword(password, settings.parentPasswordSalt) == settings.parentPasswordHash
}

private fun hashParentPassword(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
    return bytes.toBase64()
}

private fun ByteArray.toBase64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

private data class GeneratedQuestion(
    val problem: MathProblem,
    val choices: List<AnswerChoice>,
)
