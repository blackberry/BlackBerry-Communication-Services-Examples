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

// If uid is the caller, returns public and private keys. Otherwise returns
// public keys of specific uid.
app.get('/kms/:uid', (req, res) => {
  var uid = req.params.uid;
  var jwt = req.get('Authorization');
  if (jwt.startsWith('Bearer ')) {
    jwt = jwt.slice('Bearer '.length);
  }
  console.log(`\n${getTimeStamp()} : [GET] /kms/${uid}`);
  console.log(`TOKEN: ${jwt}`);
  return tokenManager.validateAndParseJwt(jwt)
  .then(decodedToken => {
    if (uid === decodedToken.oid) {
      // Get private and public keys.
      return db.getRecord(uid)
      .then(record => res.status(HTTP_STATUS_SUCCESS).json(record))
      .catch(error => {
        res.status(error.code === 'ResourceNotFound'
          ? HTTP_STATUS_NOT_FOUND
          : HTTP_STATUS_SERVER_ERROR).json({ error: error });
      });
    }
    else {
      // Get public keys only.
      return db.getPublicKeys([uid])
      .then(records =>
        res.status(HTTP_STATUS_SUCCESS).json({ public: records[uid] }))
      .catch(error => {
        res.status(error.code === 'ResourceNotFound'
          ? HTTP_STATUS_NOT_FOUND
          : HTTP_STATUS_SERVER_ERROR).json({ error: error });
      });
    }
  })
  .catch(error => {
    res.status(HTTP_STATUS_FORBIDDEN).json({ error: error });
  });
});

// Stores key information provided in 'keys' parameter for specified uid.
app.put('/kms/:uid', (req, res) => {
  const uid = req.params.uid;
  var jwt = req.get('Authorization');
  if (jwt.startsWith('Bearer ')) {
    jwt = jwt.slice('Bearer '.length);
  }
  var keysData = req.body.keys;
  const replace = req.body.replace;
  console.log(`\n${getTimeStamp()} : [PUT] /kms/${uid}`);
  console.log(`TOKEN: ${jwt}`);
  console.log(`BODY: ${JSON.stringify(req.body)}`);
  return tokenManager.validateAndParseJwt(jwt)
  .then(decodedToken => {
    if (uid !== decodedToken.oid) {
      const error = new Errors.InvalidUidError(`Not allowed to write to: ${uid}`
        + ` | User ID: ${decodedToken.oid}`);
      res.status(HTTP_STATUS_FORBIDDEN).json({ error: error });
      return;
    }

    if (!keysData) {
      const error = new Errors.IncompleteDataError
        ('Keys data is not provided in the request body');
        console.log(`Failed to save keys: ${error.message}`);
      res.status(HTTP_STATUS_BAD_REQUEST).json({ error: error });
      return;
    }

    if (replace === true) {
      // Expect new record to have public and private properties.
      if (keysData.public && keysData.private) {
        return db.setRecord(uid, keysData).then(() => {
          res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK);
        }).catch(error => {
          res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
        });
      } else {
        const error = new Errors.IncompleteDataError
          ('Must have public and private keys to replace');
        res.status(HTTP_STATUS_BAD_REQUEST).json({ error: error });
      }
      return;
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
      .then(() => res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK))
      .catch(error => {
        res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
      });
    }).catch(error => {
      if (error.code === 'ResourceNotFound') {
        // Save new record.
        return db.setRecord(uid, keysData)
        .then(() => res.status(HTTP_STATUS_SUCCESS).json(STATUS_OK))
        .catch(error => {
          res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
        });
      }
      else {
        res.status(HTTP_STATUS_SERVER_ERROR).json({ error: error });
      }
    });
  }).catch(error => {
    console.log(`Failed to save keys: ${error.message}`);
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
}

// Generates current time stamp. Required to log incoming requests.
const getTimeStamp = () => (new Date()).toLocaleString();

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
    () => console.log(`${getTimeStamp()} Started server.`
      + ` Port: ${config.serverPort}`));
}
else {
  app.listen(config.serverPort, () => {
    console.log(`${getTimeStamp()} Started server.`
      + ` Port: ${config.serverPort}`);
  });
}


