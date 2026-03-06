package io.bluetape4k.benchmark.serializer;

import net.openhft.chronicle.wire.SelfDescribingMarshallable;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkLineItem extends SelfDescribingMarshallable {
    public String sku = "";
    public int quantity;
    public double unitPrice;
    public boolean taxable;
    public List<String> attributes = new ArrayList<>();
}
