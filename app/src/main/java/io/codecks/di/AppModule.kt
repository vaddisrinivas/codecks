package io.codecks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.codecks.data.ActionRepository
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.DefaultAiArtifactRepository
import io.codecks.data.automation.AutomationRepository
import io.codecks.data.automation.DefaultAutomationTriggerEngine
import io.codecks.data.automation.DefaultAutomationRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.DefaultActionRepository
import io.codecks.data.DefaultConnectionRepository
import io.codecks.data.DefaultRunHistoryRepository
import io.codecks.data.MdnsFirstSshDiscovery
import io.codecks.data.RunHistoryRepository
import io.codecks.data.SshDiscovery
import io.codecks.data.device.DefaultTransportRegistry
import io.codecks.data.device.LocalDeviceRepository
import io.codecks.DefaultHidRepository
import io.codecks.HidRepository
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.DefaultActionRunner
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TransportRegistry
import io.codecks.domain.automation.AutomationTriggerEngine
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
    abstract fun bindSshDiscovery(implementation: MdnsFirstSshDiscovery): SshDiscovery

    @Binds
    @Singleton
    abstract fun bindRunHistoryRepository(implementation: DefaultRunHistoryRepository): RunHistoryRepository

    @Binds
    @Singleton
    abstract fun bindHidRepository(implementation: DefaultHidRepository): HidRepository
}
