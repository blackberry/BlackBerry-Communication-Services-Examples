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

#ifndef common_token_hh_included_20180816115714_
#define common_token_hh_included_20180816115714_

#include <chrono>
#include <string>

//=============================================================================

namespace common 
{
  // This function generates a full BBMDS "authToken" message that can be sent
  // to the SDK when running in the "No IDP" mode explained in the "idp"
  // example's README file.
  //
  // When a Spark Communications (sandbox) domain is configured to have no
  // authentication, endpoints need only their user id to connect.  Thus, any
  // client that knows the Spark Communications domain id and the user id will
  // be able to connect as that user.
  //
  // The \a userId will be placed into the "authToken" BBMDS message's
  // "userId" field.
  //
  // The sibling "authToken" field within the "authToken" message will contain
  // an unsigned JWT token that claims to be that same user.
  //
  // The \a expiry value will be added to the current time to compute the JWT
  // token's "exp" value.
  //
  // The token will be given a random "jti" token identifier.  There will be
  // no "iss" field.
  //
  // Except for memory exhaustion, this throws only if std::random_device
  // throws, thus the declaration of std::exception.
  //
  std::string makeUnsignedAuthToken(
    const std::string& userId,
    const std::chrono::seconds expiry = std::chrono::hours{24 * 365});
    // throw(std::exception)
}

#endif // common_token_hh_included_20180816115714_
//*****************************************************************************
