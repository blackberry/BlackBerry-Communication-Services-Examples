/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
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
// A module that provides access to a chatbot service using the REST api of
// www.botlibre.com
//
// This module is provided as an example only. Botlibre is not affiliated with
// BBM Enterprise, and using it requires agreeing to its terms of service, which
// are availble here at the time of this writing:
// https://www.botlibre.com/terms.jsp
//

var http = require('https');
var xml = require('xml');
var xml2js = require('xml2js');

module.exports = function(application, instance) {
  return function(message) {
    return new Promise(function(resolve, reject) {
      var req = http.request(
      {headers:{'Content-Type':'application/xml'}, hostname:'www.botlibre.com', port:443, path:'/rest/api/chat', method:'POST'}, function(response) {
        response.on('data', function(data) {
          var dataAsString = Buffer.from(data, 'binary').toString();
          xml2js.parseString(dataAsString, function(err, result) {
            if(!err) {
              if(result.response && result.response.message) {
                resolve(result.response.message[0]);
              } else {
                resolve(dataAsString);
              }
            } else {
              resolve(dataAsString);
            }
          });
        });
        response.on('error', function(error) {
          reject(error);
        });
      });

      var xmlMessage = xml({
        chat:[
          {_attr:{application: application,
                  instance: instance}},
          {message:message}
        ]
      });
      req.write(xmlMessage);
      req.end();
    });
  };
};
