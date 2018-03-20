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
 * Module contains set of functions to manipulate data on Cosmos DB database.
 */
module.exports = function() {
  const storage = require('azure-storage');
  const config = require('./config');
  const Errors = require('./errors');

  var tableService = storage.createTableService(config.connectionString);
  var entityGen = storage.TableUtilities.entityGenerator;
  var collection = config.collectionName;
  var partition = config.collectionPartition;

  /**
   * Retrieves record from the collection.
   * @param {string} rowId The Row Id of the entity.
   * @returns {Promise<object>} In case of success promise resolves with an
   * object which contains private and public keys. In case of failure rejects
   * with Errors.DataAccessError which contains the error message and code
   * returned by 'azure-storage'
   */
  this.getRecord = rowId => new Promise((resolve, reject) => {
    // Validate provided rowId has valid format.
    if (!(config.userIdRegex.test(rowId))) {
      const error = new Errors.DataAccessError(`Invalid row Id format: ${rowId}`);
      console.warn(`Failed to get record | Error: ${error.message}`);
      throw(error);
    }
    tableService.retrieveEntity(collection, partition, rowId,
    (error, result, resp) => {
      if (!error) {
        try {
          var ret = {
            public: JSON.parse(result.public._),
            private: JSON.parse(result.private._)
          }
          resolve(ret);
        }
        catch(error) {
          console.warn(`Failed to parse entity properties of ${rowId}`
            + ` | Error: ${error.message}`
            + error.code ? ` | ${error.code}` : ``);
          reject(new Errors.DataAccessError(error.message, error.code));
        }
      }
      else {
        console.warn(`Failed to get entity of ${rowId} | Error: ${error.message}`
          + error.code ? ` | ${error.code}` : ``);
        reject(new Errors.DataAccessError(error.message, error.code));
      }
    });
  });

  /**
   * Stores record to the collection. If record exists - replaces existing
   * record. If record doesn't exist - creates new one.
   * @param {string} rowId The Row Id of the entity to be stored.
   * @returns {Promise} In case of success promise resolves with no parameters.
   * In case of failure rejects with Errors.DataAccessError which contains the 
   * error message and code returned by 'azure-storage'
   */
  this.setRecord = (rowId, value) => new Promise((resolve, reject) => {
    // Validate provided rowId has valid format.
    if (!(config.userIdRegex.test(rowId))) {
      const error = new Errors.DataAccessError(`Invalid row Id format: ${rowId}`);
      console.warn(`Failed to set record | Error: ${error.message}`);
      throw(error);
    }
    // Check if entity exists.
    tableService.retrieveEntity(collection, partition, rowId,
    (error, entity, response) => {
      if (!error) {
        // Entity exists - update it with the new values.
        try {
          entity.public = entityGen.String(JSON.stringify(value.public));
          entity.private = entityGen.String(JSON.stringify(value.private));
          tableService.replaceEntity(collection, entity,
          (error, response) => {
            if (!error) {
              // Updated existing entity successfully.
              resolve();
            }
            else {
              // Failed to update existing entity.
              console.warn(`Failed to update entity: ${rowId}`
                + ` | Error: ${error.message}`);
              reject(new Errors.DataAccessError(error.message, error.code));
            }
          });
        }
        catch(error) {
          // Catch potential JSON serialization exceptions.
          console.warn(`Failed to update entity: ${rowId}`
            + ` | Error: ${error.message}`);
          reject(new Errors.DataAccessError(error.message, error.code));
        }
      }
      else {
        if (error.code === 'ResourceNotFound') {
          // Entity doesn't exist, create new one.
          try {
            var entity = {
              PartitionKey: entityGen.String(partition),
              RowKey: entityGen.String(rowId),
              public: entityGen.String(JSON.stringify(value.public)),
              private: entityGen.String(JSON.stringify(value.private))
            }
            // Insert new entity to the collection.
            tableService.insertEntity(collection, entity,
            (error, result, response) => {
              if (!error) {
                // Successfully inserted new entity to the collection.
                resolve();
              }
              else {
                // Failed to insert new entity to the collection.
                console.warn(`Failed to insert entity: ${rowId}`
                  + ` | Error: ${error.message}`);
                reject(new Errors.DataAccessError(error.message, error.code));
              }
            });
          }
          catch(error) {
            // Catch potential JSON serialization exceptions.
            console.warn(`Failed to insert entity: ${rowId}`
              + ` | Error: ${error.message}`);
            reject(new Errors.DataAccessError(error.message, error.code));
          };
        }
        else {
          // There was DB error when tried to check if entity exists.
          console.warn(`Failed to retrieve entity: ${rowId}`
            + ` | Error: ${error.message}`);
          reject(new Errors.DataAccessError(error.message, error.code));
        }
      }
    });
  });

  /**
   * Retrieves public section of the entities by provided collection of Row Id.
   * @param {string[]} rowIds Array of Row Ids.
   * @returns {Promise<object>} In case of success resolves with the collection
   * of public keys. In case of failure rejects with Errors.DataAccessError
   * which contains the error message and code returned by 'azure-storage'
   */
  this.getPublicKeys = rowIds => new Promise((resolve, reject) => {
    rowIds.forEach(rowId => {
      // Validate provided rowId has valid format.
      if (!(config.userIdRegex.test(rowId))) {
        const error =
          new Errors.DataAccessError(`Invalid row Id format: ${rowId}`);
        console.warn(`Failed to get public keys | Error: ${error.message}`);
        throw(error);
      }
    });
    const queryString = Array(rowIds.length);
    queryString.fill(`RowKey eq ?`);
    var query = new storage.TableQuery().where(queryString.join(' or '),
      ...rowIds);
    tableService.queryEntities(collection, query, null,
    (error, result, response) => {
      if (!error && result) {
        if (result.entries.length === 0) {
          let error = new Errors.DataAccessError('Record does not exist');
          error.code = 'ResourceNotFound';
          console.warn(`Failed to get public keys | Error: ${error.message}`);
          reject(new Errors.DataAccessError(error.message, error.code));
        } 
        else {
          try {
            var ret = {};
            result.entries.forEach(entry => {
              ret[entry.RowKey._] = JSON.parse(entry.public._);
            });
            resolve(ret);
          }
          catch (error) {
            // Catch potential JSON serialization exceptions.
            console.warn('Failed to parse entity properties'
              + ` | Error: ${error.message}`);
            reject(new Errors.DataAccessError(error.message, error.code));
          }
        }
      }
      else {
        console.warn(`Failed to query entities | Error : ${error.message}`);
        reject(new Errors.DataAccessError(error.message, error.code));
      }
    });
  });
}
