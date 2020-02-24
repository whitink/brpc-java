/*
 * Copyright (c) 2018 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.brpc.client;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.baidu.brpc.client.channel.BrpcChannel;
import com.baidu.brpc.client.channel.ChannelType;
import com.baidu.brpc.protocol.Request;
import com.baidu.brpc.protocol.RpcRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.baidu.brpc.ChannelInfo;
import com.baidu.brpc.RpcMethodInfo;
import com.baidu.brpc.exceptions.RpcException;
import com.baidu.brpc.interceptor.Interceptor;
import com.baidu.brpc.protocol.Response;
import com.baidu.brpc.protocol.RpcResponse;
import com.baidu.brpc.test.BaseMockitoTest;

import io.netty.util.Timeout;

public class RpcFutureTest extends BaseMockitoTest {
    @Mock
    private Timeout timeout;
    @Mock
    private ChannelInfo channelInfo;
    @Mock
    private BrpcChannel channelGroup;
    @Mock
    private RpcCallback<String> rpcCallback;
    @Mock
    private Interceptor interceptor;
    @Captor
    private ArgumentCaptor<Throwable> throwableCaptor;

    private RpcMethodInfo methodInfo;
    @Mock
    private Request request;

    private RpcFuture rpcFuture;

    private interface EchoService {
        String echo(String message);
    }

    @Before
    public void init() throws Exception {
        Class clazz = EchoService.class;
        Method method = clazz.getMethod("echo", String.class);
        this.methodInfo = new RpcMethodInfo(method);
        reset(rpcCallback);
        request = new RpcRequest();
        request.setRpcMethodInfo(methodInfo);
        request.setCallback(rpcCallback);
        rpcFuture = RpcFuture.createRpcFuture(request);
        rpcFuture.setTimeout(timeout);
        rpcFuture.setInterceptors(Collections.singletonList(interceptor));
        rpcFuture.setChannelInfo(channelInfo);
        rpcFuture.setChannelType(ChannelType.POOLED_CONNECTION);
        rpcFuture.setInterceptors(Collections.singletonList(interceptor));
        when(channelInfo.getChannelGroup()).thenReturn(channelGroup);
    }

    @Test
    public void testAsyncHandleSuccessfulResponse() throws Exception {
        Response response = new RpcResponse();
        response.setResult("hello world");
        rpcFuture.handleResponse(response);
        verify(rpcCallback).success(eq("hello world"));
        verify(interceptor).handleResponse(any(RpcResponse.class));
        assertThat(rpcFuture.get(), is(response.getResult()));
    }

    @Test
    public void testAsyncHandleFailResponse() throws Exception {
        Response response = new RpcResponse();
        RuntimeException ex = new RuntimeException("dummy");
        response.setException(ex);
        rpcFuture.handleResponse(response);
        verify(rpcCallback).fail(ex);
        verify(interceptor).handleResponse(any(RpcResponse.class));
        try {
            rpcFuture.get();
        } catch (RpcException ex2) {
            Assert.assertTrue(ex2.getCause() == ex);
        }
    }

    @Test
    public void testAsyncHandleNullResponse() throws Exception {
        rpcFuture.handleResponse(null);
        verify(rpcCallback).fail(throwableCaptor.capture());
        Throwable t = throwableCaptor.getValue();
        assertThat(t, instanceOf(RpcException.class));
        verify(interceptor).handleResponse(null);
    }

    @Test
    public void testSyncHandleSuccessfulResponse() throws Exception {
        RpcResponse response = new RpcResponse();
        response.setResult("hello world");
        rpcFuture.handleResponse(response);
        String resp = (String) rpcFuture.get(1, TimeUnit.SECONDS);
        assertThat(resp, is("hello world"));
    }

    @Test
    public void testSyncHandleFailResponse() throws Exception {
        RpcResponse response = new RpcResponse();
        RuntimeException ex = new RuntimeException("dummy");
        response.setException(ex);
        rpcFuture.handleResponse(response);
        try {
            rpcFuture.get(1, TimeUnit.SECONDS);
        } catch (RpcException ex2) {
            Assert.assertTrue(ex2.getCause() == ex);
        }
    }

    @Test
    public void testSyncHandleTimeout() throws Exception {
        try {
            rpcFuture.get(100, TimeUnit.MILLISECONDS);
        } catch (RpcException ex2) {
            Assert.assertTrue(ex2.getCode() == RpcException.TIMEOUT_EXCEPTION);
        }
    }

}