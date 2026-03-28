package br.com.corp.heimdall.di

import br.com.corp.heimdall.data.repository.StubValidationRepository
import br.com.corp.heimdall.domain.repository.ValidationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Vincula [StubValidationRepository] como implementação de [ValidationRepository].
     * Substituir por [ValidationRepositoryImpl] no Sprint 4 (T-18).
     */
    @Binds
    @Singleton
    abstract fun bindValidationRepository(
        stub: StubValidationRepository,
    ): ValidationRepository
}
