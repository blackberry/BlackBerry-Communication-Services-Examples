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

#ifndef common_JsonWriter_hh_included_20180816141742_
#define common_JsonWriter_hh_included_20180816141742_

#include <jsoncpp/json/writer.h>

//=============================================================================

namespace common 
{
  // Define a version of jsoncpp's Json::FastWriter that never emits the
  // trailing newline.
  struct JsonWriter : public Json::FastWriter
  {
    JsonWriter()
    { omitEndingLineFeed(); }
  };
}

#endif // common_JsonWriter_hh_included_20180816141742_
//*****************************************************************************
