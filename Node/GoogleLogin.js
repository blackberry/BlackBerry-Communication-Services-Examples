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
// The bbm sdk requires an oauth2 provider.
//
// This module implements it, using Google for the oauth2 provider.
//

// Include some needed modules.
const google = require('googleapis');
const oauth2 = google.oauth2('v2');
const { JWT } = require('google-auth-library');

module.exports = {
  login: (config) => {
    // Construct an authentication client to do token retrieval.
    const client = new JWT(
      config.googleConfig.client_email, null, config.googleConfig.private_key,
      [
        'https://www.googleapis.com/auth/userinfo.profile',
        'https://www.googleapis.com/auth/userinfo.email'
      ]);

    // The object that will represent the user info of the local user.
    let localUserInfo;

    // Create a promise that will authenticate the user.
    return client.authorize()
    .then((result) => {
      // Retrieve the User ID of the local user.
      return new Promise((resolve, reject) => {
        oauth2.userinfo.get({access_token: result.access_token},
                            (error, result) => {
          if (error) {
            console.error(
              `Google Login: Failed to authenticate; error=${error}`);
            reject(error);
            return;
          }
          // Remember the details of the local user.
          localUserInfo = {
            // Use the user's name.  Remove the @domain portion iff the name
            // happens to be an email address.  If the name is not present or
            // is the empty string, we use a default value.
            displayName: result.name
              ? result.name.replace(/@.*$/, '') : 'Spark Communications Bot',
            emailAddress: result.email,
            avatarUrl: result.picture,
            userId: result.id
          };

          console.log(
            'Google Login: Successfully authenticated; local user='
            + JSON.stringify(localUserInfo));

          // Return the object used to manage the user info and authorization.
          resolve({
            // Returns the local user info object.
            getLocalUserInfo: () => localUserInfo,

            // Returns the promise of Google access token for accessing the
            // SDK services.
            getBbmSdkToken: () => {
              return client.authorize()
              .then(result => result.access_token);
            }
          });
        });
      });
    });
  }
};
