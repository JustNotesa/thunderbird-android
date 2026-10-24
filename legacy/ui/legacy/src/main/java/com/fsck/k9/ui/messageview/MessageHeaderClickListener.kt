package com.fsck.k9.ui.messageview

interface MessageHeaderClickListener {
    fun onParticipantsContainerClick()
    fun onMenuItemClick(itemId: Int)
    fun onViewAllAttachmentsClick()

    /** The label at the end of the subject was tapped, which brings the message back into view in the list. */
    fun onLocationLabelClick()
}
