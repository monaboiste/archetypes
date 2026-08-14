package com.softwarearchetypes.rules.discounting.config;

import com.softwarearchetypes.rules.core.selection.RuleConfigProvider;
import com.softwarearchetypes.rules.discounting.client.ClientContext;
import com.softwarearchetypes.rules.discounting.offer.OfferItem;

// Nothing but a binding of the core's two type parameters to this domain:
// the selection context is the client (data known before pricing), the subject is one offer item.
public interface ConfigProvider extends RuleConfigProvider<ClientContext, OfferItem> {
}
