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

//
// Log in to BBM and start up a repl loop to allow actions to be performed
// from the command line.
//

// Usage: node ./Repl.js [idp]
//   Where idp is the identity provider of choice.   Valid values are:
//
//     * google:  Google will be used as the identity provide.  Only valid
//                Google identities can be used.
//
//     * mock:    No identity provider is to be used.  Only stubbed tokens and
//                identity will be provided.
//
//   If no idp value is supplied, the default idp is 'mock'.
//
const idp = process.argv.length === 3 ? process.argv[2] : 'mock';

// Try to load the configuration for the IDP.
let config;
let login;
switch(idp) {
case 'google':
  console.log('Loading configuration from: ./config_google.js');
  config = require('./config_google');
  login = require('./GoogleLogin');
  break;

case 'mock':
  console.log('Loading configuration from: ./config_mock.js');
  config = require('./config_mock');
  login = require('./MockLogin');
  break;

default:
  throw new Error(
    `Invalid idp argument: ${idp}; supported IDP: google, mock`);
}

// Load the module we use to setup the SDK.
const sdkSetup = require('./SdkSetup');

const repl = require('repl');

// Create an SDK instance in the loop.
login.login(config)
.then((authManager) => sdkSetup.sdkSetup(config, authManager))
.then(sdk => {
  console.log('setup completed');

  // Start up a node repl loop.
  const loop = repl.start('> ');

  loop.context.sdk = sdk;
})
.catch(error => {
  console.log('setup failed: ' + JSON.stringify(error));
});
