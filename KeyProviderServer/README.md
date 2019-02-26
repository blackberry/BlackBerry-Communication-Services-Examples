![BlackBerry Spark Communications Services](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/resources/images/bnr-bbm-enterprise-sdk-title.png)

# Key Provider Server for JavaScript

The Key Provider Server example application demonstrates how you can enforce
access control to a cloud storage solution.  It provides a RESTful interface
that your client application can use to export and import security keys.

The cryptographic keys used by BlackBerry Spark Communications Services to protect your
communications are stored and distributed in a cloud storage solution of your
choice.  If your solution does not meet the [Cloud Key Storage
requirements](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/cloudKeyStorage.html#requirements),
you may need a service layer to provide the access control necessary for
security.

<p align="center">
  <a href="screenShots/keyProviderService-azure.png"><img src="screenShots/keyProviderService-azure.png" width="50%" height="50%"></a>
</p>

### Features

The Key Provider Server application demonstrates how an application service
layer can be used to ensure users can only access the keys they own or the
public keys of others.  This example provides the following functionality:

1. Provides database access to only authenticated users by validating the JWT
   access token that's passed by the client application.

2. Allows **only** the user to read or write their private key data.

3. Allows **only** the user to write their public key data.

4. Allows all authenticated users to read public key data.

## Getting Started

This example requires the Spark Communications SDK, which you can find along
with related resources at the locations below.

* Instructions to
[Download and Configure](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted.html)
the SDK.
* [Getting Started with Web](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/gettingStarted-web.html)
instructions in the Developer Guide.
* [API Reference](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/reference/javascript/index.html)
This sample requires the Spark Communications SDK for JavaScript, which you can find along with related resources at the location below.

<p align="center">
    <a href="https://youtu.be/CSXZT2perqE"
      target="_blank"><img src="../QuickStart/screenShots/bb-spark-web-sdk-getting-started.jpg"
      alt="YouTube Getting Started Video" width="486" height="" border="364"/></a>
</p>
<p align="center">
 <b>Getting started video</b>
</p>

This example uses the popular cloud storage solution, [Azure Cosmos
DB](https://docs.microsoft.com/en-us/azure/cosmos-db/introduction).  Please
refer to the Developer Guide for more information about setting up [Azure
Cosmos DB for Cloud Key
Storage](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureCloudKeyStorage.html).

When you have a Cosmos DB instance set up and configured, edit the Key Provider
Server's `config.js` file.

Set the `applicationIds` property to the GUID assigned to the application that
will be using the Key Provider Server.  If you have more than one application
that will be using the server, this property accepts an array of GUIDs.

```javascript
  applicationIds: 'your_application_id',
```

Set the `tenantIds` property to the GUID identifying the tenant whose users
will be accessing the applications using the Key Provider Server.  If you are
providing access for more than one tenant, this property accepts an array of
GUIDs.

```javascript
  tenantIds: 'your_tenant_id',
```

Set the `connectionString` property to the primary connection string for your
instance of [Azure Cosmos
DB](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureCloudKeyStorage.html).

```javascript
  connectionString: 'your_cosmos_db_primary_connection_string',
```

Set the `collectionPartition` property to the name of the partition you chose
to contain your collection when setting up your instance of [Azure Cosmos
DB](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureCloudKeyStorage.html).

```javascript
  cllectionPartition: 'your_cosmos_db_collection_partition_name',
```


Set the `collectionName` property to the table name you chose when setting up
your instance of [Azure Cosmos
DB](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureCloudKeyStorage.html).

```javascript
  collectionName: 'your_cosmos_db_table_name',
```

Set the `accessControlAllowOrigin` property to your application's origin
value, which consists only of the scheme and fully qualified domain name of
your application's URL.  For testing purposes, you can use a wildcard to set
this property.  This will enable requests from any origin to access this
resource.  Refer to the W3C documentation for
[Access-Control-Allow-Origin](https://www.w3.org/TR/cors/#access-control-allow-origin-response-header)
for more details.  **Use of a wildcard is not recommended for production
environments.**

```javascript
  accessControlAllowOrigin: 'https://example.com',
```

The default server port in this configuration is 3000.  You may change this by
updating the `serverPort` property.

```javascript
  serverPort: 3000,
```

By default, the Key Provider Server offers its APIs over HTTPS.  You may wish
to disable this for testing purposes only.  To use HTTP, set the `useSSL`
property to `false`.  **Using HTTPS is recommended for production
environments**

```javascript
  useSsl: true,
```

When using HTTPS, set the `keyPath` and `certPath` properties to configure the
server's private encryption key and certificate.

```javascript
  keyPath: `${__dirname}/privateKey.pem`,
  certPath: `${__dirname}/publicCert.pem`,
```

Set the `keyPassphrase` property to the passphrase used to protect your
private encryption key.

```javascript
  keyPassphrase: 'your_private_key_passphrase',
```

Run `yarn install` in the Key Provider Server's application directory to
install the NPM packages needed to run this example.

Run `node app.js` in the Key Provider Server's application directory to run
the Key Provider server.

Use the [Rich Chat example
application](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/examples/javascript/RichChat/README.html)
configured for use with [Azure Active
Directory](https://developer.blackberry.com/files/bbm-enterprise/documents/guide/html/azureForWebExamples.html)
to exercise the Key Provider Server's APIs.

## License

These examples are released as Open Source and licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Reporting Issues and Feature Requests

If you find a issue in one of the Samples or have a Feature Request, simply file an [issue](https://github.com/blackberry/bbme-sdk-javascript-samples/issues).
