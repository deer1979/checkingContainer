package com.checkingcontainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    /** Drives which shell (public vs authenticated) the app shows. */
    val authState: StateFlow<AuthState> = authRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthState.Loading,
    )
}
