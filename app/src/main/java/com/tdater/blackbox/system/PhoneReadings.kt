package com.tdater.blackbox.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.tdater.blackbox.core.model.EnvField
import com.tdater.blackbox.core.model.EnvValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Reads what the phone itself can tell you right now, for a conditions snapshot. Each group is opt-in;
 * anything the phone can't or won't report is recorded as Unknown with the reason, never left blank.
 * Nothing is sent anywhere: this only fills in fields the user then reviews and saves.
 */
object PhoneReadings {
    enum class Group(val label: String, val detail: String, val permissions: List<String>) {
        DEVICE("Phone & Android version", "Model, Android version, security patch", emptyList()),
        BATTERY("Battery", "Charge, charging state, temperature, voltage", emptyList()),
        NETWORK("Network", "Connection type, Wi‑Fi signal and speed", emptyList()),
        STORAGE("Storage", "Free space on the phone", emptyList()),
        SENSORS("Room sensors", "Light, air pressure, temperature, humidity (if the phone has them)", emptyList()),
        LOCATION("Location", "GPS position and accuracy", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        BLUETOOTH("Bluetooth", "On/off and paired devices",
            if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()),
        NOISE("Noise level", "2-second sound level from the mic (relative, not calibrated)", listOf(Manifest.permission.RECORD_AUDIO)),
    }

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun known(k: String, v: String, unit: String? = null) = EnvField(k, EnvValue.Known(v, unit))
    private fun unknown(k: String, why: String) = EnvField(k, EnvValue.Unknown(why))
    private fun num(v: Double, decimals: Int = 1) = "%.${decimals}f".format(Locale.ROOT, v)

    suspend fun read(ctx: Context, groups: Set<Group>): List<EnvField> = withContext(Dispatchers.Default) {
        val out = mutableListOf<EnvField>()
        for (g in Group.entries.filter { it in groups }) {
            out += try {
                when (g) {
                    Group.DEVICE -> device()
                    Group.BATTERY -> battery(ctx)
                    Group.NETWORK -> network(ctx)
                    Group.STORAGE -> storage(ctx)
                    Group.SENSORS -> sensors(ctx)
                    Group.LOCATION -> location(ctx)
                    Group.BLUETOOTH -> bluetooth(ctx)
                    Group.NOISE -> noise(ctx)
                }
            } catch (e: Exception) {
                listOf(unknown(g.label, "could not read: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
        out
    }

    private fun device() = listOf(
        known("Phone", "${Build.MANUFACTURER} ${Build.MODEL}"),
        known("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"),
        known("Security patch", Build.VERSION.SECURITY_PATCH),
    )

    private fun battery(ctx: Context): List<EnvField> {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return listOf(unknown("Battery", "not reported"))
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        return listOf(
            if (level >= 0 && scale > 0) known("Phone battery", num(level * 100.0 / scale, 0), "%") else unknown("Phone battery", "not reported"),
            known("Charging", when {
                status == BatteryManager.BATTERY_STATUS_FULL -> "full"
                plugged == BatteryManager.BATTERY_PLUGGED_AC -> "yes (wall charger)"
                plugged == BatteryManager.BATTERY_PLUGGED_USB -> "yes (USB)"
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "yes (wireless)"
                else -> "no"
            }),
            if (temp != Int.MIN_VALUE) known("Battery temperature", num(temp / 10.0), "°C") else unknown("Battery temperature", "not reported"),
            if (mv > 0) known("Battery voltage", num(mv / 1000.0, 2), "V") else unknown("Battery voltage", "not reported"),
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun network(ctx: Context): List<EnvField> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tether"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "other"
        }
        val out = mutableListOf(known("Connection", type))
        if (caps != null) out += known("Internet reachable (per Android)", if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "yes" else "no")
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val info = ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
            if (info != null && info.rssi > -127) {
                out += known("Wi‑Fi signal", info.rssi.toString(), "dBm")
                if (info.linkSpeed > 0) out += known("Wi‑Fi link speed", info.linkSpeed.toString(), "Mbps")
                if (info.frequency > 0) out += known("Wi‑Fi band", if (info.frequency >= 5925) "6 GHz" else if (info.frequency >= 4900) "5 GHz" else "2.4 GHz")
            } else out += unknown("Wi‑Fi signal", "not reported")
            val ssid = info?.ssid?.trim('"')
            out += if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") unknown("Wi‑Fi name", "Android hides it without location permission")
                else known("Wi‑Fi name", ssid)
        }
        return out
    }

    private fun storage(ctx: Context): List<EnvField> {
        val s = StatFs(ctx.filesDir.path)
        return listOf(known("Free storage", num(s.availableBytes / 1e9), "GB"), known("Total storage", num(s.totalBytes / 1e9, 0), "GB"))
    }

    private suspend fun sensors(ctx: Context): List<EnvField> {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val wanted = listOf(
            Triple(Sensor.TYPE_LIGHT, "Light level", "lx"),
            Triple(Sensor.TYPE_PRESSURE, "Air pressure", "hPa"),
            Triple(Sensor.TYPE_AMBIENT_TEMPERATURE, "Room temperature", "°C"),
            Triple(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity", "%"),
        )
        return wanted.map { (type, name, unit) ->
            val sensor = sm.getDefaultSensor(type) ?: return@map unknown(name, "this phone has no such sensor")
            val v = withTimeoutOrNull(2_500) {
                suspendCancellableCoroutine<Float> { cont ->
                    val l = object : SensorEventListener {
                        override fun onSensorChanged(e: SensorEvent) {
                            sm.unregisterListener(this)
                            if (cont.isActive) cont.resume(e.values[0])
                        }
                        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                    }
                    sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    cont.invokeOnCancellation { sm.unregisterListener(l) }
                }
            }
            if (v == null) unknown(name, "sensor gave no reading") else known(name, num(v.toDouble()), unit)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(ctx: Context): List<EnvField> {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION))
            return listOf(unknown("Location", "location permission not granted"))
        val lm = ctx.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return listOf(unknown("Location", "location is turned off"))
        var loc: Location? = null
        if (Build.VERSION.SDK_INT >= 30) {
            loc = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(providers.first(), cancel, ContextCompat.getMainExecutor(ctx)) { l -> if (cont.isActive) cont.resume(l) }
                }
            }
        }
        if (loc == null) loc = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (loc == null) return listOf(unknown("Location", "no fix yet; try again outside"))
        val age = (System.currentTimeMillis() - loc.time) / 60_000
        return listOf(
            known("Location", "${num(loc.latitude, 5)}, ${num(loc.longitude, 5)}"),
            known("Location accuracy", num(loc.accuracy.toDouble(), 0), "m"),
        ) + if (age > 2) listOf(known("Location age", age.toString(), "min")) else emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun bluetooth(ctx: Context): List<EnvField> {
        if (Build.VERSION.SDK_INT >= 31 && !granted(ctx, Manifest.permission.BLUETOOTH_CONNECT))
            return listOf(unknown("Bluetooth", "nearby-devices permission not granted"))
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return listOf(unknown("Bluetooth", "no Bluetooth on this phone"))
        if (!adapter.isEnabled) return listOf(known("Bluetooth", "off"))
        val paired = adapter.bondedDevices.orEmpty().mapNotNull { it.name }.sorted()
        return listOf(known("Bluetooth", "on"), known("Bluetooth paired devices", paired.joinToString().ifEmpty { "none" }))
    }

    @SuppressLint("MissingPermission")
    private suspend fun noise(ctx: Context): List<EnvField> = withContext(Dispatchers.IO) {
        if (!granted(ctx, Manifest.permission.RECORD_AUDIO)) return@withContext listOf(unknown("Noise level", "microphone permission not granted"))
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@withContext listOf(unknown("Noise level", "microphone not available"))
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) return@withContext listOf(unknown("Noise level", "microphone busy"))
            rec.startRecording()
            val buf = ShortArray(min)
            var sum = 0.0
            var n = 0L
            val end = System.currentTimeMillis() + 2_000
            while (System.currentTimeMillis() < end) {
                val r = rec.read(buf, 0, buf.size)
                if (r <= 0) break
                for (i in 0 until r) { val s = buf[i] / 32768.0; sum += s * s }
                n += r
            }
            if (n == 0L) return@withContext listOf(unknown("Noise level", "no audio captured"))
            val rms = sqrt(sum / n).coerceAtLeast(1e-6)
            listOf(known("Noise level (relative)", num(20 * log10(rms), 0), "dBFS"))
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }
}
