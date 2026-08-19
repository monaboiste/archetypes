package com.softwarearchetypes.rules.discounting.config.codecs;

import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.rules.core.config.reflection.ValueCodec;

import java.math.BigDecimal;
import java.util.Map;

// Money is a domain value object, so knowing how to store it is a plugin concern, not a core one.
public class MoneyCodec implements ValueCodec {

    @Override
    public boolean supports(Class<?> type) {
        return Money.class.isAssignableFrom(type);
    }

    @Override
    public Object read(String prefix, Map<String, String> props) {
        String amount = props.get(prefix + ".money.amount");
        String currency = props.get(prefix + ".money.currency");

        if (amount == null || currency == null) {
            throw new IllegalArgumentException("No money data at: '" + prefix +
                    "' (expected " + prefix + ".money.amount and " + prefix + ".money.currency)");
        }

        return Money.of(new BigDecimal(amount), currency);
    }

    @Override
    public void write(String prefix, Object value, Map<String, String> out) {
        Money money = (Money) value;
        out.put(prefix + ".money.amount", money.value().toString());
        out.put(prefix + ".money.currency", money.currency());
    }
}
