package com.nofar.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun initialStateHasPlaceholderMessage() {
        // @HiltViewModel + @Inject constructor() are validated at compile time by Hilt/KSP.
        val viewModel = HomeViewModel()
        assertEquals(
            "Manage saved regions and enter Explore when you are inside a ready region.",
            viewModel.uiState.value.message
        )
    }
}
