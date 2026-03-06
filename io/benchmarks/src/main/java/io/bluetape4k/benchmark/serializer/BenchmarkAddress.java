package io.bluetape4k.benchmark.serializer;

import net.openhft.chronicle.wire.SelfDescribingMarshallable;

public class BenchmarkAddress extends SelfDescribingMarshallable {
    public String recipient = "";
    public String line1 = "";
    public String city = "";
    public String zipCode = "";
    public String countryCode = "";
}
