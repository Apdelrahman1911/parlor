package com.parlor.games.whodunit.authority

import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.authority.AuthorityScope
import com.parlor.games.whodunit.domain.authority.WhodunitActionAuthority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Authority matrix locks the rule that:
 *
 *  - Host-only actions (shared game progression) are rejected from any peer.
 *  - Self-actor actions (Cast/Abstain/Refuse vote, Complete reveal,
 *    Open/Close private review) are rejected
 *    when submitted by anyone other than the named actor.
 *  - Host can always submit anything (host-trusted path bypasses policy in
 *    practice, but this test pins the behaviour: `isAllowed` returns true
 *    when sender == host on host-only actions, and true when sender ==
 *    actor on self-actor actions).
 */
class WhodunitActionAuthorityTest {

    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    // ============================================================ Host-only ==

    @Test
    fun host_only_actions_reject_peers() {
        val hostOnly = listOf<WhodunitAction>(
            WhodunitAction.AssignRoles(seed = 42L),
            WhodunitAction.AdvanceFromIntro,
            WhodunitAction.AdvanceBriefingCard(1),
            WhodunitAction.RevealNextClue,
            WhodunitAction.StartDiscussionTimer(60),
            WhodunitAction.PauseDiscussionTimer,
            WhodunitAction.ResumeDiscussionTimer,
            WhodunitAction.TimerTicked(30),
            WhodunitAction.TimerExpired,
            WhodunitAction.AdvanceFromDiscussion,
            WhodunitAction.OpenVote,
            WhodunitAction.CloseVote,
            WhodunitAction.AcknowledgeRevealCard,
            WhodunitAction.AcknowledgeReveal,
            WhodunitAction.BeginReplay,
            WhodunitAction.Pause,
            WhodunitAction.Resume,
            WhodunitAction.EndGameEarly(withReveal = true),
            WhodunitAction.RequestReroll,
            WhodunitAction.MarkPlayerDisconnected(alice),
            WhodunitAction.MarkPlayerReconnected(alice),
            WhodunitAction.ContinueWithoutPlayer(alice),
        )
        for (action in hostOnly) {
            assertEquals(AuthorityScope.HostOnly, WhodunitActionAuthority.classify(action),
                "expected HostOnly for $action")
            assertTrue(WhodunitActionAuthority.isAllowed(action, host, host),
                "host must be allowed to submit $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, alice, host),
                "peer alice must NOT be allowed to submit $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "peer bob must NOT be allowed to submit $action")
        }
    }

    // ============================================================ Self-actor ==

    @Test
    fun self_actor_reveal_lifecycle_only_named_player() {
        val actions = listOf<WhodunitAction>(
            WhodunitAction.StartCharacterReveal(alice, 1L),
            WhodunitAction.CompleteCharacterReveal(alice, 1L),
            WhodunitAction.OpenPrivateReview(alice, 1L),
            WhodunitAction.CloseHide(alice, 1L),
        )
        for (action in actions) {
            assertEquals(AuthorityScope.SelfActor(alice), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, alice, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot submit alice's $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, host, host),
                "even the host cannot submit a SelfActor action with a different actor")
        }
    }

    @Test
    fun self_actor_party_readiness_acks_only_named_player() {
        val actions = listOf<WhodunitAction>(
            WhodunitAction.AcknowledgeIntro(alice),
            WhodunitAction.AcknowledgeBriefing(alice),
            WhodunitAction.ConfirmRoleViewed(alice, 1L),
        )
        for (action in actions) {
            assertEquals(AuthorityScope.SelfActor(alice), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, alice, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot acknowledge for alice")
            assertFalse(WhodunitActionAuthority.isAllowed(action, host, host),
                "host cannot ack on alice's behalf")
        }
    }

    @Test
    fun self_actor_voting_only_named_voter() {
        val actions = listOf<WhodunitAction>(
            WhodunitAction.CastVote(voter = alice, target = bob),
            WhodunitAction.AbstainVote(alice),
            WhodunitAction.RefuseToVote(alice),
        )
        for (action in actions) {
            assertEquals(AuthorityScope.SelfActor(alice), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, alice, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot vote/abstain/refuse as alice")
        }
    }

    // ============================================================ Dropped spectators ==

    @Test
    fun dropped_player_self_actor_actions_are_rejected() {
        val dropped = setOf(alice)
        val actions = listOf<WhodunitAction>(
            WhodunitAction.AcknowledgeIntro(alice),
            WhodunitAction.AcknowledgeBriefing(alice),
            WhodunitAction.ConfirmRoleViewed(alice, 1L),
            WhodunitAction.StartCharacterReveal(alice, 1L),
            WhodunitAction.CompleteCharacterReveal(alice, 1L),
            WhodunitAction.OpenPrivateReview(alice, 1L),
            WhodunitAction.CloseHide(alice, 1L),
            WhodunitAction.CastVote(voter = alice, target = bob),
            WhodunitAction.AbstainVote(alice),
            WhodunitAction.RefuseToVote(alice),
        )
        for (action in actions) {
            // Sender == actor is normally allowed, but the dropped gate rejects.
            assertFalse(
                WhodunitActionAuthority.isAllowed(action, alice, host, dropped),
                "dropped alice cannot submit $action",
            )
        }
    }

    @Test
    fun dropped_player_does_not_block_other_actors() {
        val dropped = setOf(alice)
        // Bob is not dropped — his actions remain valid.
        assertTrue(
            WhodunitActionAuthority.isAllowed(
                WhodunitAction.CastVote(voter = bob, target = alice),
                bob,
                host,
                dropped,
            ),
        )
    }

    @Test
    fun host_only_actions_pass_through_dropped_set_unaffected() {
        // Dropped set should not change host-only enforcement: host can submit
        // Grace-expiry is host-only even if stale state already contains a
        // legacy dropped-player entry; reducer phase/state guards decide it.
        val dropped = setOf(alice)
        assertTrue(
            WhodunitActionAuthority.isAllowed(
                WhodunitAction.ContinueWithoutPlayer(alice),
                host,
                host,
                dropped,
            ),
        )
    }

}
