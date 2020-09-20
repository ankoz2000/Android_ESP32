package com.example.adxl345;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.MovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
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
    private static final long DELAY_TIMER = 1000;


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

    private Button btnX;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private ProgressDialog progressDialog;

    private GraphView gvGraph;
    private LineGraphSeries series = new LineGraphSeries();

    private String lastSensorValues = "";

    private Handler handler;
    private Runnable timer;

    private int xLastValue = 0;

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

        btnX = findViewById(R.id.get_x);

        gvGraph = findViewById(R.id.gv_graph);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnX.setVisibility(View.GONE);

        btnDisconnect.setOnClickListener(this);
        switchGetting.setOnCheckedChangeListener(this);

        bluetoothDevices = new ArrayList<>();

        gvGraph.addSeries(series);
        gvGraph.getViewport().setMinX(0);
        gvGraph.getViewport().setMaxX(10);
        gvGraph.getViewport().setXAxisBoundsManual(true);
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

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);

            if (!isChecked) {
                showFrameMessage();
            }
        } else if (buttonView.equals(switchGetting)) {
            if (gvGraph.getVisibility() == View.GONE) {
                showGraphs();
            } else {
                hideGraphs();
            }
            //enableReceiving(isChecked);
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

    private void showGraphs () {
        gvGraph.setVisibility(View.VISIBLE);
        btnX.setVisibility(View.VISIBLE);
    }
    private void hideGraphs () {
        gvGraph.setVisibility(View.GONE);
        btnX.setVisibility(View.GONE);
    }

/*    private void enableReceiving(boolean state) {
        if (connectedThread != null && connectThread.isConnected()) {
            String command = "";
            command = (state) ? "off#" : "on#";
            connectedThread.write(command);
        }
    }*/

    private HashMap parseData (String data) {
        if (data.indexOf("X") > 0) {
            HashMap map = new HashMap();
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
            final MovementMethod movementMethod = new ScrollingMovementMethod();
            handler.postDelayed(timer = new Runnable() {
                @Override
                public void run() {
                    etConsole.setText(lastSensorValues);
                    etConsole.setMovementMethod(movementMethod);

                    HashMap dataSensor = parseData(lastSensorValues);
                    if (dataSensor != null) {
                        if (dataSensor.containsKey("X")) {
                            int temp = Integer.parseInt(Objects.requireNonNull(dataSensor.get("X")).toString());
                            //int tempY = Integer.parseInt(dataSensor.get("Y").toString());
                            //int tempZ = Integer.parseInt(dataSensor.get("Z").toString());
                            series.appendData(new DataPoint(xLastValue, temp), true, 10);
                        /*series.appendData(new DataPoint(xLastValue, tempY), true, 40);
                        series.appendData(new DataPoint(xLastValue, tempZ), true, 40);*/
                        }
                        xLastValue += 1;
                    }

                    handler.postDelayed(this, DELAY_TIMER);
                }
            }, DELAY_TIMER);
    }

    private void cancelTimer () {
        if (handler != null && gvGraph.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(timer);
        }
    }
}