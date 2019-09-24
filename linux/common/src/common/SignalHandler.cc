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

#include "SignalHandler.hh"

#include <cstring>
#include <stdexcept>
#include <string>

#include <sys/signalfd.h>

using namespace std::literals;
using namespace common;

//=============================================================================
// :: Construction/Destruction

//-----------------------------------------------------------------------------

SignalHandler::Block::Block(const SignalHandler::Set& set)
  // throw(std::runtime_error)
{
  // Block them from arriving normally.
  if(__builtin_expect(
       ::sigprocmask(SIG_BLOCK, &set.get(), &m_old) == -1, false))
  {
    const int error = errno;
    throw std::runtime_error{
      "Can't set sigprocmask(): "s + std::strerror(error)};
  }
}

//-----------------------------------------------------------------------------

SignalHandler::Block::~Block()
{
  // This can't fail.
  ::sigprocmask(SIG_SETMASK, &m_old, nullptr);
}

//-----------------------------------------------------------------------------

SignalHandler::SignalHandler(Map&& map)
  : m_map(std::move(map)),
    m_fd(::signalfd(-1, &Set{m_map}.get(), SFD_NONBLOCK))
{
  if(__builtin_expect(not m_fd.isOpen(), false))
  {
    const int error = errno;
    throw std::runtime_error{"Can't open signalfd: "s + std::strerror(error)};
  }
}

//=============================================================================
// :: Interface

//-----------------------------------------------------------------------------

void
SignalHandler::read()
{
  // Read the expected info.
  ::signalfd_siginfo info;
  if(__builtin_expect(
       m_fd.read(&info, sizeof(info)) != ::ssize_t(sizeof(info)), false))
  {
    // Ignore spurious wakeups, but fail on errors.
    if(errno == EAGAIN)
    {
      return;
    }
    const int error = errno;
    throw std::runtime_error{
      "Can't read from signalfd"s + std::strerror(error)};
  }

  // Dispatch the signal, ignoring unknown ones.
  const auto it = m_map.find(info.ssi_signo);
  if(__builtin_expect(it != m_map.end(), true))
  {
    it->second();
  }
}

//*****************************************************************************
