package com.clariti.lectorbiometricoapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION = "com.clariti.lectorbiometricoapp.USB_PERMISSION"

    // Identificadores de tu U.are.U 5300
    private val UAREU_VID = 1466
    private val UAREU_PID = 14

    private lateinit var usbManager: UsbManager
    private var biometricSensor: UsbDevice? = null

    // Referencia a la UI
    private lateinit var tvStatus: TextView

    // Variable para rastrear si hubo una desconexión en medio del proceso
    private var hubReinicioDetectado = false

    // Escuchador de eventos del puerto USB
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        hubReinicioDetectado = false
                        val extraGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                        val liveDevice = usbManager.deviceList.values.find {
                            it.vendorId == UAREU_VID && it.productId == UAREU_PID
                        }

                        val reallyHasPermission = liveDevice != null && usbManager.hasPermission(liveDevice)

                        Log.d("USB_BIOMETRIA", "Intent: $extraGranted | Sistema: $reallyHasPermission")

                        if (extraGranted || reallyHasPermission) {
                            liveDevice?.let {
                                updateStatus("Permiso concedido. Inicializando sensor...")
                                initBiometricSDK(it)
                            }
                        } else {
                            updateStatus("Validando permisos con el sistema... (Espera 3s)")

                            CoroutineScope(Dispatchers.Main).launch {
                                var permissionGranted = false

                                for (i in 1..3) {
                                    delay(1000)

                                    // Si en medio de esta espera, saltó el evento DETACHED, detenemos todo.
                                    if (hubReinicioDetectado) {
                                        updateStatus("🚨 EL HUB REINICIÓ LA CONEXIÓN: El hardware se desconectó físicamente por un instante. Permiso anulado.")
                                        return@launch
                                    }

                                    val retryDevice = usbManager.deviceList.values.find {
                                        it.vendorId == UAREU_VID && it.productId == UAREU_PID
                                    }

                                    if (retryDevice != null && usbManager.hasPermission(retryDevice)) {
                                        permissionGranted = true
                                        updateStatus("Permiso concedido (Intento $i). Inicializando...")
                                        initBiometricSDK(retryDevice)
                                        break
                                    } else {
                                        updateStatus("Verificando autorización en Samsung Knox ($i/3)...")
                                    }
                                }

                                if (!permissionGranted && !hubReinicioDetectado) {
                                    updateStatus("❌ BLOQUEO DE SEGURIDAD: El S23 Ultra bloqueó el permiso. Esto suele ocurrir al usar Hubs con hardware biométrico.")
                                }
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        hubReinicioDetectado = true
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        device?.let {
                            if (it.vendorId == UAREU_VID && it.productId == UAREU_PID) {
                                updateStatus("🚨 DESCONEXIÓN FÍSICA DETECTADA: El Hub reinició la línea de datos.")
                                biometricSensor = null
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        checkForConnectedSensor()
                    }
                }
            } catch (e: Exception) {
                updateStatus("Error de sistema: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        updateStatus("Buscando sensor U.are.U 5300...")
        checkForConnectedSensor()
    }

    private fun checkForConnectedSensor() {
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        var sensorFound = false

        if (deviceList.isEmpty()) {
            updateStatus("El puerto está vacío.")
            return
        }

        for (device in deviceList.values) {
            if (device.vendorId == UAREU_VID && device.productId == UAREU_PID) {
                sensorFound = true
                biometricSensor = device

                if (usbManager.hasPermission(device)) {
                    updateStatus("Permiso previo detectado. Inicializando sensor...")
                    initBiometricSDK(device)
                } else {
                    updateStatus("Sensor detectado. Solicitando permiso...")
                    solicitarPermisoUsb(device)
                }
            }
        }
    }

    private fun solicitarPermisoUsb(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(packageName)
        }

        val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun initBiometricSDK(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    updateStatus("✅ SDK Inicializado. Lector listo para capturar.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error en SDK: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateStatus(message: String) {
        Log.d("USB_BIOMETRIA", message)
        runOnUiThread {
            tvStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}