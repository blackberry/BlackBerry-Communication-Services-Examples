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

//#region Initialization

const HTTP_STATUS_SUCCESS = 200;
const HTTP_STATUS_BAD_REQUEST = 400;
const HTTP_STATUS_FORBIDDEN = 401;
const HTTP_STATUS_NOT_FOUND = 404;
const HTTP_STATUS_SERVER_ERROR = 500;

const STATUS_OK = { status: 'OK' };

const TokenManager = require('./tokenManagement');
const DbAccess = require('./databaseAccess');
const express = require('express');
const https = require('https');
const fs = require('fs');
const bodyParser = require('body-parser');
const config = require('./config');
const Errors = require('./errors');

const app = express();

// Define CORS rules: allowed origin, headers, and methods.
app.use(function(req, res, next) {
  res.header('Access-Control-Allow-Origin', config.accessControlAllowOrigin);
  res.header('Access-Control-Allow-Headers', config.accessControlAllowHeaders);
  res.header('Access-Control-Allow-Methods', config.accessControlAllowedMethods);
  next();
});

// User body parser.
app.use(bodyParser.json({ extended: true }));

var tokenManager = new TokenManager();
var db = new DbAccess();

//#endregion Initialization

//#region API

let requestIdCounter = 0;

// Logs the given message prefixed with the current date and time.
function log(message) {
  const timePrefix = (new Date()).toLocaleString();
  console.log(`${timePrefix} : ${message}`);
}

// If uid is the caller, returns public and private keys. Otherwise returns
// public keys of specific uid.
app.get('/kms/:uid', (req, res) => {
  const requestId = ++requestIdCounter;

  var uid = req.params.uid;
  var jwt = req.get('Authorization');
  if (jwt.startsWith('Bearer ')) {
    jwt = jwt.slice('Bearer '.length);
  }

  const user = { id: '' };
  const logPrefix = () => {
    const userId = user.id ? ` u=${user.id}` : '';
    return `rId=${requestId}${userId} [GET] /kms/${uid}`;
  };
  log(logPrefix());

  return tokenManager.validateAndParseJwt(jwt)
  .then(decodedToken => {
    user.id = decodedToken.oid;

    if (user.id === uid) {
      // Get private and public keys.
      return db.getRecord(uid)
      .then(record => {
        log(`${logPrefix()}; private and public keys were returned`);
        res.status(HTTP_STATUS_SUCCESS).json(record);
      })
      .catch(error => {
        log(`${logPrefix()}; error=${error}; code=${error.code}`);
        res.status(error.code === 'ResourceNotFound'
          ? HTTP_STATUS_NOT_FOUND
          : HTTP_STATUS_SERVER_ERROR).json({ error: error });
      });
    }
    else {
      // Get public keys only.
      return db.getPublicKeys([uid])
      .then(records => {
        log(`${logPrefix()}; public keys were returned`);
        res.status(HTTP_STATUS_SUCCESS).json({ public: records[uid] });
      })
      .catch(error => {
        log(`${logPrefix()}; error=${error}; code=${error.code}`);
        res.status(error.code === 'ResourceNotFound'
          ? HTTP_STATUS_NOT_FOUND
          : HTTP_STATUS_SERVER_ERROR).json({ error: error });
      });
    }
  })
  .catch(error => {
    log(`${logPrefix()}; error=${error}`);
    res.status(HTTP_STATUS_FORBIDDEN).json({ error: error });
  });
});

// Stores key information provided in 'keys' parameter for specified uid.
app.put('/kms/:uid', (req, res) => {
  const requestId = ++requestIdCounter;

  const uid = req.params.uid;
  var jwt = req.get('Authorization');
  if (jwt.startsWith('Bearer ')) {
    jwt = jwt.slice('Bearer '.length);
  }

  const user = { id: '' };
  const logPrefix = () => {
    const userId = user.id ? ` u=${user.id}` : '';
    return `rId=${requestId}${userId} [PUT] /kms/${uid}`;
  };
  log(logPrefix());

  var keysData = req.body.keys;
  const replace = req.body.replace;
  return tokenManager.validateAndParseJwt(jwt)
  .then(decodedToken => {
    user.id = decodedToken.oid;

    if (user.id !== uid) {
      const error = new Errors.InvalidUidError(
        `Not allowed to write to: ${uid} | User ID: ${user.id}`);
      log(`${logPrefix()}; error=${error}`);
      res.status(HTTP_STATUS_FORBIDDEN).json({ error: error });
      return undefined;
    }

    if (!keysData) {
      const error = new Errors.IncompleteDataError
        ('Keys data was not provided in the request body');
      log(`${logPrefix()}; error=${error}`);
      res.status(HTTP_STATUS_BAD_REQUEST).json({ error: error });
      return undefined;
    }

    if (replace === true) {
      // Expect new record to have public and private properties.
      if (keysData.public && keysData.private) {
        return db.setRecord(uid, keysData).then(() => {
          log(`${logPrefix()}; private and public keys were replaced`);
          res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK);
          return undefined;
        })
        .catch(error => {
          log(`${logPrefix()}; error=${error}`);
          res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
          return undefined;
        });
      }
      else {
        const error = new Errors.IncompleteDataError
          ('New data must have public and private keys for replacement');
        log(`${logPrefix()}; error=${error}`);
        res.status(HTTP_STATUS_BAD_REQUEST).json({ error: error });
        return undefined;
      }
    }

    return db.getRecord(uid).then(record => {
      // Update public section.
      if (keysData.public !== undefined) {
        updateObject(record.public, keysData.public);
      }
      // Update private section.
      if (keysData.private !== undefined) {
        updateObject(record.private, keysData.private);
      }
      // Write updated record to the database.
      return db.setRecord(uid, record)
      .then(() => {
        log(`${logPrefix()}; record was updated`);
        res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK);
        return undefined;
      })
      .catch(error => {
        res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
        return undefined;
      });
    })
    .catch(error => {
      if (error.code === 'ResourceNotFound') {
        // Save new record.
        return db.setRecord(uid, keysData)
        .then(() => {
        log(`${logPrefix()}; new record was inserted`);
          res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK);
          return undefined;
        })
        .catch(error => {
          log(`${logPrefix()}; error=${error}`);
          res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
          return undefined;
        });
      }
      else {
        log(`${logPrefix()}; error=${error}`);
        res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
        return undefined;
      }
    });
  })
  .catch(error => {
    log(`${logPrefix()}; error=${error}`);
    res.status(HTTP_STATUS_FORBIDDEN).json({ error: error });
  });
});

//#endregion API

//#region Helper functions

// Merges existingObject with newObject. If newObject property is 'null', then
// removes this property from the existing Object.
const updateObject = (existingObject, newObject) => {
  Object.keys(newObject).forEach(key => {
    if (newObject[key] === 'null' || newObject[key] === null) {
      if (existingObject[key] !== undefined) {
        // Delete all keys which are set to 'null' in the new record.
        delete existingObject[key];
      }
    }
    else {
      // Update existing record keys with the new record keys.
      if (key === 'mailboxes') {
        if (!existingObject[key]) {
          existingObject[key] = {};
        }
        updateObject(existingObject[key], newObject[key]);
      } else {
        existingObject[key] = newObject[key];
      }
    }
  });
};

//#endregion Helper functions

// Start server.
if (config.useSsl) {
  // Configure SSL certificates.
  const options = {
    key: fs.readFileSync(config.keyPath),
    cert: fs.readFileSync(config.certPath),
    passphrase: config.keyPassphrase
  };
  https.createServer(options, app).listen(config.serverPort,
    () => log(`Started server on port: ${config.serverPort}; TLS/SSL enabled`));
}
else {
  app.listen(config.serverPort, () => {
    log(`Started server on port: ${config.serverPort}; WARNING TLS/SSL disabled`);
  });
}

