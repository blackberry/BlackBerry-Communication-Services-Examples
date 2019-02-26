/*
 * Copyright (c) 2019 BlackBerry.  All Rights Reserved.
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

// This module implements a stubbed out login process using a mock auth token
// generator.

const BBMEnterprise = require('bbm-enterprise');
const crypto = require('crypto');

module.exports = {
  login: (config) => {
    // A statically configured local user for the bot.
    const localUserInfo = config.mockConfig;

    // Create an unsigned JWT.
    function createToken() {
      const jti = BBMEnterprise.Util.base64urlEncode(
        new Uint8Array(crypto.randomBytes(20))
      ).substring(0, 18);

      // Create the JWT header.
      const tokenHeader = BBMEnterprise.Util.base64urlEncode(JSON.stringify({
        alg: 'none'
      }));

      // The current time, in seconds.
      const now = (Date.now() / 1000) | 0;

      // Create the JWT body.
      const tokenBody = BBMEnterprise.Util.base64urlEncode(JSON.stringify({
        jti: jti,
        sub: localUserInfo.userId,
        // Valid since 60 seconds ago to avoid clock skew issues.
        iat: now - 60,
        // Expires in one day.
        exp: now + 86400
      }));

      return `${tokenHeader}.${tokenBody}.`;
    }

    console.log(
      'Mock Login: Successfully authenticated; local user='
      + JSON.stringify(localUserInfo));

    // Return the object used to manage the user info and authorization.
    return Promise.resolve({
      // Returns the local user info object.
      getLocalUserInfo: () => localUserInfo,

      // Returns the promise of a new unsigned mock token for accessing the
      // SDK services.
      getBbmSdkToken: () => Promise.resolve(createToken())
    });
  }
};

