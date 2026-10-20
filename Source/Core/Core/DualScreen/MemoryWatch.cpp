// Copyright 2026 Dolphin Dualscreen
// SPDX-License-Identifier: GPL-2.0-or-later

#include "Core/DualScreen/MemoryWatch.h"

#include <mutex>
#include <optional>
#include <utility>

#include "Core/Core.h"
#include "Core/PowerPC/MMU.h"
#include "Core/System.h"

namespace DualScreen
{
namespace
{
// Refresh every other field: 30 times a second is far more than a status screen needs.
constexpr u32 FIELDS_PER_REFRESH = 2;

// GameCube MEM1 as seen through the cached BAT mapping.
constexpr u32 POINTER_MIN = 0x80000000;
constexpr u32 POINTER_MAX = 0x81800000;

std::mutex s_mutex;
std::vector<Watch> s_watches;
Snapshot s_snapshot;
u32 s_field_counter = 0;

std::optional<u32> Read(const Core::CPUThreadGuard& guard, u32 address, u8 size)
{
  switch (size)
  {
  case 1:
    if (auto r = PowerPC::MMU::HostTryRead<u8>(guard, address))
      return r->value;
    break;
  case 2:
    if (auto r = PowerPC::MMU::HostTryRead<u16>(guard, address))
      return r->value;
    break;
  case 4:
    if (auto r = PowerPC::MMU::HostTryRead<u32>(guard, address))
      return r->value;
    break;
  default:
    break;
  }
  return std::nullopt;
}
}  // namespace

void SetWatches(std::vector<Watch> watches)
{
  std::lock_guard lock(s_mutex);
  s_watches = std::move(watches);
  s_snapshot = {};
}

Snapshot GetSnapshot()
{
  std::lock_guard lock(s_mutex);
  return s_snapshot;
}

void ClearSnapshot()
{
  std::lock_guard lock(s_mutex);
  s_snapshot = {};
}

void OnNewField(Core::System& system)
{
  if (++s_field_counter < FIELDS_PER_REFRESH)
    return;
  s_field_counter = 0;

  std::vector<Watch> watches;
  {
    std::lock_guard lock(s_mutex);
    if (s_watches.empty())
      return;
    watches = s_watches;
  }

  // We're on the CPU thread, so this guard doesn't pause anything.
  const Core::CPUThreadGuard guard(system);

  std::vector<u32> values(watches.size(), 0);
  std::vector<u8> valid(watches.size(), 0);
  for (size_t i = 0; i < watches.size(); i++)
  {
    const Watch& w = watches[i];
    u32 address = w.address;
    if (w.deref)
    {
      const std::optional<u32> pointer = Read(guard, w.address, 4);
      if (!pointer || *pointer < POINTER_MIN || *pointer >= POINTER_MAX)
        continue;
      address = *pointer + w.offset;
    }
    if (const std::optional<u32> value = Read(guard, address, w.size))
    {
      values[i] = *value;
      valid[i] = 1;
    }
  }

  std::lock_guard lock(s_mutex);
  // Ignore this result if the watch list changed while we were reading.
  if (s_watches.size() != watches.size())
    return;
  s_snapshot.serial++;
  s_snapshot.values = std::move(values);
  s_snapshot.valid = std::move(valid);
}
}  // namespace DualScreen
