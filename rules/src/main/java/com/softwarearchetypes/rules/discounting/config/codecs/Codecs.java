package com.softwarearchetypes.rules.discounting.config.codecs;

import com.softwarearchetypes.rules.core.config.reflection.ValueCodec;

import java.util.List;

// The value types this plugin can persist. Supporting a new one means adding a codec here,
// not editing the core reader and writer.
public final class Codecs {

    public static final List<ValueCodec> QUANTITY = List.of(new MoneyCodec(), new PercentageCodec());

    private Codecs() {
    }
}
