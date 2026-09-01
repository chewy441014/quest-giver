package com.prestonhill.questgiver.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prestonhill.questgiver.core.settings.AppSettings
import com.prestonhill.questgiver.core.time.AppDay
import com.prestonhill.questgiver.core.time.AppDayCalculator
import com.prestonhill.questgiver.core.time.BoundaryTimer
import com.prestonhill.questgiver.core.time.RealBoundaryTimer
import com.prestonhill.questgiver.data.repository.NutritionRepository
import com.prestonhill.questgiver.data.local.database.entity.NutritionItemEntity
import com.prestonhill.questgiver.data.repository.FoodLogDraft
import com.prestonhill.questgiver.data.repository.NutritionItemUsage
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NutritionViewModel(
    private val repository:
    NutritionRepository,
    private val settings:
    Flow<AppSettings>,
    private val clock: Clock,
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
    private val mapper:
    NutritionUiMapper =
        NutritionUiMapper(),
) : ViewModel() {
    private val currentTimestamp =
        MutableStateFlow(clock.millis())

    /*
     * Null means follow the current app day.
     * Past dates remain selected across refreshes.
     */
    private val selectedDate =
        MutableStateFlow<LocalDate?>(null)

    private val showDatePicker =
        MutableStateFlow(false)

    private val operationError =
        MutableStateFlow<String?>(null)

    private val destination =
        MutableStateFlow<
                NutritionDestination?
                >(null)

    private val logEditor = MutableStateFlow<NutritionLogEditorUiState?>(null)

    private val settingsState =
        settings.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    private val timeState =
        combine(
            currentTimestamp,
            settingsState,
        ) { timestamp, appSettings ->
            createTimeState(
                timestamp = timestamp,
                settings = appSettings,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            initialValue =
                createTimeState(
                    timestamp =
                        currentTimestamp.value,
                    settings =
                        AppSettings(),
                ),
        )

    private val selectedDay =
        combine(
            timeState,
            selectedDate,
        ) { time, requestedDate ->
            val date =
                requestedDate
                    ?.takeIf {
                        it <=
                                time.currentDay.date
                    }
                    ?: time.currentDay.date

            time.dayCalculator.forDate(date)
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            initialValue =
                timeState.value.currentDay,
        )

    private val overlayState =
        combine(
            showDatePicker,
            operationError,
            destination,
            logEditor,
        ) {
                picker,
                error,
                requestedDestination,
                editor,
            ->
            NutritionOverlayState(
                showDatePicker = picker,
                operationError = error,
                destination =
                    requestedDestination,
                logEditor = editor,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState =
        combine(
            selectedDay.flatMapLatest { day ->
                repository
                    .observeNutritionBetween(
                        startTimestampMillis =
                            day.startTimestampMillis,
                        endTimestampMillis =
                            day.endTimestampMillis,
                    )
                    .map { summary ->
                        NutritionDayState(
                            day = day,
                            summary = summary,
                        )
                    }
            },
            timeState,
            settingsState,
            overlayState,
        ) {
                dayState,
                time,
                appSettings,
                overlay,
            ->
            mapper.map(
                summary = dayState.summary,
                selectedDay = dayState.day,
                currentDay =
                    time.currentDay,
                zoneId = time.zoneId,
                settings = appSettings,
                showDatePicker =
                    overlay.showDatePicker,
                operationError =
                    overlay.operationError,
                destination =
                    overlay.destination,
                logEditor = overlay.logEditor,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted
                    .WhileSubscribed(5_000),
            initialValue =
                NutritionScreenUiState(),
        )

    init {
        startBoundaryTimer()
    }

    fun onAction(
        action: NutritionAction,
    ) {
        when (action) {
            NutritionAction
                .OpenDatePicker -> {
                showDatePicker.value = true
            }

            NutritionAction
                .DismissDatePicker -> {
                showDatePicker.value = false
            }

            is NutritionAction.SelectDate -> {
                selectDate(action.date)
            }

            NutritionAction.OpenAddLog -> {
                openNewLogEditor()
            }

            is NutritionAction.InspectLog -> {
                openLogEditor(action.logId)
            }

            is NutritionAction
            .ChangeLogItemSearch -> {
                logEditor.update {
                    it?.takeUnless {
                            editor -> editor.isBusy
                    }?.copy(
                        itemSearch = action.value,
                        versionGroupNameKey = null,
                    ) ?: it
                }
            }

            is NutritionAction.SelectLogItem -> {
                logEditor.update { editor ->
                    if (
                        editor == null ||
                        editor.isBusy ||
                        editor.itemOptions.none {
                            it.id == action.itemId
                        }
                    ) {
                        editor
                    } else {
                        editor.copy(
                            selectedItemId =
                                action.itemId,
                            itemSearch = "",
                            errorMessage = null,
                            versionGroupNameKey = null,
                        )
                    }
                }
            }

            is NutritionAction.ChangeLogWeight -> {
                logEditor.update {
                    it?.takeUnless {
                            editor -> editor.isBusy
                    }?.copy(
                        weightText = action.value,
                        errorMessage = null,
                    ) ?: it
                }
            }

            is NutritionAction.ChangeLogTime -> {
                logEditor.update {
                    it?.takeUnless {
                            editor -> editor.isBusy
                    }?.copy(
                        time = action.time,
                        errorMessage = null,
                    ) ?: it
                }
            }

            is NutritionAction.SelectLogFood -> {
                logEditor.update { editor ->
                    if (
                        editor == null ||
                        editor.isBusy
                    ) {
                        editor
                    } else {
                        val versions =
                            editor.itemOptions.filter {
                                it.nameKey ==
                                        action.nameKey
                            }

                        when (versions.size) {
                            0 -> editor

                            1 ->
                                editor.copy(
                                    selectedItemId =
                                        versions.single().id,
                                    itemSearch = "",
                                    versionGroupNameKey =
                                        null,
                                    errorMessage = null,
                                )

                            else ->
                                editor.copy(
                                    versionGroupNameKey =
                                        action.nameKey
                                )
                        }
                    }
                }
            }

            NutritionAction.DismissLogVersions -> {
                logEditor.update {
                    it?.copy(
                        versionGroupNameKey = null
                    )
                }
            }

            is NutritionAction.ChangeLogItemSort -> {
                logEditor.update {
                    it?.copy(
                        itemSort = action.sort
                    )
                }
            }

            is NutritionAction.ChangeLogMinimumProtein -> {
                logEditor.update {
                    it?.copy(
                        minimumProteinText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeLogMinimumProteinRatio -> {
                logEditor.update {
                    it?.copy(
                        minimumProteinRatioText =
                            action.value
                    )
                }
            }

            NutritionAction.ResetLogItemFilters -> {
                logEditor.update {
                    it?.copy(
                        itemSort =
                            NutritionItemSort.RECENT,
                        minimumProteinText = "",
                        minimumProteinRatioText = "",
                    )
                }
            }

            NutritionAction.SaveLog -> {
                saveLog()
            }

            NutritionAction.DismissLogEditor -> {
                dismissLogEditor()
            }

            NutritionAction.RequestDeleteLog -> {
                logEditor.update { editor ->
                    if (
                        editor?.isEditing == true &&
                        !editor.isBusy
                    ) {
                        editor.copy(
                            showDeleteConfirmation =
                                true
                        )
                    } else {
                        editor
                    }
                }
            }

            NutritionAction.DismissDeleteLog -> {
                logEditor.update { editor ->
                    if (editor?.isBusy == false) {
                        editor.copy(
                            showDeleteConfirmation =
                                false
                        )
                    } else {
                        editor
                    }
                }
            }

            NutritionAction.DeleteLog -> {
                deleteLog()
            }

            NutritionAction.OpenManage -> {
                if (
                    logEditor.value?.isBusy == true
                ) {
                    return
                }

                showDatePicker.value = false
                logEditor.value = null
                destination.value =
                    NutritionDestination.Manage
            }

            NutritionAction
                .DismissDestination -> {
                destination.value = null
            }

            NutritionAction
                .DismissOperationError -> {
                operationError.value = null
            }
        }
    }

    fun refresh() {
        currentTimestamp.value =
            clock.millis()
    }

    private fun selectDate(
        date: LocalDate,
    ) {
        val currentDate =
            timeState.value
                .currentDay.date

        if (date > currentDate) {
            operationError.value =
                "Future nutrition dates are unavailable."
            return
        }

        selectedDate.value =
            date.takeUnless {
                it == currentDate
            }

        showDatePicker.value = false
        operationError.value = null
        destination.value = null
        logEditor.value = null
    }

    private fun openNewLogEditor() {
        if (logEditor.value?.isBusy == true) {
            return
        }

        showDatePicker.value = false
        operationError.value = null

        val requestedDestination =
            NutritionDestination.AddLog

        destination.value =
            requestedDestination

        viewModelScope.launch {
            try {
                val items =
                    repository
                        .observeItemUsage()
                        .first()
                        .filter {
                            it.item
                                .archivedAtEpochMillis ==
                                    null
                        }

                if (
                    destination.value !=
                    requestedDestination
                ) {
                    return@launch
                }

                val time =
                    timeState.value

                val defaultTime =
                    clock.instant()
                        .atZone(time.zoneId)
                        .toLocalTime()
                        .withSecond(0)
                        .withNano(0)

                logEditor.value =
                    NutritionLogEditorUiState(
                        date =
                            selectedDay.value.date,
                        itemOptions =
                            itemOptions(items),
                        time = defaultTime,
                    )
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                destination.value = null
                operationError.value =
                    "Food log editor could not be opened."
            }
        }
    }

    private fun openLogEditor(
        logId: Long,
    ) {
        if (logEditor.value?.isBusy == true) {
            return
        }

        showDatePicker.value = false
        operationError.value = null

        val requestedDestination =
            NutritionDestination.EditLog(
                logId
            )

        destination.value =
            requestedDestination

        viewModelScope.launch {
            try {
                val log =
                    repository.getLog(logId)

                if (log == null) {
                    destination.value = null
                    operationError.value =
                        "Food log could not be found."
                    return@launch
                }

                val selectedItem =
                    repository.getItem(
                        log.itemId
                    )

                if (selectedItem == null) {
                    destination.value = null
                    operationError.value =
                        "Food log item could not be found."
                    return@launch
                }

                val items =
                    repository
                        .observeItemUsage()
                        .first()
                        .filter { usage ->
                            usage.item
                                .archivedAtEpochMillis ==
                                    null ||
                                    usage.item.id ==
                                    selectedItem.id
                        }

                if (
                    destination.value !=
                    requestedDestination
                ) {
                    return@launch
                }

                val time =
                    timeState.value

                val date =
                    time.dayCalculator
                        .containing(
                            log.consumedAtEpochMillis
                        )
                        .date

                val consumedTime =
                    Instant
                        .ofEpochMilli(
                            log.consumedAtEpochMillis
                        )
                        .atZone(time.zoneId)
                        .toLocalTime()

                logEditor.value =
                    NutritionLogEditorUiState(
                        logId = log.id,
                        date = date,
                        itemOptions = itemOptions(items),
                        selectedItemId =
                            selectedItem.id,
                        weightText =
                            amountText(
                                log.weightGrams
                            ),
                        time = consumedTime,
                    )
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                destination.value = null
                operationError.value =
                    "Food log editor could not be opened."
            }
        }
    }

    private fun saveLog() {
        val editor =
            logEditor.value ?: return

        if (!editor.canSave) {
            return
        }

        val weight =
            editor.weightGrams ?: return

        logEditor.value =
            editor.copy(
                isSaving = true,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                val timestamp =
                    timeState.value
                        .dayCalculator
                        .timestampFor(
                            appDate = editor.date,
                            time = editor.time,
                        )

                val draft =
                    FoodLogDraft(
                        itemId =
                            requireNotNull(
                                editor.selectedItemId
                            ),
                        consumedAtEpochMillis =
                            timestamp,
                        weightGrams = weight,
                    )

                val saved =
                    if (editor.logId == null) {
                        repository.createLog(
                            draft = draft,
                            timestampMillis =
                                clock.millis(),
                        ) != null
                    } else {
                        repository.updateLog(
                            logId = editor.logId,
                            draft = draft,
                            timestampMillis =
                                clock.millis(),
                        )
                    }

                if (saved) {
                    logEditor.value = null
                    destination.value = null
                } else {
                    logEditor.update {
                        it?.copy(
                            isSaving = false,
                            errorMessage =
                                "Food log could not be saved.",
                        )
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                logEditor.update {
                    it?.copy(
                        isSaving = false,
                        errorMessage =
                            "Food log could not be saved.",
                    )
                }
            }
        }
    }

    private fun deleteLog() {
        val editor =
            logEditor.value ?: return

        val logId =
            editor.logId ?: return

        if (editor.isBusy) {
            return
        }

        logEditor.value =
            editor.copy(
                isDeleting = true,
                showDeleteConfirmation =
                    false,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                if (
                    repository.deleteLog(logId)
                ) {
                    logEditor.value = null
                    destination.value = null
                } else {
                    logEditor.update {
                        it?.copy(
                            isDeleting = false,
                            errorMessage =
                                "Food log could not be deleted.",
                        )
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                logEditor.update {
                    it?.copy(
                        isDeleting = false,
                        errorMessage =
                            "Food log could not be deleted.",
                    )
                }
            }
        }
    }

    private fun dismissLogEditor() {
        if (logEditor.value?.isBusy == true) {
            return
        }

        logEditor.value = null
        destination.value = null
    }

    private fun itemOptions(
        items: List<NutritionItemUsage>,
    ): List<NutritionItemOptionUiState> =
        items.map { usage ->
            val item = usage.item

            NutritionItemOptionUiState(
                id = item.id,
                name = item.name,
                nameKey = item.nameKey,
                version = item.version,
                versionLabel =
                    item.versionLabel,
                caloriesPer100g =
                    item.caloriesPer100g,
                proteinPer100g =
                    item.proteinPer100g,
                createdAtEpochMillis =
                    item.createdAtEpochMillis,
                lastConsumedAtEpochMillis =
                    usage
                        .lastConsumedAtEpochMillis,
                isArchived =
                    item.archivedAtEpochMillis !=
                            null,
            )
        }

    private fun amountText(
        value: Double,
    ): String =
        value.toString()
            .removeSuffix(".0")

    private fun startBoundaryTimer() {
        viewModelScope.launch {
            settings
                .map { appSettings ->
                    NutritionBoundarySettings(
                        dayBoundary =
                            appSettings.dayBoundary,
                        daylightSavingEnabled =
                            appSettings
                                .daylightSavingEnabled,
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                        boundarySettings ->
                    val calculator =
                        AppDayCalculator(
                            dayBoundary =
                                boundarySettings
                                    .dayBoundary,
                            zoneId =
                                zoneFor(
                                    boundarySettings
                                        .daylightSavingEnabled
                                ),
                        )

                    currentTimestamp.value =
                        clock.millis()

                    while (true) {
                        val now = clock.millis()

                        val nextBoundary =
                            calculator
                                .containing(now)
                                .endTimestampMillis

                        timer.pause(
                            (nextBoundary - now)
                                .coerceAtLeast(1L)
                        )

                        currentTimestamp.value =
                            clock.millis()
                    }
                }
        }
    }

    private fun createTimeState(
        timestamp: Long,
        settings: AppSettings,
    ): NutritionTimeState {
        val zoneId =
            zoneFor(
                settings
                    .daylightSavingEnabled
            )

        val calculator =
            AppDayCalculator(
                dayBoundary =
                    settings.dayBoundary,
                zoneId = zoneId,
            )

        return NutritionTimeState(
            currentDay =
                calculator.containing(
                    timestamp
                ),
            dayCalculator = calculator,
            zoneId = zoneId,
        )
    }

    private fun zoneFor(
        daylightSavingEnabled: Boolean,
    ): ZoneId =
        if (daylightSavingEnabled) {
            clock.zone
        } else {
            clock.zone.rules
                .getStandardOffset(
                    clock.instant()
                )
        }

    private data class NutritionTimeState(
        val currentDay: AppDay,
        val dayCalculator:
        AppDayCalculator,
        val zoneId: ZoneId,
    )

    private data class NutritionDayState(
        val day: AppDay,
        val summary:
        com.prestonhill.questgiver
        .data.repository
        .NutritionDaySummary,
    )

    private data class NutritionOverlayState(
        val showDatePicker: Boolean,
        val operationError: String?,
        val destination: NutritionDestination?,
        val logEditor: NutritionLogEditorUiState?,
    )

    private data class NutritionBoundarySettings(
        val dayBoundary:
        java.time.LocalTime,
        val daylightSavingEnabled:
        Boolean,
    )
}

class NutritionViewModelFactory(
    private val repository:
    NutritionRepository,
    private val settings:
    Flow<AppSettings>,
    private val clock: Clock,
    private val timer: BoundaryTimer =
        RealBoundaryTimer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                NutritionViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return NutritionViewModel(
                repository = repository,
                settings = settings,
                clock = clock,
                timer = timer,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: " +
                    modelClass.name
        )
    }
}