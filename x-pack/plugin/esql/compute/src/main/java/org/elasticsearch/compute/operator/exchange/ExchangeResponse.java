/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.operator.exchange;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.core.AbstractRefCounted;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.RefCounted;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.transport.TransportResponse;

import java.io.IOException;
import java.util.Objects;

public final class ExchangeResponse extends TransportResponse implements Releasable {
    private final RefCounted counted = AbstractRefCounted.of(this::close);
    private final Page page;
    private final boolean finished;
    /**
     * We always use the remote exchange framwork even for local exchanges, but
     * local exchanges shouldn't close the Page. Remote exchanges totally should
     * as soon as they've serialized the block. This is a clever hack Nhat mentioned
     * that can do that. But I don't like it. But it works and might unstick us.
     */
    private boolean serialized = false;

    public ExchangeResponse(Page page, boolean finished) {
        this.page = page;
        this.finished = finished;
    }

    public ExchangeResponse(StreamInput in) throws IOException {
        super(in);
        this.page = in.readOptionalWriteable(Page::new);
        this.finished = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        serialized = true;
        out.writeOptionalWriteable(page);
        out.writeBoolean(finished);
    }

    /**
     * Returns a page responded by {@link RemoteSink}. This can be null and out of order.
     */
    @Nullable
    public Page page() {
        return page;
    }

    /**
     * Returns true if the {@link RemoteSink} is already completed. In this case, the {@link ExchangeSourceHandler}
     * can stop polling pages and finish itself.
     */
    public boolean finished() {
        return finished;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeResponse response = (ExchangeResponse) o;
        return finished == response.finished && Objects.equals(page, response.page);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page, finished);
    }

    @Override
    public void incRef() {
        counted.incRef();
    }

    @Override
    public boolean tryIncRef() {
        return counted.tryIncRef();
    }

    @Override
    public boolean decRef() {
        return counted.decRef();
    }

    @Override
    public boolean hasReferences() {
        return counted.hasReferences();
    }

    @Override
    public void close() {
        if (serialized && page != null) {
            page.releaseBlocks();
        }
    }
}
