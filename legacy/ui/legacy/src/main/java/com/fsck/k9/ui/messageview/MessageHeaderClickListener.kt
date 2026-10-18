package com.fsck.k9.ui.messageview

interface MessageHeaderClickListener {
    fun onParticipantsContainerClick()
    fun onMenuItemClick(itemId: Int)
    fun onViewAllAttachmentsClick()

    /** The account label at the end of the subject was tapped, which opens the inbox of that account. */
    fun onAccountLabelClick()
}
