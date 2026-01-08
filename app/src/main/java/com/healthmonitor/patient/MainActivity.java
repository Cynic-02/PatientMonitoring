package com.healthmonitor.patient;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private Thread connectionThread;
    private boolean isConnected = false;
    private Handler mainHandler;
    private Vibrator vibrator;
    
    private boolean lastPatientStatus = true;
    private StringBuilder dataBuffer = new StringBuilder();

    // UI Elements
    private TextView tvConnectionStatus;
    private TextView tvHeartRate;
    private TextView tvOxygenLevel;
    private TextView tvPatientStatus;
    private CardView cardHeartRate;
    private CardView cardOxygen;
    private CardView cardStatus;
    private Button btnConnect;
    private Button btnScan;
    private View statusIndicator;

    // Scanning helpers
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> discoveredAdapter;
    private BroadcastReceiver discoveryReceiver;
    private boolean isScanning = false;
    private boolean pendingScan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        mainHandler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnect.setOnClickListener(v -> checkPermissionsAndConnect());
        btnScan.setOnClickListener(v -> ensureScanPermissionAndStart());
    }

    private void initializeViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvOxygenLevel = findViewById(R.id.tvOxygenLevel);
        tvPatientStatus = findViewById(R.id.tvPatientStatus);
        cardHeartRate = findViewById(R.id.cardHeartRate);
        cardOxygen = findViewById(R.id.cardOxygen);
        cardStatus = findViewById(R.id.cardStatus);
        btnScan = findViewById(R.id.btnScan);
        btnConnect = findViewById(R.id.btnConnect);
        statusIndicator = findViewById(R.id.statusIndicator);
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            showDeviceListDialog();
        }
    }

    private void showDeviceListDialog() {
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            final ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
            
            if (deviceList.isEmpty()) {
                Toast.makeText(this, "No paired Bluetooth devices found. Please pair your device first.", Toast.LENGTH_LONG).show();
                return;
            }

            ArrayList<String> deviceNames = new ArrayList<>();
            for (BluetoothDevice device : deviceList) {
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                deviceNames.add(deviceName != null ? deviceName + "\n" + deviceAddress : deviceAddress);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Bluetooth Device");
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
            builder.setAdapter(adapter, (dialog, which) -> {
                BluetoothDevice selectedDevice = deviceList.get(which);
                connectToDevice(selectedDevice);
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (isConnected) {
            disconnect();
        }

        updateConnectionStatus("Connecting...", false);
        btnConnect.setEnabled(false);

        connectionThread = new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();

                mainHandler.post(() -> {
                    isConnected = true;
                    updateConnectionStatus("Connected", true);
                    btnConnect.setText("DISCONNECT");
                    btnConnect.setEnabled(true);
                    startDataReception();
                });

            } catch (IOException e) {
                mainHandler.post(() -> {
                    updateConnectionStatus("Connection Failed", false);
                    btnConnect.setText("CONNECT");
                    btnConnect.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                
                try {
                    if (bluetoothSocket != null) bluetoothSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            } catch (SecurityException e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
                    btnConnect.setEnabled(true);
                });
            }
        });
        connectionThread.start();
    }

    private void startDataReception() {
        Thread dataThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected) {
                try {
                    if (inputStream.available() > 0) {
                        bytes = inputStream.read(buffer);
                        String receivedData = new String(buffer, 0, bytes);
                        dataBuffer.append(receivedData);
                        
                        processDataBuffer();
                    }
                    Thread.sleep(50);
                } catch (IOException e) {
                    if (isConnected) {
                        mainHandler.post(() -> {
                            disconnect();
                            Toast.makeText(MainActivity.this, "Connection lost", Toast.LENGTH_SHORT).show();
                        });
                    }
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        dataThread.start();
    }

    private void processDataBuffer() {
        String data = dataBuffer.toString();
        
        // Try parsing as comma/semicolon/pipe delimited string
        if (data.contains(",") || data.contains(";") || data.contains("|")) {
            String delimiter = data.contains(",") ? "," : (data.contains(";") ? ";" : "\\|");
            String[] parts = data.split(delimiter);
            
            if (parts.length >= 3) {
                try {
                    int heartRate = extractNumber(parts[0]);
                    int oxygenLevel = extractNumber(parts[1]);
                    int status = extractNumber(parts[2]);
                    
                    if (heartRate >= 0 && oxygenLevel >= 0 && (status == 0 || status == 1)) {
                        updateUI(heartRate, oxygenLevel, status);
                        dataBuffer.setLength(0);
                    }
                } catch (Exception e) {
                    // Continue buffering
                }
            }
        }
        // Try parsing as key-value pairs
        else if (data.contains("HR") || data.contains("O2") || data.contains("STATUS")) {
            Pattern hrPattern = Pattern.compile("HR[:\\s]*([0-9]+)");
            Pattern o2Pattern = Pattern.compile("O2[:\\s]*([0-9]+)");
            Pattern statusPattern = Pattern.compile("STATUS[:\\s]*([0-1])");
            
            Matcher hrMatcher = hrPattern.matcher(data);
            Matcher o2Matcher = o2Pattern.matcher(data);
            Matcher statusMatcher = statusPattern.matcher(data);
            
            if (hrMatcher.find() && o2Matcher.find() && statusMatcher.find()) {
                int heartRate = Integer.parseInt(hrMatcher.group(1));
                int oxygenLevel = Integer.parseInt(o2Matcher.group(1));
                int status = Integer.parseInt(statusMatcher.group(1));
                
                updateUI(heartRate, oxygenLevel, status);
                dataBuffer.setLength(0);
            }
        }
        // Try parsing as space-delimited
        else if (data.trim().split("\\s+").length >= 3) {
            String[] parts = data.trim().split("\\s+");
            try {
                int heartRate = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int oxygenLevel = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                int status = Integer.parseInt(parts[2].replaceAll("[^0-1]", ""));
                
                if (heartRate > 0 && oxygenLevel > 0) {
                    updateUI(heartRate, oxygenLevel, status);
                    dataBuffer.setLength(0);
                }
            } catch (Exception e) {
                // Continue buffering
            }
        }
        
        // Clear buffer if it gets too large
        if (dataBuffer.length() > 500) {
            dataBuffer.setLength(0);
        }
    }

    private int extractNumber(String str) {
        String numStr = str.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) return -1;
        return Integer.parseInt(numStr);
    }

    private void updateUI(int heartRate, int oxygenLevel, int status) {
        mainHandler.post(() -> {
            tvHeartRate.setText(String.valueOf(heartRate));
            tvOxygenLevel.setText(String.valueOf(oxygenLevel));
            
            boolean isOkay = (status == 1);
            tvPatientStatus.setText(isOkay ? "OKAY" : "NOT OKAY");
            
            if (isOkay) {
                    cardStatus.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.status_ok));
                } else {
                    cardStatus.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.status_critical));
                if (lastPatientStatus != isOkay) {
                    triggerVibration();
                }
            }
            
            lastPatientStatus = isOkay;
        });
    }

    private void triggerVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    private void updateConnectionStatus(String status, boolean connected) {
        tvConnectionStatus.setText(status);
        if (connected) {
            statusIndicator.setBackgroundResource(R.drawable.status_connected);
            btnConnect.setText("DISCONNECT");
        } else {
            statusIndicator.setBackgroundResource(R.drawable.status_disconnected);
            btnConnect.setText("CONNECT");
        }
    }

    private void disconnect() {
        isConnected = false;
        
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        updateConnectionStatus("Disconnected", false);
        btnConnect.setText("CONNECT");
        btnConnect.setEnabled(true);
        
        // Reset UI
        tvHeartRate.setText("--");
        tvOxygenLevel.setText("--");
        tvPatientStatus.setText("--");
        cardStatus.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.card_background));
    }

    // ---------------------- Bluetooth Scanning ----------------------
    private void ensureScanPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                pendingScan = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                pendingScan = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        showScanDeviceDialogAndStartDiscovery();
    }

    private void showScanDeviceDialogAndStartDiscovery() {
        // Build adapter with paired devices first
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> combined = new ArrayList<>();
        ArrayList<String> deviceNames = new ArrayList<>();

        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice d : pairedDevices) {
                combined.add(d);
                String name = d.getName() != null ? d.getName() + "\n" + d.getAddress() : d.getAddress();
                deviceNames.add(name);
            }
        }

        // Add discovered device placeholders initially
        discoveredDevices.clear();
        discoveredAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);

        ListView listView = new ListView(this);
        listView.setAdapter(discoveredAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Bluetooth Device");
        builder.setView(listView);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            stopDiscovery();
            Toast.makeText(MainActivity.this, getString(R.string.scan_cancelled), Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            stopDiscovery();

            // Determine if selection is paired or discovered
            if (position < combined.size()) {
                BluetoothDevice selected = combined.get(position);
                connectToDevice(selected);
                dialog.dismiss();
            } else {
                int discoveredIndex = position - combined.size();
                if (discoveredIndex >= 0 && discoveredIndex < discoveredDevices.size()) {
                    BluetoothDevice selected = discoveredDevices.get(discoveredIndex);
                    connectToDevice(selected);
                    dialog.dismiss();
                }
            }
        });

        dialog.show();
        startDiscovery(combined);
    }

    private void startDiscovery(ArrayList<BluetoothDevice> pairedList) {
        if (bluetoothAdapter == null) return;

        // Prepare discovery receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        boolean alreadyPaired = false;
                        for (BluetoothDevice d : pairedList) {
                            if (d.getAddress().equals(device.getAddress())) {
                                alreadyPaired = true;
                                break;
                            }
                        }
                        if (!alreadyPaired) {
                            // Check duplicates
                            boolean exists = false;
                            for (BluetoothDevice d : discoveredDevices) {
                                if (d.getAddress().equals(device.getAddress())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                discoveredDevices.add(device);
                                discoveredAdapter.add((device.getName() != null ? device.getName() : "Unknown") + "\n" + device.getAddress());
                            }
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    isScanning = false;
                    btnScan.setText(R.string.scan);
                    if (discoveredDevices.isEmpty() && (pairedList == null || pairedList.isEmpty())) {
                        discoveredAdapter.add(getString(R.string.no_devices_found));
                    }
                }
            }
        };

        // Register receiver and start discovery
        try {
            registerReceiver(discoveryReceiver, filter);
            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
            boolean started = bluetoothAdapter.startDiscovery();
            if (started) {
                isScanning = true;
                btnScan.setText(R.string.scanning);
            } else {
                Toast.makeText(this, "Failed to start discovery", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            // Receiver already registered
        }
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (discoveryReceiver != null) {
            try {
                unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException ignored) {}
            discoveryReceiver = null;
        }
        isScanning = false;
        btnScan.setText(R.string.scan);
    }

    // ---------------------- End Bluetooth Scanning ----------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                showDeviceListDialog();
            } else {
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                checkPermissionsAndConnect();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDiscovery();
        if (isConnected) {
            disconnect();
        }
    }
}