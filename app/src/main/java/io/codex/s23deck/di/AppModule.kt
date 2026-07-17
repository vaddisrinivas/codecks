package io.codex.s23deck.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.codex.s23deck.data.ActionRepository
import io.codex.s23deck.data.ai.AiArtifactRepository
import io.codex.s23deck.data.ai.DefaultAiArtifactRepository
import io.codex.s23deck.data.automation.AutomationRepository
import io.codex.s23deck.data.automation.DefaultAutomationTriggerEngine
import io.codex.s23deck.data.automation.DefaultAutomationRepository
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.data.DefaultActionRepository
import io.codex.s23deck.data.DefaultConnectionRepository
import io.codex.s23deck.data.LanSshDiscovery
import io.codex.s23deck.data.SshDiscovery
import io.codex.s23deck.data.device.DefaultTransportRegistry
import io.codex.s23deck.data.device.LocalDeviceRepository
import io.codex.s23deck.DefaultHidRepository
import io.codex.s23deck.HidRepository
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.actions.DefaultActionRunner
import io.codex.s23deck.domain.device.DeviceRepository
import io.codex.s23deck.domain.device.TransportRegistry
import io.codex.s23deck.domain.automation.AutomationTriggerEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindActionRepository(implementation: DefaultActionRepository): ActionRepository

    @Binds
    @Singleton
    abstract fun bindActionRunner(implementation: DefaultActionRunner): ActionRunner

    @Binds
    @Singleton
    abstract fun bindAiArtifactRepository(implementation: DefaultAiArtifactRepository): AiArtifactRepository

    @Binds
    @Singleton
    abstract fun bindAutomationRepository(implementation: DefaultAutomationRepository): AutomationRepository

    @Binds
    @Singleton
    abstract fun bindAutomationTriggerEngine(implementation: DefaultAutomationTriggerEngine): AutomationTriggerEngine

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        implementation: DefaultConnectionRepository,
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(implementation: LocalDeviceRepository): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindTransportRegistry(implementation: DefaultTransportRegistry): TransportRegistry

    @Binds
    @Singleton
    abstract fun bindSshDiscovery(implementation: LanSshDiscovery): SshDiscovery

    @Binds
    @Singleton
    abstract fun bindHidRepository(implementation: DefaultHidRepository): HidRepository
}
