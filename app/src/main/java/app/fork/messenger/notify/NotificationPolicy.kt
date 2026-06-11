package app.fork.messenger.notify

/**
 * Pure, side-effect-free decision logic for whether an incoming message should
 * raise a local notification. Extracted from [NotificationsCenter] so it can be
 * unit-tested for every mute/archive/scope combination without TDLib or Android.
 *
 * Correctness rules implemented here:
 *  - A muted chat never notifies (per-chat mute).
 *  - A chat using its default setting inherits the SCOPE default mute
 *    (e.g. "mute all groups") — this was the bug where default-muted chats
 *    still notified because the scope default was ignored.
 *  - Per-chat settings override the scope default in both directions
 *    (chat unmuted while scope muted => notify; chat muted while scope
 *    unmuted => silent).
 *  - Archived chats follow the same mute rules; an archived+muted chat is
 *    silent because it is muted.
 */
object NotificationPolicy {

    enum class Scope { PRIVATE, GROUP, CHANNEL }

    /**
     * Effective mute for a chat. [useDefaultMute] true means the chat defers to
     * the scope default; otherwise its own [chatMuteFor] applies. muteFor > 0
     * means muted (TDLib stores remaining mute seconds).
     */
    fun isMuted(useDefaultMute: Boolean, chatMuteFor: Int, scopeMuteFor: Int): Boolean =
        if (useDefaultMute) scopeMuteFor > 0 else chatMuteFor > 0

    /**
     * Final decision. Notify only when every condition holds:
     *  - notifications enabled globally,
     *  - the message is incoming (never notify for our own messages),
     *  - the chat is not currently open on screen,
     *  - the chat is not muted (per-chat or via scope default).
     */
    fun shouldNotify(
        globalEnabled: Boolean,
        isOutgoing: Boolean,
        isViewingChat: Boolean,
        muted: Boolean,
    ): Boolean = globalEnabled && !isOutgoing && !isViewingChat && !muted
}
