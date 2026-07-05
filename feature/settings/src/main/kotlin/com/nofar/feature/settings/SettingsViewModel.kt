package com.nofar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.usecase.EvictUnusedDemTilesUseCase
import com.nofar.core.data.usecase.LruEvictionUseCase
import com.nofar.core.model.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val demCacheBytes: Long = 0L,
    val entityRowCount: Int = 0,
    val evictionThresholdMb: Float = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES / (1024f * 1024f),
    val showPurgeConfirm: Boolean = false,
    val purgeResultMessage: String? = null
)

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val storageRepository: StorageRepository,
    private val evictUnusedDemTilesUseCase: EvictUnusedDemTilesUseCase,
    private val lruEvictionUseCase: LruEvictionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = storageRepository.getStorageStats()
            _uiState.update {
                it.copy(
                    demCacheBytes = stats.demCacheSizeBytes,
                    entityRowCount = stats.entityRowCount
                )
            }
        }
    }

    fun onEvictionThresholdChanged(thresholdMb: Float) {
        _uiState.update { it.copy(evictionThresholdMb = thresholdMb) }
        viewModelScope.launch {
            val thresholdBytes = (thresholdMb * 1024 * 1024).toLong()
            lruEvictionUseCase.execute(thresholdBytes)
            refreshStats()
        }
    }

    fun showPurgeConfirm() {
        _uiState.update { it.copy(showPurgeConfirm = true) }
    }

    fun dismissPurgeConfirm() {
        _uiState.update { it.copy(showPurgeConfirm = false) }
    }

    fun confirmPurgeUnusedTiles() {
        viewModelScope.launch {
            val evicted = evictUnusedDemTilesUseCase.execute()
            val message =
                if (evicted > 0) {
                    "Removed $evicted unused tile(s)."
                } else {
                    "No unused tiles to remove."
                }
            _uiState.update {
                it.copy(showPurgeConfirm = false, purgeResultMessage = message)
            }
            refreshStats()
        }
    }

    fun dismissPurgeResult() {
        _uiState.update { it.copy(purgeResultMessage = null) }
    }
}
