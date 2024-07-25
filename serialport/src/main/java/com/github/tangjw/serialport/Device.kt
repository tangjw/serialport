package com.github.tangjw.serialport

import android.os.Build
import java.io.FileNotFoundException

enum class Device(
    val title: String,
    val vendorId: Int = 0,
    val productId: Int = 0,
    val path: String? = null
) {
    RS485_3399("串口RS485", path = "/dev/ttyXRUSB1"),
    RS485_3588("串口RS485", path = "/dev/ttyS1"),

    USB_485("串口USB-485", 1027),
    USB_TTL("串口USB-TTL", 1659);
    companion object{
        val RS485: Device
            get() = when (Build.MODEL) {
                "rk3588_s" -> RS485_3588
                "rk3399-all" -> RS485_3399
                else -> throw FileNotFoundException("该设备没有对应的RS485串口设备")
            }

    }
}