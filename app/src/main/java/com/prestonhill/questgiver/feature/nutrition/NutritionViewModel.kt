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
        ) {
                picker,
                error,
                requestedDestination,
            ->
            NutritionOverlayState(
                showDatePicker = picker,
                operationError = error,
                destination =
                    requestedDestination,
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
                showDatePicker.value = false
                destination.value =
                    NutritionDestination.AddLog
            }

            is NutritionAction.InspectLog -> {
                showDatePicker.value = false
                destination.value =
                    NutritionDestination.EditLog(
                        action.logId
                    )
            }

            NutritionAction.OpenManage -> {
                showDatePicker.value = false
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
    }

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
        val destination:
        NutritionDestination?,
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