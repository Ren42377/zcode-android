package com.zcode.android.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.engine.BuiltinModels
import com.zcode.android.core.engine.LlmClient
import com.zcode.android.core.engine.LlmEndpoint
import com.zcode.android.core.engine.ProviderPresets
import com.zcode.android.core.storage.ApiKeyVault
import com.zcode.android.core.storage.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class OnboardingForm(
    val providerId: String = ProviderPresets.all.first().id,
    val apiKey: String = "",
    val showKey: Boolean = false,
    val validating: Boolean = false,
)

// Collects the API key, validates it with one lightweight request, and stores it
// encrypted. There is no OAuth path anywhere in the app (see ADR 0003).
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val preferences: UserPreferences,
        private val apiKeyVault: ApiKeyVault,
        private val client: LlmClient,
    ) : ViewModel() {
        private val _form = MutableStateFlow(OnboardingForm())
        val form: StateFlow<OnboardingForm> = _form.asStateFlow()

        private val _done = MutableStateFlow(false)
        val done: StateFlow<Boolean> = _done.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        fun update(transform: (OnboardingForm) -> OnboardingForm) {
            _form.value = transform(_form.value)
        }

        fun dismissError() {
            _error.value = null
        }

        fun submit() {
            val state = _form.value
            val apiKey = state.apiKey.trim()
            if (state.validating || apiKey.isEmpty()) {
                return
            }
            val preset = ProviderPresets.byId(state.providerId) ?: return
            _error.value = null
            _form.value = state.copy(validating = true)
            viewModelScope.launch {
                try {
                    client.validate(LlmEndpoint(baseUrl = preset.baseUrl, protocol = preset.protocol, apiKey = apiKey))
                    apiKeyVault.save(preset.id, apiKey)
                    preferences.setProviderId(preset.id)
                    preferences.setModel(BuiltinModels.glm53.id)
                    preferences.setOnboarded(true)
                    _done.value = true
                } catch (e: IOException) {
                    _error.value = e.message ?: "Validation failed"
                } finally {
                    _form.value = _form.value.copy(validating = false)
                }
            }
        }
    }
