package com.github.tangjw.serialport

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import tp.xmaihh.serialport.SerialHelper
import tp.xmaihh.serialport.bean.ComBean
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException

class SerialPortThread private constructor(override val device: Device) :
    HandlerThread("Thread-${device.title}-${SystemClock.elapsedRealtime() / 1000}"), SerialPort {
    companion object {

        private var instance: SerialPortThread? = null

        @Synchronized
        fun getInstance(device: Device): SerialPortThread {
            if (instance == null || instance?.isOpened == false) {
                instance = null
                instance = SerialPortThread(device).apply { start() }
            }
            return instance!!
        }
    }

    private val handler by lazy { Handler(looper) }

    override fun onLooperPrepared() {
        if (serialPortHelper?.isOpen == false) {
            // 检测到串口开启失败后退出线程
            quitSafely()
        }
    }

    private var serialPortHelper: SerialHelper? = null
    override fun run() {
        openSerialPort()
        super.run()
        closeSerialPort()
    }

    private fun closeSerialPort() {
        serialPortHelper?.let {
            it.close()
            null
        }
    }

    private fun openSerialPort() {
        if (device.path != null && File(device.path).exists()) {
            execShellWithSU("chmod 666 ${device.path}")
        } else {
            throw FileNotFoundException("串口 ${device.path} 不存在")
        }

        object : SerialHelper(device.path, 9600) {
            override fun onDataReceived(comBean: ComBean) {
                callbackResponse?.invoke(comBean.bRec)
            }
        }.also {
            try {
                it.open()
            } catch (e: Exception) {
                Log.e(name, e.message.toString())
            }
            serialPortHelper = it
        }
    }

    private var callbackResponse: ((ByteArray) -> Unit)? = null

    override fun write(data: ByteArray, callback: ((ByteArray) -> Unit)?) {
        handler.post {
            if (callback == null) {
                writeSync(data)
            } else {
                callback.invoke(writeSync(data, 200))
            }
        }
    }

    override fun writeSync(data: ByteArray) {
        callbackResponse = null
        serialPortHelper?.send(data)
    }

    override fun writeSync(data: ByteArray, timeout: Int): ByteArray {
        var response = byteArrayOf()
        serialPortHelper?.let { helper ->
            callbackResponse = {
                response = it
                callbackResponse = null
                interrupt()
            }
            helper.send(data)
            SystemClock.sleep(timeout.toLong()/2)
            if (response.isEmpty()) {
                SystemClock.sleep(timeout.toLong()/2)
            }
        }
        return response
    }

    override fun runTask(delay: Int, runnable: Runnable) {
        handler.postDelayed(runnable, delay.toLong())
    }

    override val isOpened: Boolean
        get() = isAlive && looper != null && serialPortHelper?.isOpen == true

    fun execShellWithSU(cmdStr: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$cmdStr\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()

            val bytes = ByteArray(process.inputStream.available())
            process.inputStream.read(bytes)
            val result = String(bytes)

            Log.e(name, result)
        } catch (e: Exception) {
            Log.e(name, e.message.toString())
        }
    }

}
