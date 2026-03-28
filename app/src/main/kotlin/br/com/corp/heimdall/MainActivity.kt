package br.com.corp.heimdall

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.rememberNavController
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.presentation.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity principal do Heimdall.
 *
 * Responsabilidades:
 * - Manter tela sempre ativa ([WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON])
 * - Ativar/desativar foreground dispatch NFC em onResume/onPause
 * - Despachar tags NFC detectadas para o [ReaderViewModel]
 * - Decidir rota inicial (setup vs reader) com base em [ConfigPreferences.isConfigured]
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var config: ConfigPreferences

    private val readerViewModel: ReaderViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcPendingIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE,
        )

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val startDest = if (config.isConfigured) Routes.READER else Routes.SETUP
                HeimdallNavGraph(navController = navController, startDestination = startDest)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(
            this,
            nfcPendingIntent,
            null,  // intercept all TECH_DISCOVERED intents
            arrayOf(arrayOf(IsoDep::class.java.name)),
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        readerViewModel.onAppBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            val isoDep = tag?.let { IsoDep.get(it) }
            if (isoDep != null) {
                readerViewModel.onNfcTagDetected(isoDep)
            }
        }
    }
}
