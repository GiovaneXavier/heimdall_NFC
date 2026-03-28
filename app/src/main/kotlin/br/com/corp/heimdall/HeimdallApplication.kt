package br.com.corp.heimdall

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Ponto de entrada da aplicação Heimdall. Habilita injeção de dependência via Hilt. */
@HiltAndroidApp
class HeimdallApplication : Application()
