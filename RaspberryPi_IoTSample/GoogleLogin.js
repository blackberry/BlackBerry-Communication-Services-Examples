//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
// 
//  http://www.apache.org/licenses/LICENSE-2.0
// 
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

//
//  The bbm sdk requires an oauth2 provider, and a key storage service.
//
//  This module implements both, using Google for the oauth2 provider, and
//  implementing key storage using Firebase.
//

// Include needed modules.
const open = require('open');
const config = require('./config.js');


//  Include the Firebase helper functions from the BBM Enterprise SDK Support library.
//  Download the Node.js SDK from: https://developers.blackberry.com/us/en/products/blackberry-bbm-enterprise-sdk.html
const FirebaseKeyProvider = require(
  'bbm-enterprise/examples/support/protect/firebase/FirebaseKeyProvider.js');
const FirebaseUserManager = require(
  'bbm-enterprise/examples/support/identity/FirebaseUserManager.js');


const querystring = require("querystring");
const google = require('googleapis');
const OAuth2 = google.auth.OAuth2;
const oauth2 = google.oauth2('v2');

// These two modules are a little bit special - the FirebaseKeyProvider expects
// some symbols to be available globally, so import these two as global
// variables.

//***
//*** IMPORTANT! Point this towards the your instance of the BBM Enterprise SDK
global.BBMEnterprise = require('bbm-enterprise'); 
//***
//***


global.firebase = require('firebase');

// The Google authentication scope for the auth token.
const scope='https://www.googleapis.com/auth/userinfo.profile';

// The google login needs to issue a redirect to provide the authentication
// token. A local server will run at this address to capture it.
const redirectPort = 8080;
const redirectUri = "http://localhost:" + redirectPort;

// Construct an oauth client to do token retrieval.
var oauth2Client = new OAuth2(config.client_id, config.client_secret,
                              redirectUri);

module.exports = {
  login: function() {
    return new Promise(function(resolve, reject) {
      // Start up a web server to listen for the redirect from Google
      // authentication.
      var server = require("http").createServer(function(req, response) {
        // As soon as the response is received, stop the server.
        server.close();
        response.end("ok");

        // Post to Google to get an access token.
        var code = querystring.parse(req.url.split("?")[1]).code;

        // Google should redirect to this server with a query string containing
        // the authentication code. However a browser may also request other
        // things on the redirect, such as favicon.ico, without the code.
        // Ignore these requests.
        if(code) {
          oauth2Client.getToken(code, function(error, tokens) {
            if (error != null) {
              console.error('Failed to obtain access token: ' + error);
            } else {
              // Record the credentials. Most importantly, this records the
              // refresh token so that an access token can be retrieved later.
              oauth2Client.setCredentials(tokens);

              // Remember the initial access token.
              var accessToken = tokens.access_token;

              // Remember the id token. It is needed to log into firebase.
              const idToken = tokens.id_token;

              // Retrieve the User ID.
              oauth2.userinfo.get({access_token: tokens.access_token},
                                  function(error, result) {
                if(error) {
                  reject(error);
                }
                
                // Create an SDK instance.
								var bbmsdk = new BBMEnterprise({
                  domain: config.id_provider_domain,
                  environment: 'Sandbox',
                  userId: result.id,
                  getToken: function() {
                    return new Promise(function(resolve, reject) {
                      // The first time through, return the initial acces token.
                      // After that, retrieve the token using the refresh token.
                      if(accessToken) {
												console.log('ACCESS_TOKEN:');
												console.log(accessToken);
                        resolve(accessToken);
                        accessToken = null;
                      } else {
                        oauth2Client.refreshAccessToken(function(error, credentials, response) {
                          if (error != null) {
                            console.error('Failed to obtain access token: ' + error);
                            reject(error);
                          } else {
														console.log('CREDENTIALS.ACCESS_TOKEN:');
														console.log(credentials.access_token);
                            resolve(credentials.access_token);
                          }
                        });
                      }
                    });
                  },
                  getKeyProvider: (regId, accessToken) => 
                    FirebaseKeyProvider.factory.createInstance(
                      regId,
                      config.firebaseConfig, 
                      accessToken,
                      setupNeedMsg => console.warn(setupNeedMsg)
                  ),
                  description: 'node ' + process.version
                });
                
                bbmsdk.on('registrationChanged', function(registrationInfo) {
									console.log(registrationInfo);
                  if(registrationInfo.state === "Failure") {
                    console.error('BBM Login failed');
                    return;
                  } else if (registrationInfo.state === "Success") {
                    console.log('\n\n\nYour peripheral device Reg ID is:' + registrationInfo.regId + '\nCopy this value and paste it in your iOS applications PERIPHERAL_REG_ID parameter in the ConfigSettings.h file.\n\n\n');
                  } else {
										console.log('Registration Info State:' + registrationInfo.state);
									}
                  new FirebaseUserManager({
                    userRegId: registrationInfo.regId,
                    userEmail: '',
                    userImageURL: result.picture,
                    userName: result.name });
                });
                resolve(bbmsdk);
								
              });
            }
          });
        }
      }).listen(redirectPort);

      // Open the the google auth token endpoint in a browser.
      var url = oauth2Client.generateAuthUrl({
        access_type: 'offline',
        prompt: 'consent',
        scope: scope
      });
      open(url);
    });
  }
};
