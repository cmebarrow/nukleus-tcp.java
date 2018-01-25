/**
 * Copyright 2016-2017 The Reaktivity Project
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
package org.reaktivity.nukleus.tcp.internal.stream;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.reaktivity.nukleus.Configuration;
import org.reaktivity.nukleus.buffer.BufferPool;
import org.reaktivity.nukleus.route.RouteManager;
import org.reaktivity.nukleus.stream.StreamFactory;
import org.reaktivity.nukleus.stream.StreamFactoryBuilder;
import org.reaktivity.nukleus.tcp.internal.poller.Poller;
import org.reaktivity.nukleus.tcp.internal.types.control.UnrouteFW;
import org.reaktivity.nukleus.tcp.internal.types.control.RouteFW;

public class ServerStreamFactoryBuilder implements StreamFactoryBuilder
{
    private final Acceptor acceptor;
    private final Configuration config;
    private final Poller poller;
    private final Long2ObjectHashMap<Correlation> correlations;

    private final UnrouteFW unrouteRO = new UnrouteFW();

    private final Long2ObjectHashMap<LongSupplier> perRouteWriteFrameCounter;
    private final Long2ObjectHashMap<LongSupplier> perRouteReadFrameCounter;
    private final Long2ObjectHashMap<LongConsumer> perRouteWriteBytesAccumulator;
    private final Long2ObjectHashMap<LongConsumer> perRouteReadBytesAccumulator;

    private LongSupplier incrementOverflow;
    private RouteManager router;
    private LongSupplier supplyStreamId;
    private Supplier<BufferPool> supplyBufferPool;
    private LongSupplier supplyCorrelationId;
    private MutableDirectBuffer writeBuffer;
    private LongFunction<IntUnaryOperator> groupBudgetClaimer;
    private LongFunction<IntUnaryOperator> groupBudgetReleaser;

    private Function<RouteFW, LongSupplier> supplyWriteFrameCounter;
    private Function<RouteFW, LongSupplier> supplyReadFrameCounter;
    private Function<RouteFW, LongConsumer> supplyWriteBytesAccumulator;
    private Function<RouteFW, LongConsumer> supplyReadBytesAccumulator;

    public ServerStreamFactoryBuilder(Configuration config, Acceptor acceptor, Poller poller)
    {
        this.config = config;
        this.acceptor = acceptor;
        this.poller = poller;
        this.correlations = new Long2ObjectHashMap<>();

        this.perRouteWriteFrameCounter = new Long2ObjectHashMap<>();
        this.perRouteReadFrameCounter = new Long2ObjectHashMap<>();
        this.perRouteWriteBytesAccumulator = new Long2ObjectHashMap<>();
        this.perRouteReadBytesAccumulator = new Long2ObjectHashMap<>();
    }

    @Override
    public StreamFactoryBuilder setBufferPoolSupplier(
        Supplier<BufferPool> supplyBufferPool)
    {
        this.supplyBufferPool = supplyBufferPool;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setCorrelationIdSupplier(
        LongSupplier supplyCorrelationId)
    {
        this.supplyCorrelationId = supplyCorrelationId;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setRouteManager(
        RouteManager router)
    {
        this.router = router;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setStreamIdSupplier(
        LongSupplier supplyStreamId)
    {
        this.supplyStreamId = supplyStreamId;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setGroupBudgetClaimer(
        LongFunction<IntUnaryOperator> groupBudgetClaimer)
    {
        this.groupBudgetClaimer = groupBudgetClaimer;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setGroupBudgetReleaser(
        LongFunction<IntUnaryOperator> groupBudgetReleaser)
    {
        this.groupBudgetReleaser = groupBudgetReleaser;
        return this;
    }

    @Override
    public ServerStreamFactoryBuilder setWriteBuffer(
        MutableDirectBuffer writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        return this;
    }

    @Override
    public StreamFactoryBuilder setCounterSupplier(
        Function<String, LongSupplier> supplyCounter)
    {
        incrementOverflow = supplyCounter.apply("overflows");
        if (supplyWriteFrameCounter == null)
        {
            this.supplyWriteFrameCounter = r ->
            {
                final long routeId = r.correlationId();
                return perRouteWriteFrameCounter.computeIfAbsent(
                        routeId,
                        t -> supplyCounter.apply(String.format("%d.frames.wrote", t)));
            };
            this.supplyReadFrameCounter = r ->
            {
                final long routeId = r.correlationId();
                return perRouteReadFrameCounter.computeIfAbsent(
                        routeId,
                        t -> supplyCounter.apply(String.format("%d.frames.read", t)));
            };
        }
        return this;
    }

    @Override
    public StreamFactoryBuilder setAccumulatorSupplier(
            Function<String, LongConsumer> supplyAccumulator)
    {
        if (supplyWriteBytesAccumulator == null)
        {
            this.supplyWriteBytesAccumulator = r ->
            {
                final long routeId = r.correlationId();
                return perRouteWriteBytesAccumulator.computeIfAbsent(
                        routeId,
                        t -> supplyAccumulator.apply(String.format("%d.bytes.wrote", t)));
            };
            this.supplyReadBytesAccumulator = r ->
            {
                final long routeId = r.correlationId();
                return perRouteReadBytesAccumulator.computeIfAbsent(
                        routeId,
                        t -> supplyAccumulator.apply(String.format("%d.bytes.read", t)));
            };
        }
        return this;
    }

    public boolean handleRoute(int msgTypeId, DirectBuffer buffer, int index, int length)
    {
        switch(msgTypeId)
        {
            case UnrouteFW.TYPE_ID:
            {
                final UnrouteFW unroute = unrouteRO.wrap(buffer, index, index + length);
                final long routeId = unroute.correlationId();
                perRouteWriteBytesAccumulator.remove(routeId);
                perRouteReadBytesAccumulator.remove(routeId);
                perRouteWriteFrameCounter.remove(routeId);
                perRouteReadFrameCounter.remove(routeId);
            }
            break;
        }
        return true;
    }

    @Override
    public StreamFactory build()
    {
        final BufferPool bufferPool = supplyBufferPool.get();

        ServerStreamFactory factory = new ServerStreamFactory(
            config,
            router,
            writeBuffer,
            bufferPool,
            incrementOverflow,
            supplyStreamId,
            supplyCorrelationId,
            correlations,
            poller,
            groupBudgetClaimer,
            groupBudgetReleaser,
            supplyReadFrameCounter,
            supplyReadBytesAccumulator,
            supplyWriteFrameCounter,
            supplyWriteBytesAccumulator);
        acceptor.setServerStreamFactory(factory);
        return factory;

    }


}
