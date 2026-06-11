package app.fork.messenger.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the mute/archive/scope combinations required by the notification audit.
 * These are pure-logic tests — no TDLib, no Android — so they run on the JVM.
 *
 * "Archived" is not a separate flag in TDLib: an archived chat carries the same
 * notificationSettings as any other chat, so "archived + muted" is exercised by
 * the muted cases below (the archive location does not change the decision).
 */
class NotificationPolicyTest {

    // ---- isMuted: per-chat overrides scope default ----

    @Test fun chatExplicitlyMuted_isMuted() {
        // useDefaultMute=false, chat muteFor>0 => muted regardless of scope.
        assertTrue(NotificationPolicy.isMuted(useDefaultMute = false, chatMuteFor = 500, scopeMuteFor = 0))
    }

    @Test fun chatExplicitlyUnmuted_overridesMutedScope() {
        // Chat explicitly unmuted even though the whole scope is muted => not muted.
        assertFalse(NotificationPolicy.isMuted(useDefaultMute = false, chatMuteFor = 0, scopeMuteFor = 500))
    }

    @Test fun chatDefersToScope_mutedScope_isMuted() {
        // "Mute all groups" default applies to a chat using its default setting.
        assertTrue(NotificationPolicy.isMuted(useDefaultMute = true, chatMuteFor = 0, scopeMuteFor = 500))
    }

    @Test fun chatDefersToScope_unmutedScope_notMuted() {
        assertFalse(NotificationPolicy.isMuted(useDefaultMute = true, chatMuteFor = 0, scopeMuteFor = 0))
    }

    // ---- shouldNotify: full decision ----

    @Test fun mutedChat_noNotification() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = false, muted = true,
            ),
        )
    }

    @Test fun mutedGroupViaScopeDefault_noNotification() {
        val muted = NotificationPolicy.isMuted(useDefaultMute = true, chatMuteFor = 0, scopeMuteFor = 500)
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = false, muted = muted,
            ),
        )
    }

    @Test fun archivedMutedChat_noNotification() {
        // Archived chats use the same mute settings; muted => silent.
        val muted = NotificationPolicy.isMuted(useDefaultMute = false, chatMuteFor = 500, scopeMuteFor = 0)
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = false, muted = muted,
            ),
        )
    }

    @Test fun unmutedIncomingWhileNotViewing_notifies() {
        assertTrue(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = false, muted = false,
            ),
        )
    }

    @Test fun outgoingMessage_neverNotifies() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = true, isViewingChat = false, muted = false,
            ),
        )
    }

    @Test fun viewingChat_noNotification() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = true, muted = false,
            ),
        )
    }

    @Test fun globallyDisabled_noNotification() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                globalEnabled = false, isOutgoing = false, isViewingChat = false, muted = false,
            ),
        )
    }

    @Test fun perChatUnmutedInMutedScope_stillNotifies() {
        // The override must work end-to-end: chat unmuted, scope muted => notify.
        val muted = NotificationPolicy.isMuted(useDefaultMute = false, chatMuteFor = 0, scopeMuteFor = 500)
        assertTrue(
            NotificationPolicy.shouldNotify(
                globalEnabled = true, isOutgoing = false, isViewingChat = false, muted = muted,
            ),
        )
    }
}
