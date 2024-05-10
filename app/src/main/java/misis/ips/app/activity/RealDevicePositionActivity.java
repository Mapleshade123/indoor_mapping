package misis.ips.app.activity;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.misis.ips.app.R;

import io.realm.RealmConfiguration;
import misis.ips.app.adapter.RealDevicePositionAdapter;
import misis.ips.app.util.Location;
import misis.ips.app.util.RealDevice;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.exceptions.RealmPrimaryKeyConstraintException;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class RealDevicePositionActivity extends AppCompatActivity implements OnMapReadyCallback
{
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_BLUETOOTH = 1;
    private static final String TAG = "Device_Connection";
    private final static UUID BATTERY_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL= UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    private FloatingActionButton scanBLEButton, saveLocation;
    private BluetoothAdapter mBluetoothAdapter;
    private DecimalFormat df = new DecimalFormat("#.###");
    private RealDevicePositionAdapter realDevicePositionAdapter;
    private RecyclerView recyclerView;
    private RealmConfiguration realmConfiguration;

    private ConstraintLayout constraintLayout;
    private String fId;
    private Realm realm;
    private MapView mapView;
    private GoogleMap gMap;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Realm.init(this);
        realmConfiguration = new RealmConfiguration.Builder().allowWritesOnUiThread(true).build();
        realm = Realm.getInstance(realmConfiguration);
        setContentView(R.layout.activity_real_device_position);

        mapView = findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        constraintLayout = findViewById(R.id.mainLocationLayout);
        scanBLEButton = findViewById(R.id.scanBLEButton);
        saveLocation= findViewById(R.id.saveLocation);
        recyclerView = findViewById(R.id.recycleViewRLocation);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        setAll();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, PERMISSION_REQUEST_BLUETOOTH);
        }
        else
        {
            mBluetoothAdapter.startDiscovery();
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mReceiver, filter);
        }

        scanBLEButton.setOnClickListener(new View.OnClickListener()
        {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View view) {
                Snackbar.make(view, R.string.search_devices, Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
                {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, PERMISSION_REQUEST_BLUETOOTH);
                }
                else
                {

                    scanDevice();
                }



            }
        });

        saveLocation.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                determineLocation();
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
        }

        if (!isLocationPermissionGranted())
        {
            askLocationPermission();
        }


    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        if (gMap != null) {
            // Optionally set a default location to zoom to
            LatLng defaultLocation = new LatLng(55.72, 37.60);
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));
            gMap.addMarker(new MarkerOptions().position(defaultLocation).title("Москва"));
        } else {
            Log.e(TAG, "Error: Google Map was null");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }


    public void updateLocationOnMap(double x, double y) {
        LatLng position = new LatLng(y, x); // Обратите внимание на порядок: сначала широта, затем долгота
        gMap.clear(); // Очищаем все маркеры
        gMap.addMarker(new MarkerOptions().position(position).title("Current Location"));
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 18)); // Масштабирование карты
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case PERMISSION_REQUEST_BLUETOOTH:
                Log.d(TAG, "Result");
                scanDevice();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case PERMISSION_REQUEST_COARSE_LOCATION:
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(TAG, "Location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this
                    );
                    builder.setTitle(this.getResources().getString(R.string.offline));
                    builder.setMessage(this.getResources().getString(R.string.notPermitted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }


    private void askLocationPermission() {
        // Android M Location Permission check
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(this.getResources().getString(R.string.accessLocation));
            builder.setMessage(this.getResources().getString(R.string.accessBLE));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);

                }
            });
            builder.show();
        }
    }


    private boolean isLocationPermissionGranted() {
        return this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void scanDevice()
    {
        mBluetoothAdapter.startDiscovery();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
    }
    // Device scan callback.

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                double rssiDouble = (double) rssi; // Cast RSSI to double for any calculations

                Log.d(TAG, "Beacon found: " + device.getAddress() + " with RSSI: " + rssi);
                realDevicePositionAdapter.addDevice(device);
                realDevicePositionAdapter.addBatteryLevel(device, 99); // Example battery level update
                realDevicePositionAdapter.addRssi(device, rssiDouble);
                realDevicePositionAdapter.notifyDataSetChanged();

                handleBeaconDetected(device, rssiDouble); // Handle additional beacon processing
            }
        }
    };
    private void handleBeaconDetected(BluetoothDevice device, double rssi) {
        final double distance = calculateDistance(rssi, 2402); // Assuming 2402 MHz for Bluetooth
        final String currentDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        realm.executeTransactionAsync(bgRealm -> {
                    RealDevice realDevice = bgRealm.where(RealDevice.class)
                            .equalTo("macAddress", device.getAddress())
                            .findFirst();

                    if (realDevice == null) {
                        realDevice = bgRealm.createObject(RealDevice.class, UUID.randomUUID().toString());
                        realDevice.setMacAddress(device.getAddress());
                    }

                    realDevice.setRssi(rssi);
                    realDevice.setDistance(distance);
                    realDevice.setMeasureId(fId); // Ensure MeasureID is set here
                    realDevice.setCreatedAt(currentDate);
                    realDevice.setX(determineX(device.getAddress()));
                    realDevice.setY(determineY(device.getAddress()));

                    // Optionally log the successful operation
                    Log.d(TAG, "RealDevice updated or created with MeasureID: " + fId);
                }, () -> Log.d(TAG, "Transaction successful! MeasureID set."),
                error -> Log.e(TAG, "Transaction failed!", error));
    }

    public double determineX(String deviceAddress)
    {
        double x = 0.0;
        if(deviceAddress.trim().contains("DD:21:F7")) //iBeacon1-origin
            x=0.0;
        else if(deviceAddress.trim().contains("CA:1E:75")) //iBeacon2
            x=2.4;
        else if(deviceAddress.trim().contains("E4:86:E5")) //iBeacon3
            x=2.5;
        else if(deviceAddress.trim().contains("FF:0F:D9")) //iBeacon4
            x=2.5;
        return  x;
    }

    public double determineY(String deviceAddress)
    {
        double y = 0.0;
        if(deviceAddress.trim().contains("DD:21:F7")) //iBeacon1-origin
            y=0.0;
        else if(deviceAddress.trim().contains("CA:1E:75")) //iBeacon2
            y=1.08;
        else if(deviceAddress.trim().contains("E4:86:E5")) //iBeacon3
            y=0.0;
        else if(deviceAddress.trim().contains("FF:0F:D9")) //iBeacon4
            y=2.8;
        return y;
    }


    private void setAll()
    {
        createNewMeasureID();
        realDevicePositionAdapter = new RealDevicePositionAdapter(getApplicationContext());
        recyclerView.setAdapter(realDevicePositionAdapter);

    }

    public double[] calculateGeoLocation(double deviceX, double deviceY) {
        double[][] beaconLocalCoords = {
                {2.5, 1.08}, // Маяк 1
                {2.4, 0.0}, // Маяк 2
                {0.0, 0.0} // Маяк 3
        };

        double[][] beaconGeoCoords = {
                {55.713045, 37.368939}, // Широта и долгота маяка 1
                {55.712923, 37.368565}, // Широта и долгота маяка 2
                {55.712828, 37.368540} // Широта и долгота маяка 3
        };

        // Находим коэффициенты для барицентрических координат
        RealMatrix matrix = new Array2DRowRealMatrix(new double[][] {
                {beaconLocalCoords[0][0], beaconLocalCoords[1][0], beaconLocalCoords[2][0]},
                {beaconLocalCoords[0][1], beaconLocalCoords[1][1], beaconLocalCoords[2][1]},
                {1, 1, 1}
        });

        DecompositionSolver solver = new LUDecomposition(matrix).getSolver();

        // Столбец свободных членов
        RealMatrix constants = new Array2DRowRealMatrix(new double[] {deviceX, deviceY, 1});

        // Решаем систему
        double[] barycentric = solver.solve(constants).getColumn(0);

        // Используем барицентрические координаты для определения географического положения
        double lat = barycentric[0] * beaconGeoCoords[0][0] + barycentric[1] * beaconGeoCoords[1][0] + barycentric[2] * beaconGeoCoords[2][0];
        double lon = barycentric[0] * beaconGeoCoords[0][1] + barycentric[1] * beaconGeoCoords[1][1] + barycentric[2] * beaconGeoCoords[2][1];

        return new double[] {lat, lon};
    }
    public void determineLocation()
    {
        RealDevice lastDevice = realm.where(RealDevice.class).sort("createdAt", Sort.DESCENDING).contains("createdAt", "05-2024").findFirst();
        if (lastDevice == null) {
            Toast.makeText(this, "Данные по устройству отсутствуют.", Toast.LENGTH_SHORT).show();
            return; // Exit the method early
        }
        String lastUUID=lastDevice.getMeasureId();

        RealmResults<RealDevice> result = realm.where(RealDevice.class).equalTo("measureId",lastUUID).findAll();
        RealmResults<RealDevice> rd = realm.where(RealDevice.class).findAll();
        Log.d(TAG, "HI" + result.toString());
        Log.d(TAG, "RD" + rd.toString());

        if (result.size() < 2) {
            Toast.makeText(this, "Требуется 3 маяка для определения местоположения. Найдено: " + result.size(), Toast.LENGTH_SHORT).show();
            return; // Exit if not enough devices are found
        }

        final String currentDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        int size = result.size();
        double[][] positions = new double[size][2];
        double[] distances = new double[size];

        for (int i = 0; i < size; i++)
        {
            if (result.get(i) != null) {
                positions[i][0] = result.get(i).getX();
                positions[i][1] = result.get(i).getY();
            }
            else Log.d(TAG, "0000");
        }

        for (int i = 0; i < size; i++)
        {
            distances[i] = result.get(i).getDistance();
        }
        try
        {
            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
            LeastSquaresOptimizer.Optimum optimum = solver.solve();
            final double[] centroid = optimum.getPoint().toArray();
            final Location calculatedLocation = new Location(UUID.randomUUID().toString(), fId, centroid[0], centroid[1], currentDate);

            realm.executeTransaction(new Realm.Transaction()
            {
                @Override
                public void execute(Realm bgRealm)
                {
                    try
                    {
                        Location location = bgRealm.createObject(Location.class, UUID.randomUUID().toString());
                        location.setLocationX(calculatedLocation.getLocationX());
                        location.setLocationY(calculatedLocation.getLocationY());
                        location.setMeasureId(fId);
                        location.setCreatedAt(currentDate);
                        double[] coords = calculateGeoLocation(centroid[0], centroid[1]);
                        //Toast.makeText(RealDevicePositionActivity.this, "X:" + df.format(centroid[0]) + " Y:" + df.format(centroid[1]) + "Координаты: " + coords[0], Toast.LENGTH_LONG).show();
                        Toast.makeText(RealDevicePositionActivity.this, "Координаты: " + coords[0] + "; " + coords[1], Toast.LENGTH_LONG).show();
                        updateLocationOnMap(coords[1], coords[0]);
                        //updateLocationOnMap(centroid[0], centroid[1]);
                    }
                    catch (RealmPrimaryKeyConstraintException ex)
                    {
                        Toast.makeText(RealDevicePositionActivity.this, ex.getMessage(), Toast.LENGTH_SHORT).show();

                    }


                }

            });
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();

        }
        createNewMeasureID();
    }

    private void createNewMeasureID()
    {
        fId = UUID.randomUUID().toString();
        SharedPreferences pref = getSharedPreferences("KEY", 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("measureId", fId); // Storing string
        editor.apply();
        Toast.makeText(this, "Новый сеанс создан", Toast.LENGTH_SHORT).show();
    }

    public double calculateDistance(double signalLevelInDb, double freqInMHz) {
        double exp = (27.55 - (20 * Math.log10(freqInMHz)) + Math.abs(signalLevelInDb)) / 20.0;
        return Math.pow(10.0, exp);

        //Resource: https://en.wikipedia.org/wiki/Free-space_path_loss#Free-space_path_loss_in_decibels
    }

    @Override
    protected void onDestroy()
    {
        unregisterReceiver(mReceiver);
        super.onDestroy();
        mapView.onDestroy();
        realm.close();
    }
}