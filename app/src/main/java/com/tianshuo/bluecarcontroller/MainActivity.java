package com.tianshuo.bluecarcontroller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // SPP UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;

    private MaterialButton btnConnect, btnForward, btnBackward, btnLeft, btnRight, btnStop;
    private TextView tvStatus, tvCommand, tvLog;
    private View statusDot;
    private ScrollView logScroll;
    private Handler mainHandler;

    private Thread receiveThread;
    private volatile boolean receiving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);

            mainHandler = new Handler(Looper.getMainLooper());
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            initViews();
            setupListeners();
            checkPermissions();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initViews() {
        btnConnect  = findViewById(R.id.btnConnect);
        btnForward  = findViewById(R.id.btnForward);
        btnBackward = findViewById(R.id.btnBackward);
        btnLeft     = findViewById(R.id.btnLeft);
        btnRight    = findViewById(R.id.btnRight);
        btnStop     = findViewById(R.id.btnStop);
        tvStatus    = findViewById(R.id.tvStatus);
        tvCommand   = findViewById(R.id.tvCommand);
        tvLog       = findViewById(R.id.tvLog);
        statusDot   = findViewById(R.id.statusDot);
        logScroll   = findViewById(R.id.logScroll);

        setControlsEnabled(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnect();
            } else {
                showDeviceList();
            }
        });

        // Direction buttons: send command on press, send 'S' (stop) on release
        setupDirectionButton(btnForward,  "F", "前进");
        setupDirectionButton(btnBackward, "B", "后退");
        setupDirectionButton(btnLeft,     "L", "左转");
        setupDirectionButton(btnRight,    "R", "右转");

        // Stop button: just send 'S' on click
        btnStop.setOnClickListener(v -> {
            sendCommand("S");
            tvCommand.setText("停止");
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDirectionButton(MaterialButton btn, String cmd, String label) {
        btn.setOnTouchListener((v, event) -> {
            if (!isConnected) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendCommand(cmd);
                    tvCommand.setText(label);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sendCommand("S");
                    tvCommand.setText("待命");
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    private void setControlsEnabled(boolean enabled) {
        btnForward.setEnabled(enabled);
        btnBackward.setEnabled(enabled);
        btnLeft.setEnabled(enabled);
        btnRight.setEnabled(enabled);
        btnStop.setEnabled(enabled);
        float alpha = enabled ? 1.0f : 0.4f;
        btnForward.setAlpha(alpha);
        btnBackward.setAlpha(alpha);
        btnLeft.setAlpha(alpha);
        btnRight.setAlpha(alpha);
        btnStop.setAlpha(alpha);
    }

    // ======================== Bluetooth ========================

    @SuppressLint("MissingPermission")
    private void showDeviceList() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的设备，请先在系统设置中配对 JDY-31", Toast.LENGTH_LONG).show();
            return;
        }

        List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        String[] deviceNames = new String[deviceList.size()];
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice d = deviceList.get(i);
            deviceNames[i] = d.getName() + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("选择蓝牙设备")
                .setItems(deviceNames, (dialog, which) -> {
                    connectDevice(deviceList.get(which));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void connectDevice(BluetoothDevice device) {
        appendLog("正在连接 " + device.getName() + "...");
        tvStatus.setText("连接中...");
        btnConnect.setEnabled(false);

        new Thread(() -> {
            try {
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                btSocket.connect();

                outputStream = btSocket.getOutputStream();
                inputStream = btSocket.getInputStream();
                isConnected = true;

                // Start receiving thread
                startReceiving();

                mainHandler.post(() -> {
                    tvStatus.setText("已连接: " + device.getName());
                    statusDot.setBackgroundResource(R.drawable.dot_green);
                    btnConnect.setText("断开连接");
                    btnConnect.setEnabled(true);
                    setControlsEnabled(true);
                    appendLog("✓ 已连接 " + device.getName());
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    tvStatus.setText("连接失败");
                    btnConnect.setEnabled(true);
                    appendLog("✗ 连接失败: " + e.getMessage());
                });
                closeSocket();
            }
        }).start();
    }

    private void disconnect() {
        receiving = false;
        closeSocket();
        isConnected = false;
        tvStatus.setText("未连接");
        statusDot.setBackgroundResource(R.drawable.dot_red);
        btnConnect.setText("连接蓝牙");
        tvCommand.setText("待命");
        setControlsEnabled(false);
        appendLog("已断开连接");
    }

    private void closeSocket() {
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (btSocket != null) btSocket.close();
        } catch (IOException ignored) {}
        outputStream = null;
        inputStream = null;
        btSocket = null;
    }

    private void sendCommand(String cmd) {
        if (!isConnected || outputStream == null) return;
        new Thread(() -> {
            try {
                outputStream.write(cmd.getBytes());
                outputStream.flush();
                mainHandler.post(() -> appendLog("发送: " + cmd));
            } catch (IOException e) {
                mainHandler.post(() -> {
                    appendLog("发送失败: " + e.getMessage());
                    disconnect();
                });
            }
        }).start();
    }

    private void startReceiving() {
        receiving = true;
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (receiving && isConnected) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        int len = inputStream.read(buffer);
                        if (len > 0) {
                            String data = new String(buffer, 0, len);
                            mainHandler.post(() -> appendLog("收: " + data.trim()));
                        }
                    }
                    Thread.sleep(50);
                } catch (IOException e) {
                    if (receiving) {
                        mainHandler.post(() -> {
                            appendLog("接收断开");
                            disconnect();
                        });
                    }
                    break;
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    // ======================== Log ========================

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private int logLineCount = 0;

    private void appendLog(String msg) {
        String time = sdf.format(new Date());
        String line = time + " " + msg;
        if (logLineCount == 0) {
            tvLog.setText(line);
        } else {
            tvLog.append("\n" + line);
        }
        logLineCount++;
        // Keep last 50 lines
        if (logLineCount > 50) {
            String text = tvLog.getText().toString();
            int idx = text.indexOf('\n');
            if (idx >= 0) {
                tvLog.setText(text.substring(idx + 1));
            }
            logLineCount = 50;
        }
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ======================== Permissions ========================

    private void checkPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要蓝牙权限才能使用", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        receiving = false;
        closeSocket();
    }
}
