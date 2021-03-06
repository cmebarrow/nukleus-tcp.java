/**
 * Copyright 2016-2018 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.tcp.internal.streams.rfc793;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.rules.RuleChain.outerRule;
import static org.reaktivity.nukleus.tcp.internal.TcpConfiguration.MAX_CONNECTIONS_NAME;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.nukleus.tcp.internal.TcpCountersRule;
import org.reaktivity.reaktor.test.ReaktorRule;

public class ServerIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("control", "org/reaktivity/specification/nukleus/tcp/control")
            .addScriptRoot("route", "org/reaktivity/specification/nukleus/tcp/control/route")
            .addScriptRoot("client", "org/reaktivity/specification/tcp/rfc793")
            .addScriptRoot("server", "org/reaktivity/specification/nukleus/tcp/streams/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final ReaktorRule reaktor = new ReaktorRule()
        .nukleus("tcp"::equals)
        .controller("tcp"::equals)
        .directory("target/nukleus-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(2048)
        .configure(MAX_CONNECTIONS_NAME, 3)
        .clean();

    private final TcpCountersRule counters = new TcpCountersRule(reaktor);

    @Rule
    public final TestRule chain = outerRule(reaktor).around(counters).around(k3po).around(timeout);

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.and.server.sent.data.multiple.frames/server",
        "${client}/client.and.server.sent.data.multiple.frames/client"
    })
    public void shouldSendAndReceiveData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${server}/client.and.server.sent.data.with.padding/server",
            "${client}/client.and.server.sent.data.with.padding/client"
    })
    public void shouldSendAndReceiveDataWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.close/server",
        "${client}/client.close/client"
    })
    public void shouldInitiateClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.data/server",
        "${client}/client.sent.data/client"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.data/server",
        "${client}/client.sent.data/client"
    })
    @ScriptProperty("serverInitialWindow \"6\"")
    public void shouldReceiveClientSentDataWithFlowControl() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.data.multiple.frames/server",
        "${client}/client.sent.data.multiple.frames/client"
    })
    public void shouldReceiveClientSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.data.multiple.streams/server",
        "${client}/client.sent.data.multiple.streams/client"
    })
    public void shouldReceiveClientSentDataMultipleStreams() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.data.then.end/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldReceiveClientSentDataAndEnd() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 0x1f90));
            channel.write(UTF_8.encode("client data"));
            channel.shutdownOutput();

            k3po.finish();
        }
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/client.sent.end.then.received.data/server"
        // No support for "write close" in k3po tcp
    })
    public void shouldWriteDataAfterReceiveEnd() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 0x1f90));
            channel.shutdownOutput();

            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

            k3po.finish();
        }
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/concurrent.connections/server",
        "${client}/concurrent.connections/client"
    })
    public void shouldEstablishConcurrentFullDuplexConnection() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/connection.established/server",
        "${client}/connection.established/client"
    })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${control}/route/server/controller",
        "${server}/connection.established/server",
        "${client}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://0.0.0.0:8080\"")
    public void shouldEstablishConnectionToAddressAnyIPv4() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${control}/route/server/controller",
        "${server}/connection.established/server",
        "${client}/connection.established/client"
    })
    @ScriptProperty("address \"tcp://[::0]:8080\"")
    public void shouldEstablishConnectionToAddressAnyIPv6() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test(expected = IOException.class)
    @Specification({
        "${route}/server/controller",
        "${server}/connection.failed/server"
    })
    public void connectionFailed() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 0x1f90));

            ByteBuffer buf = ByteBuffer.allocate(256);
            try
            {
                channel.read(buf);
            }
            finally
            {
                k3po.finish();
            }
        }

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.close/server",
        "${client}/server.close/client"
    })
    public void shouldInitiateServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.data/server",
        "${client}/server.sent.data/client"
    })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.data/server"
    })
    public void shouldNotGetRepeatedIOExceptionsFromReaderStreamRead() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try(Socket socket = new Socket("127.0.0.1", 0x1f90))
        {
            socket.shutdownInput();
            Thread.sleep(500);
        }

        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.data.multiple.frames/server",
        "${client}/server.sent.data.multiple.frames/client"
    })
    public void shouldReceiveServerSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.data.multiple.streams/server",
        "${client}/server.sent.data.multiple.streams/client"
    })
    public void shouldReceiveServerSentDataMultipleStreams() throws Exception
    {
        k3po.finish();

        assertEquals(0, counters.routes());
        assertEquals(0, counters.overflows());
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.data.then.end/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveServerSentDataAndEnd() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 0x1f90));

            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            buf.flip();

            assertEquals("server data", UTF_8.decode(buf).toString());

            buf.rewind();
            int len = channel.read(buf);

            assertEquals(-1, len);

            k3po.finish();
        }
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${server}/server.sent.end.then.received.data/server"
        // No support for "read closed" in k3po tcp
    })
    public void shouldReceiveDataAfterSendingEnd() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_SERVER");

        try (SocketChannel channel = SocketChannel.open())
        {
            channel.connect(new InetSocketAddress("127.0.0.1", 0x1f90));

            ByteBuffer buf = ByteBuffer.allocate(256);
            int len = channel.read(buf);
            buf.flip();

            assertEquals(-1, len);

            channel.write(UTF_8.encode("client data"));

            k3po.finish();
        }
    }

    @Test
    @Specification({
        "${route}/client.and.server/controller",
        "${server}/max.connections/server"
    })
    public void shouldUnbindRebind() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("ROUTED_CLIENT");
        k3po.awaitBarrier("ROUTED_SERVER");

        SocketChannel channel1 = SocketChannel.open();
        channel1.connect(new InetSocketAddress("127.0.0.1", 8080));

        SocketChannel channel2 = SocketChannel.open();
        channel2.connect(new InetSocketAddress("127.0.0.1", 8080));

        SocketChannel channel3 = SocketChannel.open();
        channel3.connect(new InetSocketAddress("127.0.0.1", 8080));

        k3po.awaitBarrier("CONNECTION_ACCEPTED_1");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_2");
        k3po.awaitBarrier("CONNECTION_ACCEPTED_3");

        assertEquals(3, counters.connections());

        SocketChannel channel4 = SocketChannel.open();
        try
        {
            channel4.connect(new InetSocketAddress("127.0.0.1", 8080));
            fail("4th connect shouldn't succeed as max.connections = 3");
        }
        catch (IOException ioe)
        {
            // expected
        }
        assertEquals(3, counters.connections());


        channel1.close();
        channel4.close();

        k3po.awaitBarrier("CLOSED");

        // sleep so that rebind happens
        Thread.sleep(200);
        assertEquals(2, counters.connections());

        SocketChannel channel5 = SocketChannel.open();
        channel5.connect(new InetSocketAddress("127.0.0.1", 8080));
        k3po.awaitBarrier("CONNECTION_ACCEPTED_4");
        assertEquals(3, counters.connections());

        channel2.close();
        channel3.close();
        channel5.close();
        Thread.sleep(500);
        assertEquals(0, counters.connections());

        k3po.finish();
    }

}
