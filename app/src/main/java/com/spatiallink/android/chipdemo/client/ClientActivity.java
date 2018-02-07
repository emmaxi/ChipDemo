package com.spatiallink.android.chipdemo.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.spatiallink.android.chipdemo.R;
import com.spatiallink.android.chipdemo.databinding.ActivityClientBinding;
import com.spatiallink.android.chipdemo.databinding.ViewGattServerBinding;
import com.spatiallink.android.chipdemo.util.BluetoothUtils;
import com.spatiallink.android.chipdemo.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.spatiallink.android.chipdemo.Constants.SCAN_PERIOD;

public class ClientActivity extends AppCompatActivity {

    private static final String TAG = "ClientActivity";

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    private ActivityClientBinding mBinding;

    private boolean mScanning;
    private Handler mHandler;
    private Handler mLogHandler;
    private Map<String, BluetoothDevice> mScanResults;

    private boolean mConnected;
    private boolean mEchoInitialized;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private BluetoothGatt mGatt;

    // Lifecycle

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogHandler = new Handler(Looper.getMainLooper());

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_client);
        @SuppressLint("HardwareIds")
        String deviceInfo = "Device Info"
                + "\nName: " + mBluetoothAdapter.getName()
                + "\nAddress: " + mBluetoothAdapter.getAddress();
        mBinding.clientDeviceInfoTextView.setText(deviceInfo);
        mBinding.startScanningButton.setOnClickListener(v -> startScan());
        mBinding.stopScanningButton.setOnClickListener(v -> stopScan());
        mBinding.sendMessageButton.setOnClickListener(v -> sendMessage());
        mBinding.disconnectButton.setOnClickListener(v -> disconnectGattServer());
        mBinding.viewClientLog.clearLogButton.setOnClickListener(v -> clearLogs());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check low energy support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //logError("No LE Support.");
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Scanning
    private void startScan() {
        //Ensures Bluetooth is available on the device and it is enabled
        if (!hasPermissions() || mScanning) {
            return;
        }

        disconnectGattServer();

        mBinding.serverListContainer.removeAllViews();

        // Create a ScanCallBack to handle the scan results,and add a map to store the results
        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback(mScanResults);

        // Note: Filtering does not work the same (or at all) on most devices. It also is unable to
        // search for a mask or anything less than a full UUID.
        // Unless the full UUID of the server is known, manual filtering may be necessary.
        // For example, when looking for a brand of device that contains a char sequence in the UUID
//        ScanFilter scanFilter = new ScanFilter.Builder()
//                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
//                .build();
        ScanFilter scanFilter = new ScanFilter.Builder().build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        //init ScanSettings
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        //Get instance of BluetoothLeScanner.
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        Log.d("EMMA", "SCAN STARTED");

        mHandler = new Handler();
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD);

        mScanning = true;
        log("Started scanning.");
    }

    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;
        log("Stopped scanning.");
    }

    //Perform actions base on scanning results
    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            Log.d("EMMA", "scan result is empty");
            return;
        }

        for (String deviceAddress : mScanResults.keySet()) {
            BluetoothDevice device = mScanResults.get(deviceAddress);
            GattServerViewModel viewModel = new GattServerViewModel(device);

            ViewGattServerBinding binding = DataBindingUtil.inflate(LayoutInflater.from(this),
                    R.layout.view_gatt_server,
                    mBinding.serverListContainer,
                    true);
            binding.setViewModel(viewModel);
            binding.connectGattServerButton.setOnClickListener(v -> connectDevice(device));
        }
    }

    // check if the device enabled Bluetooth feature
    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth not enabled. Try to request permission
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            log("Requested user enables Bluetooth. Try starting the scan again.");
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        log("Requested user enable Location. Try starting the scan again.");
    }

    // Gatt connection
    private void connectDevice(BluetoothDevice device) {
        log("Connecting to " + device.getAddress());
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    // Messaging
    private void sendMessage() {
        if (!mConnected || !mEchoInitialized) {
            return;
        }

        BluetoothGattCharacteristic characteristic = BluetoothUtils.findEchoCharacteristic(mGatt);
        if (characteristic == null) {
            logError("Unable to find echo characteristic.");
            disconnectGattServer();
            return;
        }

        String message = mBinding.messageEditText.getText().toString();
        log("Sending message: " + message);

        byte[] messageBytes = StringUtils.bytesFromString(message);
        if (messageBytes.length == 0) {
            logError("Unable to convert message to bytes");
            return;
        }

        characteristic.setValue(messageBytes);
        boolean success = mGatt.writeCharacteristic(characteristic);
        if (success) {
            log("Wrote: " + StringUtils.byteArrayInHexFormat(messageBytes));
        } else {
            logError("Failed to write data");
        }
    }

    // Logging
    private void clearLogs() {
        mLogHandler.post(() -> mBinding.viewClientLog.logTextView.setText(""));
    }

    public void log(String msg) {
        Log.d(TAG, msg);
        mLogHandler.post(() -> {
            mBinding.viewClientLog.logTextView.append(msg + "\n");
            mBinding.viewClientLog.logScrollView.post(() -> mBinding.viewClientLog.logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    public void logError(String msg) {
        log("Error: " + msg);
    }

    // Gat Client Actions

    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    public void initializeEcho() {
        mEchoInitialized = true;
    }

    public void disconnectGattServer() {
        Log.d("EMMA", "DISCONNECT GATT SERVER");
        log("Closing Gatt connection");
//        clearLogs();
        mConnected = false;
        mEchoInitialized = false;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    // Callbacks
    private class BtleScanCallback extends ScanCallback {
        //key: device address, value: BluetoothDevice class
        private Map<String, BluetoothDevice> mScanResults;

        BtleScanCallback(Map<String, BluetoothDevice> scanResults) {
//            Log.d("EMMA", "BtleScanCallback contractor used");
            mScanResults = scanResults;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            Log.d("EMMA", "inside BtleScanCallback onScanResult");
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
//            Log.d("EMMA", "inside BtleScanCallback onBatchScanResults");
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
//            Log.d("EMMA", "SCAN FAILED");
            logError("BLE Scan Failed with code " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            Log.d("EMMA", "ADD DEVICE TO RESULTS!");
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            mScanResults.put(deviceAddress, device);

//            stopScan();
//            BluetoothDevice bluetoothDevice = scanResult.getDevice();
//            connectDevice(bluetoothDevice);
        }
    }

    private class GattClientCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            log("onConnectionStateChange newState: " + newState);

            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.d("EMMA", "BLUETOOTH GATT FAILURE");
                logError("Connection Gatt failure status " + status);
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // handle anything not SUCCESS as failure
                Log.d("EMMA", "BLUETOOTH GATT NOT SUCCESS: " + status);
                logError("Connection not GATT sucess status " + status);
                disconnectGattServer();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("EMMA", "DEVICE CONNECTED");
                log("Connected to device " + gatt.getDevice().getAddress());
                setConnected(true);
                // Discovers services offered by a remote device as well as their characteristics and descriptors.
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("EMMA", "DEVICE DISCONNECTED");
                log("Disconnected from device");
                disconnectGattServer();
            }
        }

        // Once discoverServices() is completed, the onServicesDiscovered(BluetoothGatt, int) callback is triggered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d("EMMA", "INSIDE onServicesDiscovered, Device service discovery unsuccessful");
                log("Device service discovery unsuccessful, status " + status);
                return;
            }

            List<BluetoothGattCharacteristic> matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt);
            if (matchingCharacteristics.isEmpty()) {
                logError("Unable to find characteristics.");
                return;
            }

            log("Initializing: setting write type and enabling notification");
            for (BluetoothGattCharacteristic characteristic : matchingCharacteristics) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                enableCharacteristicNotification(gatt, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic written successfully");
            } else {
                logError("Characteristic write unsuccessful, status: " + status);
                disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic read successfully");
                readCharacteristic(characteristic);
            } else {
                logError("Characteristic read unsuccessful, status: " + status);
                // Trying to read from the Time Characteristic? It doesnt have the property or permissions
                // set to allow this. Normally this would be an error and you would want to:
                // disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            log("Characteristic changed, " + characteristic.getUuid().toString());
            readCharacteristic(characteristic);
        }

        private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
            if (characteristicWriteSuccess) {
                log("Characteristic notification set successfully for " + characteristic.getUuid().toString());
                if (BluetoothUtils.isEchoCharacteristic(characteristic)) {
                    initializeEcho();
                }
            } else {
                logError("Characteristic notification set failure for " + characteristic.getUuid().toString());
            }
        }

        private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
            byte[] messageBytes = characteristic.getValue();
            log("Read: " + StringUtils.byteArrayInHexFormat(messageBytes));
            String message = StringUtils.stringFromBytes(messageBytes);
            if (message == null) {
                logError("Unable to convert bytes to string");
                return;
            }

            log("Received message: " + message);
        }
    }
}
