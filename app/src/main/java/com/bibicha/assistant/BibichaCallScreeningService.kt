package com.bibicha.assistant

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.speech.tts.TextToSpeech
import java.util.Locale

class BibichaCallScreeningService : CallScreeningService() {
    private var speaker: TextToSpeech? = null
    private var ready = false

    override fun onCreate() {
        super.onCreate()
        speaker = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arabic = speaker?.setLanguage(Locale("ar", "DZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                val language = if (arabic < TextToSpeech.LANG_AVAILABLE) speaker?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED else arabic
                ready = language >= TextToSpeech.LANG_AVAILABLE
            }
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .build()
        respondToCall(callDetails, response)

        val incoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val number = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        if (incoming && number != null && ready) {
            speaker?.speak(
                "مكالمة واردة من الرقم $number",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "bibicha-incoming-call"
            )
        }
    }

    override fun onDestroy() {
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }
}
