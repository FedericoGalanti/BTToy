package com.bttoy.btping;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.Beacon;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/*
    Start testing phase 1 (2 device).
    Logging the tests:
    - Add entries to the Arraylist of strings.
    - Add button for producing the file
    - Add request to service to get the logging list from the Scanning service
    - Write all to a file on button press.
    - Pray it works
 */

public class MainActivityTest extends AppCompatActivity {
    protected static final String TAG = "MainActivityTest";
    protected static final String SCANNING_ACTION = "SCANNING_SERVICE_UPDATE";
    protected static final String BEACON_ACTION = "BEACON_SERVICE_UPDATE";
    private static String[] perms = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION, android.permission.WRITE_EXTERNAL_STORAGE"};
    protected String minorID = "1";
    private BroadcastReceiver receiver = null;
    private ArrayList<Beacon> sauce = new ArrayList<>();
    //[TESTING]
    private ArrayList<String> logging = new ArrayList<>();
    private ListCustomAdapter listCustomAdapter;
    private TextView idTextView;
    private String startBattery;
    private long start;
    private boolean isLoggingOn = false;
    private String startBatteryState;


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

        startBatteryState = batteryLevel();
        isLoggingOn = true;
        Switch beaconSw = findViewById(R.id.beaconSwitch);
        Switch scanSw = findViewById(R.id.scanSwitch);
        idTextView = findViewById(R.id.beaconIDTextView);
        Button idConfirm = findViewById(R.id.confIDButton);
        Button logButton = findViewById(R.id.logButton);
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
                                ArrayList<Beacon> b = Objects.requireNonNull(intent.getParcelableArrayListExtra("BEACON"));
                                if (message[1].contains("Inside")) {
                                    sauce.clear();
                                    sauce.addAll(b);
                                } else sauce.removeAll(b);
                                if (isLoggingOn) {
                                    start = System.nanoTime();
                                    listCustomAdapter.notifyDataSetChanged();
                                    logging.add("Main Receiver: exec time " + ((System.nanoTime() - start) / 10000) + " ms\n");
                                } else {
                                    listCustomAdapter.notifyDataSetChanged();
                                }
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
        filter.addAction("SCANNING_LOG");
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
            Intent scanningIntent = new Intent(getApplicationContext(), ScanningServiceTest.class);
            if (isChecked) {
                startService(scanningIntent);
                Toast.makeText(this, R.string.status_scanning_on, Toast.LENGTH_SHORT).show();
            } else {
                stopService(scanningIntent);
                sauce.clear();
                listCustomAdapter.notifyDataSetChanged();
                scanSw.setEnabled(false);
                Toast.makeText(this, R.string.status_scanning_off, Toast.LENGTH_SHORT).show();
                try {
                    produceLog();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        /*
            ID button connected to the number text view. This is for debug mostly, since there is
            no persistence. Sets up the "minor" indicated in the number text view.
         */
        idConfirm.setOnClickListener(v -> {
            if (idTextView.getText() != null) minorID = idTextView.getText().toString();
        });

        logButton.setOnClickListener(v -> {
            try {
                produceLog();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }


    @Override
    protected void onDestroy() {
        /*
            Cleanup for the services and the receiver
         */
        Intent scanningIntent = new Intent(getApplicationContext(), ScanningServiceTest.class);
        stopService(scanningIntent);
        Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
        stopService(beaconIntent);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }

    //[TESTING: test 3]
    private String batteryLevel() {
        if (Build.VERSION.SDK_INT >= 21) {

            BatteryManager bm = (BatteryManager) this.getSystemService(BATTERY_SERVICE);
            assert bm != null;
            startBatteryState = "" + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "\n";
            //logging.add(startBatteryState);
        } else {
            BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    context.unregisterReceiver(this);
                    int rawlevel = intent.getIntExtra("level", -1);
                    int scale = intent.getIntExtra("scale", -1);
                    int level = -1;
                    if (rawlevel >= 0 && scale > 0) {
                        level = (rawlevel * 100) / scale;
                    }
                    startBatteryState = "" + level + "\n";
                    //logging.add(startBatteryState);
                }
            };
            IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryLevelReceiver, batteryLevelFilter);
        }
        return startBatteryState;
    }

    private void writeLog(File file) throws IOException {
        BufferedWriter buf = new BufferedWriter(new FileWriter(file));
        SimpleDateFormat stf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ITALY);
        buf.append("START OF LOG SESSION: ").append(stf.format(new Date())).append("\n\n");
        buf.append("").append(startBatteryState).append(" - ").append(batteryLevel()).append("\n");
        buf.append("LOGGING: Test for MainActivityTest ---\n");
        //2. Append Activity Logs
        for (String s : logging) {
            buf.append(s);
        }
        //4. Close file and flush
        buf.flush();
        buf.close();
    }

    public void produceLog() throws IOException {
        isLoggingOn = false;
        //1. Open File
        //Path: Scheda di memoria > Android > data > com.bbtoy > logfile.txt
        File logfile;
        if (Environment.getExternalStorageState() == null) {
            logfile = new File(this.getDataDir(), "/logfileActivity.txt");
        } else {
            logfile = new File(this.getExternalFilesDir(null), "/logfileActivity.txt");
        }
        if (logfile.exists()) writeLog(logfile);
        else {
            if (logfile.createNewFile()) writeLog(logfile);
        }
        isLoggingOn = true;
    }


    //[TESTING END FUNCTION]
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
        if (!((BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled()) {
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
        } catch (Exception e) {
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
