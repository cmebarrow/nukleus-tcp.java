# TCP Nukleus Implementation

[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/reaktivity/nukleus-tcp.java.svg?branch=develop
[build-status]: https://travis-ci.org/reaktivity/nukleus-tcp.java

## Overview

The TCP nukleus communicates with the operating system TCP/IP layer. It is used to accept incoming socket connections and establish outgoing ones and send and receive data over those connections.  Its job is to:
1. read data from a streams file that it owns (known as a source) and write it out to the network, and
2. read data off the network and write it to a streams file owned by another nukleus (known as the target).

There can be multiple sources and targets if the TCP nukleus is being used in conjunction with multiple other nuklei.

## Internal architecture

- Conductor: reads control commands, hands to router
- Router: manages the routing table
- Watcher: watches for new or deleted streams files so it can detect new streams files (created route command to another nukleus that writes to this) and tell the Router about them so it can route new streams. **TODO: handling deleted files is not done yet, no-one deletes them** 
- reader package:
  - Reader: reads all of its sources (only one for tcp), routes and writes to its targets
  - Source: reading from network (would be a streams file for non-tcp)
  - Target: writing to a streams file 
- write package:
  - Writer: reads sources (streams files) for data to write to multiple targets
  - Source: streams file(s)
  - Target: writes to network (streams file for non-tcp)
