package com.example.focusgate

enum class FocusGatePage(val depth: Int) {
    HOME(0), TARGET_PICKER(1), DEVELOPER_CENTER(1)
}

enum class BackAction {
    CLOSE_OVERLAY,
    CANCEL_EDITING,
    COLLAPSE_MODULE,
    POP_PAGE,
    EXIT_APP
}

data class BackUiState(
    val page: FocusGatePage,
    val overlayVisible: Boolean = false,
    val editing: Boolean = false,
    val expandedModuleHistory: List<String> = emptyList()
)

data class BackResolution(
    val action: BackAction,
    val moduleId: String? = null
)

object UiBackStateResolver {
    fun resolve(state: BackUiState): BackResolution = when {
        state.overlayVisible -> BackResolution(BackAction.CLOSE_OVERLAY)
        state.editing -> BackResolution(BackAction.CANCEL_EDITING)
        state.expandedModuleHistory.isNotEmpty() -> BackResolution(
            BackAction.COLLAPSE_MODULE,
            state.expandedModuleHistory.last()
        )
        state.page != FocusGatePage.HOME -> BackResolution(BackAction.POP_PAGE)
        else -> BackResolution(BackAction.EXIT_APP)
    }

    fun recordExpanded(history: List<String>, moduleId: String): List<String> =
        history.filterNot { it == moduleId } + moduleId

    fun recordCollapsed(history: List<String>, moduleId: String): List<String> =
        history.filterNot { it == moduleId }
}

object FocusGateMotion {
    const val SHORT_MS = 160
    const val MEDIUM_MS = 220
    const val PAGE_MS = 260
}
