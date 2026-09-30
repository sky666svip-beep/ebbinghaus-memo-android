package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.ui.settings.SettingsUiEvent
import com.ebbinghaus.memo.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * SettingsViewModel 配置响应与范围钳制单元测试套件
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        settingsRepository = FakeSettingsRepository(UserSettingsEntity(dailyReviewLimit = 20))
        viewModel = SettingsViewModel(settingsRepository)
    }

    @Test
    fun testInitialState_loadsDailyLimit() = runTest {
        val state = viewModel.uiState.value
        assertEquals(20, state.dailyLimit)
        assertFalse(state.isSavedSuccess)
    }

    @Test
    fun testDailyLimitChange_withinLegalRange() = runTest {
        viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(35))

        assertEquals(35, viewModel.uiState.value.dailyLimit)
        assertEquals(35, settingsRepository.settingsState.value.dailyReviewLimit)
    }

    @Test
    fun testDailyLimitChange_clampsNegativeToZero() = runTest {
        viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(-10))

        assertEquals(0, viewModel.uiState.value.dailyLimit)
        assertEquals(0, settingsRepository.settingsState.value.dailyReviewLimit)
    }

    @Test
    fun testDailyLimitChange_clampsExcessToFifty() = runTest {
        viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(100))

        assertEquals(50, viewModel.uiState.value.dailyLimit)
        assertEquals(50, settingsRepository.settingsState.value.dailyReviewLimit)
    }

    @Test
    fun testSaveSettings_setsSavedFlag() = runTest {
        viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(25))
        viewModel.onEvent(SettingsUiEvent.OnSaveSettings)

        assertTrue(viewModel.uiState.value.isSavedSuccess)
        assertEquals(25, settingsRepository.settingsState.value.dailyReviewLimit)
    }
}
