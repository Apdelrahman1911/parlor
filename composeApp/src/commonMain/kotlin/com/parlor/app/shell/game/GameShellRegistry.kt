package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.definition.GameMetadata
import com.parlor.session.PlayMode
import com.parlor.session.multidevice.MultiplayerSessionRole
import com.parlor.session.multidevice.MultiplayerSessionRoute

/** Player-facing ways a registered game can be entered from the app shell. */
internal enum class GameEntryMode {
    Solo,
    PassAndPlay,
    Host,
    Join,
}

/**
 * Shell capabilities supplied by the game binding rather than inferred from
 * game ids. Domain variants (for example Classic Vote) remain on
 * [GameDefinition.supportedModes]; these values describe device topology.
 */
internal data class GameShellCapabilities(
    val entryModes: Set<GameEntryMode>,
) {
    init {
        require(entryModes.isNotEmpty()) { "A game shell must expose at least one entry mode" }
    }

    fun supports(mode: GameEntryMode): Boolean = mode in entryModes

    fun supports(playMode: PlayMode): Boolean = when (playMode) {
        PlayMode.Solo -> supports(GameEntryMode.Solo)
        PlayMode.PassAndPlay -> supports(GameEntryMode.PassAndPlay)
        is PlayMode.MultiDevice -> false
    }
}

/**
 * Transport-independent multiplayer contract contributed by a game binding.
 * The app shell uses this metadata to validate resumable sessions before it
 * routes them into game UI. It deliberately contains no P2pKit types or
 * game-specific state; the binding remains the only owner of the actual host
 * and peer composables.
 */
internal data class GameShellMultiplayerContract(
    val gameId: GameId,
    val gameVersion: Int,
    val supportedPlayerCounts: IntRange,
) {
    init {
        require(gameId.raw.isNotBlank()) { "Multiplayer game id must not be blank" }
        require(gameVersion > 0) { "Multiplayer game version must be positive" }
        require(!supportedPlayerCounts.isEmpty()) {
            "Multiplayer player bounds must not be empty"
        }
    }

    fun supportsPlayerCount(count: Int): Boolean = count in supportedPlayerCounts
}

/** Pure catalog record used by Home and by extensibility contract tests. */
internal data class GameCatalogEntry(
    val gameId: GameId,
    val metadata: GameMetadata,
    val supportedPlayerCounts: IntRange,
    val capabilities: GameShellCapabilities,
)

/** Localized, render-ready catalog copy supplied by each UI binding. */
internal data class GameCatalogPresentation(
    val title: String,
    val subtitle: String,
    val tagline: String,
    val openLabel: String,
    val openContentDescription: String,
)

/**
 * A game launch is the only game-specific destination understood by App.kt.
 * The concrete binding owns all setup and in-game sub-navigation from here.
 */
internal sealed interface GameShellLaunch {
    val gameId: GameId

    data class New(
        override val gameId: GameId,
    ) : GameShellLaunch

    data class ResumeLocal(
        override val gameId: GameId,
        val sessionId: SessionId,
    ) : GameShellLaunch

    data class ResumeMultiplayer(
        override val gameId: GameId,
        val displayName: String,
    ) : GameShellLaunch

    data class RestoreOwnedMultiplayer(
        val route: MultiplayerSessionRoute,
    ) : GameShellLaunch {
        override val gameId: GameId = route.gameId
    }
}

/** Compose-aware adapter contributed once per shipping game at the app root. */
internal interface GameShellBinding {
    val definition: GameDefinition<*, *, *>
    val capabilities: GameShellCapabilities
    val multiplayerContract: GameShellMultiplayerContract?

    @Composable
    fun catalogPresentation(): GameCatalogPresentation

    @Composable
    fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        modifier: Modifier = Modifier,
    )
}

internal interface GameShellRegistry {
    val all: List<GameShellBinding>
    val catalog: List<GameCatalogEntry>
    fun byId(gameId: GameId): GameShellBinding?
}

internal class DefaultGameShellRegistry(
    bindings: List<GameShellBinding>,
) : GameShellRegistry {
    override val all: List<GameShellBinding> = bindings.toList()

    init {
        val duplicateIds = all
            .groupingBy { binding -> binding.definition.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sortedBy { id -> id.raw }
        require(duplicateIds.isEmpty()) {
            "Duplicate game shell ids are not allowed: ${duplicateIds.joinToString { it.raw }}"
        }
        all.forEach { binding ->
            require(!binding.definition.supportedPlayerCounts.isEmpty()) {
                "Game '${binding.definition.id.raw}' has no supported player counts"
            }
            val multiplayer = binding.multiplayerContract
            val exposesMultiplayer = binding.capabilities.supports(GameEntryMode.Host) ||
                binding.capabilities.supports(GameEntryMode.Join)
            require(!exposesMultiplayer || multiplayer != null) {
                "Game '${binding.definition.id.raw}' exposes multiplayer without a contract"
            }
            multiplayer?.let { contract ->
                require(contract.gameId == binding.definition.id) {
                    "Multiplayer contract id does not match game '${binding.definition.id.raw}'"
                }
                require(contract.supportedPlayerCounts == binding.definition.supportedPlayerCounts) {
                    "Multiplayer bounds do not match game '${binding.definition.id.raw}'"
                }
            }
        }
    }

    private val byId = all.associateBy { binding -> binding.definition.id }

    override val catalog: List<GameCatalogEntry> = all.map { binding ->
        GameCatalogEntry(
            gameId = binding.definition.id,
            metadata = binding.definition.metadata,
            supportedPlayerCounts = binding.definition.supportedPlayerCounts,
            capabilities = binding.capabilities,
        )
    }

    override fun byId(gameId: GameId): GameShellBinding? = byId[gameId]
}

/**
 * Pure launch resolver. Unknown games and unsupported entry modes fail closed
 * instead of falling through to another game's UI.
 */
internal class GameShellRouter(
    private val registry: GameShellRegistry,
) {
    fun newGame(gameId: GameId): GameShellLaunch.New? =
        registry.byId(gameId)?.let { GameShellLaunch.New(gameId) }

    fun resumeLocal(gameId: GameId, sessionId: SessionId): GameShellLaunch.ResumeLocal? =
        registry.byId(gameId)
            ?.takeIf { binding ->
                binding.capabilities.supports(GameEntryMode.Solo) ||
                    binding.capabilities.supports(GameEntryMode.PassAndPlay)
            }
            ?.let { GameShellLaunch.ResumeLocal(gameId, sessionId) }

    fun resumeMultiplayer(
        gameId: GameId,
        gameVersion: Int,
        displayName: String,
    ): GameShellLaunch.ResumeMultiplayer? = registry.byId(gameId)
        ?.takeIf { binding ->
            binding.capabilities.supports(GameEntryMode.Join) &&
                binding.multiplayerContract?.gameVersion == gameVersion
        }
        ?.let { GameShellLaunch.ResumeMultiplayer(gameId, displayName) }

    fun restoreOwned(route: MultiplayerSessionRoute): GameShellLaunch.RestoreOwnedMultiplayer? {
        val requiredMode = when (route.role) {
            MultiplayerSessionRole.Host -> GameEntryMode.Host
            MultiplayerSessionRole.Peer -> GameEntryMode.Join
        }
        return registry.byId(route.gameId)
            ?.takeIf { binding -> binding.capabilities.supports(requiredMode) }
            ?.let { GameShellLaunch.RestoreOwnedMultiplayer(route) }
    }

    fun bindingFor(launch: GameShellLaunch): GameShellBinding? = registry.byId(launch.gameId)
        ?.takeIf { binding ->
            when (launch) {
                is GameShellLaunch.New -> true
                is GameShellLaunch.ResumeLocal ->
                    binding.capabilities.supports(GameEntryMode.Solo) ||
                        binding.capabilities.supports(GameEntryMode.PassAndPlay)
                is GameShellLaunch.ResumeMultiplayer ->
                    binding.capabilities.supports(GameEntryMode.Join)
                is GameShellLaunch.RestoreOwnedMultiplayer -> when (launch.route.role) {
                    MultiplayerSessionRole.Host -> binding.capabilities.supports(GameEntryMode.Host)
                    MultiplayerSessionRole.Peer -> binding.capabilities.supports(GameEntryMode.Join)
                }
            }
        }
}
