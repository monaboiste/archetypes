package com.softwarearchetypes.rules.discounting;


import com.softwarearchetypes.rules.core.Modifier;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;

// The domain-named seam over the generic core. Hand-written offer rules implement this, so the
// discount vocabulary survives where it is useful; composition and selection speak
// Modifier<OfferItem>, and the two interoperate because this IS-A Modifier<OfferItem>.
// The name deliberately stays broader than "Discount": a rule may also raise a price,
// replace an item or add a free one.
public interface OfferItemModifier extends Modifier<OfferItem> {
}
