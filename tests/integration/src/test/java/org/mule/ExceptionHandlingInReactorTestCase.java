/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule;

import static reactor.core.publisher.Flux.empty;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.joda.time.DateTime;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ExceptionHandlingInReactorTestCase extends AbstractMuleContextTestCase {

  Processor p1 = event -> event;
  ReactiveProcessor p2 = publisher -> publisher;
  Processor error = event -> {
    throw new IllegalArgumentException();
  };
  Processor fatal = event -> {
    throw new LinkageError();
  };

  Consumer<Event> resultConsumer = result -> System.out.println("DONE: RESULT " + result);
  Consumer<Throwable> errorConsumer = throwable -> System.out.println("DONE: ERROR " + throwable);

  Function<ReactiveProcessor, ReactiveProcessor> notificationInterceptor = processor -> eventPublisher -> from(eventPublisher)
                                                                                                              .doOnNext(event -> System.out.println("BEFORE " + processor.hashCode()))
                                                                                                              .transform(processor)
                                                                                                              .doOnNext(event -> System.out.println("AFTER OK " + processor.hashCode()))
                                                                                                              .doOnError(throwable -> System.out.println("AFTER ERROR " + processor.hashCode()));



  @Test
  public void testMethod() throws MuleException {

    Hooks.onOperatorError((t, o) -> {
      return new RuntimeException(t);
    });
    just(testEvent()).flatMap(event -> {
      try {
        return just(event)
                   //.publishOn(fromExecutorService(muleContext.getSchedulerService().cpuLightScheduler()))
                   .transform(notificationInterceptor.apply(fatal))
                   //.map(x -> {if (true) { throw new IllegalArgumentException();} else { return x; } })
                   .onErrorResumeWith(throwable -> {
                     System.out.println("HANDLED ERROR");
                     return empty();
                   });
      } catch (Throwable throwable) {
        System.out.println("HANDLED FATAL");
        return empty();
      }
    })
        .transform(p1)
        .transform(notificationInterceptor.apply(p2))
        .subscribe(event -> event.getContext().success(event));

    from(testEvent().getContext().getResponsePublisher()).subscribe(resultConsumer);
  }

  @Test
  public void parallelTest() {
    Function<Integer,Integer> f1 = x -> {
      try {
        System.out.println("Transforming " + x + " on thread " + Thread.currentThread().getId());
        Thread.sleep(500*(x%5));
        System.out.println("Done transforming " + x);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return x;
    };

    Consumer<Integer> s1 = x -> {
      try {
        System.out.println("Consuming " + x + " on thread " + Thread.currentThread().getId());
        Thread.sleep(100*(x%5));
        System.out.println("Done consuming " + x);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    };

    List<Integer> list = new ArrayList<>(Arrays.asList(5, 4, 3, 2, 1));
    //Flux<Integer> flux = Flux.fromIterable(list)
    Flux<Integer> flux = Mono.fromCallable(() -> DateTime.now().millisOfDay().get()).repeat().take(5)
    //Flux<Integer> flux = Flux.create(e -> { e.next(DateTime.now().millisOfDay().get()); })
      //.take(5)
      .flatMap(x -> {
        //return just(x).map(f1).subscribeOn(Schedulers.elastic());
        //return just(x).map(f1);
        return just((Integer)x).publishOn(Schedulers.elastic()).map(f1);
      })
      .subscribeOn(Schedulers.elastic());

    flux.subscribe(s1);
    //flux.subscribe(s1);
    System.out.println("Running in thread " + Thread.currentThread().getId());
    //flux.then().block();
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    //.subscribe(System.out::println);
  }
}
