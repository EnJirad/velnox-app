package com.velnox.core.realtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide foreground/background signal.
 *
 * Uses `ProcessLifecycleOwner`, which is exactly the right granularity: a
 * configuration change or a transition between two activities must *not* look like a
 * background event, or the socket would be torn down and rebuilt on every rotation.
 *
 * The initial value is optimistic (`true`): the tracker is created from the
 * application graph while the app is by definition coming to the foreground.
 */
@Singleton
class AppForegroundTracker @Inject constructor() {

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            _isForeground.value = true
        }

        override fun onStop(owner: LifecycleOwner) {
            _isForeground.value = false
        }
    }

    /** Registers the observer. Called once from `Application.onCreate`. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }
}
