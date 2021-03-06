package tfg.ucm.com.tfgparkinson.Activities;

import android.Manifest;

import android.app.AlertDialog;
import android.app.ProgressDialog;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.os.Handler;

import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.text.Html;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tfg.ucm.com.tfgparkinson.Clases.BLE.IMultiBLEAccelServiceDelegate;
import tfg.ucm.com.tfgparkinson.Clases.BLE.MultiBLEService;
import tfg.ucm.com.tfgparkinson.Clases.utils.OpcionesVO;
import tfg.ucm.com.tfgparkinson.R;

public class EmparejarSensores extends AppCompatActivity implements IMultiBLEAccelServiceDelegate {

    private static final String TAG = Main.class.getSimpleName();
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 1001;

    private Context mContext;
    private MultiBLEService mMultiBleService;
    private List<Map<String, String>> mDevicesData;

    private ListView mDevicesListView;
    private TextView mTextStatus;
    private Button mButtonScan;
    private Button mButtonDisconnect;

    private boolean hexiwear;
    private boolean noHexiwear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emparejar_sensores);

        Intent intent = getIntent();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FloatingActionButton ayuda = (FloatingActionButton) findViewById(R.id.ayuda);
        ayuda.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mostrarAyuda();
            }
        });

        initVariables((OpcionesVO) intent.getSerializableExtra("options"));
        checkForPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Enable Bluetooth Service if it's not enabled yet.
        if (mMultiBleService.getBluetoothAdapter() == null
                || !mMultiBleService.getBluetoothAdapter().isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(intent);
        }

        // Check for BLE Support.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            String message = getResources().getString(R.string.error_no_ble_support);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMultiBleService.disconnectFromDevices();
    }

    private void initVariables(OpcionesVO options) {
        mContext = EmparejarSensores.this;
        mMultiBleService = new MultiBLEService(mContext, options); //crear hashMap y pasarlo como el segundo parametro
        mTextStatus = (TextView) findViewById(R.id.main_text_status);
        mButtonScan = (Button) findViewById(R.id.main_button_scan);
        mButtonScan.setOnClickListener(showAvailableDevices);
        mButtonDisconnect = (Button) findViewById(R.id.main_button_disconnect);
        mButtonDisconnect.setOnClickListener(disconnectFromDevices);
        mDevicesListView = (ListView) findViewById(R.id.main_list_devices);
    }

    /*
     * ListView with Item showing the device's name and mac address,
     * and SubItem showing the accelerometer's x, y and z values.
     * Parameter gatts is the list containing the connected devices.
     */
    private void addItemsToList(ArrayList<BluetoothGatt> gatts) {
        mDevicesListView = (ListView) findViewById(R.id.main_list_devices);
        mDevicesData = new ArrayList<>();

        for (int i = 0; i < gatts.size(); i++) {
            Map<String, String> values = new HashMap<>(2);
            values.put("name", String.format("%s - %s",
                    gatts.get(i).getDevice().getName(), gatts.get(i).getDevice().getAddress()));
            // Empty value for accelerometer until it's sensor is receiving data
            values.put("accelerometer", "Accelerometer and gyroscope not available");
            mDevicesData.add(values);
        }

        SimpleAdapter adapter = new SimpleAdapter(this,
                mDevicesData,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "accelerometer"},
                new int[]{android.R.id.text1, android.R.id.text2});

        mDevicesListView.setAdapter(adapter);
    }

    private void checkForPermissions() {
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_LOCATION_REQUEST_CODE);
            mMultiBleService.setupBluetoothConnection();
        } else {
            mMultiBleService.setupBluetoothConnection();
        }
    }

    public boolean hasPermissions(final Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showAvailableBleDevices() {
        String title = getResources().getString(R.string.dialog_title_select_devices);
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(EmparejarSensores.this);
        dialogBuilder.setTitle(title);

        // ArrayList to keep the selected devices
        final ArrayList<Integer> selectedItems = new ArrayList<>();
        final ArrayList<String> devicesList = new ArrayList<>();

        // Get the list of available devices
        for (int i = 0; i < mMultiBleService.getBluetoothDevices().size(); i++) {
            BluetoothDevice device = mMultiBleService.getBluetoothDevices().valueAt(i);
            devicesList.add(String.format("%s %s", device.getName(), device.getAddress()));
        }
        CharSequence[] devicesArray = devicesList.toArray(new CharSequence[devicesList.size()]);

        // Create alert dialog with multi-choice items
        dialogBuilder.setMultiChoiceItems(devicesArray, null,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            // If the user checked the item, add it to the selected items
                            if(devicesList.get(indexSelected).contains("HEXIWEAR"))
                                hexiwear = true;
                            else
                                noHexiwear = true;

                            selectedItems.add(indexSelected);
                        } else if (selectedItems.contains(indexSelected)) {
                            // Else, if the item is already in the array, remove it
                            selectedItems.remove(Integer.valueOf(indexSelected));
                        }
                    }
                }).setPositiveButton(getString(R.string.action_connect),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Save the selected items' references
                        ArrayList<BluetoothDevice> selectedDevices = new ArrayList<>();
                        for (int i = 0; i < selectedItems.size(); i++) {
                            selectedDevices.add(mMultiBleService.getBluetoothDevices()
                                    .valueAt(selectedItems.get(i)));
                        }

                        if(noHexiwear && !hexiwear) {
                            // Connect with the devices
                            mMultiBleService.connectToDevices(selectedDevices);
                            mButtonScan.setEnabled(false);
                            mButtonDisconnect.setEnabled(true);
                            dialog.dismiss();
                        } else if (hexiwear && !noHexiwear) {
                            Intent i = new Intent(EmparejarSensores.this, EscaneoHexiWear.class);
                            i.putExtra("devices", selectedDevices);
                            startActivity(i);
                        } else {
                            Toast.makeText(EmparejarSensores.this, "Los sensores deben ser del mismo tipo", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        }).create();
        dialogBuilder.show();
    }

    /*****
     * LISTENERS.
     *****/
    private Button.OnClickListener showAvailableDevices = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String title = mContext.getResources().getString(R.string.dialog_title_inr_scan);
            String message = mContext.getResources().getString(R.string.action_scanning_devices);
            final ProgressDialog progressDialog =
                    ProgressDialog.show(EmparejarSensores.this,
                            title,
                            message,
                            true);

            mMultiBleService.disconnectFromDevices();
            mMultiBleService.startScan(new Runnable() {
                @Override
                public void run() {
                    mMultiBleService.stopScan();
                    progressDialog.dismiss();
                    showAvailableBleDevices();
                }
            });
        }
    };

    private Button.OnClickListener disconnectFromDevices = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mMultiBleService.disconnectFromDevices();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    // Wait 200 milliseconds until accelerometer's is not sending more data
                    mDevicesData.clear();
                    ((BaseAdapter) mDevicesListView.getAdapter()).notifyDataSetChanged();
                }
            }, 200);

            mButtonDisconnect.setEnabled(false);
            mButtonScan.setEnabled(true);
            mTextStatus.setText(getString(R.string.no_connected_devices));
            mTextStatus.setTextColor(getResources().getColor(R.color.colorRed));
        }
    };

    @Override
    public void updateAccelerometerValues(BluetoothGatt gatt, float accelX, float accelY, float accelZ,
                                          float gyroX, float gyroY, float gyroZ) {

        if (mMultiBleService.getSelectedDevices() != null) {
            // Get the position of the device in the connected devices' list
            int position = mMultiBleService.getSelectedDevices().indexOf(gatt.getDevice());

            // Update the accelerometer's value in the device's data and notify the listView adapter.
            mDevicesData.get(position).put("accelerometer", String.format(Locale.getDefault(),
                    "Accel. values: %f, %f, %f\n Gyro. values: %f, %f, %f", accelX, accelY, accelZ, gyroX, gyroY, gyroZ));

            ((BaseAdapter) mDevicesListView.getAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    public void updateConnectedDevices(ArrayList<BluetoothGatt> gatts) {
        // Update the number of connected devices
        String message;
        if (gatts.size() == 1) {
            message = getString(R.string.connected_device);
        } else {
            message = getString(R.string.connected_devices);
        }

        mTextStatus.setText(String.format(Locale.getDefault(),
                "%d %s", gatts.size(), message));
        mTextStatus.setTextColor(getResources().getColor(R.color.colorGreen));

        // Add the devices to the listView
        addItemsToList(gatts);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private void mostrarAyuda() {
        android.support.v7.app.AlertDialog.Builder dialogBuilder = new android.support.v7.app.AlertDialog.Builder(new ContextThemeWrapper(this, R.style.dialogo));
        dialogBuilder.setTitle("Ayuda emparejar");
        dialogBuilder.setMessage(Html.fromHtml("<p align=\"justify\">" +
                "- Para emparejar con los sensores pulse en <strong>escanear</strong>. <br><br>" +
                "- Una vez localizado su sensor <strong>marque el cuadrado correspondiente y pulse en conectar</strong>.<br><br>" +
                "- <strong>Importante: deberán estar activados el bluetooth y la localización.</strong></p>"));

        dialogBuilder.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        android.support.v7.app.AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();
    }
}
