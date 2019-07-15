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

#include "JsonWriter.hh"
#include "token.hh"

#include <jsoncpp/json/json.h>

#include <cassert>
#include <cstdint>
#include <random>

using namespace common;
using namespace std::literals;

//=============================================================================
// :: Construction/Destruction

std::string
common::makeUnsignedAuthToken(
  const std::string& userId,
  const std::chrono::seconds expiry)
  // throw(std::exception)
{
  // Start with the fixed base64url no-padding JWT header and its separating
  // dot.  This string encodes the following JWT header:
  //
  //  {"alg":"none"}
  //
  auto token = "eyJhbGciOiJub25lIn0."s;

  // Encode \a input as base64url without padding to the end of \a output.
  //
  // This isn't fast, but it's simple and dependency-free, which is useful
  // here, where maximum speed isn't essential.
  //
  const auto base64 = [](std::string& output, const std::string& input)
    {
      // The base64url alphabet.
      const char * const alphabet =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

      // Make room for the entire unpadded output by predicting the precise
      // output size of unpadded base64.  Ignore overflow.
      const auto start = output.size();
      const std::size_t expectedSize = start + ((input.size() << 2) | 2) / 3;
      output.resize(expectedSize);

      // Append alphabet[index] to output.
      auto emit =
      [&output, alphabet, pos = start](const std::size_t index) mutable
      {
        assert(index < 64);
        assert(pos < output.size());
        output[pos++] = alphabet[index];
      };

     // Encode each 3-byte sequence into 4 characters of output.
     std::uint8_t slot = 0;
     std::uint8_t left = 0;
     for(const auto ch : input)
     {
       const auto byte = std::uint8_t(ch);
       switch(slot)
       {
       case 0:
         slot = 1;
         emit(byte >> 2);
         left = (byte & 0x03) << 4;
         break;

       case 1:
         slot = 2;
         emit((byte >> 4) | left);
         left = (byte & 0x0f) << 2;
         break;

       case 2:
         slot = 0;
         emit((byte >> 6) | left);
         emit(byte & 0x3f);
         break;
       }
     }

     // Finish by emitting any leftover bits.  Note that slot has already been
     // incremented, so we have leftovers whenever slot isn't zero and reset
     // for the next loop.
     if(slot)
     {
       emit(left);
     }

     // Confirm our size prediction.
     assert(output.size() == expectedSize);
   };

  // Now add the body of the JWT token
  {
    Json::Value jwt;
    jwt["sub"] = userId;

    // Generate a random token id simply but effectively without worrying
    // about seeding issues.  This can throw if std::random_device does.
    jwt["jti"] = [&base64]
      {
        // Load 16 bytes of random data from the random device, which is
        // defined to return uniformly distributed unsigned int values.
        std::random_device rng{};
        unsigned int raw[16 / sizeof(unsigned int)];
        for(auto& value : raw) { value = rng(); }

        // Base64 encode that sequence.  We copy it for simplicity.  It's
        // small.
        std::string out;
        base64(
          out,
          std::string{
            reinterpret_cast<const char *>(raw),
            // Carefully, we add the byte size of raw.
            reinterpret_cast<const char *>(raw) + sizeof(raw)});
        return out;
      }();
    
    // The current time as a POSIX timestamp in seconds.
    const auto now =
      std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch());
    jwt["iat"] = Json::LargestInt{now.count()};

    // For brevity, this ignores the possibility of integer overflow.
    jwt["exp"] = Json::LargestInt{(now + expiry).count()};

    // Serialize this to JSON, base64url encode without padding, and append to
    // serialized JWT token we have already started.
    base64(token, JsonWriter{}.write(jwt));
  }

  // Append the final '.' to indicate there is no signature and we're done.
  token += '.';

  // Finally, produce the actual authToken BBMDS message that contains all of
  // that.
  Json::Value result;
  {
    auto& msg = result["authToken"];
    msg["userId"] = userId;
    msg["authToken"] = token;
  }
  return JsonWriter{}.write(result);
}

//*****************************************************************************
