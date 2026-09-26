package com.zcode.android.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel
    @Inject
    constructor(
        private val sessionManager: TerminalSessionManager,
    ) : ViewModel() {
        private val _terminal = MutableStateFlow<TerminalSessionManager.ActiveTerminal?>(null)
        val terminal: StateFlow<TerminalSessionManager.ActiveTerminal?> = _terminal.asStateFlow()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                _terminal.value = sessionManager.acquire()
            }
        }
    }
