//*****************************************************************************
// Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
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
#include <fstream>
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
}  

//=============================================================================
// :: Helpers

//-----------------------------------------------------------------------------

void
App::prv_receiveBbmds(const std::string& name,
                      const Json::Value& data)
{
  // Look for an incoming chat message. Ignore anything else.
  if(name == "listAdd" && data["type"].asString() == "chatMessage")
  {
    // Check each message.
    for(const auto& elem : data["elements"])
    {
      // Only look at messages with the 'I' incoming flag (i.e. from others).
      if(elem["flags"].asString().find('I') == std::string::npos)
      {
        continue;
      }

      // A helper to send a 'Text' message back in the same chat with \a
      // content.
      const auto respond =
        [this, chatId = elem["chatId"].asString()](const std::string& content)
      {
        Json::Value root;
        {
          auto& msg = root["chatMessageSend"];
          msg["chatId"] = chatId;
          msg["tag"] = "Text";
          msg["content"] = content;
        }
        messageToService(common::JsonWriter{}.write(root));
      };
      
      // Look for an LED control message.
      const auto& tag = elem["tag"];
      if(tag == "Blink")
      {
        // A blink message must contain further instructions in the data
        // field.
        const auto& data = elem["data"];
        if(not data.isObject())
        {
          respond("Invalid Blink request.");
          return;
        }
        const auto& action = data["action"].asString();

        // Validate the action.
        if(action != "start" && action != "stop")
        {
          respond("Invalid Blink action=" + action);
          return;
        }

        // Try to update the light's state.
        if(not prv_controlLight(action == "start"))
        {
          respond("Unable to control the light.");
          return;
        }

        // Acknowledge the request by marking the incoming message as read.
        Json::Value root;
        {
          auto& msg = root["chatMessageRead"];
          msg["chatId"] = elem["chatId"].asString();
          msg["messageId"] = elem["messageId"].asString();
        }
        messageToService(common::JsonWriter{}.write(root));
        
        // Send a response message with some indication as to the result.
        respond(std::string(action == "start" ? "Started" : "Stopped")
                + " flashing.");
        return;
      }
      
      // Else, check for a regular text message.
      if(tag == "Text")
      {
        // Send a response message with the same content back.
        respond(elem["content"].asString());
        return;
      }
    }
  }

  // Ignore everything else.
}

//-----------------------------------------------------------------------------

bool
App::prv_controlLight(const bool on)
{
  // Attempt to open the control file for the on-board red LED.
  const std::string path("/sys/class/leds/led1/trigger");
  std::ofstream control(path);
  if(not control)
  {
    std::cout << "Unable to control LED at " << path << "\n";
    return false;
  }

  if(on)
  {
    // This will flash the light once per second indefinitely. 
    control << "timer";
  }
  else
  {
    // This returns the light to its default setting which is usually an
    // always on power indicator.
    control << "input";
  }
  return true;
}

//*****************************************************************************
