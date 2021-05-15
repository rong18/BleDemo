package com.sahooz.ble.central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScanActivity : AppCompatActivity() {

    private val TAG = "Scan"

    private val devices = ArrayList<BluetoothDevice>()

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

    private val deviceAdapter = DeviceAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = deviceAdapter
        list.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        scan()
    }

    override fun onDestroy() {
        super.onDestroy()

        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(callback)
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

    private inner class DeviceAdapter : RecyclerView.Adapter<DeviceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemView = inflater.inflate(android.R.layout.two_line_list_item, parent, false)
            return DeviceViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val device = devices[position]
            holder.bind(device)
        }

        override fun getItemCount() = devices.size

    }

    private inner class DeviceViewHolder(itemView: View): RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val text1: TextView = itemView.findViewById(android.R.id.text1)
        val text2: TextView = itemView.findViewById(android.R.id.text2)
        var device: BluetoothDevice? = null

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(device: BluetoothDevice) {
            text1.text = device.name
            text2.text = device.address
            this.device = device
        }

        override fun onClick(v: View?) {
            device ?: return

            setResult(RESULT_OK, Intent().apply { putExtra("device", device) })
            finish()
        }

    }
}