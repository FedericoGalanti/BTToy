package com.bttoy.btping;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconTransmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/*
    TODO: works, but on my phone is okay. Don't get it.
    TODO: fix the goddamn notification system for the service
 */

public class MainActivity extends AppCompatActivity {
    protected static final String TAG = "MainActivity";
    protected static final String btpingUUID = "7e6985df-4aa3-4bda-bb8b-9f11bf7077a0";
    protected static final String SCANNING_ACTION = "SCANNING_SERVICE_UPDATE";
    protected static final String BEACON_ACTION = "BEACON_SERVICE_UPDATE";
    protected String minorID = "1";
    private BroadcastReceiver receiver = null;
    private BeaconTransmitter mBeaconTransmitter = null;
    private ArrayList<Beacon> sauce = new ArrayList<>();
    private Beacon beacon = null;
    private TextView idTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean compliant = checkPrerequisites();
        //Richiediamo il permesso per la Localizzazione
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1234);

        Switch beaconSw = findViewById(R.id.beaconSwitch);
        Switch scanSw = findViewById(R.id.scanSwitch);
        idTextView = findViewById(R.id.beaconIDTextView);
        Button idConfirm = findViewById(R.id.confIDButton);
        ListView listView = findViewById(R.id.beaconList);
        sauce.clear();
        ListCustomAdapter listCustomAdapter = new ListCustomAdapter(this, sauce);
        listView.setAdapter(listCustomAdapter);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Receiver per catturare i dispositivi visti
                String action = intent.getAction();
                assert action != null;
                switch (action) {
                    case SCANNING_ACTION: {
                        String[] message = Objects.requireNonNull(intent.getStringExtra("SCANNING_UPDATE")).split(":");
                        Toast.makeText(getApplicationContext(), Arrays.toString(message), Toast.LENGTH_SHORT).show();
                        if (message[0].equals("Found")) {
                            updateList((Beacon) intent.getSerializableExtra("BEACON_FOUND"));
                        }
                        break;
                    }
                    case BEACON_ACTION: {
                        Toast.makeText(getApplicationContext(), intent.getStringExtra("BEACON_UPDATE"), Toast.LENGTH_SHORT).show();
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(SCANNING_ACTION);
        filter.addAction(BEACON_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

        if (compliant) {
            beaconSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
                if (isChecked) {
                    beaconIntent.putExtra("beacon_minor", minorID);
                    startService(beaconIntent);
                    Toast.makeText(this, R.string.status_beacon_on, Toast.LENGTH_SHORT).show();
                } else {
                    stopService(beaconIntent);
                    Toast.makeText(this, R.string.status_beacon_off, Toast.LENGTH_SHORT).show();
                }
            });
        } else beaconSw.setEnabled(false);

        scanSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent scanningIntent = new Intent(getApplicationContext(), ScanningService.class);
            if (isChecked) {
                startService(scanningIntent);
                Toast.makeText(this, R.string.status_scanning_on, Toast.LENGTH_SHORT).show();
            } else {
                stopService(scanningIntent);
                Toast.makeText(this, R.string.status_scanning_off, Toast.LENGTH_SHORT).show();
            }
        });

        idConfirm.setOnClickListener(v -> {
            if (idTextView.getText() != null) minorID = idTextView.getText().toString();
        });
    }

    @Override
    protected void onDestroy(){
        // Stoppo il Service, de-registro il receiver e chiudo tutto
        Intent scanningIntent = new Intent(getApplicationContext(), ScanningService.class);
        stopService(scanningIntent);
        Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
        stopService(beaconIntent);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }

    public void updateList(Beacon newItem) {
        runOnUiThread(() -> {
            /*
            if (message[0].equals("In") || message[0].equals("Out")) {

                switch (message[0]) {
                    case "In": {
                        if (!sauce.contains(newItem)) {
                            sauce.add(newItem);
                            listCustomAdapter.notifyDataSetChanged();
                        }
                        break;
                    }
                    case "Out": {
                        sauce.remove(newItem);
                        listCustomAdapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
             */
            if (!sauce.contains(newItem)) sauce.add(newItem);
        });
    }

    @TargetApi(21)
    private boolean checkPrerequisites() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not supported by this device's operating system");
            builder.setMessage("You will not be able to transmit as a Beacon");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {

                }

            });
            builder.show();
            return false;
        }
        if (!getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not supported by this device");
            builder.setMessage("You will not be able to transmit as a Beacon");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {

                }

            });
            builder.show();
            return false;
        }
        if (!((BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled()){
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth not enabled");
            builder.setMessage("Please enable Bluetooth and restart this app.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {

                }

            });
            builder.show();
            return false;
        }

        try {
            // Check to see if the getBluetoothLeAdvertiser is available.  If not, this will throw an exception indicating we are not running Android L
            ((BluetoothManager) this.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser();
        }
        catch (Exception e) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE advertising unavailable");
            builder.setMessage("Sorry, the operating system on this device does not support Bluetooth LE advertising.  As of July 2014, only the Android L preview OS supports this feature in user-installed apps.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                }

            });
            builder.show();
            return false;

        }
        return true;
    }


}
