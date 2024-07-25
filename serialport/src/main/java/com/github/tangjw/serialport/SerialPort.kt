package com.github.tangjw.serialport

interface SerialPort {
    /**
     * 当前串口设备
     */
    val device: Device

    /**
     * 串口是否打开
     */
    val isOpened: Boolean

    /**
     * 向串口异步写入数据
     */
    fun write(data: ByteArray, callback: ((ByteArray) -> Unit)? = null)

    /**
     * 向串口同步写入数据
     */
    fun writeSync(data: ByteArray)

    /**
     * 向串口同步写入数据并等待响应
     */
    fun writeSync(data: ByteArray, timeout: Int): ByteArray

    /**
     * 延时向串口所在线程添加任务
     */
    fun runTask(delay: Int = 0, runnable: Runnable)
}