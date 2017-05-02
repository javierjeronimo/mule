/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.util;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.api.metadata.DataType.INPUT_STREAM;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import static org.mule.runtime.api.metadata.DataType.NUMBER;
import static org.mule.runtime.api.metadata.DataType.OBJECT;
import static org.mule.runtime.api.metadata.DataType.STRING;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JAVA;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.expression.ExpressionRuntimeException;
import org.mule.runtime.core.el.DefaultExpressionManager;
import org.mule.runtime.extension.api.annotation.Ignore;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

@SmallTest
public class DWAttributeEvaluatorTestCase extends AbstractMuleContextTestCase {

  private static final String HOST_PORT_JSON = "{\"host\":\"0.0.0.0\", \"port\" : 8081}";
  private static final String JSON_CAR = "{\n  \"color\": \"RED\",\n  \"price\": 1000\n}";
  private static final String DW_CAR = "#[{color : 'RED', price: 1000}]";
  private static final DataType CAR_DATA_TYPE = DataType.fromType(Car.class);
  private Event mockMuleEvent = mock(Event.class);
  private DefaultExpressionManager expressionManager;

  @Before
  public void setUp() {
    expressionManager = new DefaultExpressionManager(muleContext);
  }

  @Test
  public void plainTextValue() {
    String staticValue = "attributeEvaluator";
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator(staticValue);
    assertThat(attributeEvaluator.resolveValue(mockMuleEvent), is(staticValue));
  }

  @Test
  public void getJavaStringFromIntJsonProperty() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.port]", STRING);
    Event event = newEvent(HOST_PORT_JSON, APPLICATION_JSON);
    Object port = attributeEvaluator.resolveValue(event);
    assertThat(port, is("8081"));
  }

  @Test
  public void getJavaIntFromIntJsonProperty() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.port]", NUMBER);
    Event event = newEvent(HOST_PORT_JSON, APPLICATION_JSON);
    Object port = attributeEvaluator.resolveValue(event);
    assertThat(port, is(8081));
  }

  @Test
  public void getJavaStringFromStringJsonProperty() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.host]", STRING);
    Event event = newEvent(HOST_PORT_JSON, APPLICATION_JSON);
    Object host = attributeEvaluator.resolveValue(event);
    assertThat(host, is("0.0.0.0"));
  }

  @Test
  public void getJavaObjectFromStringJsonProperty() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.host]", OBJECT);
    Event event = newEvent(HOST_PORT_JSON, APPLICATION_JSON);
    Object resolveValue = attributeEvaluator.resolveValue(event);
    assertThat(IOUtils.toString((InputStream) resolveValue), is("\"0.0.0.0\""));
  }

  @Test
  public void getJavaInputStreamFromStringJsonProperty() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.host]", INPUT_STREAM);
    Event event = newEvent(HOST_PORT_JSON, APPLICATION_JSON);
    Object resolveValue = attributeEvaluator.resolveValue(event);
    assertThat(IOUtils.toString((InputStream) resolveValue), is("\"0.0.0.0\""));
  }

  @Ignore
  @Test
  public void fromBytes() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload]", STRING);
    Event event = newEvent("{}".getBytes(), APPLICATION_JSON);
    Object resolveValue = attributeEvaluator.resolveValue(event);
  }

  @Test
  public void test() throws MuleException {
    expressionManager.evaluate("#[payload]", newEvent(null, APPLICATION_JAVA),
                               BindingContext.builder().addBinding("payload", new TypedValue(null, OBJECT)).build());
    MediaType mediaType = MediaType.create("application", "java", Charset.forName("UTF-8"));
    DataType build = DataType.builder(STRING).mediaType(mediaType).build();
    expressionManager.evaluate("#[payload]", build,
                               BindingContext.builder().addBinding("payload", new TypedValue("hola", build)).build(),
                               newEvent("hola", mediaType));
  }

  @Test
  public void getJavaPojo() throws MuleException {
    AttributeEvaluator attributeEvaluator =
        getAttributeEvaluator(DW_CAR, DataType.fromType(Car.class));
    Object car = attributeEvaluator.resolveValue(newEvent());
    assertThat(car, is(allOf(hasProperty("color", is("RED")), hasProperty("price", is(1000)))));
  }

  @Test
  public void getJsonObjectAsString() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator(DW_CAR, JSON_STRING);
    Object car = attributeEvaluator.resolveValue(newEvent());
    //TODO - BUG - DW SHOULD RETURN A STRING INSTEAD OF A CURSOR PROVIDER
    CursorStreamProvider streamProvider = (CursorStreamProvider) car;

    assertThat(IOUtils.toString(streamProvider.openCursor()), is(JSON_CAR));
  }

  @Test
  public void getJavaCarFromJsonCar() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload]", CAR_DATA_TYPE);
    Object car = attributeEvaluator.resolveValue(newEvent(JSON_CAR, APPLICATION_JSON));
    assertThat(car, is(allOf(hasProperty("color", is("RED")), hasProperty("price", is(1000)))));
  }

  @Test
  public void getMapFromJsonCar() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload]", DataType.fromType(Map.class));
    Map<String, String> car = (Map<String, String>) attributeEvaluator.resolveValue(newEvent(JSON_CAR, APPLICATION_JSON));
    assertThat(car, hasEntry(is("price"), is(1000)));
    assertThat(car, hasEntry(is("color"), is("RED")));
  }

  @Test
  public void getListOfCarsFromJsonCar() throws MuleException {
    AttributeEvaluator attributeEvaluator =
        getAttributeEvaluator("#[[payload as Object { class: \"org.mule.runtime.core.util.DWAttributeEvaluatorTestCase\\$Car\"}]]",
                              DataType.fromType(List.class));
    List<Car> cars = (List<Car>) attributeEvaluator.resolveValue(newEvent(JSON_CAR, APPLICATION_JSON));
    Car car = cars.get(0);
    assertThat(car, is(allOf(hasProperty("color", is("RED")), hasProperty("price", is(1000)))));
  }

  @Test
  public void getListOfMapsFromJsonCar() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[[payload as Object]]", DataType.fromType(List.class));
    List<Map<String, String>> cars =
        (List<Map<String, String>>) attributeEvaluator.resolveValue(newEvent(JSON_CAR, APPLICATION_JSON));
    Map<String, String> car = cars.get(0);
    assertThat(car, hasEntry(is("price"), is(1000)));
    assertThat(car, hasEntry(is("color"), is("RED")));
  }

  @Test(expected = ExpressionRuntimeException.class)
  public void parseExpressionAreNotSupported() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("invalid #['expression']");
    attributeEvaluator.resolveValue(newEvent());
  }

  @Test
  public void resolveIntegerValueFromJsonObject() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.port]");
    Integer port = attributeEvaluator.resolveIntegerValue(newEvent(HOST_PORT_JSON, APPLICATION_JSON));
    assertThat(port, is(8081));
  }

  @Test
  public void resolveIntegerValueFromJavaString() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload]", DataType.NUMBER);
    Object port = attributeEvaluator.resolveValue(newEvent("12", APPLICATION_JAVA));
    assertThat(port, is(12));
  }

  @Test
  public void resolveStringValue() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.port]");
    String port = attributeEvaluator.resolveStringValue(newEvent(HOST_PORT_JSON, APPLICATION_JSON));
    assertThat(port, is("8081"));
  }

  @Test
  public void getBooleanValue() throws MuleException {
    AttributeEvaluator attributeEvaluator = getAttributeEvaluator("#[payload.ok]");
    Boolean bool = attributeEvaluator.resolveBooleanValue(newEvent("{\"ok\" : true}", APPLICATION_JSON));
    assertThat(bool, is(true));
  }

  private Event newEvent(Object payload, MediaType applicationJson) throws MuleException {
    return Event.builder(newEvent())
        .message(Message.builder()
            .payload(payload)
            .mediaType(applicationJson)
            .build())
        .build();
  }

  private AttributeEvaluator getAttributeEvaluator(String expression) {
    return getAttributeEvaluator(expression, null);
  }

  private AttributeEvaluator getAttributeEvaluator(String expression, DataType expectedDataType) {
    AttributeEvaluator attributeEvaluator = new AttributeEvaluator(expression, expectedDataType);
    attributeEvaluator.initialize(expressionManager);
    return attributeEvaluator;
  }

  public static class Car {

    private String color;
    private int price;

    public String getColor() {
      return color;
    }

    public void setColor(String color) {
      this.color = color;
    }

    public int getPrice() {
      return price;
    }

    public void setPrice(int price) {
      this.price = price;
    }
  }
}
