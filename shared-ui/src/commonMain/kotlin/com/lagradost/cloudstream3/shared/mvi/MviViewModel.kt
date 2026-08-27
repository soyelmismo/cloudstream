package com.lagradost.cloudstream3.shared.mvi

import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Base marker interface for MVI UI States.
 */
interface UiState

/**
 * Base marker interface for MVI UI Events / User Intents.
 */
interface UiEvent

/**
 * Base marker interface for MVI single-shot side effects (e.g., Navigation, SnackBar, Toast).
 */
interface UiEffect

/**
 * Pure Kotlin Multiplatform base class for MVI ViewModels.
 * Exposes an immutable [state] of type [StateFlow] and a handler method [handleEvent].
 *
 * @param S The UI State type, must be non-nullable [Any].
 * @param E The UI Event type, must be non-nullable [Any].
 * @param initialState The initial state representation.
 * @param coroutineContext Optional custom coroutine context for [viewModelScope]. Defaults to [SupervisorJob] + [Dispatchers.Default].
 */
abstract class MviViewModel<S : Any, E : Any>(
    initialState: S,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) {
    constructor(
        initialState: S,
        coroutineScope: CoroutineScope?
    ) : this(initialState, coroutineScope?.coroutineContext ?: (SupervisorJob() + Dispatchers.Default))

    /**
     * Managed CoroutineScope for ViewModel operations.
     * Tied to the ViewModel lifecycle and cancelled on [onCleared].
     */
    protected val viewModelScope: CoroutineScope = CoroutineScope(coroutineContext)

    protected val _state: MutableStateFlow<S> = MutableStateFlow(initialState)

    /**
     * Public immutable [StateFlow] observing the UI state.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Synchronous getter for the current state snapshot.
     */
    val currentState: S
        get() = _state.value

    protected val _effects = Channel<UiEffect>(Channel.BUFFERED)

    /**
     * Flow exposing one-time UI side-effects.
     */
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    /**
     * Map of active jobs keyed by custom string identifiers for centralized lifecycle management.
     */
    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Main dispatch method to process incoming user events or UI actions.
     */
    abstract fun handleEvent(event: E)

    /**
     * Dispatch alias for handleEvent.
     */
    open fun onEvent(event: E) = handleEvent(event)

    /**
     * Atomically updates the current state via the given [reducer] block.
     */
    protected fun updateState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    /**
     * Directly replaces the state value.
     */
    protected fun setState(newState: S) {
        _state.value = newState
    }

    /**
     * Emits a one-time side effect to listeners.
     */
    protected fun emitEffect(effect: UiEffect) {
        val result = _effects.trySend(effect)
        if (result.isFailure) {
            viewModelScope.launch {
                _effects.send(effect)
            }
        }
    }

    /**
     * Helper to launch a coroutine inside the ViewModel's [viewModelScope].
     */
    protected fun launch(
        context: CoroutineContext = viewModelScope.coroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(context, block = block)
    }

    /**
     * Helper to launch an asynchronous job inside [viewModelScope] with optional key-based
     * cancellation and safe error handling.
     *
     * - If [key] is provided, any prior running job with the same key is cancelled before launching.
     * - If [onError] is provided, exceptions thrown inside [block] are safely caught and dispatched to [onError]
     *   (with [CancellationException] always rethrown to honor structured concurrency).
     * - If [onError] is null, uncaught exceptions are forwarded to [handleJobError].
     *
     * @param key Optional unique identifier for the job. Passing a key automatically cancels any previous job under this key.
     * @param context Coroutine context for execution. Defaults to [viewModelScope.coroutineContext].
     * @param onError Optional error handler callback for unhandled non-cancellation exceptions.
     * @param block The suspending coroutine execution body.
     * @return The created [Job] instance.
     */
    protected fun launchSafeJob(
        key: String? = null,
        context: CoroutineContext = viewModelScope.coroutineContext,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        if (key != null) {
            cancelJob(key)
        }

        val job = viewModelScope.launch(context) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (onError != null) {
                    onError(t)
                } else {
                    handleJobError(t)
                }
            } finally {
                if (key != null) {
                    synchronized(activeJobs) {
                        if (activeJobs[key] == coroutineContext[Job]) {
                            activeJobs.remove(key)
                        }
                    }
                }
            }
        }

        if (key != null) {
            synchronized(activeJobs) {
                activeJobs[key] = job
            }
        }

        return job
    }

    /**
     * Cancels an active job registered under [key], if any.
     */
    protected fun cancelJob(key: String) {
        val oldJob = synchronized(activeJobs) {
            activeJobs.remove(key)
        }
        oldJob?.cancel()
    }

    /**
     * Returns true if a job with [key] is currently tracked and active.
     */
    protected fun isJobActive(key: String): Boolean {
        return synchronized(activeJobs) {
            activeJobs[key]?.isActive == true
        }
    }

    /**
     * Default error handler hook when no [onError] is supplied to [launchSafeJob].
     * Can be overridden by subclasses to perform custom error logging or state updates.
     */
    open fun handleJobError(throwable: Throwable) {
        // Subclasses can override for centralized logging/state
    }

    /**
     * Lifecycle cleanup hook. Cancels all tracked jobs and [viewModelScope].
     */
    open fun onCleared() {
        synchronized(activeJobs) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        viewModelScope.cancel()
    }

    /**
     * Close alias for [onCleared] to support AutoCloseable / manual disposal.
     */
    open fun close() {
        onCleared()
    }
}
