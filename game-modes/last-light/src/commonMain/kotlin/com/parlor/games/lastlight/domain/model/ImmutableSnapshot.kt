// Adapted from PartyDeck df649e6c896203bdf93130f6497e229757d5da30, core/src/commonMain/kotlin/dev/partydeck/core/.
package com.parlor.games.lastlight.domain.model

/** Copies input values into a read-only implementation with no mutable backing-list escape. */
internal fun <T> Iterable<T>.immutableSnapshot(): List<T> = ImmutableSnapshot(this)

private class ImmutableSnapshot<T>(source: Iterable<T>) : AbstractList<T>() {
    private val values = source.toList()

    override val size: Int
        get() = values.size

    override fun get(index: Int): T = values[index]
}

internal fun <T> Iterable<T>.immutableSetSnapshot(): Set<T> = ImmutableSetSnapshot(this)

private class ImmutableSetSnapshot<T>(source: Iterable<T>) : AbstractSet<T>() {
    private val values = source.distinct().immutableSnapshot()
    override val size: Int get() = values.size
    override fun iterator(): Iterator<T> = values.iterator()
}

internal fun <K, V> Map<K, V>.immutableMapSnapshot(): Map<K, V> = ImmutableMapSnapshot(this)

private class ImmutableMapSnapshot<K, V>(source: Map<K, V>) : AbstractMap<K, V>() {
    override val entries: Set<Map.Entry<K, V>> = source.map { (key, value) ->
        ImmutableEntry(key, value)
    }.immutableSetSnapshot()

    private class ImmutableEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
        override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
        override fun toString(): String = "$key=$value"
    }
}
