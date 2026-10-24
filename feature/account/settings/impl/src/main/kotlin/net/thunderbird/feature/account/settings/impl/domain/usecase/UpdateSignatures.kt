package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountSettingError
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateSignaturesCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.impl.domain.AccountSignatures
import net.thunderbird.feature.account.settings.impl.domain.defaultSignature
import net.thunderbird.feature.account.settings.impl.domain.toAccountSignatures
import net.thunderbird.feature.account.settings.impl.domain.withDefaultSignature

internal class UpdateSignatures(
    private val repository: LegacyAccountRepository,
) : UseCase.UpdateSignatures {
    override suspend fun invoke(
        accountId: AccountId,
        command: UpdateSignaturesCommand,
    ): Outcome<AccountSignatures, AccountSettingError> {
        val account = repository.observeById(accountId).firstOrNull()
            ?: return Outcome.failure(AccountSettingError.NotFound("Account not found"))

        val updatedAccount = when (command) {
            is UpdateSignaturesCommand.Save -> account.save(command.signature)
            is UpdateSignaturesCommand.Delete -> account.delete(command.signatureId)
            is UpdateSignaturesCommand.SetDefault -> account.setDefault(command.signatureId)
        } ?: return Outcome.failure(AccountSettingError.NotFound("Signature not found"))

        repository.update(accountId) { updatedAccount }

        return Outcome.success(updatedAccount.toAccountSignatures())
    }

    private fun LegacyAccount.save(signature: AccountSignature): LegacyAccount {
        val isDefault = defaultSignature()?.id == signature.id
        val updatedSignatures = if (signatures.any { it.id == signature.id }) {
            signatures.map { if (it.id == signature.id) signature else it }
        } else {
            signatures + signature
        }

        return copy(signatures = updatedSignatures).let { account ->
            if (isDefault) account.withDefaultSignature(signature) else account
        }
    }

    private fun LegacyAccount.delete(signatureId: String): LegacyAccount {
        val isDefault = defaultSignature()?.id == signatureId

        return copy(
            signatures = signatures.filterNot { it.id == signatureId },
            signatureUse = signatureUse && !isDefault,
        )
    }

    private fun LegacyAccount.setDefault(signatureId: String?): LegacyAccount? {
        if (signatureId == null) return copy(signatureUse = false)

        return signatures.find { it.id == signatureId }?.let { withDefaultSignature(it) }
    }
}
