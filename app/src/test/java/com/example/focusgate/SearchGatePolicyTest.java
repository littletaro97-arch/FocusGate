package com.example.focusgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SearchGatePolicyTest {
    @Test
    public void defaultLimitsMatchNormalUserRequirements() {
        assertEquals(10, QuotaPolicy.INSTANCE.getDefaultDeveloperLimits().getMaxDailyEntertainmentCount());
        assertEquals(15, QuotaPolicy.INSTANCE.getDefaultDeveloperLimits().getMaxEntertainmentDurationMinutes());
    }

    @Test
    public void developerLimitsAreClampedToSupportedRange() {
        assertEquals(
            new DeveloperQuotaLimits(1, 180),
            QuotaPolicy.INSTANCE.sanitizeDeveloperLimits(0, 999)
        );
    }

    @Test
    public void historicalUserSelectionIsClampedWhenDeveloperLowersLimits() {
        QuotaSelection selection = QuotaPolicy.INSTANCE.sanitizeUserSelection(
            10,
            60,
            new DeveloperQuotaLimits(6, 12)
        );
        assertEquals(new QuotaSelection(6, 12), selection);
    }

    @Test
    public void selectionUsesWholeMinutesAndConvertsOnceToMillis() {
        QuotaSelection selection = QuotaPolicy.INSTANCE.sanitizeUserSelection(
            3,
            5,
            QuotaPolicy.INSTANCE.getDefaultDeveloperLimits()
        );
        assertEquals(300_000L, selection.getEntertainmentDurationMillis());
    }

    @Test
    public void confirmedTodayCannotBeConfirmedAgain() {
        assertTrue(QuotaPolicy.INSTANCE.canConfirmTodayQuota(false));
        assertFalse(QuotaPolicy.INSTANCE.canConfirmTodayQuota(true));
    }

    @Test
    public void developerModePersistsAcrossManagerRecreation() {
        MemoryStore store = new MemoryStore();
        new DeveloperSettingsManager(store).setDeveloperModeEnabled(true);
        assertTrue(new DeveloperSettingsManager(store).isDeveloperModeEnabled());
    }

    @Test
    public void explicitExitClearsModeButKeepsLongTermLimits() {
        MemoryStore store = new MemoryStore();
        DeveloperSettingsManager manager = new DeveloperSettingsManager(store);
        manager.setDeveloperModeEnabled(true);
        manager.updateQuotaLimits(42, 90);
        manager.setDeveloperModeEnabled(false);

        DeveloperSettingsManager recreated = new DeveloperSettingsManager(store);
        assertFalse(recreated.isDeveloperModeEnabled());
        assertEquals(new DeveloperQuotaLimits(42, 90), recreated.quotaLimits());
    }

    @Test
    public void invalidPersistedValuesAreSafelyClampedOnRead() {
        MemoryStore store = new MemoryStore();
        store.putInt("maxDailyEntertainmentCount", -10);
        store.putInt("maxEntertainmentDurationMinutes", 999);
        assertEquals(new DeveloperQuotaLimits(1, 180), new DeveloperSettingsManager(store).quotaLimits());
    }

    @Test
    public void restoreDefaultsDoesNotDisableDeveloperMode() {
        MemoryStore store = new MemoryStore();
        DeveloperSettingsManager manager = new DeveloperSettingsManager(store);
        manager.setDeveloperModeEnabled(true);
        assertEquals(QuotaPolicy.INSTANCE.getDefaultDeveloperLimits(), manager.restoreDefaultQuotaLimits());
        assertTrue(manager.isDeveloperModeEnabled());
    }

    @Test
    public void newlyEnabledUserAppCannotBeRemovedUntilTomorrow() {
        TargetAppConfig locked = TargetAppLockPolicy.INSTANCE.applyDailyLock(
            userConfig(true, null, null),
            null,
            true,
            "2026-07-11",
            "2026-07-12"
        );
        assertEquals("2026-07-11", locked.getAddedDate());
        assertEquals("2026-07-12", locked.getCanRemoveAfterDate());
        assertFalse(TargetAppLockPolicy.INSTANCE.canRemoveUserTarget(locked, "2026-07-11"));
        assertTrue(TargetAppLockPolicy.INSTANCE.canRemoveUserTarget(locked, "2026-07-12"));
    }

    @Test
    public void reSavingEnabledUserAppPreservesOriginalLock() {
        TargetAppConfig previous = userConfig(true, "2026-07-10", "2026-07-11");
        TargetAppConfig saved = TargetAppLockPolicy.INSTANCE.applyDailyLock(
            userConfig(true, null, null),
            previous,
            true,
            "2026-07-11",
            "2026-07-12"
        );
        assertEquals(previous.getAddedDate(), saved.getAddedDate());
        assertEquals(previous.getCanRemoveAfterDate(), saved.getCanRemoveAfterDate());
    }

    private static TargetAppConfig userConfig(boolean enabled, String addedDate, String canRemoveAfterDate) {
        return new TargetAppConfig(
            "com.example.user",
            "User App",
            TargetAppType.ENTERTAINMENT_ONLY,
            enabled,
            false,
            true,
            TargetAppSource.USER_ADDED,
            addedDate,
            canRemoveAfterDate
        );
    }

    private static final class MemoryStore implements DeveloperSettingsStore {
        private final Map<String, Boolean> booleans = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return booleans.getOrDefault(key, defaultValue);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return ints.getOrDefault(key, defaultValue);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            booleans.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            ints.put(key, value);
        }
    }
}
