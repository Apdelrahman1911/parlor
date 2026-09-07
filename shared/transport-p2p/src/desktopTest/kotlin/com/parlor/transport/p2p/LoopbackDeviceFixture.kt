package com.parlor.transport.p2p

import com.parlor.core.result.Result
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.transport.RoomTransport
import com.parlor.storage.secure.InMemorySecureKeyValueBacking
import com.parlor.storage.secure.PlatformKeyedSecureStorage
import dev.p2pkit.core.AppId
import dev.p2pkit.core.ExplicitSecurityRisk
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.PeerAuthorizationPolicy
import dev.p2pkit.core.SecurityMode
import dev.p2pkit.core.dsl.jvmSecureIdentityStore
import dev.p2pkit.core.security.JvmSecureIdentityStore
import dev.p2pkit.transport.lan.lan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** One synthetic device, retaining its own in-memory identity and credentials across kit creation. */
internal class LoopbackDeviceFixture(
    initializationDispatcher: CoroutineDispatcher,
    kitFactoryOverride: P2pKitFactory? = null,
) {
    val identityStore: JvmSecureIdentityStore = LoopbackIdentityStore()
    // Deliberately test-only plaintext storage; no OS keychain or user's identity is opened.
    val secureStorage = PlatformKeyedSecureStorage(InMemorySecureKeyValueBacking())

    @OptIn(ExplicitSecurityRisk::class)
    private val kitFactory = kitFactoryOverride ?: object : P2pKitFactory {
        override suspend fun createKit(appId: AppId, deviceName: String): P2pKit =
            createOwnedP2pKit(initializationDispatcher) {
                P2pKit.create {
                    this.appId = appId
                    this.deviceName = deviceName
                    transports { lan() }
                    jvmSecureIdentityStore(identityStore)
                    security {
                        mode = SecurityMode.AuthenticatedV2(
                            PeerAuthorizationPolicy.AcceptAnyAuthenticatedSameApp,
                        )
                    }
                }
            }
    }

    fun transport(appId: AppId, deviceName: String, scope: CoroutineScope): P2pKitRoomTransport =
        P2pKitRoomTransport(
            appId = appId,
            deviceName = deviceName,
            scope = scope,
            kitFactory = kitFactory,
            secureStorage = secureStorage,
        )
}

/**
 * Model the explicit host approval that the application normally presents in its lobby.
 * The caller takes ownership immediately, before later assertions can fail.
 */
internal suspend fun joinApprovedLoopbackPeer(
    host: LocalRoom,
    peerTransport: RoomTransport,
    displayName: String,
    onRoomOpened: (LocalRoom) -> Unit,
): LocalRoom = coroutineScope {
    val approval = async(start = CoroutineStart.UNDISPATCHED) {
        val pending = withTimeout(30.seconds) {
            host.pendingAdmissions.first { requests -> requests.any { it.displayName == displayName } }
                .single { it.displayName == displayName }
        }
        check(!pending.isRejoin) { "Loopback fixture expected initial admission" }
        check(pending.playerId != host.selfPlayerId) { "Loopback devices must have distinct identities" }
        check(host.approveAdmission(pending.playerId) is Result.Success) { "Loopback host approval failed" }
        pending.playerId
    }
    var opened: LocalRoom? = null
    var ownershipTransferred = false
    try {
        val result = withTimeout(30.seconds) {
            peerTransport.join(host.info.value.code, displayName)
        }
        check(result is Result.Success) { "Loopback peer join failed" }
        val room = result.data
        opened = room
        onRoomOpened(room)
        ownershipTransferred = true
        val approvedPlayerId = approval.await()
        check(room.selfPlayerId == approvedPlayerId) { "Loopback admission returned another seat" }
        withTimeout(10.seconds) {
            host.members.first { members -> members.any { it.playerId == approvedPlayerId && it.connected } }
        }
        room
    } finally {
        withContext(NonCancellable) {
            approval.cancelAndJoin()
            if (!ownershipTransferred) opened?.leave()
        }
    }
}

private class LoopbackIdentityStore : JvmSecureIdentityStore {
    private val values = mutableMapOf<String, ByteArray>()

    override fun read(namespace: String): ByteArray? =
        synchronized(values) { values[namespace]?.copyOf() }

    override fun putIfAbsent(namespace: String, value: ByteArray): ByteArray =
        synchronized(values) {
            values.getOrPut(namespace) { value.copyOf() }.copyOf()
        }

    override fun delete(namespace: String): Boolean =
        synchronized(values) { values.remove(namespace)?.also { it.fill(0) } != null }
}
