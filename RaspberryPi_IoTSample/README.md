[![image_alt_preview25](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)
# **Raspberry Pi IoT Sample**

The Raspberry Pi IoT Sample provides an example of how to interface with a hardware peripheral device that is running the Node.js BBM Enterprise SDK. This sample reads the colour selected on a mobile application and displays it on an RGB LED connected to a Raspberry Pi.

If you would like to learn more about BlackBerry's CPaaS, be sure to head over to our [website](https://us.blackberry.com/enterprise/bbm-enterprise-sdk).

### BBM Enterprise SDK Guide
For more details see the
[Quick Start Swift page in the BBM Enterprise SDK Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/ios/QuickStartSwift/README.html).


### Screenshots
[![image_alt_preview25](screenShots/screen-shots.png)](screenShots/screen-shots.png)

### Features
- **Send and receive secure data** - using the BBM Enterprise SDK to communicate between the mobile app and hardware peripheral device.
- **Build on** - using this sample, which can be used as a skeleton for more complex secure data transfer in your enterprise use case.


## Getting Started

These instructions will help you get this project up and running on your Rasberry Pi and iOS device. **Note that this project was built using the BBM-E SDK version 1.1.0.18 for iOS**

### Prerequisites
**Important Note:** This project requires you have [this Node.js sample](https://github.com/blackberry/bbme-sdk-javascript-samples/raspberryPi_IoTSample) running on a Raspberry Pi 3. Refer to the Node.js sample [README.md](https://github.com/blackberry/bbme-sdk-javascript-samples/raspberryPi_IoTSample) for instructions on setting up the Raspberry Pi.

This project also requires that you have the following:
- Raspberry Pi 3 running Raspbian OS
- GPIO breakout board or equivilant jumper wires
- RGB LED

### Installation
Make sure that your development machines have cocoapods installed on it. To install the required pods for this project, `cd` to your project directory and run the following command:

```
pod install
```
This will install the required Google and Firebase pods for your app to authenticate. This will also install the required MSColorPicker pod to allow your app to select a new colour.

In order for you to run this project, you will need to include BBM Enterprise SDK and the classes in the support group from one of the examples in BBM Enterprise SDK bundle. Follow the instructions provided [here](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html) to download the bundle.

### Configure
Your project must be configured with a BlackBerry BBME Domain, database and an IDP. For this sample, we will use Google Sign In.

If you have not already done so for the [Node.js sample]() for your Raspberry Pi, set up a new project with Google. Then open `ConfigSettings.h` and paste in the `GOOGLE_SIGNIN_CLIENTID` parameter found on [Google's Developer API Console](https://console.developers.google.com/apis/). Then head on over to your [Firebase Console](https://console.firebase.google.com/), add an new iOS project, and paste the `google.plist` file into the project to replace the missing reference.

Your Firebase database rules will need to be edited to allow the application the requireed access while keeping your data secure. Head to Database in your Firebase Console, then to Rules. Paste in the contents of rules.json (included in this repository).

You can find your `SDK_SERVICE_DOMAIN` by logging into [BlackBerry PCE](http://account.good.com) and navigating to the application you are using for this project.

The final step to to retrieve the Reg ID of the user logged in on the peripheral device. To do so, monitor the console output from the Raspberry Pi the first time you run it. A line will appear that reads similar to:
```sh
Your peripheral device Reg ID is: 897689437282941152
```
Your console will contain the Reg ID unique to your account. Copy that value and paste it in the `PERIPHERAL_REG_ID` parameter in your iOS app's `ConfigSettings.h`.

### Deploy
The app can be started by building and running it on your physical iOS device.
Note that this application was built for use between one iOS device and one Raspberry Pi. If multiple iOS devices are used and the application stops working, head over to  your [Firebase Console](https://console.firebase.google.com/) and delete all users from Authentication and all data from the Database.


## Authors

- **Connor Holowachuk** - Initial Work - [connor-holowachuk](https://github.com/connor-holowachuk)

## License

These samples are released as Open Source and licensed under [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-ios-samples/issues).

