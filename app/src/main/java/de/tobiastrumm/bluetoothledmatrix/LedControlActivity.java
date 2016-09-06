package de.tobiastrumm.bluetoothledmatrix;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.RxBleLog;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import butterknife.BindColor;
import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class LedControlActivity extends RxAppCompatActivity {

    @BindColor(R.color.color_grey)
    int color_grey;
    @BindColor(R.color.color_red)
    int color_red;
    @BindColor(R.color.color_green)
    int color_green;
    @BindColor(R.color.color_yellow)
    int color_yellow;

    private int colors[];
    private int selected_color = 1;

    private final static String SERVICE_UUID = "12345678-1234-5678-1234-56789abc0010";
    private final static String ROW_UUID_BASE = "12345678-1234-5678-1234-56789abc000";
    private final static String TAG = LedControlActivity.class.getSimpleName();
    private final static int SCAN_TIMEOUT = 60; // in seconds
    private final static int REQUEST_ENABLE_BT = 1;

    @BindView(R.id.tv_connection_status)
    TextView tvConnectionStatus;

    @BindViews({R.id.button_0_0, R.id.button_0_1, R.id.button_0_2, R.id.button_0_3, R.id.button_0_4, R.id.button_0_5, R.id.button_0_6, R.id.button_0_7,
                R.id.button_1_0, R.id.button_1_1, R.id.button_1_2, R.id.button_1_3, R.id.button_1_4, R.id.button_1_5, R.id.button_1_6, R.id.button_1_7,
                R.id.button_2_0, R.id.button_2_1, R.id.button_2_2, R.id.button_2_3, R.id.button_2_4, R.id.button_2_5, R.id.button_2_6, R.id.button_2_7,
                R.id.button_3_0, R.id.button_3_1, R.id.button_3_2, R.id.button_3_3, R.id.button_3_4, R.id.button_3_5, R.id.button_3_6, R.id.button_3_7,
                R.id.button_4_0, R.id.button_4_1, R.id.button_4_2, R.id.button_4_3, R.id.button_4_4, R.id.button_4_5, R.id.button_4_6, R.id.button_4_7,
                R.id.button_5_0, R.id.button_5_1, R.id.button_5_2, R.id.button_5_3, R.id.button_5_4, R.id.button_5_5, R.id.button_5_6, R.id.button_5_7,
                R.id.button_6_0, R.id.button_6_1, R.id.button_6_2, R.id.button_6_3, R.id.button_6_4, R.id.button_6_5, R.id.button_6_6, R.id.button_6_7,
                R.id.button_7_0, R.id.button_7_1, R.id.button_7_2, R.id.button_7_3, R.id.button_7_4, R.id.button_7_5, R.id.button_7_6, R.id.button_7_7})
    Button[] buttons;

    @BindView(R.id.sp_color)
    Spinner spColor;

    @BindView(R.id.button_scan_again)
    Button btnScanAgain;

    private RxBleClient rxBleClient;
    private Subscription scanSubscription;
    private RxBleDevice bleDevice;
    private Observable<RxBleConnection> connectionObservable;

    private BluetoothAdapter bluetoothAdapter;

    private LedMatrix ledMatrix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led_control);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);

        colors = new int[]{color_grey, color_red, color_green, color_yellow};

        setupButtonOnClickListener();

        spColor.setAdapter(ArrayAdapter.createFromResource(this, R.array.colors, R.layout.support_simple_spinner_dropdown_item));

        tvConnectionStatus.setText(getString(R.string.connection_status, ""));

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ledMatrix = new LedMatrix();

        rxBleClient = RxBleClientSingelton.getInstance(this);
        RxBleClient.setLogLevel(RxBleLog.DEBUG);
    }

    /**
     * Check if bluetooth is enabled on the device.
     * @return True, if bluetooth is enabled.
     */
    private boolean isBluetoothEnabled(){
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @OnItemSelected(value = R.id.sp_color, callback = OnItemSelected.Callback.ITEM_SELECTED)
    void onSpinnerSelectedItem(int position){
        selected_color = position;
    }

    /**
     * Set up the buttons' OnClickListener
     */
    private void setupButtonOnClickListener(){
        for(int i = 0; i < 64; i++){
            final int row = i/8;
            final int col = i%8;
            buttons[i].setOnClickListener(view -> {
                Log.d(TAG, "Pressed button [" + row + "][" + col + "]");
                writeColorToLedMatrix(row, col, selected_color);
            });
        }
    }

    /**
     * Check if the device is connected to the BLE peripheral
     * @return True if it is connected
     */
    private boolean isConnected() {
        return bleDevice != null && bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }


    /**
     * Set a pixel on the bluetooth led display to a certain color
     * @param row Between 0 and 7
     * @param col Between 0 and 7
     * @param color Between 0 and 3
     */
    private void writeColorToLedMatrix(int row, int col, int color){
        final String row_uuid = ROW_UUID_BASE + Integer.toString(row);
        UUID uuid = UUID.fromString(row_uuid);
        if(isConnected()){
            // Get the modified byte array for that row
            byte[] new_bytes = ledMatrix.setColor(row, col, color);
            // Change the button color
            buttons[8*row + col].setBackgroundColor(colors[color]);
            // Write the modified byte array to the ble peripheral
            connectionObservable
                    .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(uuid, new_bytes))
                    .subscribe(ignoreBytes -> {}, this::onConnectionFailure);
        }
        else{
            Log.d(TAG, "Not connected");
        }
    }


    /**
     * Sets the background color of buttons
     * @param row Row of buttons that should be updated
     * @param bytes Array consisting of two bytes containing the color information for one row.
     */
    private void setButtonColors(int row, byte[] bytes){
        int value[] = new int[8];
        value[0] = (bytes[0] & 0xC0) >> 6;
        value[1] = (bytes[0] & 0x30) >> 4;
        value[2] = (bytes[0] & 0x0C) >> 2;
        value[3] = (bytes[0] & 0x03);
        value[4] = (bytes[1] & 0xC0) >> 6;
        value[5] = (bytes[1] & 0x30) >> 4;
        value[6] = (bytes[1] & 0x0C) >> 2;
        value[7] = (bytes[1] & 0x03);

        for(int i = 0; i<8; i++){
            buttons[8*row + i].setBackgroundColor(colors[value[i]]);
        }
    }

    /**
     * Check if the device is scanning for the BLE device
     * @return True, if device is scanning
     */
    private boolean isScanning(){
        return scanSubscription != null;
    }

    /**
     * Start scanning for the BLE device. If bluetooth is not enabled, it asks the user to enable
     * it.
     */
    @OnClick(R.id.button_scan_again)
    void startScanning(){
        // only start scanning for ble peripheral if bluetooth is enabled.
        if(isBluetoothEnabled()) {
            if (!isScanning()) {
                btnScanAgain.setVisibility(View.INVISIBLE);
                tvConnectionStatus.setText(getString(R.string.connection_status, "Searching for device..."));
                scanSubscription = rxBleClient.scanBleDevices(UUID.fromString(SERVICE_UUID))
                        .compose(bindUntilEvent(ActivityEvent.STOP))           // Unsubscribe on STOP event
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnUnsubscribe(this::clearScanSubscription)
                        .timeout(SCAN_TIMEOUT, TimeUnit.SECONDS)                // Stop scanning if no device was found within SCAN_TIMEOUT
                        .subscribe(this::onDeviceFound, this::onBleScanFailure);
            }
        }
        // if bluetooth is not enabled, ask the user to enable it.
        else{
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check ACCESS_COARSE_LOCATION
        RxPermissions.getInstance(this)
            .request(Manifest.permission.ACCESS_COARSE_LOCATION)
            .subscribe(granted -> {
                if (granted) {
                    // Start scanning if permission is granted.
                    startScanning();
                } else {
                    Log.d(TAG, "coarse location permission denied");
                    tvConnectionStatus.setText(getString(R.string.connection_status, "Location permission is required to scan for BLE devices!"));
                    btnScanAgain.setVisibility(View.INVISIBLE);
                }
            });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK){
                startScanning();
            }
            else{
                tvConnectionStatus.setText(getString(R.string.connection_status, "Bluetooth not enabled"));
            }
        }
    }

    private void onDeviceFound(RxBleScanResult bleScanResult){
        tvConnectionStatus.setText(getString(R.string.connection_status, "Device found. Connecting..."));

        // Stop scanning when the device was found.
        scanSubscription.unsubscribe();

        // Get the RxBleDevice and the Observable<RxConnection>
        bleDevice = bleScanResult.getBleDevice();
        connectionObservable = bleDevice.establishConnection(this, false)
                .compose(bindUntilEvent(ActivityEvent.STOP))            // Unsubscribe on STOP event
                .doOnUnsubscribe(this::clearConnectionObservable)       // Clear the connectionObservable variable when unsubscribing
                .unsubscribeOn(AndroidSchedulers.mainThread())
                .compose(new ConnectionSharingAdapter());               // Necessary to share connection between multiple subscribers


        // Subscribe on main thread because onDeviceConnected changes UI elements.
        connectionObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onDeviceConnected, this::onConnectionFailure);
    }

    /**
     * Handle exceptions that occurred while scanning for BLE devices.
     * @param t Exception
     */
    private void onBleScanFailure(Throwable t){
        if(t instanceof BleScanException){
            handleBleScanException((BleScanException)t);
        }
        else if(!(t instanceof TimeoutException)){
            Log.e(TAG, Log.getStackTraceString(t));
        }
        runOnUiThread(() -> {
            btnScanAgain.setVisibility(View.VISIBLE);
            tvConnectionStatus.setText(getString(R.string.connection_status, "Disconnected"));
        });
    }

    /**
     * Set the scanSubscription variable to null.
     */
    private void clearScanSubscription(){
        scanSubscription = null;
    }

    private void onDeviceConnected(RxBleConnection connection) {
        tvConnectionStatus.setText(getString(R.string.connection_status, "Connected"));
        for(int row = 0; row < 8; row++){
            String row_uuid = ROW_UUID_BASE + Integer.toString(row);
            int temp_row = row;
            connection
                    .readCharacteristic(UUID.fromString(row_uuid))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bytes -> {
                        setButtonColors(temp_row, bytes);
                        ledMatrix.setRow(temp_row, bytes);
                    }, this::onConnectionFailure);
        }
    }

    /**
     * Handle exceptions that occurred while the device was connected
     * @param t Thrown exception
     */
    private void onConnectionFailure(Throwable t) {
        Toast.makeText(this, "Connection error: " + t, Toast.LENGTH_SHORT).show();
        tvConnectionStatus.setText(getString(R.string.connection_status, "error"));
        Log.e(TAG, Log.getStackTraceString(t));
    }

    /**
     * Sets the connectionObservable variable to null.
     */
    private void clearConnectionObservable(){
        connectionObservable = null;
        btnScanAgain.setVisibility(View.VISIBLE);
        tvConnectionStatus.setText(getString(R.string.connection_status, "Disconnected"));
    }

    /**
     * Handle exceptions that occurred while scanning for BLE devices.
     * @param bleScanException Exception
     */
    private void handleBleScanException(BleScanException bleScanException) {

        switch (bleScanException.getReason()) {
            case BleScanException.BLUETOOTH_NOT_AVAILABLE:
                tvConnectionStatus.setText(getString(R.string.connection_status, "Bluetooth not enabled"));
                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_DISABLED:
                tvConnectionStatus.setText(getString(R.string.connection_status, "Bluetooth not enabled"));
                Toast.makeText(this, "Enable bluetooth and try again", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_PERMISSION_MISSING:
                Toast.makeText(this,
                        "On Android 6.0 location permission is required. Implement Runtime Permissions", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_SERVICES_DISABLED:
                Toast.makeText(this, "Location services needs to be enabled on Android 6.0", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_CANNOT_START:
            default:
                Toast.makeText(this, "Unable to start scanning", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_led_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.item_info:
                openInfoActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private void openInfoActivity(){
        Intent intent = new Intent(this,InfoActivity.class);
        startActivity(intent);
    }
}
