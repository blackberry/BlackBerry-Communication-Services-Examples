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

#ifndef app_App_hh_included_20180601540546_
#define app_App_hh_included_20180601540546_

#include <common/BbmdsApp.hh>

#include <string>

namespace bbm { namespace sdk { class Sdk; } }
namespace Json { class Value; }

//=============================================================================

namespace app
{
  // This class implements the features unique to this sample app.
  class App : public common::BbmdsApp
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
    // :: Helpers

    // Process an incoming BBMDS message with \a name and \a data.
    void prv_receiveBbmds(const std::string& name,
                          const Json::Value& data);

    // A helper to control the red LED on-board light (when available).
    //
    // If \a on is true, the light will be set to flash.
    // If \a on is false, the light will be returned to its default setting
    // which may or may not be on.
    //
    // Returns true iff the light's control file was updated.
    //
    bool prv_controlLight(const bool on);
  };
}

#endif // app_App_hh_included_20180601540546_
//*****************************************************************************
