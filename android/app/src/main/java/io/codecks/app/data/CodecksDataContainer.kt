package io.codecks.app.data

import android.content.Context
import io.codecks.data.decks.DeckDatabase
import io.codecks.data.decks.RoomDeckRepository
import io.codecks.data.receipts.ReceiptDatabase
import io.codecks.data.receipts.RoomActionReceiptRepository
import io.codecks.data.targets.AndroidKeystoreSshCredentialProvider
import io.codecks.domain.targets.MacTarget
import io.codecks.runtime.actions.RunFirstFinderProofUseCase
import io.codecks.transport.ssh.SshMacActionExecutor
import io.codecks.transport.ssh.SshjPublicKeyInstaller

class CodecksDataContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val deckDatabase = DeckDatabase.create(appContext)
    private val receiptDatabase = ReceiptDatabase.create(appContext)

    val deckRepository = RoomDeckRepository(deckDatabase.deckDao())
    val receiptRepository = RoomActionReceiptRepository(receiptDatabase.receiptDao())
    val sshCredentialProvider = AndroidKeystoreSshCredentialProvider()
    @Volatile
    var currentTarget: MacTarget? = null
    val publicKeyInstaller = SshjPublicKeyInstaller()
    val firstFinderProof = RunFirstFinderProofUseCase(
        executor = SshMacActionExecutor(sshCredentialProvider),
    )

    companion object {
        @Volatile
        private var instance: CodecksDataContainer? = null

        fun get(context: Context): CodecksDataContainer =
            instance ?: synchronized(this) {
                instance ?: CodecksDataContainer(context.applicationContext).also { created ->
                    instance = created
                }
            }
    }
}
