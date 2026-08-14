package com.softwarearchetypes.rules.discounting.config.codecs;

import com.softwarearchetypes.quantity.money.Percentage;
import com.softwarearchetypes.rules.core.config.reflection.ValueCodec;

import java.math.BigDecimal;
import java.util.Map;

public class PercentageCodec implements ValueCodec {

    @Override
    public boolean supports(Class<?> type) {
        return Percentage.class.isAssignableFrom(type);
    }

    @Override
    public Object read(String prefix, Map<String, String> props) {
        String key = prefix + ".percentage.value";
        String value = props.get(key);

        if (value == null) {
            throw new IllegalArgumentException("No percentage value at: '" + key + "'");
        }

        return Percentage.of(new BigDecimal(value));
    }

    @Override
    public void write(String prefix, Object value, Map<String, String> out) {
        out.put(prefix + ".percentage.value", ((Percentage) value).value().toString());
    }
}
