/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef RUNTIME_TYPES_H
#define RUNTIME_TYPES_H

#include <stdlib.h>

#include "Alloc.h"
#include "Common.h"
#include "Memory.h"
#include "TypeInfo.h"
#include "std_support/Deque.hpp"
#include "std_support/List.hpp"
#include "std_support/Map.hpp"
#include "std_support/Memory.hpp"
#include "std_support/Set.hpp"
#include "std_support/String.hpp"
#include "std_support/UnorderedMap.hpp"
#include "std_support/UnorderedSet.hpp"
#include "std_support/Vector.hpp"

// Note that almost all types are signed.
typedef bool KBoolean;
typedef int8_t  KByte;
typedef uint16_t KChar;
typedef int16_t KShort;
typedef int32_t KInt;
typedef int64_t KLong;
typedef uint8_t  KUByte;
typedef uint16_t KUShort;
typedef uint32_t KUInt;
typedef uint64_t KULong;
typedef float   KFloat;
typedef double  KDouble;
typedef void*   KNativePtr;
typedef KFloat __attribute__ ((__vector_size__ (16)))   KVector4f;

typedef const void* KConstNativePtr;

typedef ObjHeader* KRef;
typedef const ObjHeader* KConstRef;
typedef const ArrayHeader* KString;

// TODO: Remove these typedefs. Use std_support directly everywhere.
using KStdString = kotlin::std_support::string;
template <typename Value>
using KStdDeque = kotlin::std_support::deque<Value>;
template <typename Key, typename Value>
using KStdUnorderedMap = kotlin::std_support::unordered_map<Key, Value>;
template <typename Value>
using KStdUnorderedSet = kotlin::std_support::unordered_set<Value>;
template <typename Value, typename Compare = std::less<Value>>
using KStdOrderedMultiset = kotlin::std_support::multiset<Value, Compare>;
template <typename Key, typename Value>
using KStdOrderedMap = kotlin::std_support::map<Key, Value>;
template <typename Value>
using KStdVector = kotlin::std_support::vector<Value>;
template <typename Value>
using KStdList = kotlin::std_support::list<Value>;
template <typename Value>
using KStdUniquePtr = kotlin::std_support::unique_ptr<Value>;
using kotlin::std_support::make_unique;

#ifdef __cplusplus
extern "C" {
#endif

extern const TypeInfo* theAnyTypeInfo;
extern const TypeInfo* theArrayTypeInfo;
extern const TypeInfo* theBooleanArrayTypeInfo;
extern const TypeInfo* theByteArrayTypeInfo;
extern const TypeInfo* theCharArrayTypeInfo;
extern const TypeInfo* theDoubleArrayTypeInfo;
extern const TypeInfo* theForeignObjCObjectTypeInfo;
extern const TypeInfo* theIntArrayTypeInfo;
extern const TypeInfo* theLongArrayTypeInfo;
extern const TypeInfo* theNativePtrArrayTypeInfo;
extern const TypeInfo* theFloatArrayTypeInfo;
extern const TypeInfo* theForeignObjCObjectTypeInfo;
extern const TypeInfo* theFreezableAtomicReferenceTypeInfo;
extern const TypeInfo* theObjCObjectWrapperTypeInfo;
extern const TypeInfo* theOpaqueFunctionTypeInfo;
extern const TypeInfo* theShortArrayTypeInfo;
extern const TypeInfo* theStringTypeInfo;
extern const TypeInfo* theThrowableTypeInfo;
extern const TypeInfo* theUnitTypeInfo;
extern const TypeInfo* theWorkerBoundReferenceTypeInfo;
extern const TypeInfo* theCleanerImplTypeInfo;

KBoolean IsInstance(const ObjHeader* obj, const TypeInfo* type_info) RUNTIME_PURE;
KBoolean IsInstanceOfClassFast(const ObjHeader* obj, int32_t lo, int32_t hi) RUNTIME_PURE;
void CheckCast(const ObjHeader* obj, const TypeInfo* type_info);
KBoolean IsArray(KConstRef obj) RUNTIME_PURE;
bool IsSubInterface(const TypeInfo* thiz, const TypeInfo* other) RUNTIME_PURE;

/// Utility function that is used to determine long type size in compile time.
long Kotlin_longTypeProvider();

#ifdef __cplusplus
}
#endif

#endif // RUNTIME_TYPES_H
