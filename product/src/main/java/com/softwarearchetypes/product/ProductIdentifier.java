package com.softwarearchetypes.product;

public interface ProductIdentifier {

    String type();
    String toString();

    static ProductIdentifier of(String value) {
        return new TextProductIdentifier(value);
    }
}