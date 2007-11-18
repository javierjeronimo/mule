/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.issues;

import org.mule.extras.client.MuleClient;
import org.mule.tck.FunctionalTestCase;
import org.mule.umo.UMOException;
import org.mule.umo.UMOMessage;

public class ResponseTransformerMule2165TestCase extends FunctionalTestCase
{

    public static final long TIMEOUT = 1000L;
    public static final String MESSAGE = "a message";
    // i don't know if this is the "correct" response - it's *one* of the responses in 1.4,
    // and it seems vaguely reasonable.

    /* RM
    Described as:
    1. Client dispatch = "outbound"
    2. Compoennt receiver = "inbound"
    3. Response from Component = "response"
    Note that because the response transformer is configured locally on the outbound endppoint it only gets called once
    */
    public static final String LOCAL_RESPONSE = MESSAGE + " outbound inbound response";
    // an alternative in 1.4 is " outbound outbound response response" for the global
    // transformers, which also makes some sense
    /* RM
    Described as:
    1. Client dispatch = "outbound"
    2. Compoennt receiver = "inbound"
    3. Response from Component = "response"
    4. Response from outbound endpoint (to the component) = "response"
    Note that because the global outbound inpoint is also the inbound endpoint of the bounce component
    The "response" ResponseTransformer gets called twice
    */
    public static final String GLOBAL_RESPONSE = MESSAGE + " outbound inbound response response";

    protected String getConfigResources()
    {
        return "issues/response-transformer-mule-2165-test.xml";
    }

    protected MuleClient send(String endpoint) throws UMOException
    {
        MuleClient client = new MuleClient();
        client.sendNoReceive(endpoint, MESSAGE, null);
        return client;
    }

    protected String receive(MuleClient client, String endpoint) throws Exception
    {
        UMOMessage message = client.receive(endpoint, TIMEOUT);
        assertNotNull("no response from " + endpoint, message);
        assertNotNull(message.getPayloadAsString());
        return message.getPayloadAsString();
    }

    protected void testTransformered(String endpoint, String response) throws Exception
    {
        String message = receive(send("in-" + endpoint), "out-" + endpoint);
        assertEquals("bad response (" + message + ")  for " + endpoint, response, message);
    }

    public void testGlobalNameGlobalTransformer() throws Exception
    {
        testTransformered("global-name-global-transformer", GLOBAL_RESPONSE);
    }

    public void testGlobalNameUrlTransformer() throws Exception
    {
        testTransformered("global-name-url-transformer", GLOBAL_RESPONSE);
    }

    public void testGlobalNameLocalTransformer() throws Exception
    {
        testTransformered("global-name-local-transformer", LOCAL_RESPONSE);
    }

    public void testLocalNameLocalTransformer() throws Exception
    {
        testTransformered("local-name-local-transformer", LOCAL_RESPONSE);
    }

    public void testLocalNameUrlTransformer() throws Exception
    {
        testTransformered("local-name-url-transformer", LOCAL_RESPONSE);
    }

}
