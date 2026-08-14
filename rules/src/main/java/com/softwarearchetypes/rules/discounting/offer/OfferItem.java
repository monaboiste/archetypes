package com.softwarearchetypes.rules.discounting.offer;

import com.softwarearchetypes.quantity.Quantity;
import com.softwarearchetypes.quantity.money.Money;
import com.softwarearchetypes.rules.core.Modification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Immutable value object: a modifier returns a new item and leaves the original alone, which is what
// makes chaining rules safe. It also keeps the list of modifications, so the offer can explain
// how the final price came about.
public class OfferItem {
    private final UUID productId;
    private final Quantity quantity;
    private final Money basePrice;
    private final Money finalPrice;
    private final List<Modification<Money>> modifications;

    //package scope for unit testing
    OfferItem(UUID productId, Quantity quantity, Money basePrice, Money finalPrice, List<Modification<Money>> modifications) {
        this.productId = productId;
        this.quantity = quantity;
        this.basePrice = basePrice;
        this.finalPrice = finalPrice;
        this.modifications = modifications;
    }

    public OfferItem(UUID productId, Quantity quantity, Money basePrice) {
        this(productId, quantity, basePrice, basePrice, new ArrayList<>());
    }

    public OfferItem apply(Modification<Money> modification) {
        Money newPrice = modification.amount();
        List<Modification<Money>> newModifications = new ArrayList<>(modifications.size() + 1);
        newModifications.addAll(modifications);
        newModifications.add(modification);
        return new OfferItem(productId, quantity, basePrice, newPrice, Collections.unmodifiableList(newModifications));
    }

    public Money getBasePrice() {
        return basePrice;
    }

    public Money getFinalPrice() {
        return finalPrice;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public UUID getProductId() {
        return productId;
    }

    @Override
    public String toString() {
        return "OfferItem{" +
                "basePrice=" + basePrice +
                ", finalPrice=" + finalPrice +
                '}';
    }
}
