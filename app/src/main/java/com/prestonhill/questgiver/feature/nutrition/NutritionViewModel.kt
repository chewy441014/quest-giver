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
import com.prestonhill.questgiver.data.repository.ComposedNutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionComponentDraft
import com.prestonhill.questgiver.data.repository.NutritionItemDraft
import com.prestonhill.questgiver.data.repository.NutritionValuesInput
import com.prestonhill.questgiver.data.repository.FoodLogDraft
import com.prestonhill.questgiver.data.repository.NutritionItemUsage
import com.prestonhill.questgiver.data.repository.NutritionItemDetails
import com.prestonhill.questgiver.data.repository.NutritionItemRemovalMode
import com.prestonhill.questgiver.data.repository.NutritionItemRemovalResult
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

    private val itemEditor =
        MutableStateFlow<
                NutritionItemEditorUiState?
                >(null)

    private var itemEditorRequestVersion = 0L

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

    private val manageControls =
        MutableStateFlow(
            NutritionManageControls()
        )

    private val manageState =
        combine(
            repository.observeItemUsage(),
            manageControls,
        ) { usage, controls ->
            NutritionManageUiState(
                itemOptions =
                    itemOptions(usage),
                itemSearch =
                    controls.itemSearch,
                itemSort =
                    controls.itemSort,
                minimumProteinText =
                    controls.minimumProteinText,
                minimumProteinRatioText =
                    controls
                        .minimumProteinRatioText,
                archiveFilter =
                    controls.archiveFilter,
            )
        }

    private val editorOverlayState =
        combine(
            logEditor,
            itemEditor,
        ) { log, item ->
            NutritionEditorOverlayState(
                logEditor = log,
                itemEditor = item,
            )
        }

    private val overlayState =
        combine(
            showDatePicker,
            operationError,
            destination,
            editorOverlayState,
            manageState,
        ) {
                picker,
                error,
                requestedDestination,
                editors,
                manage,
            ->
            NutritionOverlayState(
                showDatePicker = picker,
                operationError = error,
                destination =
                    requestedDestination,
                logEditor =
                    editors.logEditor,
                itemEditor =
                    editors.itemEditor,
                manage = manage,
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
                manage = overlay.manage,
                itemEditor = overlay.itemEditor,
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

            is NutritionAction.ChangeItemName -> {
                updateItemEditor {
                    it.copy(
                        nameText = action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemVersionLabel -> {
                updateItemEditor {
                    it.copy(
                        versionLabelText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemEntryMode -> {
                updateItemEditor { editor ->
                    if (editor.isComposed) {
                        editor
                    } else {
                        editor.copy(
                            entryMode = action.mode
                        )
                    }
                }
            }

            is NutritionAction
            .ChangeItemCaloriesPer100g -> {
                updateItemEditor {
                    it.copy(
                        caloriesPer100gText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemProteinPer100g -> {
                updateItemEditor {
                    it.copy(
                        proteinPer100gText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemServingWeight -> {
                updateItemEditor {
                    it.copy(
                        servingWeightText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemServingCalories -> {
                updateItemEditor {
                    it.copy(
                        servingCaloriesText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeItemServingProtein -> {
                updateItemEditor {
                    it.copy(
                        servingProteinText =
                            action.value
                    )
                }
            }

            NutritionAction.RequestRemoveItem -> {
                updateItemEditor { editor ->
                    val canRemove =
                        editor.isEditing &&
                                !editor.isDirty &&
                                (
                                        !editor.isArchived ||
                                                editor.removalMode ==
                                                NutritionItemRemovalModeUiState
                                                    .DELETE
                                        )

                    if (canRemove) {
                        editor.copy(
                            showRemovalConfirmation =
                                true
                        )
                    } else {
                        editor
                    }
                }
            }

            NutritionAction.DismissRemoveItem -> {
                updateItemEditor {
                    it.copy(
                        showRemovalConfirmation =
                            false
                    )
                }
            }

            NutritionAction.ConfirmRemoveItem -> {
                removeItem()
            }

            NutritionAction.RestoreItem -> {
                restoreItem()
            }

            NutritionAction
                .OpenItemComponentPicker -> {
                updateItemEditor {
                    it.copy(
                        showComponentPicker = true,
                        componentSearch = "",
                    )
                }
            }

            NutritionAction
                .DismissItemComponentPicker -> {
                updateItemEditor {
                    it.copy(
                        showComponentPicker = false,
                        componentSearch = "",
                    )
                }
            }

            is NutritionAction
            .ChangeItemComponentSearch -> {
                updateItemEditor { editor ->
                    if (!editor.showComponentPicker) {
                        editor
                    } else {
                        editor.copy(
                            componentSearch =
                                action.value
                        )
                    }
                }
            }

            is NutritionAction.AddItemComponent -> {
                updateItemEditor { editor ->
                    val selected =
                        editor
                            .selectableComponentOptions
                            .firstOrNull {
                                it.id == action.itemId
                            }
                            ?: return@updateItemEditor editor

                    val remaining =
                        editor.componentWeightRemaining

                    editor.copy(
                        components =
                            editor.components +
                                    NutritionItemComponentUiState(
                                        item = selected,
                                        gramsText =
                                            if (remaining > 0.0) {
                                                amountText(
                                                    remaining
                                                )
                                            } else {
                                                ""
                                            },
                                    ),
                        showComponentPicker = false,
                        componentSearch = "",
                    )
                }
            }

            is NutritionAction
            .ChangeItemComponentWeight -> {
                updateItemEditor { editor ->
                    editor.copy(
                        components =
                            editor.components.map {
                                    component ->
                                if (
                                    component.item.id ==
                                    action.itemId
                                ) {
                                    component.copy(
                                        gramsText =
                                            action.value
                                    )
                                } else {
                                    component
                                }
                            }
                    )
                }
            }

            is NutritionAction.RemoveItemComponent -> {
                updateItemEditor { editor ->
                    editor.copy(
                        components =
                            editor.components.filterNot {
                                it.item.id ==
                                        action.itemId
                            }
                    )
                }
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

            NutritionAction.OpenAddItem -> {
                openNewItemEditor()
            }

            is NutritionAction.InspectItem -> {
                openItemEditor(action.itemId)
            }

            NutritionAction.DismissItemEditor -> {
                dismissItemEditor()
            }

            is NutritionAction
            .SelectItemEditorVersion -> {
                if (
                    itemEditor.value?.isDirty ==
                    true
                ) {
                    return
                }

                openItemEditor(action.itemId)
            }

            NutritionAction.SaveItem -> {
                saveItem(
                    asVersion = false
                )
            }

            NutritionAction.SaveItemAsVersion -> {
                saveItem(
                    asVersion = true
                )
            }

            is NutritionAction.ChangeManageSearch -> {
                manageControls.update {
                    it.copy(
                        itemSearch = action.value
                    )
                }
            }

            is NutritionAction.ChangeManageSort -> {
                manageControls.update {
                    it.copy(
                        itemSort = action.sort
                    )
                }
            }

            is NutritionAction
            .ChangeManageMinimumProtein -> {
                manageControls.update {
                    it.copy(
                        minimumProteinText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeManageMinimumProteinRatio -> {
                manageControls.update {
                    it.copy(
                        minimumProteinRatioText =
                            action.value
                    )
                }
            }

            is NutritionAction
            .ChangeManageArchiveFilter -> {
                manageControls.update {
                    it.copy(
                        archiveFilter =
                            action.filter
                    )
                }
            }

            NutritionAction.ResetManageFilters -> {
                manageControls.update {
                    it.copy(
                        itemSort =
                            NutritionItemSort.RECENT,
                        minimumProteinText = "",
                        minimumProteinRatioText = "",
                        archiveFilter =
                            NutritionArchiveFilter.ACTIVE,
                    )
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
                    logEditor.value?.isBusy == true ||
                    itemEditor.value?.isBusy == true
                ) {
                    return
                }

                showDatePicker.value = false
                logEditor.value = null
                manageControls.value =
                    NutritionManageControls()
                itemEditorRequestVersion += 1L
                itemEditor.value = null
                destination.value =
                    NutritionDestination.Manage
            }

            NutritionAction.DismissDestination -> {
                if (
                    logEditor.value?.isBusy == true ||
                    itemEditor.value?.isBusy == true
                ) {
                    return
                }

                itemEditorRequestVersion += 1L
                logEditor.value = null
                itemEditor.value = null
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

    private data class NutritionEditorOverlayState(
        val logEditor:
        NutritionLogEditorUiState?,
        val itemEditor:
        NutritionItemEditorUiState?,
    )

    private fun removeItem() {
        val editor =
            itemEditor.value ?: return

        val itemId =
            editor.itemId ?: return

        val mode =
            editor.removalMode ?: return

        val canRemove =
            !editor.isBusy &&
                    !editor.isDirty &&
                    (
                            !editor.isArchived ||
                                    mode ==
                                    NutritionItemRemovalModeUiState
                                        .DELETE
                            )

        if (!canRemove) {
            return
        }

        itemEditor.value =
            editor.copy(
                isRemoving = true,
                showRemovalConfirmation =
                    false,
                showComponentPicker = false,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                val result =
                    repository.removeItem(
                        itemId = itemId,
                        timestampMillis =
                            clock.millis(),
                    )

                when (result) {
                    NutritionItemRemovalResult
                        .ARCHIVED,
                    NutritionItemRemovalResult
                        .DELETED,
                    NutritionItemRemovalResult
                        .ALREADY_ARCHIVED,
                        -> {
                        itemEditorRequestVersion +=
                            1L
                        itemEditor.value = null
                    }

                    NutritionItemRemovalResult
                        .ITEM_NOT_FOUND -> {
                        itemEditor.update {
                            it?.copy(
                                isRemoving = false,
                                errorMessage =
                                    removalError(mode),
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                itemEditor.update {
                    it?.copy(
                        isRemoving = false,
                        errorMessage =
                            removalError(mode),
                    )
                }
            }
        }
    }

    private fun removalError(
        mode:
        NutritionItemRemovalModeUiState,
    ): String =
        when (mode) {
            NutritionItemRemovalModeUiState
                .ARCHIVE ->
                "Food could not be archived."

            NutritionItemRemovalModeUiState
                .DELETE ->
                "Food could not be deleted."
        }

    private fun restoreItem() {
        val editor =
            itemEditor.value ?: return

        val itemId =
            editor.itemId ?: return

        if (
            editor.isBusy ||
            editor.isDirty ||
            !editor.isArchived
        ) {
            return
        }

        itemEditor.value =
            editor.copy(
                isRemoving = true,
                showComponentPicker = false,
                errorMessage = null,
            )

        viewModelScope.launch {
            try {
                if (
                    repository.restoreItem(
                        itemId = itemId,
                        timestampMillis =
                            clock.millis(),
                    )
                ) {
                    itemEditorRequestVersion +=
                        1L
                    itemEditor.value = null
                } else {
                    itemEditor.update {
                        it?.copy(
                            isRemoving = false,
                            errorMessage =
                                "Food could not be restored.",
                        )
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                itemEditor.update {
                    it?.copy(
                        isRemoving = false,
                        errorMessage =
                            "Food could not be restored.",
                    )
                }
            }
        }
    }

    private fun updateItemEditor(
        transform:
            (NutritionItemEditorUiState) ->
        NutritionItemEditorUiState,
    ) {
        itemEditor.update { editor ->
            if (
                editor == null ||
                editor.isBusy
            ) {
                editor
            } else {
                transform(editor).copy(
                    errorMessage = null
                )
            }
        }
    }

    private fun saveItem(
        asVersion: Boolean,
    ) {
        val editor =
            itemEditor.value ?: return

        if (
            if (asVersion) {
                !editor.canSaveAsVersion
            } else {
                !editor.canSave
            }
        ) {
            return
        }

        itemEditor.value =
            editor.copy(
                isSaving = true,
                errorMessage = null,
                showComponentPicker = false,
            )

        viewModelScope.launch {
            try {
                val saved =
                    persistItem(
                        editor = editor,
                        asVersion = asVersion,
                    )

                if (saved) {
                    itemEditorRequestVersion += 1L
                    itemEditor.value = null
                } else {
                    itemEditor.update {
                        it?.copy(
                            isSaving = false,
                            errorMessage =
                                "Food could not be saved.",
                        )
                    }
                }
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                itemEditor.update {
                    it?.copy(
                        isSaving = false,
                        errorMessage =
                            "Food could not be saved.",
                    )
                }
            }
        }
    }

    private suspend fun persistItem(
        editor: NutritionItemEditorUiState,
        asVersion: Boolean,
    ): Boolean =
        if (editor.isComposed) {
            val draft =
                ComposedNutritionItemDraft(
                    name = editor.nameText,
                    versionLabel =
                        editor.versionLabelText,
                    components =
                        editor.components.map {
                                component ->
                            NutritionComponentDraft(
                                itemId =
                                    component.item.id,
                                gramsPer100g =
                                    requireNotNull(
                                        component
                                            .gramsPer100g
                                    ),
                            )
                        },
                )

            when {
                editor.itemId == null ->
                    repository.createComposedItem(
                        draft = draft,
                        timestampMillis =
                            clock.millis(),
                    ) > 0L

                asVersion ->
                    repository
                        .saveComposedAsVersion(
                            itemId =
                                editor.itemId,
                            draft = draft,
                            timestampMillis =
                                clock.millis(),
                        ) != null

                else ->
                    repository.updateComposedItem(
                        itemId = editor.itemId,
                        draft = draft,
                        timestampMillis =
                            clock.millis(),
                    )
            }
        } else {
            val nutrition =
                when (editor.entryMode) {
                    NutritionEntryMode
                        .PER_100_GRAMS ->
                        NutritionValuesInput
                            .Per100Grams(
                                calories =
                                    requireNotNull(
                                        editor
                                            .caloriesPer100gText
                                            .trim()
                                            .toDoubleOrNull()
                                    ),
                                proteinGrams =
                                    requireNotNull(
                                        editor
                                            .proteinPer100gText
                                            .trim()
                                            .toDoubleOrNull()
                                    ),
                            )

                    NutritionEntryMode.SERVING ->
                        NutritionValuesInput.Serving(
                            weightGrams =
                                requireNotNull(
                                    editor
                                        .servingWeightText
                                        .trim()
                                        .toDoubleOrNull()
                                ),
                            calories =
                                requireNotNull(
                                    editor
                                        .servingCaloriesText
                                        .trim()
                                        .toDoubleOrNull()
                                ),
                            proteinGrams =
                                requireNotNull(
                                    editor
                                        .servingProteinText
                                        .trim()
                                        .toDoubleOrNull()
                                ),
                        )
                }

            val draft =
                NutritionItemDraft(
                    name = editor.nameText,
                    versionLabel =
                        editor.versionLabelText,
                    nutrition = nutrition,
                )

            when {
                editor.itemId == null ->
                    repository.createItem(
                        draft = draft,
                        timestampMillis =
                            clock.millis(),
                    ) > 0L

                asVersion ->
                    repository.saveAsVersion(
                        itemId = editor.itemId,
                        draft = draft,
                        timestampMillis =
                            clock.millis(),
                    ) != null

                else ->
                    repository.updateItem(
                        itemId = editor.itemId,
                        draft = draft,
                        timestampMillis =
                            clock.millis(),
                    )
            }
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
        itemEditorRequestVersion += 1L
        itemEditor.value = null
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
                totalConsumedGrams =
                    usage.totalConsumedGrams,
                consumptionCount =
                    usage.consumptionCount,
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

    private fun openNewItemEditor() {
        if (
            destination.value !=
            NutritionDestination.Manage ||
            itemEditor.value?.isBusy == true
        ) {
            return
        }

        operationError.value = null

        val requestVersion =
            ++itemEditorRequestVersion

        viewModelScope.launch {
            try {
                val usage =
                    repository
                        .observeItemUsage()
                        .first()

                if (
                    requestVersion !=
                    itemEditorRequestVersion ||
                    destination.value !=
                    NutritionDestination.Manage
                ) {
                    return@launch
                }

                itemEditor.value =
                    NutritionItemEditorUiState(
                        knownItems =
                            itemOptions(usage),
                    )
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                if (
                    requestVersion ==
                    itemEditorRequestVersion
                ) {
                    operationError.value =
                        "Food editor could not be opened."
                }
            }
        }
    }

    private fun openItemEditor(
        itemId: Long,
    ) {
        if (
            destination.value !=
            NutritionDestination.Manage ||
            itemEditor.value?.isBusy == true
        ) {
            return
        }

        operationError.value = null

        val requestVersion =
            ++itemEditorRequestVersion

        viewModelScope.launch {
            try {
                val details =
                    repository.getItemDetails(
                        itemId
                    )

                if (details == null) {
                    if (
                        requestVersion ==
                        itemEditorRequestVersion
                    ) {
                        operationError.value =
                            "Food could not be found."
                    }

                    return@launch
                }

                val usage =
                    repository
                        .observeItemUsage()
                        .first()

                val knownItems =
                    itemOptions(usage)

                val removalMode =
                    repository.getRemovalMode(
                        itemId
                    )

                if (
                    requestVersion !=
                    itemEditorRequestVersion ||
                    destination.value !=
                    NutritionDestination.Manage
                ) {
                    return@launch
                }

                val loaded =
                    itemEditorState(
                        details = details,
                        knownItems = knownItems,
                        removalMode =
                            removalMode,
                    )

                itemEditor.value =
                    loaded.copy(
                        initialSnapshot =
                            loaded.currentSnapshot
                    )
            } catch (error: Exception) {
                if (
                    error is CancellationException
                ) {
                    throw error
                }

                if (
                    requestVersion ==
                    itemEditorRequestVersion
                ) {
                    operationError.value =
                        "Food editor could not be opened."
                }
            }
        }
    }

    private fun itemEditorState(
        details: NutritionItemDetails,
        knownItems:
        List<NutritionItemOptionUiState>,
        removalMode:
        NutritionItemRemovalMode?,
    ): NutritionItemEditorUiState {
        val item = details.item

        val optionsById =
            knownItems.associateBy {
                it.id
            }

        return NutritionItemEditorUiState(
            itemId = item.id,
            originalNameKey =
                item.nameKey,
            version = item.version,
            knownItems = knownItems,
            nameText = item.name,
            versionLabelText =
                item.versionLabel.orEmpty(),
            entryMode =
                NutritionEntryMode
                    .PER_100_GRAMS,
            caloriesPer100gText =
                amountText(
                    item.caloriesPer100g
                ),
            proteinPer100gText =
                amountText(
                    item.proteinPer100g
                ),
            components =
                details.components.map {
                        component ->
                    NutritionItemComponentUiState(
                        item =
                            requireNotNull(
                                optionsById[
                                    component.item.id
                                ]
                            ),
                        gramsText =
                            amountText(
                                component
                                    .gramsPer100g
                            ),
                    )
                },
            isArchived =
                item.archivedAtEpochMillis !=
                        null,
            removalMode =
                when (removalMode) {
                    NutritionItemRemovalMode
                        .ARCHIVE ->
                        NutritionItemRemovalModeUiState
                            .ARCHIVE

                    NutritionItemRemovalMode
                        .DELETE ->
                        NutritionItemRemovalModeUiState
                            .DELETE

                    null -> null
                },
        )
    }

    private fun dismissItemEditor() {
        if (itemEditor.value?.isBusy == true) {
            return
        }

        itemEditorRequestVersion += 1L
        itemEditor.value = null
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
        val manage: NutritionManageUiState,
        val itemEditor: NutritionItemEditorUiState?,
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

private data class NutritionManageControls(
    val itemSearch: String = "",
    val itemSort:
    NutritionItemSort =
        NutritionItemSort.RECENT,
    val minimumProteinText: String = "",
    val minimumProteinRatioText:
    String = "",
    val archiveFilter:
    NutritionArchiveFilter =
        NutritionArchiveFilter.ACTIVE,
)