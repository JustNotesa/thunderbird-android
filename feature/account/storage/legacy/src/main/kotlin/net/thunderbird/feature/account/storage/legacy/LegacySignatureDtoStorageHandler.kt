package net.thunderbird.feature.account.storage.legacy

import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.preference.storage.Storage
import net.thunderbird.core.preference.storage.StorageEditor

/**
 * Stores the saved signatures of an account as an indexed list, the same way identities are stored.
 */
class LegacySignatureDtoStorageHandler : SignatureDtoStorageHandler {

    override fun load(
        data: LegacyAccountDto,
        storage: Storage,
    ) {
        val keyGen = AccountKeyGenerator(data.id)
        val signatures = mutableListOf<AccountSignature>()

        var index = 0
        while (true) {
            val id = storage.getStringOrNull(keyGen.create("$KEY_ID.$index")) ?: break
            signatures.add(
                AccountSignature(
                    id = id,
                    name = storage.getStringOrDefault(keyGen.create("$KEY_NAME.$index"), ""),
                    text = storage.getStringOrDefault(keyGen.create("$KEY_TEXT.$index"), ""),
                    isHtml = storage.getBoolean(keyGen.create("$KEY_IS_HTML.$index"), false),
                ),
            )
            index++
        }

        data.signatures = signatures
    }

    override fun save(
        data: LegacyAccountDto,
        storage: Storage,
        editor: StorageEditor,
    ) {
        delete(data, storage, editor)

        val keyGen = AccountKeyGenerator(data.id)
        data.signatures.forEachIndexed { index, signature ->
            editor.putString(keyGen.create("$KEY_ID.$index"), signature.id)
            editor.putString(keyGen.create("$KEY_NAME.$index"), signature.name)
            editor.putString(keyGen.create("$KEY_TEXT.$index"), signature.text)
            editor.putBoolean(keyGen.create("$KEY_IS_HTML.$index"), signature.isHtml)
        }
    }

    override fun delete(
        data: LegacyAccountDto,
        storage: Storage,
        editor: StorageEditor,
    ) {
        val keyGen = AccountKeyGenerator(data.id)

        var index = 0
        while (storage.getStringOrNull(keyGen.create("$KEY_ID.$index")) != null) {
            editor.remove(keyGen.create("$KEY_ID.$index"))
            editor.remove(keyGen.create("$KEY_NAME.$index"))
            editor.remove(keyGen.create("$KEY_TEXT.$index"))
            editor.remove(keyGen.create("$KEY_IS_HTML.$index"))
            index++
        }
    }

    private companion object {
        const val KEY_ID = "savedSignatureId"
        const val KEY_NAME = "savedSignatureName"
        const val KEY_TEXT = "savedSignatureText"
        const val KEY_IS_HTML = "savedSignatureIsHtml"
    }
}
