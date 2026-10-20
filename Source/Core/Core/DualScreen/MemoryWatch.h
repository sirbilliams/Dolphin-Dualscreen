// Copyright 2026 Dolphin Dualscreen
// SPDX-License-Identifier: GPL-2.0-or-later

// A small set of guest memory reads that a companion display (such as the AYN Thor's bottom
// screen) wants to show. The UI thread sets the watch list; the CPU thread copies the watched
// values once every few fields; the UI thread reads the latest copy without ever pausing
// emulation.

#pragma once

#include <vector>

#include "Common/CommonTypes.h"

namespace Core
{
class System;
}

namespace DualScreen
{
struct Watch
{
  // Effective (virtual) address to read. If deref is set, a 32-bit pointer is read here first.
  u32 address = 0;
  // Added to the pointer when deref is set; ignored otherwise.
  u32 offset = 0;
  // 1, 2 or 4 bytes, read big-endian as the game stores it.
  u8 size = 4;
  bool deref = false;
};

struct Snapshot
{
  // Increments every time the CPU thread refreshes the values. 0 means nothing captured yet.
  u64 serial = 0;
  std::vector<u32> values;
  // 1 where the read succeeded (and, for deref watches, the pointer was valid), else 0.
  std::vector<u8> valid;
};

// UI thread. Replaces the watch list and clears the current snapshot. An empty list stops all
// reads.
void SetWatches(std::vector<Watch> watches);

// UI thread. Returns a copy of the latest values.
Snapshot GetSnapshot();

// CPU thread, once per video field.
void OnNewField(Core::System& system);

// Any thread. Drops the snapshot, e.g. when emulation stops.
void ClearSnapshot();
}  // namespace DualScreen
