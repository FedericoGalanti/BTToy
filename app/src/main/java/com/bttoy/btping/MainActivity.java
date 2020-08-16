package com.bttoy.btping;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/*
    TODO: fix the goddamn notification system for the service
 */

public class MainActivity extends AppCompatActivity {
    protected static final String TAG = "MainActivity";
    protected static final String SCANNING_ACTION = "SCANNING_SERVICE_UPDATE";
    protected static final String BEACON_ACTION = "BEACON_SERVICE_UPDATE";
    private static String[] perms = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"};
    protected String minorID = "1";
    private BroadcastReceiver receiver = null;
    private ArrayList<Beacon> sauce = new ArrayList<>();
    private ArrayList<Beacon> missing = new ArrayList<>();
    private ListCustomAdapter listCustomAdapter;
    private TextView idTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        boolean compliant = checkPrerequisites();
        /*
            Requesting permissions: had to hardcode it, since apk 27+ (Android 10+)
            wouldn't force requests on app opening.
         */
        requestPermissions(perms, 1234);

        Switch beaconSw = findViewById(R.id.beaconSwitch);
        Switch scanSw = findViewById(R.id.scanSwitch);
        idTextView = findViewById(R.id.beaconIDTextView);
        Button idConfirm = findViewById(R.id.confIDButton);
        ListView listView = findViewById(R.id.beaconList);
        /*
            Clearing sauce and connecting the adapter
         */
        sauce.clear();
        listCustomAdapter = new ListCustomAdapter(this, sauce);
        listView.setAdapter(listCustomAdapter);

        /*
            Setting up the receiver. This takes broadcasts from both Beacon and Scanning Service:
            - Scanning service:
                - "Info": should implement logic for disabling button while Service is going off
                - "Beacon": by information given from the "Monitor" function, controls view content,
                    namely, the sauce of the ListView.
                - Note: "Beacon" case may be at his load limit for a normal receiver. Ulterior
                    manipulations should be done in a thread. In this case, adjust by making a
                    companion thread that serves the list or create Runnable to post to the listview

            - Beacon service: takes only notifications of beacon service status. Nothing more.
            Implementation is made via "LocalBroadcast", which may be deprecated, thus should be
            implemented with stable implementations (Broadcast with custom permissions...?)
         */

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Main receiver for local broadcasts
                String action = intent.getAction();
                assert action != null;
                switch (action) {
                    case SCANNING_ACTION: {
                        String[] message = Objects.requireNonNull(intent.getStringExtra("SCANNING_UPDATE")).split(":");
                        switch (message[0]) {
                            case "Info": {
                                if (message[1].contains("off")) scanSw.setEnabled(true);
                                break;
                            }
                            case "Beacon": {
                                Beacon b = intent.getParcelableExtra("BEACON");
                                if (message[1].contains("Inside") && !sauce.contains(b))
                                    sauce.add(b);
                                else sauce.remove(b);
                                    listCustomAdapter.notifyDataSetChanged();
                                }
                            default: {
                                Toast.makeText(getApplicationContext(), Arrays.toString(message), Toast.LENGTH_SHORT).show();
                                break;
                            }
                        }
                        break;
                    }
                    case BEACON_ACTION: {
                        String[] message = Objects.requireNonNull(intent.getStringExtra("BEACON_UPDATE")).split(":");
                        if (message[1].contains("off")) beaconSw.setEnabled(true);
                        Toast.makeText(getApplicationContext(), Arrays.toString(message), Toast.LENGTH_SHORT).show();
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

        /*
            Setting up switches. Compliant = control over APIs and stuff (checkRequisites).
            The beacon switch will pass via Intent the "Minor" (aka the distinguished personal id)
            to the beacon Service for building.

         */

        if (compliant) {
            beaconSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
                if (isChecked) {
                    beaconIntent.putExtra("beacon_minor", minorID);
                    startService(beaconIntent);
                    Toast.makeText(this, R.string.status_beacon_on, Toast.LENGTH_SHORT).show();
                } else {
                    stopService(beaconIntent);
                    beaconSw.setEnabled(false);
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
                scanSw.setEnabled(false);
                Toast.makeText(this, R.string.status_scanning_off, Toast.LENGTH_SHORT).show();
            }
        });

        /*
            ID button connected to the number text view. This is for debug mostly, since there is
            no persistence. Sets up the "minor" indicated in the number text view.
         */
        idConfirm.setOnClickListener(v -> {
            if (idTextView.getText() != null) minorID = idTextView.getText().toString();
        });
    }


    @Override
    protected void onDestroy(){
        /*
            Cleanup for the services and the receiver
         */
        Intent scanningIntent = new Intent(getApplicationContext(), ScanningService.class);
        stopService(scanningIntent);
        Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
        stopService(beaconIntent);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }


    @TargetApi(21)
    private boolean checkPrerequisites() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not supported by this device's operating system");
            builder.setMessage("You will not be able to transmit as a Beacon");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(dialog -> {

            });
            builder.show();
            return false;
        }
        if (!getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not supported by this device");
            builder.setMessage("You will not be able to transmit as a Beacon");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(dialog -> {

            });
            builder.show();
            return false;
        }
        if (!((BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled()){
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth not enabled");
            builder.setMessage("Please enable Bluetooth and restart this app.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(dialog -> {

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
            builder.setOnDismissListener(dialog -> finish());
            builder.show();
            return false;

        }
        return true;
    }

}
