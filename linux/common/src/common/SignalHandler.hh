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

#ifndef common_SignalHandler_hh_included_20180606101448_
#define common_SignalHandler_hh_included_20180606101448_

#include "FileDescriptor.hh"

#include <csignal>
#include <functional>
#include <map>

//=============================================================================

namespace common
{
  // A simple class that can register to handle most signals.
  class SignalHandler
  {
  public:
    // :: ---------------------------------------------------------------------
    // :: Construction

    // A map of signals to their functions.
    using Map = std::map<int, std::function<void ()>>;
    
    // Construct with the \a map of signals to monitor and the functions that
    // will be called after they happen.  The functions must not be empty.
    //
    // Throws if the signals can't be monitored.
    //
    explicit SignalHandler(Map&& map);
      // throw(std::runtime_error)

    // Non-copyable, non-movable.
    SignalHandler(const SignalHandler&) = delete;
    SignalHandler& operator=(const SignalHandler&) = delete;
    
  public:
    // :: ---------------------------------------------------------------------
    // :: Interface

    // A sane wrapper around ::sigset_t.
    struct Set
    {
      // Construct as an empty set.
      Set()
      { sigemptyset(&m_mask); }

      // Construct from a \a map of signal functions.
      explicit Set(const Map& map)
        : Set()
      {
        for(const auto& pair : map) { add(pair.first); }
      }
      
      // Construct from an initializer \a list.
      Set(std::initializer_list<int> list)
        : Set()
      {
        for(auto signal : list) { add(signal); }
      }
      
      // Add \a signal to the set.
      Set& add(const int signal)
      {
        sigaddset(&m_mask, signal);
        return *this;
      }

      // Return the set.
      const ::sigset_t &get() const
      { return m_mask; }
      
    private:
      ::sigset_t m_mask;
    };
    
    // An RAII helper that makes it easy to block a set of signals.
    struct Block
    {
      // Block all the signals in \a set.  Purposely implicit.
      Block(const Set& set);
        // throw(std::runtime_error)

      // Restore the original mask.
      ~Block();

      // The original mask.
      ::sigset_t m_old;
    };
    
    // Return the fd to wait on for signals.  Never -1.
    int getFd() const
    { return m_fd.fd(); }

    // Called when the fd becomes readable.  Throws on error.
    void read();
      // throw(std::runtime_error)
    
  private:
    // :: ---------------------------------------------------------------------
    // :: Data Members

    // The map of signals to their functions.
    const Map m_map;

    // The actual file descriptor.
    FileDescriptor m_fd;
  };
  
  // Logging output operator
  std::ostream &operator<<(std::ostream &out,
                           const SignalHandler &obj);
}

#endif // common_SignalHandler_hh_included_20180606101448_
//*****************************************************************************
