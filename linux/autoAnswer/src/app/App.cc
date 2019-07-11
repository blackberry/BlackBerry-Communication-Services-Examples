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

#include <bbm/sdk/Sdk.hh>
#include <bbm/sdk/exceptions.hh>

#include <jsoncpp/json/json.h>

#include <fstream>
#include <iostream>

using namespace app;

//=============================================================================
// :: Construction

//-----------------------------------------------------------------------------

App::App(bbm::sdk::Sdk& sdk,
         const std::string& directory)
  : BbmdsApp(sdk, directory)
{
  // Set the callback interfaces.  We don't need to clear them since we're the
  // driver so no events can happen after we're destroyed.
  m_sdk.setCallCallbacks(this);

  std::cout
    << "Available call features are "
    << m_sdk.getAvailableCallFeatures() << ".\n";
}

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
      // Only look at messages with the 'I' incoming flag (i.e. from others)
      // that are tagged as normal 'Text' messages.
      if(elem["flags"].asString().find('I') == std::string::npos
         || elem["tag"] != "Text")
      {
        continue;
      }

      // Send a hard-coded response.
      Json::Value root;
      {
        auto& msg = root["chatMessageSend"];
        msg["chatId"] = elem["chatId"].asString();
        msg["tag"] = "Text";
        msg["content"] = "Call me. I'll answer.";
      }
      messageToService(common::JsonWriter{}.write(root));
    }
  }

  // Ignore everything else.
}

//=============================================================================
// :: Inherited CallCallbacks Interface

//-----------------------------------------------------------------------------

void
App::callOffered(const bbm::sdk::call::Offer& offer)
{
  // Tell the user.
  std::cout << offer << "\n";
  
  // If it's a data call, ignore it.
  if(offer.isDataOnly())
  {
    std::cout << "Ignoring data only call.\n";
    return;
  }
  
  // Update current call data.
  prv_updateCurrentCall(offer);

  // Auto answer incoming calls.
  std::cout << "Automatically answering incoming call.\n";
    
  // Answer it, and report any error.
  try
  {
    m_sdk.answerCall(m_currentCall->id);
  }
  catch(const bbm::sdk::exception& ex)
  {
    std::cout
      << "Can't answer call id " << m_currentCall->id << ": " << ex.what()
      << '\n';
  }
}

//-----------------------------------------------------------------------------

void
App::callChanged(const bbm::sdk::call::Change& change)
{
  // Tell the user.
  std::cout << change << "\n";

  // Has the call connected?
  if(change.getState() == bbm::sdk::call::State::Connected)
  {    
    // Answered successfully, start flashing the light.
    prv_toggleLight(true);
  }
  // Is this a call ending?
  else if(change.getState() == bbm::sdk::call::State::Disconnected)
  {
    // Is this is the current call ending?  If it's not, that's weird, but
    // allow it.
    if(m_currentCall && m_currentCall->id != change.getId())
    {
      std::cout << "Ignoring " << change << " for unknown call id.\n";
      return;
    }

    // Forget the current call.
    m_currentCall.release();

    // Return the light to its default setting.
    prv_toggleLight(false);

    // There's nothing else to do when a call ends.
    return;
  }

  // Update current call data.
  prv_updateCurrentCall(change);
}

//-----------------------------------------------------------------------------

void
App::callMediaChanged(const bbm::sdk::call::MediaChange& mediaChange)
{
  // Tell the user.
  std::cout << mediaChange << "\n";

  // Update current call data.
  prv_updateCurrentCall(mediaChange);

  // Look for media first getting connected.
  if(not m_currentCall->media)
  {
    // Remember that the current call's media has been established.
    m_currentCall->media = true;
    
    // If we have a camera, turn it on now.
    if(not m_currentCall->camera)
    {
      prv_toggleCamera();
    }
  }
}

//-----------------------------------------------------------------------------

void
App::cameraStateSet(const bool success)
{
  // Tell the user.
  if(success)
  {
    std::cout << "Toggled camera on current call.\n";
  }
  else
  {
    std::cout << "Couldn't toggle camera on current call.\n";
  }
}

//=============================================================================
// :: Helpers

//-----------------------------------------------------------------------------

void
App::prv_toggleLight(const bool on) const
{
  // Attempt to open the control file for the on-board red LED.
  const std::string path("/sys/class/leds/led1/trigger");
  std::ofstream control(path);
  if(not control)
  {
    std::cout << "Unable to control LED at " << path << "\n";
    return;
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
}

//-----------------------------------------------------------------------------

void
App::prv_toggleCamera()
{
  // Is there a call going on?
  if(not m_currentCall)
  {
    std::cout << "Can't toggle camera when there is no current call.\n";
    return;
  }

  // Is media already established?
  if(not m_currentCall->media)
  {
    std::cout << "Can't toggle camera when media isn't yet established.\n";
    return;
  }
  
  // Figure out if the camera is on or off and pick the opposite.
  const bool enable = not m_currentCall->camera;

  // Emit what we're trying to do first.
  std::cout
    << "Turning camera " << (enable ? "on" : "off")
    << " for call id " << m_currentCall->id << ".\n";
                               
  // The action is asynchronous, but the callbacks can't carry state.  So we
  // presume it works if the enable function returns successfully.
  try
  {
    m_sdk.setCameraState(enable);
    m_currentCall->camera = enable;
  }
  catch(const bbm::sdk::exception& ex)
  {
    std::cout << "Can't toggle camera: " << ex.what() << '\n';
  }
}

//*****************************************************************************
