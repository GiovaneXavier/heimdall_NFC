package br.com.corp.heimdall.di

import br.com.corp.heimdall.data.repository.ValidationRepositoryImpl
import br.com.corp.heimdall.domain.repository.ValidationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindValidationRepository(
        impl: ValidationRepositoryImpl,
    ): ValidationRepository
}
