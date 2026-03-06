package io.bluetape4k.benchmark.serializer;

import net.openhft.chronicle.wire.SelfDescribingMarshallable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkLineItem extends SelfDescribingMarshallable implements Serializable {
    public String sku = "";
    public int quantity;
    public double unitPrice;
    public boolean taxable;
    public List<String> attributes = new ArrayList<>();
}
