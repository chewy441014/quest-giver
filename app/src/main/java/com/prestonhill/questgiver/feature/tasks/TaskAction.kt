package com.prestonhill.questgiver.feature.tasks

sealed interface TaskAction {
    data class Complete(
        val taskId: Long,
        val completionEpochDay: Long,
    ) : TaskAction

    data class Inspect(
        val taskId: Long,
    ) : TaskAction

    data class RequestDelete(
        val taskId: Long,
    ) : TaskAction

    data object DismissDetails : TaskAction
    data object DismissDelete : TaskAction
    data object DeleteTask : TaskAction
    data object DeleteTaskAndHistory : TaskAction
    data object DismissError : TaskAction

    data object Add : TaskAction

    data class Edit(
        val taskId: Long,
    ) : TaskAction

    data class UpdateEditor(
        val editor: TaskEditorUiState,
    ) : TaskAction

    data object Save : TaskAction
    data object DismissEditor : TaskAction
    data object ToggleHidden : TaskAction
    data class SetCompletion(
        val taskId: Long,
        val completionEpochDay: Long,
        val completed: Boolean,
    ) : TaskAction
}