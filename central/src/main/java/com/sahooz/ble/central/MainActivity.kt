package com.sahooz.ble.central

import android.bluetooth.*
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "Central"

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var descriptor: BluetoothGattDescriptor? = null
    private val random = Random()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun toScan(v: View) {
        startActivityForResult(Intent(applicationContext, ScanActivity::class.java), 1000)
    }

    /**
     * 读取特征值
     */
    fun readChar(v: View) {
        val c = characteristic ?: return
        gatt?.readCharacteristic(c)
    }

    /**
     * 写特征值
     */
    fun writeChar(v: View) {
        val c = characteristic ?: return
        val value = ByteArray(6)
        c.value = value
        random.nextBytes(value)
        gatt?.writeCharacteristic(c)
    }

    /**
     * 写入描述符
     */
    fun writeDesc(v: View) {
        val d = descriptor ?: return
        val value = ByteArray(6)
        random.nextBytes(value)
        d.value = value
        gatt?.writeDescriptor(d)
    }

    /**
     * 读取描述符
     */
    fun readDesc(v: View) {
        val d = descriptor ?: return
        gatt?.readDescriptor(d)
    }

    /**
     * 连接设备
     */
    private fun connect(device: BluetoothDevice) {
        if(gatt != null) {
            gatt!!.connect()
            return
        }

        // 连接设备的关键代码
        gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {

            /**
             * 连接状态发生改变的回调
             * @param status：蓝牙状态码
             * @param newState：发生变化后新的状态
             */
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    this@MainActivity.gatt = null
                    return
                }

                if(newState == BluetoothProfile.STATE_CONNECTED) {
                    // 发现服务
                    gatt.discoverServices()
                }
            }

            /**
             * 发现服务步骤完成的回调
             */
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d(TAG, "onServicesDiscovered: $status")

                // 找我们需要的服务
                val service = gatt.services?.firstOrNull { it.uuid == Constants.SERVICEID }
                if(service == null) {
                    toast("Service not found!")
                    gatt.close()
                    return
                }

                // 找我们需要的特征值
                val characteristic = service.characteristics.firstOrNull { it.uuid == Constants.CHARID }
                if(characteristic == null) {
                    toast("Characteristic not found!")
                    gatt.close()
                    return
                }

                gatt.setCharacteristicNotification(characteristic, true)

                // 找我们需要的描述符
                val descriptor = characteristic.descriptors.firstOrNull { it.uuid == Constants.DESCID }
                if(descriptor == null) {
                    toast("Descriptor not found!")
                    gatt.close()
                    return
                }

                this@MainActivity.characteristic = characteristic
                this@MainActivity.descriptor = descriptor

                Log.i(TAG, "All ready!")
            }

            /**
             * 接收到客户端主动发送特征值变化通知的回调
             */
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)

                Log.d(TAG, "onCharacteristicChanged: ${bytesToString(characteristic.value)}")
            }

            /**
             * 读取到特征值的回调
             */
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d(TAG, "onCharacteristicRead: ${bytesToString(characteristic.value)}, status: $status")
            }

            /**
             * 写入到特征值的回调
             */
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                Log.d(TAG, "onCharacteristicWrite: $status")
            }

            /**
             * 写入到描述符的回调
             */
            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                Log.d(TAG, "onDescriptorWrite: $status")
            }

            /**
             * 读取到描述符的回调
             */
            override fun onDescriptorRead(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.d(TAG, "onDescriptorRead: ${bytesToString(descriptor.value)} $status")

            }

        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode != RESULT_OK || data == null) {
            return
        }

        val device = data.getParcelableExtra<BluetoothDevice>("device")!!
        connect(device)
    }

    private fun toast(msg: CharSequence) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun bytesToString(bytes: ByteArray): String {
        val sb = StringBuilder()
        if (bytes.isNotEmpty()) {
            for (i in bytes.indices) {
                sb.append(String.format("%02X", bytes[i]))
            }
        }
        return sb.toString()
    }
}