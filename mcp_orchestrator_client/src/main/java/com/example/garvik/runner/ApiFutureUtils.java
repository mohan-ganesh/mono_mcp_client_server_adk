package com.example.garvik.runner;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiFutureUtils {

  private static final Logger logger = LoggerFactory.getLogger(ApiFutureUtils.class);

  public static <T> Single<T> toSingle(ApiFuture<T> future) {
    return Single.create(
        emitter -> {
          ApiFutures.addCallback(
              future,
              new ApiFutureCallback<T>() {
                @Override
                public void onSuccess(T result) {
                  logger.debug("ApiFuture succeeded.");
                  emitter.onSuccess(result);
                }

                @Override
                public void onFailure(Throwable t) {
                  // Log the failure of the future before passing it down the reactive chain.
                  logger.error("ApiFuture failed with an exception.", t);
                  emitter.onError(t);
                }
              },
              Executors.newSingleThreadExecutor());
        });
  }

  public static <T> Maybe<T> toMaybe(ApiFuture<T> future) {
    return toSingle(future).toMaybe();
  }
}
