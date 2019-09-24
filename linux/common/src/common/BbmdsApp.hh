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

#ifndef common_BbmdsApp_hh_included_20180601840546_
#define common_BbmdsApp_hh_included_20180601840546_

#include <bbm/sdk/BbmdsCallbacks.hh>

#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace bbm { namespace sdk { class Sdk; } }
namespace Json { class Value; }

//=============================================================================

namespace common
{
  // This class is intended to help get an app registered and setup by sending
  // and receiving BBMDS messages.
  class BbmdsApp : private bbm::sdk::BbmdsCallbacks
  {
  public:
    // :: ---------------------------------------------------------------------
    // :: Construction

    // Construct with the \a sdk and the persistent \a directory that should
    // house the local data files.
    BbmdsApp(bbm::sdk::Sdk& sdk,
             const std::string& directory);

  public:
    // :: ---------------------------------------------------------------------
    // :: Interface

    // Run the main loop of the tool and return true if the tool should
    // return successfully from main().
    bool run();

    // The callback function used to relay an incoming BBMDS message with
    // \a name and \a data after local processing.
    using ReceiveBbmdsFunction = std::function<void (
      const std::string& name,
      const Json::Value& data)>;

    // Set the optional \a callback to receive an incoming BBMDS message after
    // processing locally.
    void setBbmdsCallback(ReceiveBbmdsFunction&& callback)
    { m_receiveCallback = std::move(callback); }

    // The callback function used to relay command \a line input if supported.
    using CommandLineFunction = std::function<void (std::string&& line)>;

    // Set the optional \a callback to receive command line input.
    void setCommandLineCallback(CommandLineFunction&& callback)
    { m_commandLineFunction = std::move(callback); }

  private:
    // :: ---------------------------------------------------------------------
    // :: Inherited BbmdsCallbacks Interface

    // See base class.
    virtual void bbmcoreStarted() override;

    // See base class.
    virtual void bbmcoreStopped(const bool fatalError) override;

    // See base class.
    virtual void receiveBbmds(std::string&& message) override;

  protected:
    // :: ---------------------------------------------------------------------
    // :: Helpers

    // Send a "requestListElements" BBMDS request on the list named \a type.
    // 
    // The \a key is the element key name and the \a values are the element
    // key values to request.
    //
    void requestListElements(const std::string& type,
                             const std::string& key,
                             const std::vector<std::string>& values);

    
    // Send the BBMDS \a msg to bbmcore.  Reports errors.
    void messageToService(std::string msg);

    // Quit the tool if it is in run().
    void quit()
    {
      // Stop the loop.
      m_running = false;
    }

  private:
    // :: ---------------------------------------------------------------------
    // :: Helpers

    // Read in the user identity (for "no authentication" configurations) or the
    // full BBMDS authToken message from the command-line IDP (for "micro IDP"
    // configurations).  Returns false if the auth token can't be made.
    bool prv_makeAuthToken();
    
    // Start bbmcore.
    void prv_startBbmcore();

    // Handle a list of \a globals from bbmcore and react as needed.
    void prv_handleGlobals(const Json::Value& globals);
    
    // Try to progress setup to 'setupState:Success' and 'authTokenState:Ok'
    // based on current globals.
    void prv_progressSetup();

    // Send the 'authToken' BBMDS message to bbmcore.
    void prv_sendAuthToken();

  protected:
    // :: ---------------------------------------------------------------------
    // :: Data Members

    // The BlackBerry Spark Communications Services SDK central API object.
    bbm::sdk::Sdk& m_sdk;

    // The persistent directory that should house the local data files.
    const std::string m_directory;
    
    // The auth token used to identify and authenticate the user.
    std::string m_authToken;
    
    // Our regId, if we know it and if we have one. Otherwise empty.
    std::string m_regId;

    // Our PIN, if we know it and if we have one. Otherwise empty.
    std::string m_pin;

    // Set to false to make the main loop stop.
    bool m_running = false;

    // The latest globals synced from bbmcore.
    std::string m_authTokenState;
    std::string m_endpointId;
    std::string m_setupState;
    std::string m_syncPasscodeState;

    // The callback function used to relay an incoming BBMDS message after
    // local processing if set.
    ReceiveBbmdsFunction m_receiveCallback = nullptr;

    // The callback function used to relay command \a line input if supported.
    CommandLineFunction m_commandLineFunction;
  };
}

#endif // common_BbmdsApp_hh_included_20180601840546_
//*****************************************************************************
