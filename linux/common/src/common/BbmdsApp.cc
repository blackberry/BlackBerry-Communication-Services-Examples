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

#include "BbmdsApp.hh"
#include "JsonWriter.hh"
#include "SignalHandler.hh"
#include "token.hh"

#include <bbm/sdk/Sdk.hh>
#include <bbm/sdk/exceptions.hh>

#include <jsoncpp/json/json.h>

#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include <poll.h>

using namespace common;

//=============================================================================
// :: Construction

//-----------------------------------------------------------------------------

BbmdsApp::BbmdsApp(bbm::sdk::Sdk& sdk,
                   const std::string& directory)
  : m_sdk(sdk),
    m_directory(directory)
{
  // Set the callback interfaces.  We don't need to clear them since we're the
  // driver so no events can happen after we're destroyed.
  m_sdk.setBbmdsCallbacks(this);
}

//=============================================================================
// :: Interface

//-----------------------------------------------------------------------------

bool
BbmdsApp::run()
{
  // Read our identity or token file and generate the BBMDS authToken we'll
  // send as an example application.  If this fails, we stop right away.
  if(not prv_makeAuthToken())
  {
    return false;
  }
  
  // We're about to run.
  m_running = true;
  
  // Handle some signals by safely dispatching them to the loop.
  common::SignalHandler signalHandler({
    // Quit nicely on the usual signals.
    { SIGINT,   [this]{ quit(); }},
    { SIGTERM,  [this]{ quit(); }},
    { SIGQUIT,  [this]{ quit(); }}
  });

  // Start bbmcore!
  prv_startBbmcore();

  // The file descriptors we monitor are fixed, so keep it simple.
  struct Fd
  {
    enum : std::size_t
    {
      CommandLine = 0,
      Signals = 1,
      Sdk = 2,
        
      // The number of file descriptors we monitor.
      count
    };
  };

  // We'll poll these.
  struct ::pollfd fds[Fd::count];
  std::memset(&fds, 0, sizeof(fds));
  fds[Fd::CommandLine].fd     = 0;
  fds[Fd::CommandLine].events = POLLIN;
  fds[Fd::Signals].fd         = signalHandler.getFd();
  fds[Fd::Signals].events     = POLLIN;
  fds[Fd::Sdk].fd             = m_sdk.getFd();
  fds[Fd::Sdk].events         = POLLIN;

  // Until someone tells us to stop, run.
  while(m_running)
  {
    // Look for events.
    const auto n = ::poll(fds, sizeof(fds) / sizeof(fds[0]), -1);

    // Did we stop running?
    if(not m_running)
    {
      // Stop now.
      break;
    }

    // What happened?
    if(n == -1)
    {
      // Check m_running on signal.
      if(errno == EINTR)
      {
        continue;
      }
      const int error = errno;
      std::cout
        << "Error polling for new input: " << std::strerror(error) << '\n';
      return false;
    }

    // Any events to process?
    if(n == 0)
    {
      // No.
      continue;
    }

    // Handle signals.
    const auto mask = POLLIN | POLLERR | POLLHUP;
    // Keep it simple and monitor stdin all the time. This works because stdin
    // and thus cin is line buffered by default
    if(fds[Fd::CommandLine].revents & mask)
    {
      std::string line;
      std::getline(std::cin, line);

      // If we have a callback, pass it along.
      if(m_commandLineFunction)
      {
        m_commandLineFunction(std::move(line));
      }
    }
    if(fds[Fd::Signals].revents & mask)
    {
      signalHandler.read();
    }
    // Pop and process events from the SDK queue.
    if(fds[Fd::Sdk].revents & mask)
    {
      // Process events pending from the SDK.
      m_sdk.read();
    }
  }

  // Stop.
  return true;
}

//=============================================================================
// :: Inherited BbmdsCallbacks Interface

//-----------------------------------------------------------------------------

void
BbmdsApp::bbmcoreStarted()
{
  // Bbmcore has started. Ask for for the globals that we care about. The
  // response will decide what we do next.
  requestListElements(
    "global", "name",
    {"authTokenState", "endpointId", "syncPasscodeState", "setupState",
      "localUri"});
}

//-----------------------------------------------------------------------------

void
BbmdsApp::bbmcoreStopped(const bool fatalError)
{
  std::cout << "\nBbmcore has stopped. Shutting down.\n";
  quit();
}

//-----------------------------------------------------------------------------

void
BbmdsApp::receiveBbmds(std::string&& msg)
{
  Json::Value root;
  if(not Json::Reader{Json::Features::strictMode()}.parse(msg, root, false)
     || not root.isObject()
     || root.empty())
  {
    std::cout << "Failed to parse invalid BBMDS: " << msg << "\n";
    return;
  }

  // Get the message name and verify we have a data object.
  const auto& name = root.begin().name();
  const auto& data = *root.begin();
  if(not data.isObject())
  {
    std::cout << "Ignoring invalid data in BBMDS: " << msg << "\n";
    return;
  }
  
  // A helper to parse out a regId and PIN from a listChunk or listElements.
  const auto lookForIds = [this, &name](const auto& data)
    {
      for(const auto& elem : data["elements"])
      {
        // Is this us?
        if(elem["uri"].asString() != "bbmpim://user/id/0")
        {
          continue;
        }

        // Look for the regId in the element.
        const auto oldRegId = m_regId;
        const auto foundRegId = elem["regId"].asString();

        // For listChunk, copy whatever regId is in the element as our
        // regId.  If there's nothing, we don't have one.
        // 
        // For listChange, we can't remove the existing one, but can see a
        // change.
        //
        if(not foundRegId.empty() || name == "listChunk")
        {
          m_regId = foundRegId;
        }

        // Look for the PIN in the element.
        const auto oldPin = m_pin;
        const auto foundPin = elem["pin"].asString();

        // For listChunk, copy whatever pin is in the element as our
        // pin.  If there's nothing, we don't have one.
        // 
        // For listChange, we can't remove the existing one, but can see a
        // change.
        //
        if(not foundPin.empty() || name == "listChunk")
        {
          m_pin = foundPin;
        }

        // Did anything change?
        if(m_regId != oldRegId || m_pin != oldPin)
        {
          std::cout
            << "Profile regId: " << m_regId << " PIN: " << m_pin << "\n";
        }
          
        // We're done examining this.
        break;
      }
    };

  // What is it?    
  if(name == "listChunk")
  {
    const auto& type = data["type"].asString();

    // Is it the user list?  Then try to pick out our regId and PIN.
    if(type == "user")
    {
      // Look for and emit the ids for the local user.
      lookForIds(data);
    }
    // Is this an update of globals?
    else if(type == "global")
    {
      // Parse the globals we're interested in.
      prv_handleGlobals(data["elements"]);
    }
  }
  else if(name == "listChange")
  {
    const auto& type = data["type"].asString();

    // Look for a user's regId changing via listChange.
    if(type == "user")
    {
      // Look for and emit the ids for the local user.
      lookForIds(data);
    }

    // Has a global changed?
    else if(type == "global")
    {
      // Parse the globals we're interested in.
      prv_handleGlobals(data["elements"]);
    }
  }
  else if(name == "setupError" || name == "syncError")
  {
    // We sent an 'authToken' or 'syncStart' and it ended badly. For this demo
    // app, there's not much to be done.
    std::cout
      << "Received " << name << "=" << data["error"].asString() << "\n";
    quit();
    return;
  }

  // If we have a callback set, pass along the received message for further
  // processing.
  if(m_receiveCallback)
  {
    m_receiveCallback(name, data);
  }
}

//=============================================================================
// :: Protected Helpers

//-----------------------------------------------------------------------------

void
BbmdsApp::requestListElements(const std::string& type,
                              const std::string& key,
                              const std::vector<std::string>& values)
{
  Json::Value root;
  {
    auto& msg = root["requestListElements"];
    msg["type"] = type;
    {
      auto& elements = msg["elements"];
      for(const auto& value : values)
      {
        auto& element = elements[elements.size()];
        element[key] = value;
      }
    }
  }

  messageToService(JsonWriter{}.write(root));
}

//-----------------------------------------------------------------------------

void
BbmdsApp::messageToService(std::string message)
{
  // Report failures.
  if(__builtin_expect(not m_sdk.sendBbmds(std::move(message)), false))
  {
    std::cout << "Error sending message to bbmcore.\n";

    // This should never happen and if it does, something has gone horribly
    // wrong.
    quit();
  }
}

//=============================================================================
// :: Private Helpers

//-----------------------------------------------------------------------------

bool
BbmdsApp::prv_makeAuthToken()
{
  // Try to read the identity file for the "no authentication" model first
  // since it's the default behaviour.
  {
    const auto path = m_directory + "/identity";
    std::ifstream file{path};
    std::string identity;
    file >> identity;
    if(file && not identity.empty())
    {
      try
      {
        // Generating the unsigned authToken can fail in extreme
        // circumstances.
        m_authToken = makeUnsignedAuthToken(identity);

        // Report that we're using that.
        std::cout
          << "Loaded identity for \"no authentication\" mode from " << path
          << '\n';
      }
      catch(const std::exception& ex)
      {
        std::cout
          << "Can't make authToken from identity: " << ex.what() << '\n';
        return false;
      }
      return true;
    }
  }

  // Try to read the micro IDP already fully-formed authToken since no
  // identity file exists.
  const auto path = m_directory + "/token";
  std::stringstream stream;
  stream << std::ifstream{path}.rdbuf();
  m_authToken = stream.str();
  if(not m_authToken.empty())
  {
    std::cout << "Loaded token for \"micro IDP\" mode from " << path << '\n';
    return true;
  }

  // We couldn't find anything.  Provide some help.
  std::cout <<
    "No identity or token is configured.\n"
    "\n"
    "If your Spark Communications sandbox domain uses no authentication,\n"
    "configure the user identity that this application should use in this file:"
    "\n"
    << m_directory << "/identity\n"
    "\n"
    "If your Spark Communications sandbox domain uses the micro IDP, configure\n"
    "the pre-built authToken message that this application should use in this\n"
    "file:\n"
    << m_directory << "/token\n"
    "\n"
    "See the application's README for help on configuring your domain and these"
    "files.\n";
  return false;
}

//-----------------------------------------------------------------------------

void
BbmdsApp::prv_startBbmcore()
{
  std::cout << "Asking bbmcore to start.\n";
  
  // Report failures.
  if(__builtin_expect(not m_sdk.startBbmcore(), false))
  {
    std::cout << "Error sending bbmcore the start command.\n";
    quit();
  }
}

//-----------------------------------------------------------------------------

void
BbmdsApp::prv_handleGlobals(const Json::Value& globals)
{
  // Track if we should attempt to progress setup after processing.
  bool progress = false;
  
  // Process each global.
  for(const auto& elem : globals)
  {
    const auto& name = elem["name"].asString();

    // Record the globals we care about.
    if(name == "authTokenState")
    {
      m_authTokenState = elem["value"].asString();
      progress = true;
    }
    else if(name == "setupState")
    {
      // Detect a change to setupState so we can emit it.
      const auto newState = elem["value"]["state"].asString();
      if(m_setupState != newState)
      {
        m_setupState = newState;
        std::cout << "SetupState is " << m_setupState << "\n";
        progress = true;
      }
    }
    else if(name == "syncPasscodeState")
    {
      const auto newState = elem["value"].asString();
      if(m_syncPasscodeState != newState)
      {
        // If the passcode state is changing from None, we might be waiting
        // for this information before proceeding with a sync request.
        if(m_syncPasscodeState == "None")
        {
          progress = true;
        }
        // Otherwise, we'll wait for a setupState change.

        // Record and emit the new state.
        m_syncPasscodeState = newState;
        std::cout << "SyncPasscodeState is " << m_syncPasscodeState << "\n";
      }
    }
    else if(name == "endpointId" && m_endpointId.empty())
    {
      m_endpointId = elem["value"].asString();
      std::cout << "EndpointId is " << m_endpointId << "\n";
    }
    else if(name == "localUri")
    {        
      // We asked for the localUri so that we could then query details about
      // the local user. Send a "requestListElements" BBMDS request for the
      // local user's record so we can parse out and emit the regId and PIN.
      requestListElements("user", "uri", {elem["value"].asString()});
    }
    // Else, ignore the rest.
  }

  // If state has changed enough, make an attempt to move setup forward.
  if(progress)
  {
    prv_progressSetup();
  }
}

//-----------------------------------------------------------------------------

void
BbmdsApp::prv_progressSetup()
{
  // Always re-send the authToken if needed.
  if(m_authTokenState == "Needed")
  {
    prv_sendAuthToken();
    // Continue in case there is other work to do.
  }

  // Have we already completed setup?
  if(m_setupState == "Success")
  {
    // Handle all possible authTokenStates.
    
    // Stop if there's nothing to do
    if(m_authTokenState == "Ok")
    {
      return;
    }

    // Re-send the authToken if needed.
    if(m_authTokenState == "Needed")
    {
      // Already sent 'authToken' above.
      return;
    }

    // Has our authToken been rejected?
    if(m_authTokenState == "Rejected")
    {
      // There's no fixing this in this simple demo program.
      std::cout << "Stopping due to rejected authToken.\n";
      quit();
      return;
    }
    std::cout
      << "Ignoring unsupported authTokenState=" << m_authTokenState << "\n";
    return;
  }

  // If setup has not started, start it now.
  if(m_setupState == "NotRequested")
  {
    prv_sendAuthToken();
    return;
  }

  // If setup has progressed to 'SyncRequired', try to sync.
  if(m_setupState == "SyncRequired")
  {
    Json::Value root;
    {
      auto& msg = root["syncStart"];
      // If there is existing sync data, attempt to reuse it by sending
      // 'Existing'.
      msg["action"] = (m_syncPasscodeState == "Existing" ? "Existing" : "New");
      msg["passcode"] = "demo";
    }
    messageToService(JsonWriter{}.write(root));
    return;
  }

  // If there are too many registered endpoints, stop. Deciding which existing
  // endpoint(s) to remove is outside the scope of this demo app.
  if(m_setupState == "Full")
  {
    std::cout
      << "Stopping due to reaching maximum number of registered endpoints.\n";
    quit();
    return;
  }
  
  // Wait for setup to complete.
}

//----------------------------------------------------------------------------

void
BbmdsApp::prv_sendAuthToken()
{
  messageToService(m_authToken);
}

//*****************************************************************************
