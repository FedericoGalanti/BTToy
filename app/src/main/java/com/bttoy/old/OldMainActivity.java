package com.bttoy.old;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bttoy.btping.BeaconService;
import com.bttoy.btping.R;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

/*
    Okay, questo è il consumatore. Binding può essere effettuato anche per un Service.
    Quindi, per fare un app che va da sé, posso mettere sia il consumer che il beacon in background
    Ma voglio tenere i controlli in foreground.
 */

public class OldMainActivity extends AppCompatActivity implements BeaconConsumer {
    protected static final String TAG = "MainActivity";
    protected static final String btpingUUID = "7e6985df-4aa3-4bda-bb8b-9f11bf7077a0";
    /*
        Beacon Manager serve per creare o gestire i Beacons che si vogliono creare.
        Va prima dichiarato e poi istanziato nell'onCreate().
     */
    private BeaconManager beaconManager = null;
    /*
        La Region è il range del dispositivo per cui i Beacon sono visti.
        Va settata per specificare qual'è il funzionamento dello scanner:
        - Chi è lo scanner
        - Cosa stiamo cercando (UUID, Major e minor) visto che usiamo AltBeacon
     */
    private Region beaconRegion = null;

    private Switch beaconSw, scanSw;
    private boolean compliant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        compliant = checkPrerequisites();

        beaconSw = findViewById(R.id.beaconSwitch);
        scanSw = findViewById(R.id.scanSwitch);

        if (compliant) {
            beaconSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Intent beaconIntent = new Intent(getApplicationContext(), BeaconService.class);
                if (isChecked) {
                    startService(beaconIntent);
                    Toast.makeText(this, R.string.status_beacon_on, Toast.LENGTH_SHORT).show();
                } else {
                    stopService(beaconIntent);
                    Toast.makeText(this, R.string.status_beacon_on, Toast.LENGTH_SHORT).show();
                }
            });
        } else beaconSw.setEnabled(false);

        scanSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startBeaconingMonitor();
                Toast.makeText(this, R.string.status_scanning_on, Toast.LENGTH_SHORT).show();
            } else {
                stopBeaconingMonitor();
                Toast.makeText(this, R.string.status_scanning_off, Toast.LENGTH_SHORT).show();
            }
        });

        //Richiediamo il permesso per la Localizzazione
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1234);
        //Istanzio il Beacon Manager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        /*
            Il Beacon Manager, per riconoscere i Beacons (AltBeacon, quindi tutti i beacon),
            sfrutta dei parser per fare in modo di "capire" la struttura di qualsiasi protocollo usato,
            che sia "iBeacon", "Eddystone" (tutti e quattro i frames) o altri protocolli.

            In particolare, i <protocol>_LAYOUTS specificano come dovrebbero essere i pacchetti inviati
            da ogni protocollo, in modo da poterli catturare e comprendere. Nello specifico:

            ALTBEACON   "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"

            EDDYSTONE  TLM  "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15"

            EDDYSTONE  UID  "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"

            EDDYSTONE  URL  "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v"

            IBEACON  "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"

            Dove:
            - Termine 1: ID del Manufacturer
            - Termine "i" (Altbeacon e IBeacon): UUID, Major e Minor
            - Termine "p": potenza del segnale (RSSI, legato al TX)
            - Termine "d": data payload
            Per ora lavoriamo solo con AltBeacon.
         */
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        // Effettuiamo il binding del manager a quest'attività
        beaconManager.bind(this);

    }

    @Override
    public void onBeaconServiceConnect() {
        //Log.d(TAG, "onBeaconServiceConnect called");
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                if (!entryMessageRaised) {
                    showAlert("Beacon detected!", "Beacon: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                }
                entryMessageRaised = true;
            }

            @Override
            public void didExitRegion(Region region) {
                if (!exitMessageRaised) {
                    showAlert("Beacon gone!", "Beacon: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                }
                entryMessageRaised = true;
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                /* Not implemented */
            }
        });
    }

    /*
        Alert per far vedere il messaggio corrispondente all'entrata e all'uscita del Beacon
     */
    private void showAlert(final String title, final String msg) {
        runOnUiThread(() -> {
            AlertDialog alertDialog = new AlertDialog.Builder(OldMainActivity.this).create();
            alertDialog.setTitle(title);
            alertDialog.setMessage(msg);
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    (dialog, which) -> {
                        dialog.dismiss();
                    });
        });
    }

    private boolean entryMessageRaised = false;
    private boolean exitMessageRaised = false;

    private void startBeaconingMonitor() {
        //Log.d(TAG, "startBeaconingMonitor called");
        try {
            //Mi creo la region con cui voglio effettuare il monitoring
            beaconRegion = new Region("BTPing Region", Identifier.parse(btpingUUID),
                    Identifier.parse("1"), Identifier.parse("1"));
            beaconManager.startMonitoringBeaconsInRegion(beaconRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void stopBeaconingMonitor() {
        //Log.d(TAG, "startBeaconingMonitor called");
        try {
            beaconManager.stopMonitoringBeaconsInRegion(beaconRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
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
                    finish();
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
                    finish();
                }

            });
            builder.show();
            return false;
        }
        if (!((BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled()) {
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bluetooth not enabled");
            builder.setMessage("Please enable Bluetooth and restart this app.");
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

        try {
            // Check to see if the getBluetoothLeAdvertiser is available.  If not, this will throw an exception indicating we are not running Android L
            ((BluetoothManager) this.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser();
        } catch (Exception e) {
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
