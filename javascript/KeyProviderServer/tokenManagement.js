/*
 * Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
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

"use strict";

const config = require('./config');
const Errors = require('./errors');

const asn1 = require('asn1.js');
const webtoken = require('jsonwebtoken');
const request = require('request');

/**
 * This module will manage the following high-level tasks:
 *
 * 1. Token verification
 *
 *    This module will attempt to verify a token issued by any issuer for any
 *    audience for any tenant.  The tenant ID, issuer, and audience
 *    information for verification are taken from the token to be validated.
 *    If necessary, the public keys of the issuer will be fetched for
 *    validation.
 *
 * 2. Caching of tenant public keys
 *
 *    The public keys associated with each tenant's configured jwks_uri will
 *    be fetched and cached for each tenant ID encountered.  If the public key
 *    ID identified in the token is not already cached, the tenant's public
 *    keys will be refreshed.
 */
module.exports = function() {
  // When null, a request can be made to fetch the configuration.  Otherwise,
  // this will be a promise that resolves to be the OpenID config object of
  // the 'common' tenant.  This allows the KeyProviderServer to work in both a
  // single or multi tenant setup.
  //
  // This value is populated by calling getAzureOpenIdConfig().
  let commonTidConfigPromise = null;

  // The array of issuers that match the set of tenants from whom we will
  // accept tokens.  We will also add to this array using the issuer URL
  // returned in the OpenID config for the 'common' tenant and the configured
  // tenant IDs.
  //
  // This will be set once on resolution of the promise created by
  // getAzureOpenIdConfig().
  //
  // See the following reference for more details on validating the issuer:
  // https://docs.microsoft.com/en-us/azure/active-directory/develop/howto-convert-app-to-be-multi-tenant
  const issuers = (
    typeof config.tenantIds === 'string' ? [ config.tenantIds ] : config.tenantIds
  ).map((tenantId) => `https://sts.windows.net/${tenantId}/`);

  // The cache of public signing keys indexed by key ID.  This will be fetched
  // once at startup and refreshed whenever a token with an unknown key ID is
  // received (key roll on the Azure servers).  On failure to fetch, the keys
  // will be refreshed on the next token received.
  let publicKeys = null;

  // Kick off the request to get the 'common' tenant configuration and its
  // public keys by initiating a request to fetch a key ID that very likely
  // doesn't exist.
  //
  // We don't care about a failure here.  This is just to pre-populate our
  // cached data as soon as possible after startup.  If this fails, the next
  // token validation will kick new requests to get the missing data.
  getPublicKey('ignored').catch(() => {});

  /**
   * Validates the given JWT and returns the promise of the decoded token
   * object.
   *
   * @param {string} jwt
   *   JSON Web Token to be validated.
   *
   * @returns {Promise<object>}
   *   The promise of the decoded JWT object.  On failure, the promise will be
   *   rejected with an Error.
   */
  this.validateAndParseJwt = (jwt) => {
    // Synchronously decode the token so we can look at its header.  We need
    // the algorithm and key ID to validate this token.
    const decoded = webtoken.decode(jwt, { complete: true });

    // Did the decoding fail?
    if (! decoded || ! decoded.header) {
      return Promise.reject(
        new Errors.TokenInvalidError("Failed to decode token"));
    }

    // Make sure that the only signature algorithm we support is the one used.
    // In theory, we can support RS256, RS384, and RS512 with the current
    // implementation, however this has only been tested with RS256.
    if (decoded.header.alg !== 'RS256') {
      return Promise.reject(new Errors.TokenInvalidError("Bad algorithm"));
    }

    // Make sure we have a key ID we can use to identify which public key we
    // need to use from the keys we fetch.
    const jwtKid = decoded.header.kid;
    if (! jwtKid) {
      return Promise.reject(new Errors.TokenInvalidError("Missing Key ID"));
    }

    // Get the key we need to validate the signature.
    return getPublicKey(jwtKid)
    .then(publicKey => {
      // Verify the token with the public key that was identified in the
      // token.
      return new Promise((resolve, reject) => {
        webtoken.verify(
          jwt,
          publicKey,
          {
            // We only support the RSA-256 signature algorithm.
            algorithms: ['RS256'],
            // Pass the configured application IDs straight through.  Only
            // tokens issued for the allowed applications will be allowed.
            audience: config.applicationIds,
            // Verify that the token was issued by one of the configured
            // tenants.  The issuers will be set at this point because we were
            // able to get the public keys for the 'common' tenant.
            issuer: issuers
          },
          (error, decoded) => {
            error ? reject(new Errors.TokenInvalidError(
                             `Token is invalid; error=${error}`))
                  : resolve(decoded);
          }
        );
      });
    });
  };

  /**
   * Gets the Azure OpenID config object for the 'common' tenant ID.
   *
   * @returns {Promise<object>}
   *   Promise of OpenID config.  The promise will be rejected with an Error
   *   on failure.
   *
   * @private
   */
  function getAzureOpenIdConfig() {
    // If we have a non-null value for the config already, just return it.
    // It's either an outstanding promise or one that was already resolved to
    // be the desired OpenId config.
    if (commonTidConfigPromise !== null) {
      return commonTidConfigPromise;
    }

  // The well known URL for fetching the configuration for the common tenant.
  const openIdConfigUrl =
    'https://login.windows.net/common/v2.0/.well-known/openid-configuration';

    // We don't yet have the config and we must request it.
    commonTidConfigPromise = getJson(openIdConfigUrl)
    .then(openIdConfig => {
      // Make sure that the issuers array value has been setup so that we
      // allow tokens issued from any supported tenant that was configured.
      const commonIssuer = openIdConfig.issuer;
      const tenants =
        (typeof config.tenantIds === 'string' ? [ config.tenantIds ]
                                              : config.tenantIds);

      for (const tenantId of tenants) {
        issuers.push(commonIssuer.replace('{tenantid}', tenantId));
      }

      // Resolve the promise with the updated openIdConfig.
      return openIdConfig;
    })
    .catch(error => {
      // Reset the commonTidConfigPromise value so that we can re-request the
      // OpenID config again later.
      commonTidConfigPromise = null;
      throw new Error(`Failed to fetch OpenID config; error=${error}`);
    });

    // Return the request promise which will resolve to the desired OpenID
    // configuration.
    return commonTidConfigPromise;
  }

  /**
   * Gets the public key identified by jwtKid.  If the key ID is not known the
   * keys will be (re)fetched from the jwks_uri of the commonTidConfigPromise.
   *
   * @param {string} jwtKid
   *   The identifier for the public key to be used to validate a token.
   *
   * @returns {Promise<string>}
   *   The promise of the requested public key.  The promise will be rejected
   *   with an error on failure.
   */
  function getPublicKey(jwtKid) {
    // If we already have cached the public keys, try there first.
    if (publicKeys instanceof Map) {
      const key = publicKeys.get(jwtKid);
      if (key) {
        return Promise.resolve(key);
      }

      // The key is not known, so we must re-request the public keys before
      // proceeding.
      publicKeys = null;
    }

    // We didn't find the key in our cache.  If we need to make a request to
    // fetch the public keys the publicKeys will be null.
    if (publicKeys === null) {
      // Remember the promise to fetch the keys.  This will also (re)request
      // the OpenID config if it previously failed.
      publicKeys = getAzureOpenIdConfig()
      .then(openIdConfig => getJson(openIdConfig.jwks_uri))
      .then(json => {
        if (! json.keys) {
          throw new Error('No keys in response');
        }

        // We have keys, so make a new map for them.
        publicKeys = new Map();

        // Convert each key modulus and exponent to its PEM encoded
        // equivalent.
        for (const key of json.keys) {
          try { publicKeys.set(key.kid, getPemPublicKey(key)); }
          catch(error) {
            console.error(`Ignoring key.id=${key.id}; error=${error}`);
          }
        }
      })
      .catch(error => {
        // Reset the publicKeys so we can re-request the keys later.
        publicKeys = null;

        // Rethrow the error to fail validation.
        throw error;
      });
    }

    // Once here, we know that the publicKeys variable is pointing to a
    // promise for an outstanding request.  So, we need to wait on that
    // promise before we can lookup the desired public key.
    return publicKeys
    .then(() => {
      // If we don't have the public key after the request promise resolved,
      // then we can fail the validation.
      if (! publicKeys.has(jwtKid)) {
        throw new Error(`No such key; kid=${jwtKid}`);
      }
      return publicKeys.get(jwtKid);
    });
  }
};

/**
 * Fetches JSON data from the given url.
 *
 * @param {string} url
 *   The URL from which the JSON data is to be fetched.
 *
 * @returns {Promise<Object>}
 *   The promise of the JSON object that was fetched.  On failure the promise
 *   will be rejected with an Error.
 */
function getJson(url) {
  return new Promise((resolve, reject) => {
    request.get({ json: true, url: url }, (error, response, json) => {
      if (error) {
        reject(error);
        return;
      }
      if (response.statusCode !== 200) {
        reject(new Error(
          `Unexpected status=${response.statusCode} for request=${url}`));
        return;
      }

      resolve(json);
    });
  });
}

/**
 * Returns the ASN.1 DER encoded modulus (n) and exponent (e).  We use the
 * PKCS#1 format.
 *
 * As per: https://tools.ietf.org/html/rfc8017#section-3.1
 * RSAPublicKey ::= SEQUENCE {
 *   modulus           INTEGER,  -- n
 *   publicExponent    INTEGER   -- e
 * }
 */
const RsaPublicKey = asn1.define('RsaPublicKey', function() {
  this.seq().obj(
    this.key('n').int(),
    this.key('e').int()
  );
});

/**
 * Node crypto only accepts PEM encoded public keys.  This will take the given
 * RSA JWK public key (PKCS#1) containing a modulus and exponent and create an
 * ASN.1 DER PEM encoded version of the public key.
 *
 * @param {object} jwk
 *   A JWK object that conforms to the structure defined in
 *   [RFC 7517]{@link https://tools.ietf.org/html/rfc7517}.
 *
 * @returns {string}
 *   The ASN.1 DER (PKCS#1) PEM encoded public key.
 *
 * @throws {Error}
 *   An error will be thrown if the given jwk object does not meet
 *   expectations.
 *
 * @private
 */
function getPemPublicKey(jwk) {
  // Don't process a JWK object that doesn't meet our expectations.
  if (
    // Make sure we were given an RSA key type.
    jwk.kty !== 'RSA' ||
    // Make sure that we were given the modulus and exponent values for the
    // public key (n and e, respectively).
    typeof jwk.n !== 'string' || typeof jwk.e !== 'string'
  ) {
    throw new Error("Invalid key");
  }

  // The modulus (n) and the exponent (e) are base64url encoded positive
  // integers.
  //
  // Upon decoding, the resulting buffers represent a big number, in base 256,
  // with the most significant digit first.  This encoding lacks the leading
  // 0x00 byte for numbers whose most significant digit is >= 0x80 as is
  // expected for a DER encoded integer.
  //
  // To ensure that the correct DER encoding for an integer is obtained, we
  // first convert the base64url decoded integers to a bignum value before
  // calling the encode() API.
  //
  // NOTE: the asn1.js's encode API allows us to pass the base64url decoded
  // value directly, however, the subtlety of the leading 0x00 is lost.
  //
  // RFC 7468 section 13 specifies that the label for a public key is 'PUBLIC
  // KEY'.  We use a label of 'RSA PUBLIC KEY' because this is the value
  // expected by PEM_read_bio_PUBKEY in the underlying OpenSSL implementation.
  //
  // Please see:
  //  * https://tools.ietf.org/html/rfc8017#section-4.2
  //  * ftp://ftp.rsasecurity.com/pub/pkcs/ascii/layman.asc (section 5.7)
  //  * https://tools.ietf.org/html/rfc7468#section-13
  //
  // The Buffer base64 encoding accepts the URL safe alphabet:
  // https://nodejs.org/docs/latest-v6.x/api/buffer.html#buffer_class_method_buffer_from_string_encoding
  return RsaPublicKey.encode({
    n: new asn1.bignum(Buffer.from(jwk.n, 'base64')),
    e: new asn1.bignum(Buffer.from(jwk.e, 'base64'))
  }, 'pem', { label: 'RSA PUBLIC KEY'});
}
