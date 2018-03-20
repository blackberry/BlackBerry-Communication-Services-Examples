/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/**
 * Module contains server configuration parameters.
 */
module.exports = {

  // The connection string to connect to CosmosDB database. Obtain this
  // string from Azure Portal, in CosmosDB database settings.
  connectionString: 'YOUR DB CONNECTION STRING',

  // Your application Id
  applicationId: 'YOUR APPLICATION ID',

  // Open Id configuration URL
  openIdConfigURL: tid => `https://login.windows.net/${tid}/v2.0/.well-known/openid-configuration`,

  // Permission tokens audit schedule configuration
  // For details please refer to https://www.npmjs.com/package/node-cron
  //                 # ┌─────────── Second (optional)
  //                 # │ ┌───────── Minute
  //                 # │ │ ┌─────── Hour
  //                 # │ │ │ ┌───── Day of month
  //                 # │ │ │ │ ┌─── Month
  //                 # │ │ │ │ │ ┌─ Day of week
  tokenAuditSchedule: '0 0 0 * * *', // Every midnight

  // Name of the collection (table) where keys are stored. For example type
  // 'KeyStore' if your table name is 'KeyStore'.
  collectionName: 'YOUR TABLE NAME IN DATABASE',
  
  // Entities partition in the collection. All entities reside in the same
  // partition. For example: 'KeysPartition'.
  collectionPartition: 'YOUR PARTITION NAME',

  // Regular expression that defines the format of user ID, issued by your
  // identity provider.
  userIdRegex: /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,

  // CORS settings
  accessControlAllowOrigin: '*',
  accessControlAllowHeaders: 'Origin, X-Requested-With, Content-Type, Accept, Authorization',
  accessControlAllowedMethods: 'GET, PUT',
  
  // Server private key location (full path).
  keyPath: __dirname + '/cert/privateKey.pem',

  // Server public certificate location (full path).
  certPath: __dirname + '/cert/publicCert.pem',

  // Server key passphrase
  keyPassphrase: 'PASSPHRASE FOR PRIVATE KEY',

  // Indicate if server should be HTTP or HTTPS.
  useSsl: true,

  // Server port.
  serverPort: 3000,

};
