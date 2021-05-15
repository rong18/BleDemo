package com.sahooz.ble.peripheral

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG ="Peripheral"
    private val SERVICEID = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB")
    private val CHARID = ParcelUuid.fromString("00008888-0000-1000-8000-00805F9B34FB")
    private val DESCID = ParcelUuid.fromString("0000666-0000-1000-8000-00805F9B34FB")

    private val advCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.d(TAG, "onStartFailure: $errorCode")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "onStartSuccess: $settingsInEffect")
        }
    }

    // 广播设置
    private val settings = AdvertiseSettings.Builder()
        //设置广播频率，LOW_LATENCY：100ms左右，BALANCED：250～300ms左右，LOW_POWER:1000ms左右
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        // 是否可以连接，false就是不可连接只广播
        .setConnectable(true)
        // 设置广播信号强度
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        // 设置超时时间（毫秒），最大不能超过180000，0为不限制
        .setTimeout(100)
        .build()

    // 广播数据设置
    private val data = AdvertiseData.Builder()
        // 16 bit Service UUID
        .addServiceUuid(SERVICEID)
        // 设置厂商自定义数据
        //.addManufacturerData(0x202, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xF, 0x10))
        // 是否在广播中包含设备名称
        .setIncludeDeviceName(true)
        // 是否包含广播信号强度
        //.setIncludeTxPowerLevel(true)
        .build()

    // 扫描响应设置
    private val response = AdvertiseData.Builder()
        // 设置厂商自定义数据
        .addManufacturerData(0x202, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD))
        // 是否在广播中包含设备名称
        .setIncludeDeviceName(true)
        // 是否包含广播信号强度
        //.setIncludeTxPowerLevel(true)
        .build()

    private var gattServer: BluetoothGattServer? = null
    private var device: BluetoothDevice? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val serverCallback = object : BluetoothGattServerCallback() {

        private var state = BluetoothProfile.STATE_DISCONNECTED

        /**
         * 连接状态发生改变回调。一共有四种状态：连接中、已连接、断开连接中、断开连接。
         * @param device：连接的设备
         * @param status：蓝牙状态码
         * @param newState：改变后新的连接状态
         */
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val oldState = state
            state = newState
            if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                this@MainActivity.device = device
            }
            Log.i(TAG, "onConnectionStateChange: $oldState => $newState, status: $status")
        }

        /**
         * 添加服务回调
         */
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Log.i(TAG, "onServiceAdded: ${service?.uuid}, stateus: $status")
        }

        /**
         * 收到读特征值的请求，在此方法中需要通过BluetoothGattServer#sendResponse方法做出响应
         * @param device：客户端设备
         * @param requestId：本次请求的id
         * @param characteristic：请求读取的特征值
         */
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.i(TAG, "onCharacteristicReadRequest, requestId: $requestId, offset: $offset")
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic.value
            )
        }

        /**
         * 客户端请求写入特征值，如果客户端需要回应，在此方法中需要通过BluetoothGattServer#sendResponse方法做出响应
         * @param device：客户端设备
         * @param requestId：本次请求的id
         * @param characteristic：请求写入的特征值
         */
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            characteristic.value = value
            Log.i(TAG, "onCharacteristicWriteRequest, requestId: $requestId, offset: $offset, preparedWrite: $preparedWrite, responseNeeded: $responseNeeded, value: ${bytesToString(value)}")
            if(responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    characteristic.value
                )
            }
        }

        /**
         * 收到读取描述符的请求，在此方法中需要通过BluetoothGattServer#sendResponse方法做出响应
         * @param device：客户端设备
         * @param requestId：本次请求的id
         * @param descriptor：请求读取的描述符
         */
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.i(TAG, "onDescriptorReadRequest, requestId: $requestId, offset: $offset")
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                descriptor.value
            )
        }

        /**
         * 客户端请求写入描述符，如果客户端需要回应，在此方法中需要通过BluetoothGattServer#sendResponse方法做出响应
         * @param device：客户端设备
         * @param requestId：本次请求的id
         * @param descriptor：请求写入的描述符
         */
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            descriptor.value = value
            Log.i(TAG, "onCharacteristicReadRequest, requestId: $requestId, offset: $offset, preparedWrite: $preparedWrite, responseNeeded: $responseNeeded")
            if(responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    descriptor.value
                )
            }
        }

        /**
         * MTU发生变化的回调
         * @param mtu：变化后MTU的大小
         */
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            Log.d(TAG, "onMtuChanged: $mtu")
        }

        /**
         * 给客户端发送完通知的回调
         */
        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            Log.d(TAG, "onNotificationSent: $status")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun advertise(v: View) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val advertiser = adapter.bluetoothLeAdvertiser

        if(adapter == null || advertiser == null) {
            adapter?.enable()
            Toast.makeText(this, "Open BT function first", Toast.LENGTH_SHORT).show()
            return
        }

        adapter.name = "Peripheral"

        // 获取系统蓝牙服务
        val manager: BluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        // 开启Gatt server
        val gattServer = manager.openGattServer(this, serverCallback)

        // 初始化可读可写的Descriptor
        val gattDescriptor = BluetoothGattDescriptor(
            DESCID.uuid,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        gattDescriptor.value = byteArrayOf(1)

        // 初始化可读可写的Characteristic
        val characteristic = BluetoothGattCharacteristic(
            CHARID.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristic.writeType
        characteristic.addDescriptor(gattDescriptor)
        characteristic.value = byteArrayOf( 3, 4)

        // 初始化Service
        val gattService = BluetoothGattService(
            SERVICEID.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        gattService.addCharacteristic(characteristic)

        // 把service添加到Gatt server，对外提供服务
        gattServer.addService(gattService)

        this.characteristic = characteristic
        this.gattServer = gattServer

        // 开启广播
        advertiser.startAdvertising(settings, data, response, advCallback)
    }

    fun notify(v: View) {
        device?.also {
            val random = Random()
            val values = ByteArray(2)
            random.nextBytes(values)
            characteristic!!.value = values

            // 发送通知的关键代码
            gattServer?.notifyCharacteristicChanged(it, characteristic, false)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeAdvertiser?.stopAdvertising(advCallback)
        gattServer?.close()
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