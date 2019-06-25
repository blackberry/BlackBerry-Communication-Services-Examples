![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Click To Call for JavaScript

The Click to Call example application demonstrates how to integrate a video
call experience into your website with BlackBerry Spark Communications
Services.  This example allows a user to click a button on a webpage to
start a secure video call with a predefined user or agent. The `bbmCall`
component handles the rendering of the incoming and outgoing video streams.

This example builds on the [Quick Start](../QuickStart/README.md) example that
demonstrates setting up the SDK in a domain with user authentication disabled
and the BlackBerry Key Management Service enabled.

### Features

This example demonstrates how easy it is to integrate the `bbmCall`
component into your webpage. It initializes the SDK and starts a video call
with a predefined user.

<br/>
<p align="center">
  <a href="screenShots/ClickToCall.png"><img src="screenShots/ClickToCall.png" width="50%" height="50%"></a>
</p>

## Getting Started

This example requires the Spark Communications SDK, which you can find along
with related resources at the locations below.

* Instructions to
[Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
the SDK.
* [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html)
instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)

<p align="center">
    <a href="https://youtu.be/CSXZT2perqE"
      target="_blank"><img src="../QuickStart/screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
  <b>Getting started video</b>
</p>

This example application works in a sandbox domain with user authentication
disabled and the BlackBerry Key Management Service enabled.  See the
[Download & Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
section of the Developer Guide to get started configuring a
[domain](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#domain)
in the [sandbox](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/faq.html#sandbox).

When you have a domain in the sandbox, edit Click to Call's `config_mock.js`
file to configure the example with your domain ID, your agent's user ID, and a
key passcode.

Set the `SDK_CONFIG.domain` parameter to your sandbox domain ID.

```javascript
const SDK_CONFIG = {
  domain: 'your_domain_id'
};
```

Set the `AGENT_USER_ID` parameter to the user ID of the agent that will
receive the call.  This example cannot receive calls, but the [Rich Chat
example application](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/RichChat/README.html)
can.  You can configure the Rich Chat example application to use your domain.
The user ID of the user logged into the Rich Chat application may be used as
the `AGENT_USER_ID` for this example as long as the Rich Chat user remains
logged in.

```javascript
const AGENT_USER_ID = 'agent_user_id';
```

Set the `KEY_PASSCODE` parameter to the string used to protect the logged in
user's keys stored in the [BlackBerry Key Management
Service](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/security.html).
Real applications should not use the same passcode for all users.   However,
it allows this example application to be smaller and focus on demonstrating
its call functionality instead of passcode management.

```javascript
const KEY_PASSCODE = 'passcode';
```

Run `yarn install` in the Click to Call application directory to install the
required packages.

When you run the Click to Call application, it will prompt you for a user ID.
Because you've configured your domain to have user authentication disabled, you
can enter any string you like for the user ID and an SDK identity will be
created for it.  Other applications that you run in the same domain will be
able to find this identity by this user ID.

## Walkthrough

Before a video call with a configured user can be initiated, the user must be
[authenticated](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#authentication)
and the [SDK
started](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html#start-sdk).

Follow this guide for a walkthrough of how to integrate a video call into your
webpage.

- [Import the bbmCall component into your web application](#importCall)
- [Create the user manager](#createUserManager)
- [Start a video call with a predefined user](#startCall)

### <a name="importCall"></a>Import the bbmCall component into your web application

The `bbmCall` component will manage all aspects of the video call interaction
for your application.

```html
  <link rel="import" href="node_modules/bbmCall/bbmCall.html">
```

### <a name="createUserManager"></a>Create the user manager

The `bbmCall` component requires a user manager to supply information
about the user for display purposes.  The `createUserManager` function is
defined in `config_mock.js` to create a `MockUserManager` instance from
the support library.

```javascript
  // Create and initialize the user manager.
  const userManager = await createUserManager(
    sdk.getRegistrationInfo().regId,
    authManager,
    (...args) => sdk.getIdentitiesFromAppUserIds(...args)
  );
  await userManager.initialize();
```

### <a name="startCall"></a>Start a video call with a predefined user

For every call you place, you must create a new `bbmCall` component and use
`makeCall()`.  When the call finishes, the `bbmCall` component will send your
application the `CallEnded` event, and you should discard the component.

```javascript
  // bbmCall is a single-use component.  Create an instance and add
  // it to the application.
  let bbmCall = document.createElement('bbm-call');
  await window.customElements.whenDefined('bbm-call');

  // Associate the bbmCall component with the SDK and user manager we
  // created.
  bbmCall.setBbmSdk(sdk);
  bbmCall.setContactManager(userManager);

  // When the call is finished, the CallEnded event is fired.
  bbmCall.addEventListener('CallEnded', () => {
    // The call has ended.  We can now clean up the dynamically added
    // component and close the popup window.
    document.body.removeChild(bbmCall);
    bbmCall= null;
    window.close();
  });

  // Add the component to the application.
  document.body.appendChild(bbmCall);

  // Place the call to the configured user ID once we have looked up their
  // regId.
  const identity = await sdk.getIdentitiesFromAppUserId(AGENT_USER_ID);
  bbmCall.makeCall(identity.regId, true);
```

## License

These examples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
