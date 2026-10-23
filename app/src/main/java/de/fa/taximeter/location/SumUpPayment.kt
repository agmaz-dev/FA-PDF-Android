package de.fa.taximeter.location

import android.content.Intent
import android.net.Uri
import de.fa.taximeter.domain.trip.MeterMachine
import java.math.RoundingMode

/**
 * Anbindung an die SumUp-App über den „Payment Switch“ (sumupmerchant://pay/1.0).
 * Voraussetzungen: SumUp-App installiert und eingeloggt, Affiliate Key für die App-ID dieser App.
 * Quelle: https://developer.sumup.com/terminal-payments/payment-switch/android/
 */
object SumUpPayment {

    const val CALLBACK_SCHEME = "fataximeter"
    const val CALLBACK_HOST = "sumup-callback"
    private const val CALLBACK = "$CALLBACK_SCHEME://$CALLBACK_HOST"

    fun buildIntent(req: MeterMachine.CardRequest, affiliateKey: String, appId: String): Intent {
        val amount = req.amount.setScale(2, RoundingMode.HALF_UP).toPlainString() // Punkt als Dezimaltrenner
        val uri = Uri.parse("sumupmerchant://pay/1.0").buildUpon()
            .appendQueryParameter("affiliate-key", affiliateKey.trim())
            .appendQueryParameter("app-id", appId)
            .appendQueryParameter("total", amount)
            .appendQueryParameter("amount", amount) // ältere SumUp-App-Versionen
            .appendQueryParameter("currency", "EUR")
            .appendQueryParameter("title", req.title)
            .appendQueryParameter("foreign-tx-id", req.paymentId)
            .appendQueryParameter("callback", CALLBACK)
            .appendQueryParameter("callbacksuccess", CALLBACK) // ältere SumUp-App-Versionen
            .appendQueryParameter("callbackfail", CALLBACK)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    data class Result(val success: Boolean, val paymentId: String?, val txCode: String?, val message: String?)

    /** Liest die Rückmeldung; null, wenn der Intent nicht von SumUp stammt. */
    fun parse(intent: Intent?): Result? {
        val data = intent?.data ?: return null
        if (data.scheme != CALLBACK_SCHEME || data.host != CALLBACK_HOST) return null
        val status = data.getQueryParameter("smp-status")
        return Result(
            success = status.equals("success", ignoreCase = true),
            paymentId = data.getQueryParameter("foreign-tx-id"),
            txCode = data.getQueryParameter("smp-tx-code"),
            message = data.getQueryParameter("smp-message"),
        )
    }
}
