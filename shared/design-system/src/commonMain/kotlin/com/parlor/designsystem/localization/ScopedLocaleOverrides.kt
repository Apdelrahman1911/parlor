package com.parlor.designsystem.localization

/**
 * UI-thread ownership for process-global locale defaults. Activity compositions
 * can overlap: equal language values do not mean the older scope still owns
 * the global setting. Each release is tied to its exact acquisition, and a
 * removed predecessor cannot remain a successor's restoration target.
 *
 * [read] and [write] must synchronously access the complete platform defaults.
 * A later external change is preserved, not mistaken for an owned override.
 */
internal class ScopedLocaleOverrides<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
) {
    private class Entry<T>(var installed: T, var previous: T)

    class Scope<T> internal constructor(
        private val release: () -> Unit,
        private val refresh: (T, T) -> Unit,
    ) {
        operator fun invoke() = release()

        /** A real platform configuration callback, never a composition-body write. */
        fun refresh(defaults: T, platformDefaults: T) = refresh.invoke(defaults, platformDefaults)
    }

    private val entries = mutableListOf<Entry<T>>()

    fun acquire(defaults: T): Scope<T> {
        val entry = Entry(installed = defaults, previous = read())
        write(defaults)
        entries += entry
        return Scope(release = { release(entry) }, refresh = { desired, platform -> refresh(entry, desired, platform) })
    }

    private fun refresh(entry: Entry<T>, defaults: T, platformDefaults: T) {
        // Application callbacks can outlive another Activity's acquisition.
        // Equal language values never grant an older scope write ownership.
        if (entries.lastOrNull() !== entry) return
        val observed = read()
        if (observed != defaults) write(defaults)
        // Preserve live predecessors, but retire the obsolete system baseline
        // behind their restoration chain. The callback supplies that baseline:
        // the system can legitimately change to the same value as our override.
        var baseline = entry
        for (index in entries.lastIndex downTo 1) {
            val predecessor = entries[index - 1]
            if (baseline.previous != predecessor.installed) break
            baseline = predecessor
        }
        baseline.previous = platformDefaults
        entry.installed = defaults
    }

    private fun release(entry: Entry<T>) {
        val index = entries.indexOf(entry)
        if (index < 0) return
        val successor = entries.getOrNull(index + 1)
        entries.removeAt(index)
        if (successor != null) {
            // The successor may have captured a real platform change instead
            // of this predecessor. Never replace that independent baseline.
            if (successor.previous == entry.installed) successor.previous = entry.previous
        } else if (read() == entry.installed) {
            write(entry.previous)
        }
    }
}
