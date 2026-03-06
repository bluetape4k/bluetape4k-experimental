package io.bluetape4k.benchmark.serializer;

import net.openhft.chronicle.wire.SelfDescribingMarshallable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkPayload extends SelfDescribingMarshallable implements Serializable {
    public long orderId;
    public String customerId = "";
    public String status = "";
    public int loyaltyTier;
    public double grandTotal;
    public long createdAtEpochMillis;
    public BenchmarkAddress shippingAddress = new BenchmarkAddress();
    public List<BenchmarkLineItem> lineItems = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
}
