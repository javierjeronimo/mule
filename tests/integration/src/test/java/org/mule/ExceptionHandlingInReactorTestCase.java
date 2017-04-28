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

import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Test;
import reactor.core.publisher.Hooks;

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


}
