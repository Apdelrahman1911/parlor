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
    private class Entry<T>(val installed: T, var previous: T)

    private val entries = mutableListOf<Entry<T>>()

    fun acquire(defaults: T): () -> Unit {
        val entry = Entry(installed = defaults, previous = read())
        write(defaults)
        entries += entry
        return { release(entry) }
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
