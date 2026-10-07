package com.fsck.k9.mail.store.imap

private const val RESOURCE_STORAGE = "STORAGE"
private const val RESOURCE_ENTRY_SIZE = 3
private const val STORAGE_UNIT_IN_BYTES = 1024L

/**
 * Parses the `QUOTA` responses of the IMAP QUOTA extension (RFC 9208), e.g. `* QUOTA "" (STORAGE 10 512)`.
 */
internal object QuotaResponse {
    /**
     * @return The storage quota of the first quota root that reports one, or `null` if there is none.
     */
    fun parse(responses: List<ImapResponse>): ImapStorageQuota? {
        return responses.firstNotNullOfOrNull { response -> parseSingleLine(response) }
    }

    private fun parseSingleLine(response: ImapResponse): ImapStorageQuota? {
        val isQuotaResponse = !response.isTagged &&
            response.size >= RESOURCE_ENTRY_SIZE &&
            ImapResponseParser.equalsIgnoreCase(response[0], Responses.QUOTA)
        if (!isQuotaResponse || !response.isList(2)) return null

        // The list consists of entries with the name of a resource, its usage and its limit.
        val resources = response.getList(2)
        return (0..resources.size - RESOURCE_ENTRY_SIZE step RESOURCE_ENTRY_SIZE)
            .firstOrNull { index ->
                ImapResponseParser.equalsIgnoreCase(resources[index], RESOURCE_STORAGE) &&
                    resources.isLong(index + 1) &&
                    resources.isLong(index + 2)
            }
            ?.let { index ->
                ImapStorageQuota(
                    usedBytes = resources.getLong(index + 1) * STORAGE_UNIT_IN_BYTES,
                    limitBytes = resources.getLong(index + 2) * STORAGE_UNIT_IN_BYTES,
                )
            }
    }
}
