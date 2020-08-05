package com.bttoy.btping;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

public class ScanningService extends Service implements BeaconConsumer {
    protected static final String TAG = "ScanningService";
    protected static final String btpingUUID = "7e6985df-4aa3-4bda-bb8b-9f11bf7077a0";
    private static final String CHANNEL_ID = "001";
    private NotificationManager mNotification = null;
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
    private boolean entryMessageRaised, exitMessageRaised = false;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotification = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //Istanzio il Beacon Manager
        beaconManager = BeaconManager.getInstanceForApplication(getApplicationContext());
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
        //Mi creo la region con cui voglio effettuare il monitoring
        beaconRegion = new Region("BTPing Region", Identifier.parse(btpingUUID),
                Identifier.parse("1"), null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
            Considerando che deve effettuare un'operazione long-running e che controllo il service
            dall'attività con i pulsanti, mi basta farlo avviare. I controlli sul Bluetooth, verranno
            fatti dall'attività, quindi, se il BT dovesse mancare o il dispositivo non fosse abilitato,
            posso controllare il Service per farlo stoppare o per prevenirne la creazione.
         */
        startBeaconingMonitor();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopBeaconingMonitor();
        beaconManager.unbind(this);
        super.onDestroy();
    }

    @Override
    public void onBeaconServiceConnect() {
        //Log.d(TAG, "onBeaconServiceConnect called");
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                if (!entryMessageRaised) {
                    //showAlert("Beacon detected!" , "Beacon: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                    tellMain("Enters: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                }
                entryMessageRaised = true;
            }

            @Override
            public void didExitRegion(Region region) {
                if (!exitMessageRaised) {
                    //showAlert("Beacon gone!" , "Beacon: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                    tellMain("Leaves: " + region.getId1() + " " + region.getId2() + " " + region.getId3());
                }
                exitMessageRaised = true;
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                /* Not implemented */
            }
        });
    }

    private void startBeaconingMonitor() {
        //Log.d(TAG, "startBeaconingMonitor called");
        try {
            //Mi creo la region con cui voglio effettuare il monitoring
            beaconManager.setBackgroundScanPeriod(2000L);
            beaconManager.startMonitoringBeaconsInRegion(beaconRegion);
        } catch (RemoteException e) {
            tellMain("Debug: Remote exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void stopBeaconingMonitor() {
        //Log.d(TAG, "startBeaconingMonitor called");
        try {
            beaconManager.stopMonitoringBeaconsInRegion(beaconRegion);
            entryMessageRaised = false;
            exitMessageRaised = false;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void tellMain(String msg) {
        Intent intent = new Intent("SCANNING_SERVICE_UPDATE");
        // You can also include some extra data.
        intent.putExtra("SCANNING_UPDATE", msg);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        showNotification(msg);
    }

    private void showNotification(final String msg) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.bt_notification_icon)
                .setContentTitle("Scanning Service")
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        if (mNotification != null) mNotification.notify(0, notification);
    }
}

