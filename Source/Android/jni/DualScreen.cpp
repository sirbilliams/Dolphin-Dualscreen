// Copyright 2026 Dolphin Dualscreen
// SPDX-License-Identifier: GPL-2.0-or-later

// JNI bridge for the companion (bottom) screen. See Core/DualScreen/MemoryWatch.h.

#include <utility>
#include <vector>

#include <jni.h>

#include "Common/CommonTypes.h"
#include "Core/DualScreen/MemoryWatch.h"

extern "C" {

// spec holds groups of four ints: address, offset, size (1/2/4), deref (0/1).
JNIEXPORT void JNICALL
Java_org_dolphinemu_dolphinemu_features_dualscreen_DualScreenNative_setWatches(JNIEnv* env, jclass,
                                                                              jintArray spec)
{
  std::vector<DualScreen::Watch> watches;
  if (spec != nullptr)
  {
    const jsize length = env->GetArrayLength(spec);
    std::vector<jint> raw(static_cast<size_t>(length));
    env->GetIntArrayRegion(spec, 0, length, raw.data());
    for (jsize i = 0; i + 3 < length; i += 4)
    {
      DualScreen::Watch w;
      w.address = static_cast<u32>(raw[i]);
      w.offset = static_cast<u32>(raw[i + 1]);
      w.size = static_cast<u8>(raw[i + 2]);
      w.deref = raw[i + 3] != 0;
      watches.push_back(w);
    }
  }
  DualScreen::SetWatches(std::move(watches));
}

// Returns [serial, value0, valid0, value1, valid1, ...]. serial is 0 until the first capture.
JNIEXPORT jintArray JNICALL
Java_org_dolphinemu_dolphinemu_features_dualscreen_DualScreenNative_getSnapshot(JNIEnv* env,
                                                                               jclass)
{
  const DualScreen::Snapshot snapshot = DualScreen::GetSnapshot();
  std::vector<jint> out;
  out.reserve(1 + snapshot.values.size() * 2);
  out.push_back(static_cast<jint>(snapshot.serial & 0x7FFFFFFF));
  for (size_t i = 0; i < snapshot.values.size(); i++)
  {
    out.push_back(static_cast<jint>(snapshot.values[i]));
    out.push_back(snapshot.valid[i]);
  }
  jintArray result = env->NewIntArray(static_cast<jsize>(out.size()));
  env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
  return result;
}
}
