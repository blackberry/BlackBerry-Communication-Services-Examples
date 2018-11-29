//*****************************************************************************
// Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "App.hh"

#include <common/JsonWriter.hh>

#include <jsoncpp/json/json.h>

#include <algorithm>
#include <iostream>

using namespace app;

//=============================================================================
// :: Construction

//----------------------------------------------------------------------------

App::App(bbm::sdk::Sdk& sdk,
         const std::string& directory)
  : BbmdsApp(sdk, directory)
{
  // Set the callback to receive incoming BBMDS messages.
  setBbmdsCallback([this](const std::string& name,
                          const Json::Value& data)
                   { prv_receiveBbmds(name, data); });

  // Set the callback to receive command line input.
  setCommandLineCallback([this](std::string&& line)
                         { prv_handleCommandLineInput(std::move(line)); });
}

//=============================================================================
// :: Helpers

//-----------------------------------------------------------------------------

void
App::prv_receiveBbmds(const std::string& name,
                      const Json::Value& data)
{
  // We must wait for setup to complete before requesting the initial list of
  // endpoints.
  if((name == "listChange" || name == "listChunk")
     && data["type"].asString() == "global")
  {
    // Loop through the globals looking for setupState.
    for(const auto& elem : data["elements"])
    {
      if(elem["name"].asString() == "setupState")
      {
        const auto& state = elem["value"]["state"].asString();

        // If the state indicates registration completed.  Or, registration
        // failed due to too many endpoints.
        if(state == "Success" || state == "Full")
        {
          // We can now request the endpoint list.
          prv_getEndpoints();
        }
      }
    }
  }
  // Look for a response to our request for the list of endpoints.
  else if(name == "endpoints")
  {
    if(data["result"].asString() == "Success")
    {
      // The array of endpoints must be present in a successful response.
      const auto& endpoints = data["registeredEndpoints"];
      if(not endpoints.isArray())
      {
        std::cout << "Ignoring invalid data in BBMDS: " << name << "\n";
        return;
      }

      std::cout
        << "Endpoint lookup successful with " << endpoints.size() << " of "
        << data["maxEndpoints"].asString() << " endpoints.\n";

      if(endpoints.size() > 0)
      {
        // Emit each endpoint.
        std::size_t count = 0;
        for(const auto& endpoint : endpoints)
        {
          ++count;
          std::cout
            << count << ") "
            "Id: " << endpoint["endpointId"].asString() << "\n"
            "   Description: " << endpoint["description"].asString() << "\n"
            "   Nickname: " << endpoint["nickname"].asString() << "\n"
            "   IsCurrent: " << std::boolalpha
            << endpoint["isCurrent"].asBool() << std::noboolalpha << "\n";
        }

        prv_emitOptions();
      }
    }
    else
    {
      std::cout << "Endpoint lookup failed\n";
    }
  }
  // Look for a response to a request to delete an endpoint.
  else if(name == "endpointDeregisterResult")
  {
    std::cout
      << "Endpoint deregister result=" << data["result"].asString() << "\n";

    // Always follow up a deregister request with a request to refresh the
    // list.
    prv_getEndpoints();
  }

  // Ignore the rest.
}

//-----------------------------------------------------------------------------

void
App::prv_handleCommandLineInput(std::string&& line)
{
  // Empty input means exit.
  if(line.empty())
  {
    quit();
    return;
  }
  
  std::cout << "\nDeleting endpoint id: " << line << "\n";

  // Send the BBMDS message to the SDK.
  Json::Value root;
  {
    auto& msg = root["endpointDeregister"];
    msg["cookie"] = "c";
    msg["endpointId"] = line;
  }
  messageToService(common::JsonWriter{}.write(root));
}
                      
//----------------------------------------------------------------------------

void
App::prv_getEndpoints()
{
  // Request the endpoint list.
  Json::Value root;
  {
    auto& msg = root["endpointsGet"];
    msg["cookie"] = "c";
  }
  messageToService(common::JsonWriter{}.write(root));
}

//----------------------------------------------------------------------------

void
App::prv_emitOptions() const
{
  // Emit command line options.
  std::cout << "\nEnter an endpoint ID to be deleted or <Enter> to exit: ";
  std::cout.flush();
}

//*****************************************************************************
