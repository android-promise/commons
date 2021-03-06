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

package promise.commons.tx;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import promise.commons.AndroidPromise;

/**
 * @param <R>
 */
public class Promise<R> {
  /**
   *
   */
  private static final AndroidPromise instance = AndroidPromise.instance();
  /**
   *
   */
  private State state;
  /**
   *
   */
  private R result;
  /**
   *
   */
  private Throwable error;
  /**
   *
   */
  private List<Resolver> handlers = new promise.commons.model.List<>();

  /**
   *
   */
  public Promise() {
    this.state = State.Fulfilled;
  }

  /**
   * @param callback
   * @param <A>
   */
  public <A> Promise(final CallbackWithResolver<? super A, R> callback) {
    this.state = State.Pending;
    final Resolver<? super R> finalResolver = (result, error) -> {
      final List<Resolver> nextPromises = new ArrayList<>();
      final CountDownLatch signal = new CountDownLatch(1);
      instance.execute(() -> {
        if (this.getState() == State.Pending) {
          nextPromises.addAll(Promise.this.handlers);
          this.result = result;
          this.error = error;
          this.state = error != null ? State.Rejected : State.Fulfilled;
        }
        signal.countDown();
      });

      try {
        signal.await();
      } catch (InterruptedException ignored) {
      }

      for (Resolver next : nextPromises) next.resolve(result, error);
    };

    final Resolver<? super R> resolver = (Resolver<R>) (result, error) -> {
      if (result != null && error != null)
        throw new IllegalArgumentException("result and error must both be null");
      if (Promise.this.state == State.Pending) {
        if (result instanceof Promise)
          ((Promise<R>) result).pipe((Resolver<R>) finalResolver);
        else finalResolver.resolve(result, error);
      }
    };

    instance.execute(() -> {
      try {
        callback.call(null, resolver);
      } catch (RuntimeException ex) {
        finalResolver.resolve(null, ex);
      }
    });
  }

  /**
   * @param delayMillis
   * @param callback
   * @param <A>
   */
  public <A> Promise(final long delayMillis, final CallbackWithResolver<? super A, R> callback) {
    this((CallbackWithResolver<A, R>) (arg, resolver) -> instance.execute(() -> {
      try {
        callback.call(arg, resolver);
      } catch (RuntimeException ex) {
        resolver.resolve(null, ex);
      }
    }, delayMillis));
  }

  /**
   * @param delayMillis
   * @param callback
   * @param <A>
   */
  public <A> Promise(final long delayMillis, final Callback2<? super A, ? extends R> callback) {
    this((arg, resolver) -> instance.execute(() -> {
      try {
        resolver.resolve(callback.call((A) arg), null);
      } catch (RuntimeException ex) {
        resolver.resolve(null, ex);
      }
    }, delayMillis));
  }

  /**
   * @param delayMillis
   * @param callback
   * @param <A>
   */
  public <A> Promise(final long delayMillis, final VoidArgCallback<? extends R> callback) {
    this((CallbackWithResolver<A, R>) (arg, resolver) -> instance.execute(() -> {
      try {
        resolver.resolve(callback.call(), null);
      } catch (RuntimeException ex) {
        resolver.resolve(null, ex);
      }
    }, delayMillis));
  }

  /**
   * @param callback
   * @param <A>
   */
  public <A> Promise(final Callback2<? super A, ? extends R> callback) {
    this((arg, resolver) -> {
      try {
        resolver.resolve(callback.call((A) arg), null);
      } catch (RuntimeException ex) {
        resolver.resolve(null, ex);
      }
    });
  }

  /**
   * @param error
   * @param <N>
   */
  public <N> Promise(final RuntimeException error) {
    this((arg, resolver) -> resolver.resolve(null, error));
  }

  /**
   * @param promise
   * @param <R>
   * @return
   */
  @Contract(value = "_ -> param1", pure = true)
  public static <R> Promise<R> resolve(Promise<R> promise) {
    return promise;
  }

  /**
   * @param result
   * @param <R>
   * @return
   */
  @NonNull
  @Contract("_ -> new")
  public static <R> Promise<R> resolve(final R result) {
    return new Promise<>((arg, resolver) -> resolve(result));
  }

  /**
   * @param ex
   * @param <A>
   * @param <R>
   * @return
   */
  public static <A, R> Promise<R> resolve(final Throwable ex) {
    return new Promise<>((CallbackWithResolver<A, R>)
        (arg, resolver) -> resolver.resolve(null, ex));
  }

  /**
   * @param promises
   * @param <A>
   * @param <R>
   * @return
   */
  @NonNull
  @Contract("_ -> new")
  @SafeVarargs
  public static <A, R> Promise<List<R>> all(final Promise<R>... promises) {
    return new Promise<>((CallbackWithResolver<A, List<R>>) (arg, resolver) -> {
      final AtomicInteger totalCount = new AtomicInteger(promises.length);
      for (Promise<R> promise : promises)
        promise.pipe((result, error) -> {
          if (error != null)
            resolver.resolve(null, new RuntimeException("one of promise in promises was rejected", error));
          else if (totalCount.decrementAndGet() == 0) {
            List<R> results = new ArrayList<>(promises.length);
            for (Promise<R> promise1 : promises) results.add(promise1.result);
            resolver.resolve(results, null);
          }
        });
    });
  }

  /**
   * @param promises
   * @param <A>
   * @param <R>
   * @return
   */
  @NonNull
  @Contract("_ -> new")
  public static <A, R> Promise<List<R>> all(final List<? extends Promise<R>> promises) {
    return new Promise<>((CallbackWithResolver<A, List<R>>) (arg, resolver) -> {
      final AtomicInteger totalCount = new AtomicInteger(promises.size());
      for (Promise<R> promise : promises)
        promise.pipe((result, error) -> {
          if (error != null)
            resolver.resolve(null, new RuntimeException("one of promise in promises was rejected", error));
          else if (totalCount.decrementAndGet() == 0) {
            List<R> results = new ArrayList<>(promises.size());
            for (Promise<R> promise1 : promises) results.add(promise1.result);
            resolver.resolve(results, null);
          }
        });
    });
  }

  /**
   * @param promises
   * @param <A>
   * @param <R>
   * @return
   */
  public static <A, R> Promise<R> race(final List<? extends Promise<R>> promises) {
    return new Promise<>((CallbackWithResolver<A, R>) (arg, resolver) -> {
      final AtomicInteger totalCount = new AtomicInteger(promises.size());
      for (Promise<R> promise : promises)
        promise.pipe((result, ex) -> {
          if (ex == null) resolver.resolve(result, null);
          else if (totalCount.decrementAndGet() == 0)
            resolver.resolve(null, new RuntimeException("all promise were rejected."));
        });
    });
  }

  public State getState() {
    return state;
  }

  public R getResult() {
    return result;
  }

  public Throwable getError() {
    return error;
  }

  public boolean isSuccess() {
    return error == null;
  }

  /**
   * @param resolver
   */
  public void pipe(Resolver<R> resolver) {
    if (this.state == State.Pending) this.handlers.add(resolver);
    else resolver.resolve(result, error);
  }

  /**
   * @param self
   * @param piper
   * @param <A>
   * @param <R>
   * @return
   */
  private <A, R> Promise<R> __pipe(final Promise<A> self,
                                   final Piper<? super A, R> piper) {
    return new Promise<>((CallbackWithResolver<A, R>) (arg, resolver) ->
        self.pipe((result, error) ->
            piper.pipe(result, error, resolver)));
  }

  /**
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> then(final Callback2<? super R, ? extends N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          resolver.resolve(then.call(arg), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
    });
  }

  /**
   * @param then
   */
  public void then(final VoidReturnCallback<? super R> then) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call(arg);
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
    });
  }

  /**
   * @param then
   */
  public void then(final VoidArgVoidReturnCallback then) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call();
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
    });
  }

  /**
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> then(final VoidArgCallback<? extends N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          resolver.resolve(then.call(), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
    });
  }

  /**
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> then(final CallbackWithResolver<? super R, N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call(arg, resolver);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
    });
  }

  /**
   * @param delayMillis
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> thenDelay(final long delayMillis, final Callback2<? super R, ? extends N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          resolver.resolve(then.call(arg), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      }, delayMillis);
    });
  }

  /**
   * @param delayMillis
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> thenDelay(final long delayMillis, final VoidArgCallback<? extends N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          resolver.resolve(then.call(), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      }, delayMillis);
    });
  }

  /**
   * @param delayMillis
   * @param then
   */
  public void thenDelay(final long delayMillis, final VoidReturnCallback<? super R> then) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call(arg);
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      }, delayMillis);
    });
  }

  /**
   * @param delayMillis
   * @param then
   */
  public void thenDelay(final long delayMillis, final VoidArgVoidReturnCallback then) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call();
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      }, delayMillis);
    });
  }

  /**
   * @param delayMillis
   * @param then
   * @param <N>
   * @return
   */
  public <N> Promise<N> thenDelay(final long delayMillis, final CallbackWithResolver<? super R, N> then) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) resolver.resolve(null, error);
      else instance.execute(() -> {
        try {
          then.call(arg, resolver);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      }, delayMillis);
    });
  }

  /**
   * @param callback
   * @return
   */
  public Promise<R> error(final Callback2<Throwable, ? extends R> callback) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) instance.execute(() -> {
        try {
          resolver.resolve(callback.call(error), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
      else {
        resolver.resolve(arg, null);
      }
    });
  }

  /**
   * @param callback
   * @return
   */
  public Promise<R> error(final VoidArgCallback<? extends R> callback) {
    return __pipe(this, (arg, error, resolver) -> {
      if (error != null) instance.execute(() -> {
        try {
          resolver.resolve(callback.call(), null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
      else resolver.resolve(arg, null);
    });
  }

  /**
   * @param callback
   */
  public void error(final VoidReturnCallback<Throwable> callback) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) instance.execute(() -> {
        try {
          callback.call(error);
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
      else resolver.resolve(arg, null);
    });
  }

  /**
   * @param callback
   */
  public void error(final VoidArgVoidReturnCallback callback) {
    __pipe(this, (arg, error, resolver) -> {
      if (error != null) instance.execute(() -> {
        try {
          callback.call();
          resolver.resolve(null, null);
        } catch (RuntimeException ex) {
          resolver.resolve(null, ex);
        }
      });
      else resolver.resolve(arg, null);
    });
  }

  /**
   *
   */
  public enum State {

    /**
     *
     */
    Pending,
    /**
     *
     */
    Fulfilled,
    /**
     *
     */
    Rejected
  }

  /**
   * @param <A>
   * @param <R>
   */
  public interface Piper<A, R> {

    /**
     * @param arg
     * @param error
     * @param resolver
     */
    void pipe(A arg, Throwable error, Resolver<? super R> resolver);
  }
}