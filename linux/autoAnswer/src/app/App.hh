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

#ifndef app_App_hh_included_20180601140546_
#define app_App_hh_included_20180601140546_

#include <common/BbmdsApp.hh>
#include <bbm/sdk/call.hh>
#include <bbm/sdk/CallCallbacks.hh>

#include <string>

namespace bbm { namespace sdk { class Sdk; } }
namespace Json { class Value; }

//=============================================================================

namespace app
{
  // This class implements the features unique to this sample app.
  class App : public common::BbmdsApp,
              private bbm::sdk::CallCallbacks
  {
  public:
    // :: ---------------------------------------------------------------------
    // :: Construction

    // Construct with the \a sdk and the persistent \a directory that should
    // house the local data files.
    App(bbm::sdk::Sdk& sdk,
        const std::string& directory);
    
  private:
    // :: ---------------------------------------------------------------------
    // :: Inherited CallCallbacks Interface

    // See base class.
    virtual void callOffered(const bbm::sdk::call::Offer& offer) override;
    
    // See base class.
    virtual void callChanged(const bbm::sdk::call::Change& change) override;
    
    // See base class.
    virtual void callMediaChanged(
      const bbm::sdk::call::MediaChange& mediaChange) override;
    
    // See base class.
    virtual void cameraStateSet(const bool success) override;

  private:
    // :: ---------------------------------------------------------------------
    // :: Helpers

    // Process an incoming BBMDS message with \a name and \a data.
    void prv_receiveBbmds(const std::string& name,
                          const Json::Value& data);

    // Update m_currentCall with \a data as necessary.  When this returns,
    // m_currentCall is always set.
    void prv_updateCurrentCall(const bbm::sdk::call::Info& info)
    {
      // Remember the current call's id.
      if(not m_currentCall)
      {
        m_currentCall = std::make_unique<Call>(info.getId());
      }
    }

    // A helper to control the red LED on-board light (when available).
    //
    // If \a on is true, the light will be set to flash.
    // If \a on is false, the light will be returned to its default setting
    // which may or may not be on.
    //
    void prv_toggleLight(const bool on) const;

    // Toggle the camera on or off when available and there is an existing
    // call with media.
    void prv_toggleCamera();

  private:
    // :: ---------------------------------------------------------------------
    // :: Data Members

    // Information about the current media call, if any.
    struct Call
    {
      explicit Call(const bbm::sdk::call::id_type id)
        : id(id)
      {}
      
      // The media API id of the current call.
      bbm::sdk::call::id_type id;

      // True iff we (think we) have enabled the camera in the call.
      bool camera = false;
      
      // True iff media has been established in the call.
      bool media = false;
    };
    std::unique_ptr<Call> m_currentCall;
  };
}

#endif // app_App_hh_included_20180601140546_
//*****************************************************************************
