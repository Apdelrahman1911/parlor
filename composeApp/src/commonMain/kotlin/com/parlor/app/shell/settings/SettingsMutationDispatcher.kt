package com.parlor.app.shell.settings

import com.parlor.storage.settings.SettingsPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs preference writes in the app-owned scope so leaving the Settings screen
 * cannot silently cancel a choice that the user already made.
 *
 * Persistence failures are deliberately surfaced through [onFailure]. Scope
 * cancellation remains cancellation and is never translated into a UI error.
 */
internal class SettingsMutationDispatcher(
    private val scope: CoroutineScope,
    private val onFailure: (SettingsPersistenceException) -> Unit,
) {
    fun submit(mutation: suspend () -> Unit): Job = scope.launch {
        try {
            mutation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SettingsPersistenceException) {
            onFailure(failure)
        }
    }
}
