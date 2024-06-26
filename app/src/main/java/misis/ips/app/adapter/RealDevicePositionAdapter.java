package misis.ips.app.adapter;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.misis.ips.app.R;
import misis.ips.app.filters.KalmanFilter;
import misis.ips.app.util.RealDevice;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import io.realm.Realm;
import io.realm.exceptions.RealmPrimaryKeyConstraintException;


public class RealDevicePositionAdapter extends RecyclerView.Adapter<RealDevicePositionAdapter.ViewHolder> {
    private ArrayList<BluetoothDevice> deviceList;
    private static final String TAG = "Device_Connection";
    private HashMap<BluetoothDevice, Double> hashRssiMap;
    private HashMap<BluetoothDevice, Double> hashTxPowerMap;
    private HashMap<BluetoothDevice, Integer> hashBatteryLevel;
    private HashMap<String, KalmanFilter> mKalmanFilters;
    private DecimalFormat df2 = new DecimalFormat("#.####");
    private Realm realm;
    private Context context;
    private static final double KALMAN_R = 0.5d;
    private static final double KALMAN_Q = 0.125d;

    public RealDevicePositionAdapter(Context context)
    {
        this.context=context;
        deviceList = new ArrayList<BluetoothDevice>();
        hashRssiMap = new HashMap<BluetoothDevice, Double>();
        hashTxPowerMap = new HashMap<BluetoothDevice, Double>();
        hashBatteryLevel = new HashMap<BluetoothDevice, Integer>();
        mKalmanFilters=new HashMap<String,KalmanFilter>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.real_device_list, parent, false);
        return new ViewHolder(view);
    }

    public void addDevice(BluetoothDevice device)
    {
        if (!deviceList.contains(device))
        {
            if(device.getAddress().equals("DD:21:F7:8D:98:05")
            ||device.getAddress().equals("CA:1E:75:F7:A7:1E")
            ||device.getAddress().equals("E4:86:E5:26:1F:45")
            ||device.getAddress().equals("6D:F3:03:9A:24:E3")
            ||device.getAddress().equals("E8:7F:95:5F:35:74"))
                deviceList.add(device);

        }
    }

    public void addRssi(BluetoothDevice device, double rssi)
    {
        double smoothedRssi;
        if (deviceList.contains(device))
        {

            if (mKalmanFilters.keySet().contains(device.getAddress()))
            {
                KalmanFilter mKalman = mKalmanFilters.get(device.getAddress());

                // This will give you a smoothed RSSI value because 'x == lastRssi'
                smoothedRssi = mKalman.applyFilter(rssi);
                Log.i(TAG, "Old Rssi: " + rssi + "Smoothed RSSI: " + smoothedRssi);
                // Do what you want with this rssi
            }
            else
            {
                KalmanFilter mKalman = new KalmanFilter(KALMAN_R, KALMAN_Q);
                smoothedRssi = mKalman.applyFilter(rssi);
                mKalmanFilters.put(device.getAddress(), mKalman);
                Log.i(TAG, "Old Rssi: " + rssi + "Smoothed RSSI: " + smoothedRssi);
            }

            hashRssiMap.put(device, smoothedRssi);
        }
    }


    public void addBatteryLevel(BluetoothDevice device, Integer batteryLevel)
    {
        if (deviceList.contains(device))
        {
            hashBatteryLevel.put(device, batteryLevel);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, @SuppressLint("RecyclerView") final int position)
    {
        final BluetoothDevice device = deviceList.get(position);
        holder.deviceMac.setText(context.getString(R.string.ble_device_name,device.getAddress()+" - " +determineDeviceName(device.getAddress())));
        holder.deviceRssi.setText(context.getString(R.string.ble_rssi,hashRssiMap.get(device)));
        holder.deviceBattery.setText("%"+context.getString(R.string.ble_battery,hashBatteryLevel.get(device)));
        holder.deviceCoordinates.setText(context.getString(R.string.ble_coordinates,
                                                            determineX(device.getAddress()),
                                                            determineY(device.getAddress())));
        holder.deviceDistance.setText(context.getString(R.string.ble_distance,df2.format(calculateDistance(hashRssiMap.get(device)))));

        holder.cardView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                SharedPreferences pref = context.getSharedPreferences("KEY", 0);
                final String fId = pref.getString("measureId", "not defined"); // getting UUID
                String currentDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
                String macAddress = device.getAddress();
                double distance = calculateDistance(hashRssiMap.get(device));
                int battery=hashBatteryLevel.get(device);
                double x = determineX(device.getAddress());
                double y = determineY(device.getAddress());
                double rssi = hashRssiMap.get(device);


                final RealDevice deviceBLE = new RealDevice(fId, macAddress, distance, x, y, rssi, battery,currentDate);
                realm = Realm.getDefaultInstance();
                realm.executeTransaction(new Realm.Transaction()
                {
                    @Override
                    public void execute(Realm bgRealm) {
                        try {
                            RealDevice device1 = bgRealm.createObject(RealDevice.class, UUID.randomUUID().toString());
                            device1.setMeasureId(fId);
                            device1.setMacAddress(deviceBLE.getMacAddress());
                            device1.setDistance(deviceBLE.getDistance());
                            device1.setX(deviceBLE.getX());
                            device1.setY(deviceBLE.getY());
                            device1.setRssi(deviceBLE.getRssi());
                            device1.setBatteryLevel(deviceBLE.getBatteryLevel());
                            device1.setCreatedAt(deviceBLE.getCreatedAt());
                            //Toast.makeText(context, device.getAddress() + " saved!", Toast.LENGTH_SHORT).show();
                        } catch (RealmPrimaryKeyConstraintException ex) {
                            Toast.makeText(context, "BLE information already exists for this ID!", Toast.LENGTH_SHORT).show();

                        }

                    }
                });

                removeAt(position);
            }
         });

    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceMac;
        TextView deviceRssi;
        TextView deviceDistance;
        TextView deviceCoordinates;
        TextView deviceBattery;
        Button saveButton;
        CardView cardView;
        public ViewHolder(@NonNull View view)
        {
            super(view);
            deviceRssi = view.findViewById(R.id.device_rssi);
            deviceMac = view.findViewById(R.id.device_name);
            deviceDistance = view.findViewById(R.id.device_distance);
            deviceCoordinates=view.findViewById(R.id.device_coordinates);
            deviceBattery = view.findViewById(R.id.deviceBattery);
            saveButton=view.findViewById(R.id.saveLocation);
            cardView=view.findViewById(R.id.cardView);
        }
    }


    public double calculateDistance(double signalLevelInDb)
    {
        double exp = (92.45 - (20 * Math.log10(2.45)) + Math.abs(signalLevelInDb)) / 20.0;
        return Math.pow(10.0, exp);

        //Resource: https://en.wikipedia.org/wiki/Free-space_path_loss#Free-space_path_loss_in_decibels
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
        else if(deviceAddress.trim().contains("48:74:12")) //iBeacon5
            x=0.3;
        else if(deviceAddress.trim().contains("71:DB:5E")) //iBeacon6
            x=2.8;
        else if(deviceAddress.trim().contains("59:E7:99")) //iBeacon7
            x=0.7;
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
        else if(deviceAddress.trim().contains("48:74:12")) //iBeacon5
            y=0.0;
        else if(deviceAddress.trim().contains("71:DB:5E")) //iBeacon6
            y=2.8;
        else if(deviceAddress.trim().contains("59:E7:99")) //iBeacon7
            y=1.7;
        return y;
    }

    public String determineDeviceName(String deviceAddress)
    {
        String name="";
        if(deviceAddress.equals("E4:AA:EA:AE:C6:E4"))
            name= "PC";
        else if(deviceAddress.equals("34:F3:9A:9F:8A:52"))
            name= "Maibenben Laptop";
        else if(deviceAddress.equals("C4:A3:6B:D1:B2:24"))
            name= "MI BAND";
        else if(deviceAddress.equals("18:87:40:70:CC:E6"))
            name= "Samsung Galaxy";
        else if(deviceAddress.equals("18:87:40:75:5C:C4"))
            name= "Xiaomi Redmi Note 9";
        return name;
    }

    public void removeAt(int position)
    {
        deviceList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, deviceList.size());
    }
}