## 1、开篇  

本文将主要讲述Android应用开发中对BLE API的使用。Android 4.3（API 18）开始支持蓝牙4.0，但此时Android手机只能作为中心设备或者说主设备，不能作为从设备。Android 5.0（API 21）以后，Android开始支持从设备模式。Android 4.3和5.0以后的API会有一些差别，本文实例会使用5.0以后的API。本文会分别讲解主设备和从设备两种模式下的开发流程。  

# 2、从设备模式 

先从从设备模式开始，从设备的工作是发送广播，等待主设备发起连接，双方通过约定好的具有特定的UUID的Service、Characteristic和Descriptor进行通信，从设备为服务端，主设备为客户端。  

### 2.1. manifest配置  

在开发之前，我们需要在Manifest里面添加如下权限和功能声明：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- Android 6.0以上需要定位，9.0及以下可以只需要ACCESS_COARSE_LOCATION，但是Android 10及以上需要ACCESS_FINE_LOCATION -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- 声明需要蓝牙BLE功能的手机才适用本App -->
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true" />
```  

### 2.2 开启Gatt Server

既然作为服务端，那么肯定是需要为客户端提供服务了。一个Service我们可以理解为某一个类型的数据服务，比如手环里的健康管理数据等。一个Service可以包含0个或者若干个其他Service，前者称为priamry service，后者称为Secondary service。一个Service应该包含1个以上的Characteristic，一个Characteristic可以包含0个或者若干个Descriptor。开启Gatt Server后应至少添加一个Service。  

```kotlin

// 相关ID的定义
private val SERVICEID = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB")
private val CHARID = ParcelUuid.fromString("00008888-0000-1000-8000-00805F9B34FB")
private val DESCID = ParcelUuid.fromString("0000666-0000-1000-8000-00805F9B34FB")

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
```  

Characteristic和Descriptor都是可以指定读写权限的。  
另外，openGattServer方法需要传入一个BluetoothGattServerCallback类参数，也就是上面的serverCallback，在这个Callback中，我们会收到客户端的请求的回调，并需要根据请求作出响应：

```kotlin
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
```

### 2.3 发送广播  

要使别的设备可以扫描到我们的设备，那么我们必须发送蓝牙广播才行。  

```kotlin
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

// 广播数据
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

// 扫描响应数据
private val response = AdvertiseData.Builder()
    // 设置厂商自定义数据
    .addManufacturerData(0x202, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD))
    // 是否在广播中包含设备名称
    .setIncludeDeviceName(true)
    // 是否包含广播信号强度
    //.setIncludeTxPowerLevel(true)
    .build()


// 开启广播
advertiser.startAdvertising(settings, data, response, advCallback)
```

其中response参数可以为null。  

### 2.4 响应客户端请求

事实上2.2节已经展示了响应请求的代码，这里提出来再说一下。  

```kotlin
gattServer?.sendResponse(
    device,
    requestId,
    BluetoothGatt.GATT_SUCCESS,
    offset,
    descriptor.value
)
```  

在收到客户端请求的相关回调之后，根据是否需要响应以及实际业务情况发送响应到客户端。  

### 2.5 给客户端发送通知  

根据本机运行状态或者接收到外部数据之后，特征值可能会发生变化，这时候我们可能需要主动通知客户端。  

```kotlin
val random = Random()
val values = ByteArray(2)
random.nextBytes(values)
characteristic!!.value = values

// 发送通知的关键代码
gattServer?.notifyCharacteristicChanged(it, characteristic, false)
```  

这里使用了随机数据模拟变化。   

## 3、主设备模式  

主设备模式开发流程大致有扫描设备、连接设备、发现服务和数据通信几个步骤。在开发之前，我们也需要在manifest中配置权限和功能声明。具体可参考上面从机模式，这里不赘述。  

### 3.1 扫描设备  


```kotlin
// 扫描过滤
private val filters = listOf(
    ScanFilter.Builder()
        // 厂商数据过滤
        //.setManufacturerData(0, byteArrayOf(0))
        // 设备名称过滤
        .setDeviceName("Peripheral")
        // Service UUID过滤
        .setServiceUuid(ParcelUuid(Constants.SERVICEID) )
        .build(),
)

// 扫描设置
private val settings = ScanSettings.Builder()
    .setReportDelay(0)
    // 扫描频率，对应广播频率
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()

// 扫描回调
private val callback = object : ScanCallback() {

    override fun onScanResult(callbackType: Int, result: ScanResult) {

        val device = result.device ?: return

        Log.d(TAG, "Found device ${result.device.name}: ${result.device.address}")

        if(!devices.contains(device)) {
            devices.add(device)
            deviceAdapter.notifyItemInserted(devices.size - 1)
        }

    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)

        Log.e(TAG, "Scan failed: $errorCode")
        Toast.makeText(this@ScanActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
    }
}

private fun scan() {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    if(adapter == null || !adapter.isEnabled) {
        Toast.makeText(this, "Open BT function first", Toast.LENGTH_SHORT).show()
        finish()
        return
    }

    // 开启扫描的关键代码
    adapter.bluetoothLeScanner.startScan(filters, settings, callback)
}
``` 

startScan还有一个只传递callback的重载方法，使用默认的扫描设置且不过滤设备。  

### 3.2 连接设备  

```kotlin
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
```

### 3.3 发现服务

上面callback中其实已经包含发现服务的代码了，这里提出来，啰嗦一下

```kotlin
// 发现服务
gatt.discoverServices()

/**
 * 发现服务步骤完成的回调
 */
override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) { 
    // 这里获取约定的服务、特征值和描述符
    ...
}
```  

### 3.3 读写  

```kotlin
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
```  

这里都是写入随机值，实际开发中根据业务需求写即可。  

### 3.4 接收客户端通知

```kotlin 
object : BluetoothGattCallback() {
    ...  

    /**
    * 接收到客户端主动发送特征值变化通知的回调
    */
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // 根据业务需求进行实际操作
        ...
        Log.d(TAG, "onCharacteristicChanged: ${bytesToString(characteristic.value)}")
    }

    ...
}
```
