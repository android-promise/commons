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

package promise.commonsapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import promise.commons.tx.Callback2
import promise.commons.tx.CallbackWithResolver
import promise.commons.tx.Promise
import promise.commons.tx.PromiseCallback
import promise.commons.tx.PromiseResult
import promise.commons.tx.Resolver

object FakeRepository {
  fun getItems(): PromiseCallback<List<Any>> = PromiseCallback { resolve, _ ->
    resolve(promise.commons.model.List.generate(3) { Any() })
  }

  fun getItems(result: PromiseResult<List<Any>, Throwable>) {
    result.response(promise.commons.model.List.generate(3) { Any() })
  }

  fun getItems2(): Promise<List<Any>> = Promise.resolve(promise.commons.model.List.generate(3) { Any() })

  fun getItems3(): Promise<List<Any>> = Promise(object : CallbackWithResolver<Any, List<Any>> {
    override fun call(arg: Any, resolver: Resolver<List<Any>>) {
      resolver.resolve(promise.commons.model.List.generate(3) { Any() }, null)
    }
  })
}

class UsingCallbacksActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_using_callbacks)
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    FakeRepository.getItems().then {
      /// log items
      it
    }.execute()

    FakeRepository.getItems(PromiseResult<List<Any>, Throwable>()
        .withCallback {
          /// log items
        })

    FakeRepository.getItems2().then(object : Callback2<List<Any>, Unit> {
      override fun call(arg: List<Any>) {
        /// log items
      }
    })

    Promise.all<Unit, List<Any>>(FakeRepository.getItems2(), FakeRepository.getItems3())
        .then(object : Callback2<List<List<Any>>, Unit> {
          override fun call(arg: List<List<Any>>) {
            /// log items, collected together
          }
        })
  }
}
