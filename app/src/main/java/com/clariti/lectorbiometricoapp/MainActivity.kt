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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION = "com.clariti.lectorbiometricoapp.USB_PERMISSION"

    // Identificadores de tu U.are.U 5300
    private val UAREU_VID = 1466
    private val UAREU_PID = 10

    private lateinit var usbManager: UsbManager
    private var biometricSensor: UsbDevice? = null

    // Referencia a la UI
    private lateinit var tvStatus: TextView

    // Escuchador de eventos del puerto USB
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                updateStatus("Permiso concedido. Inicializando sensor...")
                                initBiometricSDK(it)
                            }
                        } else {
                            updateStatus("Error: El usuario denegó el permiso USB.")
                            Log.e("USB_BIOMETRIA", "Permiso denegado para el dispositivo $device")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        if (it.vendorId == UAREU_VID && it.productId == UAREU_PID) {
                            updateStatus("Sensor desconectado.")
                            biometricSensor = null
                            // TODO: Llamar al método de limpieza del SDK (ej. mReader.Close())
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    // Si se conecta con la app ya abierta, volvemos a escanear
                    checkForConnectedSensor()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Asegúrate de tener un TextView con el id tvStatus en tu activity_main.xml
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus) // Reemplaza con tu ID real de UI

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // Registramos el Receiver para escuchar eventos del sistema
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        // Usando ContextCompat para manejar la compatibilidad de versiones sin errores de Lint
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        updateStatus("Buscando sensor U.are.U 5300...")
        checkForConnectedSensor()
    }

    private fun checkForConnectedSensor() {
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        var sensorFound = false

        for (device in deviceList.values) {
            if (device.vendorId == UAREU_VID && device.productId == UAREU_PID) {
                sensorFound = true
                biometricSensor = device
                updateStatus("Sensor detectado. Solicitando permiso...")
                solicitarPermisoUsb(device)
                break
            }
        }

        if (!sensorFound) {
            updateStatus("Por favor, conecte el lector biométrico.")
        }
    }

    private fun solicitarPermisoUsb(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        // Esta línea lanza el popup del sistema
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun initBiometricSDK(device: UsbDevice) {
        // Ejecutamos la lógica bloqueante del SDK en un hilo secundario usando Corrutinas
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // --- INICIO DE LA LÓGICA DE DIGITALPERSONA ---
                // Aquí instanciarás UareUGlobal.getReaderCollection()
                // Y llamarás a reader.Open() y reader.Capture()

                withContext(Dispatchers.Main) {
                    updateStatus("SDK Inicializado. Lector listo para capturar.")
                }

                // Simulación de una captura bloqueante
                // val captureResult = reader.Capture(...)

                // --- FIN DE LA LÓGICA DE DIGITALPERSONA ---

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error en SDK: ${e.localizedMessage}")
                }
            }
        }
    }

    // Función auxiliar para actualizar la UI desde cualquier hilo
    private fun updateStatus(message: String) {
        Log.d("USB_BIOMETRIA", message)
        runOnUiThread {
            tvStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Es vital desregistrar el receiver para evitar fugas de memoria
        unregisterReceiver(usbReceiver)
    }
}