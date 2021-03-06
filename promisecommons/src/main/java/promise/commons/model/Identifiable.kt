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

package promise.commons.model

/**
 * used to identify an instance in a collection
 *
 * @param T type of identifying id
 */
interface Identifiable<T> {
  /**
   * set the id to the instance
   *
   * @param t id
   */
  fun setId(t: T)

  /**
   * get the id from the instance
   *
   * @return
   */
  fun getId(): T
}