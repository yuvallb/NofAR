package com.nofar.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.preferences.SimpleModeDefaultsInitializer
import com.nofar.core.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppStartupState {
    data object Loading : AppStartupState

    data class Ready(val simpleModeEnabled: Boolean) : AppStartupState
}

@HiltViewModel
class AppStartupViewModel
@Inject
constructor(
    private val simpleModeDefaultsInitializer: SimpleModeDefaultsInitializer,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

    init {
        viewModelScope.launch {
            simpleModeDefaultsInitializer.ensureApplied()
            val simpleModeEnabled = userPreferencesRepository.simpleModeEnabled.first()
            _startupState.value = AppStartupState.Ready(simpleModeEnabled)
        }
    }
}
