package com.example.adxl345;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.MovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int REQUEST_CODE_LOC  = 1;

    private static final int REQ_ENABLE_BT    = 10;
    public static final int BT_BOUNDED        = 21;
    public static final int BT_SEARCH         = 22;
   // private static final long DELAY_TIMER = 100;


    private FrameLayout frameMessage;
    private LinearLayout frameControls;

    private RelativeLayout frameDataControls;
    private Button btnDisconnect;
    private Switch switchGetting;
    private EditText etConsole;


    private Switch switchEnableBt;
    private Button btnEnableSearch;
    private ProgressBar pbProgress;
    private ListView listBtDevices;

    private BluetoothAdapter bluetoothAdapter;
    private BtListAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private ProgressDialog progressDialog;

    private GraphView gvGraph;
    private LineGraphSeries seriesX;
    private LineGraphSeries seriesY;
    private LineGraphSeries seriesZ;

    private String lastSensorValues = "";

    private Handler handler;
    private Runnable timer;

    private double xLastValue = 0.0;
    private double yLastValue = 0.0;
    private double zLastValue = 0.0;

    private SettingsModel preference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameMessage      = findViewById(R.id.frame_message);
        frameControls     = findViewById(R.id.frame_control);

        switchEnableBt    = findViewById(R.id.switch_enable_bt);
        btnEnableSearch   = findViewById(R.id.btn_enable_search);
        pbProgress        = findViewById(R.id.pb_progress);
        listBtDevices     = findViewById(R.id.lv_bt_device);

        frameDataControls = findViewById(R.id.frameDataControls);
        btnDisconnect     = findViewById(R.id.btn_disconnect);
        switchGetting     = findViewById(R.id.start_data_receiving);
        etConsole         = findViewById(R.id.et_console);

        gvGraph = findViewById(R.id.gv_graph);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        preference = new SettingsModel(this);

        btnDisconnect.setOnClickListener(this);
        switchGetting.setOnCheckedChangeListener(this);

        bluetoothDevices = new ArrayList<>();

        seriesX = new LineGraphSeries();
        seriesX.setColor(Color.RED);
        seriesX.setThickness(3);
        seriesY = new LineGraphSeries();
        seriesY.setColor(Color.BLUE);
        seriesY.setThickness(3);
        seriesZ = new LineGraphSeries();
        seriesZ.setColor(Color.GREEN);
        seriesZ.setThickness(3);

        gvGraph.addSeries(seriesX);
        gvGraph.addSeries(seriesY);
        gvGraph.addSeries(seriesZ);
        gvGraph.getViewport().setMinX(0);
        gvGraph.getViewport().setMaxX(preference.getPointsCount());
        gvGraph.getViewport().setXAxisBoundsManual(true);
        gvGraph.getViewport().setMinY(-5);
        gvGraph.getViewport().setMaxY(5);
        gvGraph.getViewport().setYAxisBoundsManual(true);
        gvGraph.setVisibility(View.GONE);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(R.string.connecting));
        progressDialog.setMessage(getString(R.string.wait));

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        if (bluetoothAdapter == null) {
            Toast.makeText( this, getString(R.string.Bluetooth_is_not_available), Toast.LENGTH_SHORT).show();
            Log.d(TAG,"onCreate: " + getString(R.string.Bluetooth_is_not_available));
            finish();
        }

        if (bluetoothAdapter.isEnabled()) {
            showFrameControls();
            switchEnableBt.setChecked(true);
            setListAdapter(BT_BOUNDED);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.item_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectedThread != null) {
            startTimer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
        unregisterReceiver(receiver);

        if (connectThread != null) {
            connectThread.cancel();
        }
        if (connectedThread != null) {
            connectedThread.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(btnEnableSearch)) {
            enableSearch();
        } else if (v.equals(btnDisconnect)) {
            cancelTimer();
            if (connectedThread != null) {
                connectedThread.cancel();
            }
            if (connectThread != null) {
                connectThread.cancel();
            }
            showFrameControls();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.equals(listBtDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start();

                startTimer();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);

            if (!isChecked) {
                showFrameMessage();
            }
        } else if (buttonView.equals(switchGetting)) {
            if (gvGraph.getVisibility() == View.GONE) {
                showGraphUi();
                switchGetting.setText("Спрятать график");
            } else {
                hideGraphUi();
                switchGetting.setText("Показать график");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameControls();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                enableBt(true);
            }
        }
    }

    private void showFrameMessage() {
        frameMessage.setVisibility(View.VISIBLE);
        frameControls.setVisibility(View.GONE);
        frameDataControls.setVisibility(View.GONE);
    }

    private void showFrameControls() {
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.VISIBLE);
        frameDataControls.setVisibility(View.GONE);
    }

    private void showFrameDataControls() {
        frameDataControls.setVisibility(View.VISIBLE);
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void enableBt (boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void setListAdapter (int type) {

        bluetoothDevices.clear();
        int iconType = R.drawable.ic_bluetooth_bounded_device;

        switch(type) {
            case BT_BOUNDED:
                bluetoothDevices = getBoundedDevices();
                break;
            case BT_SEARCH:
                iconType = R.drawable.ic_bluetooth_search_device;
                break;
        }
        listAdapter = new BtListAdapter(this, bluetoothDevices, iconType);
        listBtDevices.setAdapter(listAdapter);
    }

    private ArrayList<BluetoothDevice> getBoundedDevices () {
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();

        if (deviceSet.size() > 0) {
            for (BluetoothDevice device: deviceSet)
                if (!tmpArrayList.contains(device)) {
                     tmpArrayList.add(device);
                }
        }

        return tmpArrayList;
    }

    private void enableSearch () {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED: {
                    btnEnableSearch.setText(R.string.stop_searching);
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;
                }
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                    btnEnableSearch.setText(R.string.start_searching);
                    pbProgress.setVisibility(View.GONE);
                    break;
                }
                case BluetoothDevice.ACTION_FOUND: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        if (!bluetoothDevices.contains(device)) {
                            bluetoothDevices.add(device);
                            listAdapter.notifyDataSetChanged();
                        }
                    }
                    break;
                }
            }
        }
    };

    private void accessLocationPermission() {
        int accessCoarseLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int accessFineLocation   = this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:

                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                    //TODO - Add your code here to start Discovery
                }
                break;
            default:
                return;
        }
    }

    private class ConnectThread extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;

        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device,1);

                progressDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;

                progressDialog.dismiss();
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Cannot to connect", Toast.LENGTH_SHORT).show();
                    }
                });

                cancel();
            }

            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameDataControls();
                    }
                });
            }
        }

        public boolean isConnected () {
            return bluetoothSocket.isConnected();
        }

        public void cancel() {
            try {
                Log.d(TAG, "cancel: " + this.getClass().getSimpleName());
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {

        private final InputStream inputStream;
        private final OutputStream outputStream;

        private boolean isConnected = false;

        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.inputStream = inputStream;
            this.outputStream = outputStream;
            isConnected = true;
        }

        @Override
        public void run() {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            StringBuffer buffer = new StringBuffer();
            final StringBuffer sbConsole = new StringBuffer();
            final ScrollingMovementMethod movementMethod = new ScrollingMovementMethod();

            while (isConnected) {
                try {
                    int bytes = bis.read();
                    buffer.append((char) bytes);
                    int eof = buffer.indexOf("\r\n");

                    if (eof > 0) {
                        sbConsole.append(buffer.toString());
                        lastSensorValues = buffer.toString();
                        buffer.delete(0, buffer.length());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                bis.close();
                cancel();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void write(String command) {
            byte[] bytes = command.getBytes();
            if (outputStream != null) {
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                isConnected = false;
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void showGraphUi () {
        gvGraph.setVisibility(View.VISIBLE);
    }
    private void hideGraphUi () {
        gvGraph.setVisibility(View.GONE);
    }

    private HashMap<String, String> parseData (String data) {
        if (data.indexOf(':') > 0) {
            HashMap<String, String> map = new HashMap<String, String>();
            String[] pairs = data.split("\\t+");
            for (String pair: pairs) {
                String[] keyValue = pair.split("\\W\\s");
                map.put(keyValue[0], keyValue[1]);
            }
            return map;
        }
        return null;
    }

    private void startTimer () {
            cancelTimer();
            handler = new Handler();
            final ScrollingMovementMethod movementMethod = new ScrollingMovementMethod();
            handler.postDelayed(timer = new Runnable() {
                @Override
                public void run() {
                    etConsole.setText(lastSensorValues);
                    etConsole.setMovementMethod(movementMethod);

                    HashMap<String, String> dataSensor = parseData(lastSensorValues);
                    try {
                        if (dataSensor != null) {
                            if (dataSensor.containsKey("X")) {
                                double temp = 0.00;
                                double tempY = 0.00;
                                double tempZ = 0.00;

                                try {
                                    temp = Double.parseDouble(dataSensor.get("X"));
                                    tempY = Double.parseDouble(dataSensor.get("Y"));
                                    tempZ = Double.parseDouble(dataSensor.get("Z"));
                                } catch (Exception e) {
                                    Log.e(TAG, e.toString());
                                }
                                if (preference.isCheckBoxX()) {
                                    seriesX.appendData(new DataPoint(xLastValue, temp), true, preference.getPointsCount());
                                }
                                if (preference.isCheckBoxY()) {
                                    seriesY.appendData(new DataPoint(yLastValue, tempY), true, preference.getPointsCount());
                                }
                                if (preference.isCheckBoxZ()) {
                                    seriesZ.appendData(new DataPoint(zLastValue, tempZ), true, preference.getPointsCount());
                                }
                            }
                            xLastValue += 1;
                            yLastValue += 1;
                            zLastValue += 1;
                        }
                    } catch (final Exception e) {
                        Log.e(TAG, "Err: ", e);
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    handler.postDelayed(this, preference.getDelayTimer());
                }
            }, preference.getDelayTimer());
    }

    private void cancelTimer () {
        if (handler != null && gvGraph.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(timer);
        }
    }
}