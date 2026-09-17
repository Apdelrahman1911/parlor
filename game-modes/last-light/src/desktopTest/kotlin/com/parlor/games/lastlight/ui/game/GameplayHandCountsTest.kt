package com.parlor.games.lastlight.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.lastlight.LastLightTestFixture
import com.parlor.games.lastlight.domain.action.LastLightAction
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.CardId
import com.parlor.games.lastlight.domain.model.GamePhase
import com.parlor.games.lastlight.domain.model.GameView
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.protocol.LastLightPeerSnapshotValidator
import com.parlor.games.lastlight.protocol.LastLightProjectionCodec
import com.parlor.games.lastlight.ui.PendingAction
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GameplayHandCountsTest {
    @Test
    fun compact_english_phone_shows_all_counts_without_expanding_or_scrolling() =
        verifyCompactPhone(AppLanguage.English)

    @Test
    fun compact_arabic_phone_shows_all_counts_without_expanding_or_scrolling() =
        verifyCompactPhone(AppLanguage.Arabic)

    @Test
    fun narrow_english_phone_keeps_all_counts_visible() = verifyCompactPhone(AppLanguage.English, 320.dp)

    @Test
    fun narrow_arabic_phone_keeps_all_counts_visible() = verifyCompactPhone(AppLanguage.Arabic, 320.dp)

    @Test
    fun english_claim_and_counts_stay_visible_with_play_and_challenge_controls() =
        verifyCompactPhone(AppLanguage.English, withClaim = true)

    @Test
    fun arabic_claim_and_counts_stay_visible_with_play_and_challenge_controls() =
        verifyCompactPhone(AppLanguage.Arabic, withClaim = true)

    @Test
    fun taller_english_phone_uses_the_available_public_space_for_counts() =
        verifyCompactPhone(AppLanguage.English, height = 760.dp, withClaim = true)

    @Test
    fun taller_arabic_phone_uses_the_available_public_space_for_counts() =
        verifyCompactPhone(AppLanguage.Arabic, height = 760.dp, withClaim = true)

    @Test
    fun two_player_english_table_has_five_and_three_backs_and_a_visible_claim() =
        verifyCompactPhone(AppLanguage.English, seats = 2, withClaim = true)

    @Test
    fun two_player_arabic_table_has_five_and_three_backs_and_a_visible_claim() =
        verifyCompactPhone(AppLanguage.Arabic, seats = 2, withClaim = true)

    @Test
    fun three_player_english_table_keeps_fans_and_claim_above_the_private_hand() =
        verifyCompactPhone(AppLanguage.English, seats = 3, withClaim = true)

    @Test
    fun three_player_arabic_table_keeps_fans_and_claim_above_the_private_hand() =
        verifyCompactPhone(AppLanguage.Arabic, seats = 3, withClaim = true)

    @Test
    fun two_player_english_opening_table_has_no_fake_claim_cards() =
        verifyCompactPhone(AppLanguage.English, seats = 2)

    @Test
    fun two_player_arabic_opening_table_has_no_fake_claim_cards() =
        verifyCompactPhone(AppLanguage.Arabic, seats = 2)

    @Test
    fun three_player_english_opening_table_has_no_fake_claim_cards() =
        verifyCompactPhone(AppLanguage.English, seats = 3)

    @Test
    fun three_player_arabic_opening_table_has_no_fake_claim_cards() =
        verifyCompactPhone(AppLanguage.Arabic, seats = 3)

    @Test
    fun narrow_english_phone_keeps_counts_reachable_with_stacked_controls() =
        verifyStackedControls(AppLanguage.English)

    @Test
    fun narrow_arabic_phone_keeps_counts_reachable_with_stacked_controls() =
        verifyStackedControls(AppLanguage.Arabic)

    @Test
    fun two_through_six_seats_keep_their_order_and_exact_card_fans_in_english() =
        verifyTableOrder(AppLanguage.English)

    @Test
    fun two_through_six_seats_keep_their_order_and_exact_card_fans_in_arabic() =
        verifyTableOrder(AppLanguage.Arabic)

    @Test
    fun large_english_text_keeps_counts_and_full_duplicate_names_reachable() =
        verifyLargeText(AppLanguage.English, "Alexandria Longname the Third")

    @Test
    fun large_arabic_text_keeps_counts_and_full_duplicate_names_reachable() =
        verifyLargeText(AppLanguage.Arabic, "عبد الرحمن محمد عبد الرحمن")

    @Test
    fun english_empty_and_eliminated_hands_have_no_card_backs_and_keep_their_status() =
        verifyEmptyHands(AppLanguage.English, listOf("Claim pending", "Next deal", "Out · watching"))

    @Test
    fun arabic_empty_and_eliminated_hands_have_no_card_backs_and_keep_their_status() =
        verifyEmptyHands(AppLanguage.Arabic, listOf("الادّعاء قابل للتحدّي", "بانتظار التوزيع التالي", "خرج · يتابع اللعب"))

    @Test
    fun counts_follow_accepted_views_not_selection_and_survive_peer_handoff_recovery_and_redeal() = runComposeUiTest {
        val fixture = LastLightTestFixture()
        var state = fixture.initial(count = 6)
        val actor = PlayerId(requireNotNull(state.public.turnPlayerId))
        var view by mutableStateOf(peerView(state, actor))
        var pending by mutableStateOf<PendingAction?>(null)
        var generation by mutableStateOf(0)
        var submitted = emptyList<CardId>()
        setContent {
            LastLightTestFrame {
                key(generation) {
                    TestTable(view, pendingAction = pending, onPlay = { submitted = it; pending = PendingAction.PLAY_CARDS })
                }
            }
        }

        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        repeat(3) { onNodeWithTag("game-card-$it").performClick() }
        assertCount(actor.raw, 5)
        onNodeWithTag("game-play").bringIntoView().performClick()
        assertEquals(3, submitted.size)
        assertCount(actor.raw, 5) // Neither a local selection nor an in-flight submission removes cards.

        runOnIdle {
            state = fixture.accepted(state, LastLightAction.PlayCards(actor, submitted))
            view = peerView(state, actor)
            pending = null
        }
        assertCount(actor.raw, 2)
        state.public.roster.filter { it.id != actor.raw }.forEach { assertCount(it.id, 5) }

        val peer = state.players.first { it.id != actor }.id
        runOnIdle { view = peerView(state, peer) }
        assertCount(actor.raw, 2) // Same two backs when the hand belongs to an opponent.
        onNodeWithTag("game-card-0").assertDoesNotExist()
        assertPublicPanelHasNoCardSecrets()

        runOnIdle {
            val codec = fixture.definition.snapshotCodec()
            state = codec.decode(codec.encode(state))
            view = peerView(state, peer)
            generation++
        }
        assertCount(actor.raw, 2)
        onNodeWithTag("game-card-0").assertDoesNotExist()

        runOnIdle {
            repeat(10) {
                if (state.phase == GamePhase.PLAYING) state = fixture.accepted(state, fixture.legalAction(state))
            }
            assertEquals(GamePhase.ROUND_ENDED, state.phase)
            state = fixture.accepted(state, LastLightAction.NextRound)
            view = peerView(state, peer)
        }
        assertEquals(2, view.roundNumber)
        view.players.forEach { assertCount(it.id, if (it.eliminated) 0 else 5, scroll = true) }
        assertPublicPanelHasNoCardSecrets()
    }

    private fun verifyCompactPhone(
        language: AppLanguage,
        width: Dp = 360.dp,
        height: Dp = 640.dp,
        withClaim: Boolean = false,
        seats: Int = 6,
    ) = runComposeUiTest {
        val view = playingView().let { initial ->
            val shortNames = listOf("Mina", "Noor", "Ari")
            initial.copy(players = initial.players.take(seats).mapIndexed { index, player ->
                if (seats <= 3) player.copy(displayName = shortNames[index]) else player
            })
        }.let { initial ->
            val claimant = initial.players.last().id
            if (withClaim) initial.copy(
                latestClaim = PublicClaim(claimant, 2),
                players = initial.players.map { if (it.id == claimant) it.copy(handCount = 3) else it },
                availableActions = AvailableActions(canPlay = true, canChallenge = true, maxPlayableCards = 3),
            ) else initial
        }
        setContent { LastLightTestFrame(width = width, height = height, language = language) { TestSessionTable(view) } }

        val viewport = onNodeWithTag(LAST_LIGHT_VIEWPORT).getUnclippedBoundsInRoot()
        assertEquals(width, viewport.right - viewport.left)
        assertEquals(height, viewport.bottom - viewport.top)
        assertSeatsDisplayed(view, language)
        assertClaimDisplayed()
        onNodeWithTag("game-reveal-hand").assertIsDisplayed()
        onNodeWithTag("game-card-0").assertDoesNotExist()
        assertPublicPanelHasNoCardSecrets()
        val prefix = "hand-counts-${language.tag}-${width.value.toInt()}x${height.value.toInt()}-claim-$withClaim-$seats-players"
        val claimBack = SemanticsMatcher("public claim card back") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("game-claim-back-") == true
        }
        onAllNodes(claimBack, useUnmergedTree = true).assertCountEquals(if (withClaim) 2 else 0)
        capture("$prefix-concealed")

        onNodeWithTag("game-reveal-hand").performClick()
        assertSeatsDisplayed(view, language)
        assertClaimDisplayed()
        assertPublicPanelHasNoCardSecrets()
        capture("$prefix-revealed")
    }

    private fun verifyStackedControls(language: AppLanguage) = runComposeUiTest {
        val view = playingView().let { initial ->
            initial.copy(
                latestClaim = PublicClaim("f", 2),
                players = initial.players.map { if (it.id == "f") it.copy(handCount = 3) else it },
                availableActions = AvailableActions(canPlay = true, canChallenge = true, maxPlayableCards = 3),
            )
        }
        setContent { LastLightTestFrame(width = 320.dp, language = language) { TestSessionTable(view) } }
        view.players.forEach { assertCount(it.id, it.handCount, scroll = true, language = language) }
        onNodeWithTag("game-reveal-hand").bringIntoView().performClick()
        view.players.forEach { assertCount(it.id, it.handCount, scroll = true, language = language) }
        onNodeWithTag("game-play").bringIntoView().assertIsDisplayed()
        onNodeWithTag("game-challenge").bringIntoView().assertIsDisplayed()
        capture("hand-counts-${language.tag}-stacked-controls")
    }

    private fun verifyTableOrder(language: AppLanguage) = runComposeUiTest {
        var view by mutableStateOf(playingView())
        setContent {
            LastLightTestFrame(width = 320.dp, language = language) {
                PublicPlayerHands(view, largeText = false, compact = true)
            }
        }
        for (size in 2..6) {
            runOnIdle {
                view = playingView().copy(
                    viewerId = null,
                    turnPlayerId = playingView().players[size - 1].id,
                    players = playingView().players.take(size).mapIndexed { index, player -> player.copy(handCount = index) },
                )
            }
            view.players.forEachIndexed { index, player -> assertCount(player.id, index, language = language) }
            val seats = view.players.map { onNodeWithTag("game-player-${it.id}").getUnclippedBoundsInRoot() }
            seats.zipWithNext().forEach { (first, next) ->
                if (first.top == next.top) {
                    assertTrue(if (language == AppLanguage.Arabic) first.left > next.left else first.left < next.left)
                } else assertTrue(next.top >= first.bottom, "Seat rows overlap or changed table order")
            }
            val suffix = if (language == AppLanguage.Arabic) "المقعد" else "seat"
            onNodeWithTag("game-player-a").assert(hasContentDescription("Guest · $suffix 1", substring = true))
            onNodeWithTag("game-player-b").assert(hasContentDescription("Guest · $suffix 2", substring = true))
        }
        capture("hand-counts-${language.tag}-zero-to-five")
    }

    private fun verifyLargeText(language: AppLanguage, longName: String) = runComposeUiTest {
        val view = playingView().let { initial ->
            initial.copy(players = initial.players.map { it.copy(displayName = longName) })
        }
        setContent {
            LastLightTestFrame(width = 320.dp, height = 740.dp, fontScale = 2f, language = language) {
                TestSessionTable(view)
            }
        }
        view.players.forEach { player ->
            assertCount(player.id, 5, scroll = true, language = language)
            onNodeWithTag("game-player-${player.id}").assert(hasContentDescription(longName, substring = true))
            onNodeWithTag("game-player-name-${player.id}", useUnmergedTree = true).assertTextNotTruncated()
        }
        capture("hand-counts-${language.tag}-large-text")
        onNodeWithTag("game-reveal-hand").bringIntoView().assertIsDisplayed()
    }

    private fun verifyEmptyHands(language: AppLanguage, statuses: List<String>) = runComposeUiTest {
        val view = playingView().let { initial ->
            initial.copy(
                players = initial.players.mapIndexed { index, player ->
                    player.copy(handCount = if (index in 1..3) 0 else 5, eliminated = index == 3, penaltyAttempts = if (index == 3) 2 else 0)
                },
                latestClaim = PublicClaim("b", 1),
            )
        }
        setContent {
            LastLightTestFrame(language = language) { PublicPlayerHands(view, largeText = false, compact = true) }
        }
        listOf("b", "c", "d").zip(statuses).forEach { (id, status) ->
            assertCount(id, 0, language = language)
            val zero = if (language == AppLanguage.Arabic) "لا أوراق (0)" else "0 cards"
            onNodeWithTag("game-player-$id").assert(hasContentDescription(zero, substring = true))
                .assert(hasContentDescription(status, substring = true))
        }
        val fuse = if (language == AppLanguage.Arabic) "اختُبر الفتيل 2 من 6 مرات" else "Fuse tested 2 of 6"
        onNodeWithTag("game-player-d").assert(hasContentDescription(fuse, substring = true))
        capture("hand-counts-${language.tag}-empty-and-out")
    }

    private fun ComposeUiTest.assertCount(
        id: String,
        count: Int,
        scroll: Boolean = false,
        language: AppLanguage = AppLanguage.English,
    ) {
        val seat = onNodeWithTag("game-player-$id")
        if (scroll) seat.bringIntoView()
        val fan = onNodeWithTag("game-hand-fan-$id", useUnmergedTree = true).assertIsDisplayed()
        val bounds = fan.getUnclippedBoundsInRoot()
        val frame = seat.getUnclippedBoundsInRoot()
        assertEquals(bounds, fan.getBoundsInRoot(), "Card fan for $id is clipped")
        assertTrue(bounds.left >= frame.left && bounds.right <= frame.right, "Fan escaped seat $id")
        assertTrue(bounds.top >= frame.top && bounds.bottom <= frame.bottom, "Fan escaped seat $id")
        val name = onNodeWithTag("game-player-name-$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom <= name.top, "Cards must be above the player's name")
        val back = SemanticsMatcher("card back belonging to $id") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("game-hand-back-$id-") == true
        }
        onAllNodes(back, useUnmergedTree = true).assertCountEquals(count)
        // Decorative backs are not extra VoiceOver/TalkBack stops; the seat announces the precise count once.
        onAllNodes(back).assertCountEquals(0)
        val cards = (0 until count).map { index ->
            val card = onNodeWithTag("game-hand-back-$id-$index", useUnmergedTree = true).assertIsDisplayed()
                .getBoundsInRoot()
            assertTrue(card.left >= bounds.left && card.right <= bounds.right, "Card $index escaped fan $id")
            assertTrue(card.top >= bounds.top && card.bottom <= bounds.bottom, "Card $index escaped fan $id")
            card
        }
        cards.zipWithNext().forEach { (first, next) ->
            val distance = kotlin.math.abs(((first.left + first.right) - (next.left + next.right)).value / 2f)
            assertTrue(distance >= 10f, "Overlapping cards must remain individually countable: $distance dp")
        }
        val countDescription = if (language == AppLanguage.Arabic) when (count) {
            0 -> "لا أوراق (0)"
            1 -> "ورقة واحدة (1)"
            2 -> "ورقتان (2)"
            else -> "$count أوراق"
        } else "$count ${if (count == 1) "card" else "cards"}"
        seat.assert(hasContentDescription(countDescription, substring = true))
        onNodeWithTag("game-hand-count-$id", useUnmergedTree = true).assertDoesNotExist()
        if (count == 0) {
            onNodeWithTag("game-hand-empty-$id", useUnmergedTree = true).assertIsDisplayed().assertTextNotTruncated()
        } else {
            onNodeWithTag("game-hand-empty-$id", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    private fun ComposeUiTest.assertSeatsDisplayed(view: GameView, language: AppLanguage) {
        view.players.forEach { player ->
            assertCount(player.id, player.handCount, language = language)
            val seat = onNodeWithTag("game-player-${player.id}")
            assertEquals(seat.getUnclippedBoundsInRoot(), seat.getBoundsInRoot(), "Public seat is clipped: ${player.id}")
        }
    }

    private fun ComposeUiTest.assertClaimDisplayed() {
        val claim = onNodeWithTag("game-latest-claim").assertIsDisplayed()
        assertEquals(claim.getUnclippedBoundsInRoot(), claim.getBoundsInRoot(), "The play area must not be clipped by the private hand")
    }

    private fun SemanticsNodeInteraction.assertTextNotTruncated() {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized))
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue(fetchSemanticsNode().size.height >= kotlin.math.ceil(layout.multiParagraph.height))
    }

    private fun ComposeUiTest.assertPublicPanelHasNoCardSecrets() {
        for (secret in listOf("Crown", "Moon", "Star", "Wild", "تاج", "قمر", "نجمة", "جوكر", "private-", "r1-c", "r2-c")) {
            val inRoster = hasAnyAncestor(hasTestTag("game-player-hands"))
            val containsSecret = hasText(secret, substring = true) or hasContentDescription(secret, substring = true)
            onAllNodes(inRoster and containsSecret, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun ComposeUiTest.capture(name: String) {
        val file = File("build/ui-snapshots/$name.png").apply { parentFile.mkdirs() }
        ImageIO.write(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().toAwtImage(), "png", file)
    }

    private fun peerView(state: LastLightState, recipient: PlayerId): GameView {
        val public = LastLightProjectionCodec.decodePublic(LastLightProjectionCodec.encodePublic(state))
        val own = LastLightProjectionPolicy.toPlayer(state, recipient).state.privatePerPlayer.getValue(recipient)
        val private = LastLightProjectionCodec.decodePrivate(LastLightProjectionCodec.encodePrivate(own))
        assertTrue(LastLightPeerSnapshotValidator.isValid(public, private, recipient))
        return LastLightProjectionPolicy.viewFor(public.copy(privatePerPlayer = mapOf(recipient to private)), recipient)
    }
}
