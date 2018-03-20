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

module.exports = {
  TokenInvalidError: function(message) {
    this.name = 'TokenInvalidError';
    this.message = (message || '');
  },
  // JWT provided by client is expired
  TokenExpiredError: function(message) {
    this.name = 'TokenExpiredError';
    this.message = (message || '');
  },
  // Failed to get Open Id Configuration for the provided JWT
  TokenOpenIdError: function(message) {
    this.name = 'TokenOpenIdError';
    this.message = (message || '');
  },
  // The Application Id doesn't match to the one configured on the server
  InvalidAppIdError: function(message) {
    this.name = 'InvalidAppIdError';
    this.message = (message || '');
  },
  // The UID passed by the client is invalid
  InvalidUidError: function(message) {
    this.name = 'InvalidUidError';
    this.message = (message || '');
  },
  // Client passed incomplete data
  IncompleteDataError: function(message) {
    this.name = 'IncompleteDataError';
    this.message = (message || '');
  },
  // Database access layer failed to perform an operation
  DataAccessError: function(message, code) {
    this.name = 'DataAccessError';
    this.message = (message || '');
    this.code = code
  },
}
