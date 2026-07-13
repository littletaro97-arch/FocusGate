package com.example.focusgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNavigationTest {
    @Test
    fun overlayClosesBeforeEditingModuleAndPage() {
        val result = UiBackStateResolver.resolve(
            BackUiState(FocusGatePage.DEVELOPER_CENTER, overlayVisible = true, editing = true, expandedModuleHistory = listOf("quota"))
        )
        assertEquals(BackAction.CLOSE_OVERLAY, result.action)
    }

    @Test
    fun editingCancelsBeforeExpandedModule() {
        val result = UiBackStateResolver.resolve(
            BackUiState(FocusGatePage.TARGET_PICKER, editing = true, expandedModuleHistory = listOf("targets"))
        )
        assertEquals(BackAction.CANCEL_EDITING, result.action)
    }

    @Test
    fun latestExpandedModuleCollapsesFirst() {
        val result = UiBackStateResolver.resolve(
            BackUiState(FocusGatePage.HOME, expandedModuleHistory = listOf("permissions", "targets", "quota"))
        )
        assertEquals(BackAction.COLLAPSE_MODULE, result.action)
        assertEquals("quota", result.moduleId)
    }

    @Test
    fun secondBackPopsPageAfterModuleWasCollapsed() {
        val before = listOf("status")
        val first = UiBackStateResolver.resolve(BackUiState(FocusGatePage.DEVELOPER_CENTER, expandedModuleHistory = before))
        val after = UiBackStateResolver.recordCollapsed(before, first.moduleId!!)
        val second = UiBackStateResolver.resolve(BackUiState(FocusGatePage.DEVELOPER_CENTER, expandedModuleHistory = after))
        assertEquals(BackAction.COLLAPSE_MODULE, first.action)
        assertEquals(BackAction.POP_PAGE, second.action)
    }

    @Test
    fun secondaryPagesPopWithoutChangingDeveloperModeState() {
        val developerModeEnabled = true
        assertEquals(BackAction.POP_PAGE, UiBackStateResolver.resolve(BackUiState(FocusGatePage.DEVELOPER_CENTER)).action)
        assertTrue(developerModeEnabled)
        assertEquals(BackAction.POP_PAGE, UiBackStateResolver.resolve(BackUiState(FocusGatePage.TARGET_PICKER)).action)
    }

    @Test
    fun rootOnlyExitsWhenNoHigherPriorityStateExists() {
        assertEquals(BackAction.EXIT_APP, UiBackStateResolver.resolve(BackUiState(FocusGatePage.HOME)).action)
        assertFalse(UiBackStateResolver.resolve(BackUiState(FocusGatePage.HOME, expandedModuleHistory = listOf("advanced"))).action == BackAction.EXIT_APP)
    }

    @Test
    fun expandingAgainMovesModuleToTopWithoutDuplicates() {
        val history = UiBackStateResolver.recordExpanded(listOf("targets", "quota"), "targets")
        assertEquals(listOf("quota", "targets"), history)
        assertEquals(2, history.distinct().size)
    }

    @Test
    fun motionDurationsStayLightweightAndOrdered() {
        assertTrue(FocusGateMotion.SHORT_MS in 120..200)
        assertTrue(FocusGateMotion.MEDIUM_MS in 180..260)
        assertTrue(FocusGateMotion.PAGE_MS in 200..320)
        assertTrue(FocusGateMotion.SHORT_MS < FocusGateMotion.MEDIUM_MS)
        assertTrue(FocusGateMotion.MEDIUM_MS < FocusGateMotion.PAGE_MS)
        assertEquals(240, FocusGateMotion.EXPAND_MS)
        assertEquals(200, FocusGateMotion.COLLAPSE_MS)
        assertEquals(200, FocusGateMotion.ARROW_MS)
        assertTrue(FocusGateMotion.FADE_OUT_MS <= FocusGateMotion.COLLAPSE_MS)
    }

    @Test
    fun collapsingOnlyChangesTargetModule() {
        val initial = mapOf("targets" to true, "quota" to true, "advanced" to false)
        val updated = ModuleExpansionPolicy.update(initial, "quota", false)
        assertTrue(updated.getValue("targets"))
        assertFalse(updated.getValue("quota"))
        assertFalse(updated.getValue("advanced"))
    }

    @Test
    fun rapidReversalEndsAtLatestRequestedState() {
        val initial = mapOf("targets" to true)
        val collapsed = ModuleExpansionPolicy.update(initial, "targets", false)
        val expandedAgain = ModuleExpansionPolicy.update(collapsed, "targets", true)
        assertTrue(expandedAgain.getValue("targets"))
    }
}
