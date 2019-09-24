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

#include <common/SignalHandler.hh>

#include <bbm/sdk/Sdk.hh>
#include <bbm/sdk/exceptions.hh>

#include <iostream>
#include <fstream>
#include <stdexcept>
#include <cstring>

#include <errno.h>
#include <signal.h>
#include <sys/file.h>
#include <getopt.h>

using namespace app;
using namespace std::literals;

//=============================================================================
// :: Entry Point

int
main(const int argc, char * const * const argv)
{
  try
  {
    // This tool requires the "C" locale because the SDK does so that its
    // collation rules are not affected by user settings.
    if(__builtin_expect(::setenv("LANG", "C", 1) == -1, false))
    {
      const int error = errno;
      throw std::runtime_error{"Can't set LANG=C: "s + std::strerror(error)};
    }
  
    // First up, block all the signals we use so they don't end up on other
    // threads.
    const common::SignalHandler::Block block({ SIGINT, SIGTERM, SIGQUIT });

    // Start with a default path which may be overridden by command line
    // argument.
    const auto home = ::getenv("HOME");
    std::string dir(home
                    ? std::string{home} + "/.autoAnswer"
                    : std::string{"."});

    int ch;
    while(1)
    {
      const struct option options[] = {
        {"directory", required_argument, 0,  's' },
        {nullptr,   0,                 nullptr,  0 }
      };

      ch = ::getopt_long(argc, argv, "s:", options, nullptr);
      if(ch == -1)
      {
        break;
      }

      switch (ch)
      {
      case 's':
        // Save the path.
        dir = ::optarg;
        break;

      default:
        std::cout << "Usage: autoAnswer [-s directory]\n";
        return 1;
      }
    }

    // Setup the directory layout.
    const std::string domainFilename(dir + "/domain"); 

    // The domain we pass to the SDK.
    std::string domain;
    
    // Read the domain from the file.
    {
      std::ifstream file{domainFilename};
      file >> domain;

      if(not file)
      {
        std::cout
          << "No domain is configured.\n"
          "Please configure a valid Spark Communications domain id as the "
          "only string in this file:\n" << domainFilename << '\n';
        return 1;
      }
      
      std::cout << "Configured for domain " << domain << ".\n";
    }
    
    // Construct the SDK.
    bbm::sdk::Sdk sdk{
      bbm::sdk::Sdk::Options{}
      .setDirectory(dir)
      .setDomain(domain)
      .setSandboxMode(true)
      // A hard-coded database key is good enough for demonstration
      // purposes. See bbm::sdk::Sdk::Options::setDatabaseKey() for more
      // details.
      .setDatabaseKey("blackberry")
      .move()};

    std::cout
      << "Follow bbmcore's logs with this command in another terminal:\n"
      << "tail --follow=name " << dir << "/logs/bbmcore.txt\n";

    // Run the test tool.
    return App{sdk, dir}.run() ? 1 : 0;
  }
  catch(const std::exception& ex)
  {
    std::cout << "Uncaught exception: " << ex.what() << '\n';
    return 1;
  }
  return 0;
}

//*****************************************************************************
