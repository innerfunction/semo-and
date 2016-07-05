// Copyright 2016 InnerFunction Ltd.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License
package com.innerfunction.q;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jloriente on 17/03/2016.
 *
 */
public class Q {

    /**
     * Return a promise resolving to the value argument.
     */
    public static <T> Promise<T> resolve(T value){
        Promise<T> promise = new Promise<T>();
        promise.resolve(value);
        return promise;
    }

    /** Return a rejected promise with the specified error. */
    public static Promise reject(Object error){
        Promise promise = new Promise();
        if ( error instanceof String ){
            promise.reject((String) error );
        }
        else if ( error instanceof Exception ){
            promise.reject((Exception) error );
        }
        return promise;
    }

    /** Return a rejected promise with the specified error. */
    public static Promise reject(String error){
        Promise promise = new Promise();
        promise.reject(error);
        return promise;
    }

    /**
     * Return a promise which is resolved once all promises in the array argument have been resolved.
     * Wait for all promises in a list to resolve or reject.
     * The resulting promise will resolve to an array containing the value result of each promise in the
     * array argument.
     * If any promise in the argument is rejected then the result is rejected with the first generated error.
     */
    public static <R> Promise<List<R>> all(final List<Promise<R>> deferreds) {
        final Promise<List<R>> dresult = new Promise<List<R>>();
        final Promise.DeferredSet<R> dset = new Promise.DeferredSet<>();
        for (Promise<R> deferred : deferreds) {
            deferred
                .then(new Promise.Callback<R, Object>() {
                    @Override
                    public R result(R result) {
                        dset.results.add(result);
                        if (dset.results.size() == deferreds.size()) {
                            dresult.resolve(dset.results);
                        }
                        return result;
                    }
                })
                .error(new Promise.ErrorCallback() {
                    @Override
                    public void error(Exception e) {
                        dresult.reject(e);
                    }
                });
        }
        return dresult;
    }

    /** Test whether an argument is a promise. */
    public static Boolean isPromise(Object obj){
        return obj instanceof Promise;
    };


    public static class Promise<T> {

        /**
         * Marker interface for callback types.
         */
        public static interface ICallback<T, R> {}

        /**
         * Callback for passing a deferred promise result.
         */
        public static interface Callback<T, R> extends ICallback<T, R> {
            public R result(T result);
        }

        /**
         * Callback for passing a deferred promise result with an asynchronous chained result.
         */
        public static interface AsyncCallback<T, R> extends ICallback<T, R> {
            public Promise<R> result(T result);
        }

        /**
         * Callback for passing a deferred promise error.
         */
        public static interface ErrorCallback {
            public void error(Exception e);
        }

        /**
         * A deferred continuation. Composed of a then callback and a next deferred.
         */
        static class Continuation<T, R> {
            ICallback<T, R> thenCallback;
            Promise<R> next;

            Continuation(ICallback<T, R> cb, Promise<R> n) {
                thenCallback = cb;
                next = n;
            }

            /**
             * Invoke the continuation.
             */
            void resolve(T result) {
                try {
                    if (thenCallback instanceof Callback) {
                        R thenResult = ((Callback<T, R>) thenCallback).result(result);
                        next.resolve(thenResult);
                    } else if (thenCallback instanceof AsyncCallback) {
                        Promise<R> thenResult = ((AsyncCallback<T, R>) thenCallback).result(result);
                        next.resolve(thenResult);
                    }
                } catch (Exception e) {
                    next.reject(e);
                }
            }

            /**
             * Invoke the continuation with a rejection.
             */
            void reject(Exception e) {
                next.reject(e);
            }
        }

        /**
         * The promise result.
         */
        private T result;
        /**
         * The promise error.
         */
        private Exception error;
        /**
         * Error callback waiting for promise error.
         */
        private ErrorCallback errCallback;
        /**
         * The deferred continuation.
         */
        private Continuation<T, ?> continuation;
        /**
         * Flag indicating whether the promise has been resolved.
         */
        private boolean resolved;
        /**
         * Flag indicating whether the promise has been rejected.
         */
        private boolean rejected;

        public Promise() {}

        public Promise(T result) {
            resolve(result);
        }

        public static <R> Promise<R> defer(R result) {
            return new Promise<R>(result);
        }

        /**
         * A set of deferred promise results.
         */
        private static class DeferredSet<R> {
            List<R> results;

            public DeferredSet() {
                results = new ArrayList<R>();
            }
        }



        /**
         * Resolve the promise by passing a result.
         *
         * @param result
         */
        public void resolve(T result) {
            if (!(resolved || rejected)) {
                try {
                    resolved = true;
                    this.result = result;
                    if (continuation != null) {
                        continuation.resolve(result);
                    }
                } catch (Exception e) {
                    reject(e);
                }
            }
        }

        /**
         * Resolve the promise by passing a deferred result.
         */
        public void resolve(Promise<T> result) {
            if (!(resolved || rejected)) {
                try {
                    result
                        .then(new Callback<T, T>() {
                            @Override
                            public T result(T result) {
                                Promise.this.resolve(result);
                                return result; // NOTE: Return value not actually used or needed here.
                            }
                        })
                        .error(new ErrorCallback() {
                            @Override
                            public void error(Exception e) {
                                Promise.this.reject(e);
                            }
                        });
                } catch (Exception e) {
                    reject(e);
                }
            }
        }

        /**
         * Reject the promise by passing an error.
         *
         * @param e
         */
        public void reject(Exception e) {
            if (!(resolved || rejected)) {
                rejected = true;
                error = e;
                if (errCallback != null) {
                    errCallback.error(e);
                } else if (continuation != null) {
                    continuation.reject(e);
                }
            }
        }

        public void reject(String message) {
            reject(new Exception(message));
        }

        /**
         * Add a promise result callback.
         *
         * @param cb
         * @return
         */
        public <R> Promise<R> then(ICallback<T, R> cb) {
            Promise<R> next = new Promise<R>();
            if (resolved) {
                // Current promise is already resolved, so immediately invoke the callback.
                try {
                    if (cb instanceof Callback<?, ?>) {
                        next.resolve(((Callback<T, R>) cb).result(result));
                    } else if (cb instanceof AsyncCallback<?, ?>) {
                        next.resolve(((AsyncCallback<T, R>) cb).result(result));
                    }
                } catch (Exception e) {
                    next.reject(e);
                }
            } else if (rejected) {
                // If the current promise is already rejected then pass the error onto the
                // next promise.
                next.reject(error);
            } else {
                // Promise is neither resolved nor rejected, copy the callback for later usage.
                continuation = new Continuation<T, R>(cb, next);
            }
            return next;
        }

        /**
         * Add a promise reject callback.
         *
         * @param cb
         * @return
         */
        public Promise<T> error(ErrorCallback cb) {
            if (rejected) {
                cb.error(error);
            } else if (!resolved) {
                errCallback = cb;
            }
            return this;
        }

    }

}