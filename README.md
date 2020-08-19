# BTToy
BLE Toy case, made to test with Beaconing both in Scanning and in Advertising mode, using the "Android Beacon Library" and AltBeacon standard.
BTToy is a simple applications that allow a device with a Bluetooth 5.0 chip to be both a Beacon and a Scanner. During scanning, if any beacon happens to be nearby the scanner,
this are shown in a simple list under all of the controls. If beacons get out of range, the app will notify the event to the user, reporting which beacons went missing during last
scan, and will rectify the content of the list.

## Requirings
The app requires that Bluetooth is active.
The app requires the following permissions:
- BLUETOOTH
- BLUETOOTH_ADMIN
- ACCESS_FINE_LOCATION
- ACCESS_BACKGROUND_LOCATION
All of those are necessary in order for the app to work with any API level and Android verion. Even if location is requested, no data is harvested about location:
those are necessary because, since scanning retrieve approximate distance of a beacon by default using RSSI, scanning is considered as a "location" service, even though, again,
no data about any kind of location is harvested. The only "location data" inferred is the presence or absence of a device within the BLE range.
The app, of course, uses:
- bluetooth
- bluetooth_le
All informations before written are contained in the Manifest file.

## Usage
Once the app is installed and ready, there will be two switch button, a text field for numbers, a confirm button and a list:
- Beacon Switch: allows to activate or deactivate the Beaconing Service, thus activating or shutting down beacon advertising
    Default beacon holds the following informations: 
    Id1 = 7e6985df-4aa3-4bda-bb8b-9f11bf7077a0, Id2 = 1, Id3 = 1
    Those are the default ID fields set for the Beacon, according to AltBeacon standard. Only modifiable Id is Id3, which can be modified by the text box and confirm button.
    Notice: if device is not compatible for beaconing, this switch will be disabled.

- Scanning Switch: allows to activate or deactivate scanning. This will scan for beacons with Id1 = 7e6985df-4aa3-4bda-bb8b-9f11bf7077a0.
    Notice: even if device is not compatible for beaconing, can still use the scanner.
    
- Minor text field: allows to set a minor (Id3) for the beacon. Even if AltBeacon doesn't use Id fiels as iBeacon, this test app wanted to just use those fields in a similar way.
     To confirm the written number as a minor, tap on the confirm button.

- Confirm button: will set the minor for the device beacon as the number contained in the Minor text field. Closing and re-opening the app will reset back the minor to 1.

- List view: will display all of the beacons in sight, by showing all the Ids

## Behavior
Once activated the device beacon by tapping the Beacon Switch, it will start advertising immediatly, and any other BTToy nearby will be able to see the device.
The device will be visible in the list view of the scanners. Activating the scanning too will make the device both an advertiser and a scanner.
If a device will get out of range, a Notification will be sended to the user about the beacons that have left the range of the scanner, and those beacons will disappear from the
list.

## Disclaimer
This is by any means NOT a full stable app. It is a toy case for which behavior is as intended by my starting specifics. Any external modifications of the following code are beyond my responsibility, both in functionality or possible misusages. The project itself can be considered as a starting point for major projects involving BLE, Android Beacon Library and other similar technologies.

Major issue: on older mobiles (Andoid Version 9-), BLE interface allows for only one beacon to be active at any given time. This means that, if Apple/Google Exposure Notification Service is active, beaconing will not be working. Trying activating a beacon in this situation will return Toast message "Debug: advertising not started 2". The number 2 refers to the error code from the "startAdvertising" callback. To fix the issue, deactivate temporarily the A/G Exposure Notification Service and run the app. After app usage, REACTIVATE A/G EXPOSURE NOTIFICATION SERVICE. Stay healty!
