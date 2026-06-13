package com.adarsh.hellomom.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

abstract class BaseViewModel<I : UiIntent, S : UiState, E : UiEffect> : ViewModel() {

    abstract fun createInitialState(): S

    private val _uiState: MutableStateFlow<S> by lazy { MutableStateFlow(createInitialState()) }
    val uiState: StateFlow<S> by lazy { _uiState.asStateFlow() }

    private val _intent: MutableSharedFlow<I> = MutableSharedFlow()

    private val _effect: Channel<E> = Channel()
    val effect: Flow<E> = _effect.receiveAsFlow()

    init {
        subscribeIntents()
    }

    private fun subscribeIntents() {
        viewModelScope.launch {
            _intent.collect {
                handleIntent(it)
            }
        }
    }

    abstract fun handleIntent(intent: I)

    fun sendIntent(intent: I) {
        viewModelScope.launch {
            _intent.emit(intent)
        }
    }

    protected fun setState(reduce: S.() -> S) {
        val newState = _uiState.value.reduce()
        _uiState.value = newState
    }

    protected fun setEffect(builder: () -> E) {
        val effectValue = builder()
        viewModelScope.launch {
            _effect.send(effectValue)
        }
    }
}
