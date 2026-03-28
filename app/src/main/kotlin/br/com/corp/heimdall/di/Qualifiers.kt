package br.com.corp.heimdall.di

import javax.inject.Qualifier

/** Qualifica o systemId configurado para este ponto de acesso Heimdall. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConfiguredSystemId
