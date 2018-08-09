![BlackBerry Spark Communications Platform](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# BBMBot Sample for JavaScript

The BBMBot sample app demonstrates how to build a chat bot in Node.js with the
Spark SDK.  The BBMbot uses the [www.botlibre.com](www.botlibre.com)
webservice as an example of how to generate responses.

<p align="center">
<br>
    <a href="http://www.youtube.com/watch?feature=player_embedded&v=7idoN9YpGGc"
      target="_blank"><img src="screenShots/bbme-sdk-node-chatbot.jpg" 
      alt=Integrate Chat Bots into your Apps" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Demo video: Integrate Chat Bots into your Apps</b>
</p>

### Features

With the BBMBot example, your app can do the following:

- Invite the bot to a 1:1 or multi-person chat.
- In a 1:1 chat, write to the bot, and the bot will respond.
- In a multi-person chat, the bot will respond only to messages which begin with "@bbmbot".

<br>

<p align="center">
<a href="screenShots/BBMBot_chat.png"><img src="screenShots/BBMBot_chat.png" width="25%" height="25%"></a>
</p>

## Getting Started

This sample requires the Spark SDK, which you can find along with related resources at the location below.
    
* Getting started with the [Spark SDK](https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html)
* [Development Guide](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/index.html)
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)

<p align="center">
    <a href="https://www.youtube.com/watch?v=LAbxok2EQtI"
      target="_blank"><img src="../QuickStart/screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

### <a name="prereq"></a>Prerequisites

Visit the [Getting Started with Node](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-node.html) section to see the minimum requirements.

To use the BBMBot example, you must set up the following elements in config.js:

-id_provider_domain: Your Spark user domain.

-firebaseConfig: The firebase API config.

-password: A password to use to protect keys.

The firebase API config can be generated from the Firebase console: https://console.firebase.google.com

Create a project, then choose 'Add Firebase to your web app'.

-botLibre: The botlibre configuration.

The bot uses the botlibre chatbot service to provide responses to messages. An account can be created by going to https://botlibre.com

The bot can be created by choosing:
#Sign up (and complete sign up procedure)
#Create (and fill in bot information)

Then choose 'Embed' and copy the application and instance information.

-googleConfig: The google service account configuration.

A google service account can be created by logging into the google api console at https://console.developers.google.com

Choose credentials, then in the credentials tab, choose 'Manage service accounts'.
Choose 'CREATE SERVICE ACCOUNT'.

Make sure to check the box labelled 'Enable G Suite Domain-wide Delegation' when creating the account.

After creating the google service account, the account needs to be authorized in firebase by going back to the firebase console and choose 'Authentication'.
In the 'SIGN-IN METHOD' tab, choose to edit the google sign-in method.
In the 'Whitelist client IDs from external projects' section, choose to add the client_id specified in the google config.

## <a name="running"></a>Running

The app can be started with:

```shell
node BBMBot
```

## License

These samples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

This page includes icons from: https://material.io/icons/ used under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).



