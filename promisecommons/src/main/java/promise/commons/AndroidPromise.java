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

package promise.commons;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import promise.commons.data.log.LogUtil;
import promise.commons.model.List;
import promise.commons.model.Message;
import promise.commons.model.function.MapFunction;
import promise.commons.tx.AsyncEither;
import promise.commons.tx.Either;
import promise.commons.tx.PromiseResult;
import promise.commons.util.Conditions;

/**
 *
 */
public class AndroidPromise {
  /**
   *
   */
  public static final String CLEANING_UP_RESOURCES = "Cleaning up resources";
  /**
   *
   */
  private static String TAG;

  /**
   *
   */
  public boolean enableDebug;
  /**
   *
   */
  private Application context;
  /**
   *
   */
  private ExecutorService executor;
  /**
   *
   */
  private PublishSubject<Message> bus;
  /**
   *
   */
  private Handler handler;
  /**
   *
   */
  private CompositeDisposable disposable;
  /**
   *
   */
  private List<Disposable> disposables;

  /**
   * @param context
   */
  AndroidPromise(Application context) {
    this.context = context;
    disposable = new CompositeDisposable();
  }

  /**
   * initialize promise with only application instance and single threaded environment
   *
   * @param context application
   */
  public static void init(Application context, boolean enableDebug) {
    try {
      AndroidPromiseInstanceProvider.instance();
      throw new IllegalStateException("Promise can only be instantiated once");
    } catch (IllegalAccessException ignored) {
      initializeRxUndeliverableError();
      SingletonInstanceProvider.provider(
          AndroidPromiseInstanceProvider.create(
              ApplicationInstanceProvider.create(context), enableDebug));
      TAG = LogUtil.makeTag(AndroidPromise.class);
    }
  }

  /**
   * initialize promise with application and number of threads to use
   *
   * @param context      application
   * @param numOfThreads threads to use in background tasks
   */
  public static void init(Application context, int numOfThreads, boolean enableDebug) {
    try {
      AndroidPromiseInstanceProvider.instance();
      throw new IllegalStateException("Promise can only be instantiated once");
    } catch (IllegalAccessException ignored) {
      initializeRxUndeliverableError();
      SingletonInstanceProvider.provider(
          AndroidPromiseInstanceProvider.create(
              ApplicationInstanceProvider.create(context),
              numOfThreads, enableDebug));
      TAG = LogUtil.makeTag(AndroidPromise.class);
    }
  }

  private static void initializeRxUndeliverableError() {
    RxJavaPlugins.setErrorHandler(throwable -> {
      if (throwable instanceof UndeliverableException)
        LogUtil.e(TAG, "undeliverable error: ", throwable);
      else Thread.currentThread().getUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), throwable);
    });
  }

  public static AndroidPromise instance() {
    try {
      return SingletonInstanceProvider.provider(
          AndroidPromiseInstanceProvider.instance()).get();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  /**
   * @param object
   */
  public void send(Message object) {
    if (bus == null) bus = PublishSubject.create();
    bus.onNext(object);
  }

  /**
   * @param sender
   * @param callBack
   * @return
   */
  public int listen(final String sender, final PromiseResult<Object, Throwable> callBack) {
    if (bus == null) bus = PublishSubject.create();
    if (disposables == null) disposables = new List<>();
    disposables.add(
        bus.subscribeOn(Schedulers.from(executor()))
            .observeOn(Schedulers.from(executor()))
            .subscribe(
                object -> {
                  if (sender.equals(object.sender())) callBack.response(object);
                },
                callBack::error));
    disposable.add(Conditions.checkNotNull(disposables.last()));
    return disposables.size() - 1;
  }

  /**
   * @param id
   */
  public void stopListening(int id) {
    if (bus == null) return;
    if (disposables == null || disposables.isEmpty()) return;
    disposable.remove(disposables.get(id));
  }

  public Application context() {
    return context;
  }

  /**
   * @return
   */
  public ExecutorService executor() {
    if (executor == null) return Executors.newSingleThreadExecutor();
    return executor;
  }

  /**
   * @param threads
   * @return
   */
  AndroidPromise threads(int threads) {
    if (executor == null) executor = Executors.newFixedThreadPool(threads);
    return this;
  }

  /**
   * @return
   */
  public AndroidPromise disableErrors() {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    });
    return this;
  }

  /**
   * @param runnable
   */
  public void execute(Runnable runnable) {
    executor().execute(runnable);
  }

  /**
   * @param runnable
   * @param wait
   */
  public void execute(Runnable runnable, long wait) {
    execute(() -> {
      try {
        Thread.sleep(wait);
        execute(runnable);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
  }

  /**
   * @param runnable
   */
  public void executeOnUi(Runnable runnable) {
    if (handler == null) handler = new Handler(Looper.getMainLooper());
    handler.post(runnable);
  }

  /**
   * @param runnable
   * @param wait
   */
  public void executeOnUi(Runnable runnable, long wait) {
    if (handler == null) handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(runnable, wait);
  }

  /**
   * @param runnable
   * @param waitInterval
   */
  public void executeRepeatativelyWithSeconds(Runnable runnable, long waitInterval) {
    ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate
        (runnable, 0, waitInterval, TimeUnit.SECONDS);
  }

  /**
   * @param action
   * @param promiseResult
   * @param <T>
   */
  public <T> void execute(
      final Callable<? extends T> action, final PromiseResult<T, Throwable> promiseResult) {
    if (disposables == null) disposables = new List<>();
    disposables.add(
        Observable.fromCallable(
            action)
            .observeOn(Schedulers.from(executor()))
            .subscribeOn(Schedulers.from(executor()))
            .subscribe(
                promiseResult::response,
                promiseResult::error));
    disposable.add(Conditions.checkNotNull(disposables.last()));
  }

  public <T> Either<T> execute(
      final Callable<? extends T> action) {
    return new AsyncEither<>((unitFunction1, unitFunction12) -> {
      if (disposables == null) disposables = new List<>();
      disposables.add(
          Observable.fromCallable(
              action)
              .observeOn(Schedulers.from(executor()))
              .subscribeOn(Schedulers.from(executor()))
              .subscribe((Consumer<T>) unitFunction1::invoke,
                  unitFunction12::invoke));
      disposable.add(Conditions.checkNotNull(disposables.last()));
      return null;
    });
  }

 /* public <T> void executeAsync(
    final AsyncAction<T> action, final Result<T, Throwable> response) {
    if (disposables == null) disposables = new List<>();
    disposables.add(
      Observable.fromCallable(
        new Callable<T>() {
          @Override
          public T call() throws Exception {
            return action.execute();
          }
        })
        .observeOn(Schedulers.from(instance().executor))
        .subscribeOn(Schedulers.from(instance().executor))
        .subscribe(
          new Consumer<T>() {
            @Override
            public void accept(T t) {
              response.response(t);
            }
          },
          new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
              response.error(throwable);
            }
          }));
    disposable.add(Conditions.checkNotNull(disposables.last()));
  }*/

  /**
   * @param actions
   * @param promiseResult
   */
  public void execute(
      final List<? extends Callable<?>> actions, final PromiseResult<List<?>, Throwable> promiseResult) {
    if (disposables == null) disposables = new List<>();
    disposables.add(
        Observable.zip(
            actions.map(
                (MapFunction<ObservableSource<?>, Callable<?>>) action -> (ObservableSource<Object>) observer -> {
                  try {
                    observer.onNext(action.call());
                  } catch (Exception e) {
                    observer.onError(e);
                  }
                }),
            List::fromArray)
            .observeOn(Schedulers.from(executor()))
            .subscribeOn(Schedulers.from(executor()))
            .subscribe(
                promiseResult::response,
                promiseResult::error));
    disposable.add(Conditions.checkNotNull(disposables.last()));
  }

  public CompositeDisposable getCompositeDisposable() {
    return disposable;
  }

  public Application getApplication() {
    return context;
  }

  /**
   * @return
   */
  public void terminate() {
    send(new Message(TAG, CLEANING_UP_RESOURCES));
    executeOnUi(() -> {
      context = null;
      disposable.dispose();
      disposables.clear();
      bus = null;
      executor().shutdownNow();
    }, 50);
  }
}
