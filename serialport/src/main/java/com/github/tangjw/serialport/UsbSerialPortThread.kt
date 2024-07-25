package com.github.tangjw.serialport

import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.github.tangjw.serialport.usb.FtdiSerialDriver
import com.github.tangjw.serialport.usb.ProlificSerialDriver
import com.github.tangjw.serialport.usb.UsbSerialDriver
import com.github.tangjw.serialport.usb.UsbSerialPort

class UsbSerialPortThread private constructor(
    override val device: Device, private val usbManager: UsbManager
) : HandlerThread(device.title), SerialPort {
    companion object {

        private var instance: UsbSerialPortThread? = null

        @Synchronized
        fun getInstance(device: Device, usbManager: UsbManager): UsbSerialPortThread {
            if (instance == null || instance?.isOpened == false) {
                instance = null
                instance = UsbSerialPortThread(device, usbManager).apply { start() }
            }
            return instance!!
        }
    }


    private val handler by lazy {
        Handler(looper)
    }

    private var usbSerialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null

    private fun openUsbDevice(usbManager: UsbManager, device: UsbDevice, driver: UsbSerialDriver) {
//        Log.e("UsbSerialPortThread", device.toString())
        usbManager.openDevice(device)?.let {
            usbSerialPort = driver.getPorts()[0]
            try {
                usbSerialPort!!.open(it)
                usbSerialPort!!.setParameters(
                    9600,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                usbConnection = it
            } catch (e: Exception) {
                Log.e("UsbSerialPortThread", e.message.toString())
            }
        }
    }

    override fun onLooperPrepared() {
        // 检查串口是否已经打开了，未打开成功则退出线程
        if (usbSerialPort == null || usbConnection == null) {
            quitSafely()
        }
    }

    override fun run() {
        openSerialPort()
        super.run()
        closeSerialPort()
    }

    private fun closeSerialPort() {
        try {
            usbSerialPort?.close()
            usbSerialPort = null
            usbConnection?.close()
            usbConnection = null
        } catch (_: Exception) {
        }
    }

    private fun openSerialPort() {
        val usbDevice = usbManager.deviceList.values.firstOrNull { it.vendorId == device.vendorId }
        if (usbDevice != null ) {
            if (!usbManager.hasPermission(usbDevice)) {
                usbManager.requestPermission(usbDevice, null)
            }
            val driver = when (device) {
                Device.USB_485 -> FtdiSerialDriver(usbDevice)
                Device.USB_TTL -> ProlificSerialDriver(usbDevice)
                else -> null
            }
            if (driver != null) {
                openUsbDevice(usbManager, usbDevice, driver)
            }
        }
    }

    override fun write(data: ByteArray, callback: ((ByteArray) -> Unit)?) {
        handler.post {
            val response = writeSync(data, 200)
            callback?.invoke(response)
        }
    }

    override fun writeSync(data: ByteArray) {
        usbSerialPort?.write(data, 200)
    }

    override fun writeSync(data: ByteArray, timeout: Int): ByteArray {
        var response = byteArrayOf()
        usbSerialPort?.let {
            it.write(data, timeout)
            val count = 10   // 循环10次 读取
            SystemClock.sleep(50)
            for (i in 1..count) {
                val buffer = ByteArray(512) // UsbEndpoint#maxPacketSize
                val readLength = it.read(buffer, timeout)
                if (readLength == 0 && response.isNotEmpty()) {
                    break
                }
                val read = buffer.copyOf(readLength)
                response += read
                if (i < count) {
                    SystemClock.sleep(timeout / count.toLong())
                } else {
                    Log.e(name, "timeout after $timeout ms")
                }
            }
        }
        return response
    }

    override fun runTask(delay: Int, runnable: Runnable) {
        handler.postDelayed(runnable, delay.toLong())
    }

    override val isOpened: Boolean
        get() = isAlive && looper != null && usbSerialPort != null && usbConnection != null


}