package com.tdater.blackbox.system

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads text and barcodes/QR codes out of a photo already saved in app storage. Uses ML Kit's bundled models, so it
 * runs entirely on the phone; the photo itself is never changed.
 */
object Vision {
    data class Code(val value: String, val format: String, val type: String)

    suspend fun readText(context: Context, file: File): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return client.process(image).await().text.trim()
        } finally {
            client.close()
        }
    }

    suspend fun scanCodes(context: Context, file: File): List<Code> {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val client = BarcodeScanning.getClient()
        try {
            return client.process(image).await().mapNotNull { b ->
                val v = b.rawValue ?: b.displayValue ?: return@mapNotNull null
                Code(v, formatName(b.format), typeName(b.valueType))
            }.distinctBy { it.value }
        } finally {
            client.close()
        }
    }

    fun describe(codes: List<Code>): String = codes.joinToString("\n") { "${it.value}\n(${it.format}${if (it.type.isNotEmpty()) ", ${it.type}" else ""})" }

    private fun formatName(f: Int) = when (f) {
        Barcode.FORMAT_QR_CODE -> "QR code"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "barcode"
    }

    private fun typeName(t: Int) = when (t) {
        Barcode.TYPE_URL -> "link"
        Barcode.TYPE_WIFI -> "Wi-Fi details"
        Barcode.TYPE_PRODUCT -> "product"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_CONTACT_INFO -> "contact"
        Barcode.TYPE_TEXT -> ""
        else -> ""
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
