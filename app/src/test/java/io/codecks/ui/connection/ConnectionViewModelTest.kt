package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.SshDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun discoverySelectsSingleMacAndAuthorizationDiscardsPassword() = runTest(dispatcher) {
        val repository = FakeConnectionRepository()
        var scanCount = 0
        val viewModel = ConnectionViewModel(repository, SshDiscovery {
            scanCount += 1
            listOf("10.0.0.173")
        })

        runCurrent()
        assertEquals("", viewModel.uiState.value.host)

        viewModel.scan()
        runCurrent()
        assertEquals("10.0.0.173", viewModel.uiState.value.host)
        assertEquals(1, scanCount)

        viewModel.setUser("mac-user")
        viewModel.verifyHostKey()
        runCurrent()
        assertTrue(repository.trustCalled)
        assertEquals("Fingerprint found: ssh-ed25519 SHA256:test", viewModel.uiState.value.pendingFingerprint)

        viewModel.confirmHostKey()
        runCurrent()
        assertTrue(repository.confirmCalled)

        viewModel.setPassword("one-time-secret")
        viewModel.authorize()
        runCurrent()

        assertEquals("", viewModel.uiState.value.password)
        assertTrue(repository.installCalled)
        assertTrue(repository.testCalled)
        assertEquals("mac-user", repository.config.value.user)
    }

    @Test
    fun readyConfigurationDoesNotAutoScanOnStartup() = runTest(dispatcher) {
        val repository = FakeConnectionRepository(
            initialConfig = ConnectionConfig(
                host = "10.0.0.173",
                port = 22,
                user = "mac-user",
                hasKey = true,
                hostKey = "10.0.0.173 ssh-ed25519 key",
            ),
        )
        var scanCount = 0

        val viewModel = ConnectionViewModel(
            repository,
            SshDiscovery {
                scanCount += 1
                listOf("10.0.0.200")
            },
        )
        runCurrent()

        assertEquals(0, scanCount)
        assertEquals(ConnectionOperation.Idle, viewModel.uiState.value.operation)
        assertEquals("10.0.0.173", viewModel.uiState.value.host)
    }

    @Test
    fun savedPasswordMustMatchCurrentMacProfile() = runTest(dispatcher) {
        val viewModel = ConnectionViewModel(FakeConnectionRepository(), SshDiscovery { emptyList() })
        runCurrent()

        viewModel.setHost("mac.local")
        viewModel.setPort("2222")
        viewModel.setUser("testuser")

        viewModel.applyPasswordCredential("other@mac.local:2222", "wrong-secret")

        assertEquals("", viewModel.uiState.value.password)
        assertEquals("Selected password does not match this Mac profile", viewModel.uiState.value.error)

        viewModel.applyPasswordCredential("testuser@mac.local:2222", "right-secret")

        assertEquals("right-secret", viewModel.uiState.value.password)
        assertEquals("Password filled from password manager", viewModel.uiState.value.message)
    }

    @Test
    fun resetTrustClearsFingerprintAndRequiresReverify() = runTest(dispatcher) {
        val repository = FakeConnectionRepository(
            initialConfig = ConnectionConfig(
                host = "mac.local",
                port = 22,
                user = "testuser",
                hasKey = true,
                hostKey = "mac.local ssh-ed25519 key",
            ),
        )
        val viewModel = ConnectionViewModel(repository, SshDiscovery { emptyList() })
        runCurrent()

        viewModel.resetTrust()
        runCurrent()

        assertTrue(repository.resetTrustCalled)
        assertEquals("", repository.config.value.hostKey)
        assertEquals("Mac fingerprint reset. Verify the fingerprint again before running commands.", viewModel.uiState.value.message)
    }

    @Test
    fun removeCurrentTargetClearsConnectionForm() = runTest(dispatcher) {
        val repository = FakeConnectionRepository(
            initialConfig = ConnectionConfig(
                host = "mac.local",
                port = 2222,
                user = "testuser",
                hasKey = true,
                hostKey = "mac.local ssh-ed25519 key",
            ),
        )
        val viewModel = ConnectionViewModel(repository, SshDiscovery { emptyList() })
        runCurrent()

        viewModel.removeCurrentTarget()
        runCurrent()

        assertTrue(repository.removeTargetCalled)
        assertEquals("", viewModel.uiState.value.host)
        assertEquals("22", viewModel.uiState.value.port)
        assertEquals("", viewModel.uiState.value.user)
        assertEquals("Removed mac.local", viewModel.uiState.value.message)
    }
}

private class FakeConnectionRepository(
    initialConfig: ConnectionConfig = ConnectionConfig(),
) : ConnectionRepository {
    override val config = MutableStateFlow(initialConfig)
    var installCalled = false
    var testCalled = false
    var trustCalled = false
    var confirmCalled = false
    var resetTrustCalled = false
    var removeTargetCalled = false

    override suspend fun save(host: String, port: Int, user: String) {
        val previous = config.value
        val hostKey = if (previous.host == host && previous.port == port && previous.user == user) {
            previous.hostKey
        } else {
            ""
        }
        config.value = ConnectionConfig(host, port, user, hasKey = previous.hasKey, hostKey = hostKey)
    }

    override suspend fun generateKey(): Result<String> = Result.success("public-key")
    override suspend fun publicKey(): String = "public-key"

    override suspend fun trustHostKey(): Result<String> {
        trustCalled = true
        return Result.success("Fingerprint found: ssh-ed25519 SHA256:test")
    }

    override suspend fun confirmPendingHostKey(): Result<String> {
        confirmCalled = true
        config.value = config.value.copy(hostKey = "mac ssh-ed25519 key")
        return Result.success("Fingerprint trusted: ssh-ed25519 SHA256:test")
    }

    override suspend fun rotateKey(): Result<String> = Result.success("rotated")

    override suspend fun resetTrust(): Result<String> {
        resetTrustCalled = true
        config.value = config.value.copy(hostKey = "")
        return Result.success("Mac fingerprint reset. Verify the fingerprint again before running commands.")
    }

    override suspend fun removeTarget(targetId: String): Result<String> {
        removeTargetCalled = true
        val host = config.value.host
        config.value = ConnectionConfig()
        return Result.success("Removed $host")
    }

    override suspend fun installKey(password: String): Result<String> {
        installCalled = true
        config.value = config.value.copy(hasKey = true, hostKey = "mac ssh-ed25519 key")
        return Result.success("installed")
    }

    override suspend fun test(password: String?): Result<String> {
        testCalled = true
        return Result.success("Connected")
    }

    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> =
        Result.success("sent")
}
