package com.zcode.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.storage.SessionDao
import com.zcode.android.core.storage.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StartupState(
    val onboarded: Boolean,
    val lastSessionId: String?,
)

// Resolves the start destination: onboarding when no key is configured yet,
// otherwise the chat of the most recent session (or a fresh one).
@HiltViewModel
class StartupViewModel
    @Inject
    constructor(
        preferences: UserPreferences,
        sessionDao: SessionDao,
    ) : ViewModel() {
        val startup: StateFlow<StartupState?> =
            combine(preferences.onboarded, sessionDao.observeAll()) { onboarded, sessions ->
                StartupState(onboarded = onboarded, lastSessionId = sessions.firstOrNull()?.id)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
