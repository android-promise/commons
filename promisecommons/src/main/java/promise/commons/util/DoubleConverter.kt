/*
 * Copyright 2017, Peter Vincent
 * Licensed under the Apache License, Version 2.0, Android Promise.
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package promise.commons.util

/**
 * The double converter converts a class instance to and from another form
 *
 * @param <T> the type instance to be converted
 * @param <DESERIALIZABLE> type to get the instance from during de-serializing
 * @param <SERIALIZABLE> type returned after serializing the instance
</SERIALIZABLE></DESERIALIZABLE></T> */
interface DoubleConverter<T, in DESERIALIZABLE, out SERIALIZABLE> {
  /*
   * deserialize back the instance from the serializes form
   * @param e serialized form
   * @return back the instance
   */
  fun deserialize(e: DESERIALIZABLE): T

  /*
   *  serializes the instance to another form
   * @param t the instance to be serialized
   * @return the serialized form of t
   */
  fun serialize(t: T): SERIALIZABLE
}