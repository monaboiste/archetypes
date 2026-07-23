package com.softwarearchetypes.product;

import static com.softwarearchetypes.common.Preconditions.checkArgument;

record TextProductIdentifier(String value) implements ProductIdentifier {

    TextProductIdentifier {
        checkArgument(value != null && !value.isBlank(), "TextProductIdentifier value cannot be null or blank");
    }

    @Override
    public String type() {
        return "TEXT";
    }

    @Override
    public String toString() {
        return value;
    }
}
