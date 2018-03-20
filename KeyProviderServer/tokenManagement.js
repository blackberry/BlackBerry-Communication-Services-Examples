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
 * Module contains set of functions to validate and parse Azure JWT tokens.
 */
module.exports = function() {
  const webtoken = require('jsonwebtoken');
  const azureJWT = require('azure-jwt-verify');
  const request = require('request');
  const config = require('./config');
  const Errors = require('./errors');
  const cron = require('node-cron');
  const validatedTokens = new Map();
  const configs = new Map();

  /**
   * Gets decoded JWT token if token was previously cached and not expired.
   * @param {string} jwt JWT issued by Azure.
   * @returns {object} Decoded JWT object if token exists in cache. Returns null
   * if JWT is not in cache.
   * @throws {Errors.TokenInvalidError} When passed JWT format is invalid.
   * @throws {Errors.TokenExpiredError} When cached JWT is expired.
   */
  var getValidJwtFromCache = jwt => {
    var hash;
    try {
      // Get token hash.
      hash = jwt.split('.').pop();
    }
    catch(error) {
      console.log('Failed to parse token');
    }

    if (!hash) {
      // Token format is invalid.
      console.warn(`Cannot parse JWT: ${jwt}`);
      let error = new Errors.TokenInvalidError('Token is invalid');
      throw error;
    }

    if (validatedTokens.has(hash)) {
      var token = validatedTokens.get(hash);
      if (token.exp >= new Date().getTime()) {
        // Cached token is expired.
        validatedTokens.delete(hash);
        console.log(`Token is expired: ${token.name} - ${token.oid}`);
        let error = new Errors.TokenExpiredError('Token is expired');
        throw error;
      } else {
        // Not expired. Return cached token.
        return validatedTokens.get(hash);
      }
    }
    return null;
  }

  /**
   * Validates and decodes JWT if valid.
   * @param {string} jwt Json Web Token issued by Azure.
   * @returns {Promise<object>} Promise of decoded JWT object. Rejects with an 
   * error in case of failure.
   */
  this.validateAndParseJwt = jwt => new Promise((resolve, reject) => {
    var decodedJwt;
    try {
      decodedJwt = getValidJwtFromCache(jwt);
      if (decodedJwt) {
        resolve(decodedJwt);
        return;
      }
    } 
    catch(error) {
      reject(error);
    }

    decodedJwt = webtoken.decode(jwt);
    // Check if app id of the token matches to the one specified in the
    // config file. If app id in config file is a wildcard then ignore it.
    if (config.applicationId !== '*'
      && decodedJwt.appid !== config.applicationId) {
      console.warn(`App Id does not match to config: ${decodedJwt.appid}`);
      let error = new Errors.InvalidAppIdError('Invalid App Id');
      reject(error);
    }
    // Get Open Id config for JWT validation.
    this.getAzureOpenIdConfig(decodedJwt.tid)
    .then(openIdConfig => {
      const verifyConfig = {
        JWK_URI: openIdConfig.jwks_uri,
        ISS: decodedJwt.iss,
        AUD: decodedJwt.aud
      };
      // Invoke validation function with the retrieved Open Id jwks_uri.
      azureJWT.verify(jwt, verifyConfig).then(response => {
        // JWT token validation is successful.
        let hash = jwt.split('.').pop();
        validatedTokens.set(hash, decodedJwt);
        resolve(decodedJwt);
      }, err => {
        // Failed to validate JWT token.
        console.log(`Failed to validate JWT token: ${jwt}`);
        let error = new Error.TokenInvalidError('Token is invalid');
        reject(error);
      });
    });
  });

  /**
   * Gets Azure Open Id config for the specific tenant Id.
   * @param {string} tid Tenant ID to get Open Id config for.
   * @returns {Promise<object>} Promise of Open Id config.
   */
  this.getAzureOpenIdConfig = tid => new Promise((resolve, reject) => {
    // Check if Open Id config for the provided JWT is cached.
    if (configs.has(tid)) {
      // Resolve with the Open Id config retrieved from the cache.
      resolve(configs.get(tid));
      return;
    }
    // Request Open Id config from the server.
    var tidOpenIdConfig = {
      json: true,
      url: config.openIdConfigURL(tid)
    }
    // Send request to retrieve the Open Id config.
    request.get(tidOpenIdConfig, (error, response, openIdConfig) => {
      if (error) {
        // Failed to retrieve Open Id config.
        console.log(`Failed to get Open Id config for ${tid}`
          + ` | Error: ${error.message}`);
        reject(new Errors.TokenOpenIdError(error.message));
      } else {
        // Cache retrieved Open Id config.
        configs.set(tid, openIdConfig);
        resolve(openIdConfig);
      }
    });
  });

  /**
   * Initiates scheduling of the permission tokens audit. Removes expired
   * permission tokens from cache.
   */
  cron.schedule(config.tokenAuditSchedule, () => {
    console.log(`Auditing permission tokens: ${new Date()}`);
    // Look up for all expired tokens.
    Object.keys(validatedTokens).forEach((token, hash) => {
      var now = new Date().getTime();
      // Delete expired token.
      if (now >= token.exp) {
        map.delete(hash);
      }
    });
  });
}
