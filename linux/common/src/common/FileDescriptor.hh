//****************************************************************************
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

#ifndef common_FileDescriptor_hh_included_20180605121546_
#define common_FileDescriptor_hh_included_20180605121546_

#include <cstddef>
#include <cerrno>
#include <unistd.h>

//============================================================================

namespace common
{
  // This class helps maintain a POSIX file descriptor as an RAII object.
  // Common operations are exposed as member functions, and the raw file
  // descriptor is exposed to allow basically all operations.
  //
  // This class emits no logs.  It is little more than an RAII helper.
  //
  class FileDescriptor
  {
  public:
    // :: -----------------------------------------------------------------
    // :: Construction

    // Construct around an existing file descriptor \a fd.  Iff \a fd is not
    // -1, it is presumed to be a valid, open file descriptor.
    //
    // This does not modify errno.
    //
    explicit FileDescriptor(const int fd = -1) noexcept
      : m_fd(fd)
    {}

    // Move constructible.  Afterwards, \a that is closed.
    FileDescriptor(FileDescriptor &&that) noexcept
      : m_fd(that.m_fd)
    {
      that.m_fd = -1;
    }

    // Move assignable.  Any descriptor this had open is closed as a first
    // step.  Afterwards, \a that is closed.
    FileDescriptor &operator=(FileDescriptor &&that) noexcept
    {
      close();
      m_fd = that.m_fd;
      that.m_fd = -1;
      return *this;
    }
    
    // Close the file iff it is open.
    //
    // This does not modify errno in the event a failure occurred
    // when closing the file.
    //
    virtual ~FileDescriptor()
    {
      // Close the descriptor
      close();
    }

    // Non-copyable.
    FileDescriptor(const FileDescriptor&) = delete;
    FileDescriptor& operator=(const FileDescriptor&) = delete;

  public:
    // :: -----------------------------------------------------------------
    // :: Interface

    // Return the file descriptor for the open file, or -1 iff the file is
    // not open.
    //
    // Do not close this file descriptor without using the close() member
    // function or the destructor!
    //
    int fd() const noexcept
    { return m_fd; }

    // Returns true iff the file is open.
    bool isOpen() const noexcept
    { return m_fd != -1; }

    // Calls ::read(fd(), ...).  Preserves errno.
    ::ssize_t read(void * const buf,
                   const std::size_t nbytes) noexcept
    {
      ::ssize_t bytesRead;
      while((bytesRead = ::read(m_fd, buf, nbytes)) == ::ssize_t(-1) &&
            errno == EINTR);
      return bytesRead;
    }
      
    // Calls close(fd()). Iff this returns false, errno is set.
    //
    // This exists because some applications require the return value of
    // close(), which the destructor cannot provide.
    //
    // Note that even if this returns false, the FileDescriptor is
    // considered to be closed (i.e. isOpen() will return false).  This is
    // because, barring someone manually calling ::close() on our file
    // descriptor and thus possibly triggering EBADF, the only other failure
    // reasons are EINTR (which is handled internally with a retry), or an
    // EIO error deferred from previous I/O operations.  In the EIO case,
    // the state of the file descriptor is unspecified as per POSIX, but we
    // assume it to be closed since there is no fool-proof way to check or
    // handle any other condition.
    //
    // Obviously, after this is called, operations on the FileDescriptor will
    // fail.
    //
    bool close() noexcept
    {
      // If we're still open, close
      if(isOpen())
      {
        int result;
        while((result = ::close(m_fd)) == -1 && errno == EINTR)
          ;
        m_fd = -1;
        return result != -1; 
      }
      return true;
    }

  private:
    // :: -----------------------------------------------------------------
    // :: Data Members

    // The file descriptor, or -1 iff the file is not open
    int m_fd;
  };
}

#endif // common_FileDescriptor_hh_included_20180605121546_
//****************************************************************************
