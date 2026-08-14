package com.softwarearchetypes.rules.core.config.reflection;

import java.util.Map;

// PATTERN: plugin point (Strategy + registry). Value objects such as Money or Percentage belong to
// a domain, so the core reader and writer must not name them. A plugin registers one codec per
// value type instead, and the core only asks "does anybody support this type?".
public interface ValueCodec {

    boolean supports(Class<?> type);

    Object read(String prefix, Map<String, String> props);

    void write(String prefix, Object value, Map<String, String> out);
}
    