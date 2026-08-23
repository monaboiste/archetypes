# Module 3: Pricing Archetype - Summary

## Overview

The Pricing archetype (more precisely: the **Valuation archetype**) addresses one of the most commonly mismodelled
concepts in software systems. A price is not a number stored in a `price` column. A price is a **function** - of time,
channel, customer segment, usage parameters, and often of negotiation. This module builds a complete, production-grade
model for computing value, from pure mathematical calculators up through versioned, semantically-rich component trees
and their integration with the broader system architecture.

The running example throughout the module is **EV charging** (electromobility), which concentrates every pricing
challenge in one domain: time-dependent rates, multi-party revenue splits, VAT, versioned tariff changes, and
retroactive billing.

## Bridge words for recognising Pricing

The following words are useful signals during domain discovery. They point to pricing rules and questions, not to a
single `price` attribute:

| Polish word / phrase | English translation | What it signals in the Pricing archetype |
| --- | --- | --- |
| **Cena** | price | A rule for calculating value, not an attribute. |
| **Stawka** | rate | A rule for calculating value, not an attribute. |
| **Taryfa** | tariff | A rule for calculating value, not an attribute. |
| **Rabat** | discount | A conditional rule that changes the result. |
| **Zniżka** | discount | A conditional rule that changes the result. |
| **Promocja** | promotion | A conditional rule that changes the result. |
| **Odsetki** | interest | A calculator that depends on time and context. |
| **Kara** | penalty | A conditional component. |
| **Opłata dodatkowa** | surcharge | A conditional component. |
| **Podatek** | tax | An always-applicable component, independent of the product. |
| **VAT** | VAT | An always-applicable component, independent of the product. |
| **Prowizja** | commission | A value dependent on another value, often a percentage calculator or a composite component with an explicit calculation base. |
| **Limit** | limit | A stepped or volume-based rule, typically a step-function calculator. |
| **Próg** | threshold | A stepped or volume-based rule, typically a step-function calculator. |
| **„Dlaczego klient X ma inaczej?”** | “Why is customer X treated differently?” | Applicability and customer segmentation. |
| **„Od kiedy to obowiązuje?”** | “Since when does this apply?” | Temporal versioning. |

These words are bridge terms between business language and model choices: calculator, component, applicability rule,
or validity period. The exact choice depends on whether the term changes the formula, eligibility, composition, or
period of validity.

---

## L01 - Price as Business Value

### Core insight

In most systems price is a function, not a scalar:

| Function | Example |
|---|---|
| `f(time)` | Massage costs 150 PLN on weekdays, 220 PLN on Sundays |
| `f(channel)` | Web price differs from branch price differs from app price |
| `f(customerSegment)` | Premium -20%, corporate different VAT, new customer first-for-half |
| `f(params)` | After 10 min add time surcharge; above 5 kg add rate X |
| `arbitrary` | "You get it for 300, but don't tell anyone" |

### Price needs memory

A price change is not a database update - it is a **fact in time**. Without versioning, history, and context-binding it
becomes impossible to:

- reconstruct the exact amount charged a month ago,
- prove correctness to a customer or regulator,
- explain to accounting where a surcharge came from.

### Price is a decomposed value

What we call "the price" is often several distinct streams:

- gross amount for the customer
- net amount, VAT
- partner commission, operator margin
- operational cost, activation cost

Systems that store only the final number force accounting departments to reconstruct these streams by hand.

---

## L02 - Hidden Pricing in Systems

### Levels of complexity

Every codebase starts with a single `BigDecimal price` field. Each business requirement pushes the model to a new level
of complexity:

1. **Static value** - one currency, rarely changes. A `BigDecimal` is fine.
2. **Multiple currencies** - first step towards the `Money` type.
3. **Time-dependent price** - if-chains grow; tables sprout `weekendPriceFlag`, `nightRateStart` columns.
4. **Parameter-dependent price** - `kWh`, minutes, kg, GB, segment: each adds another if.
5. **Multi-party revenue split** - the client pays one number, but the firm needs six.
6. **Price history** - "what did the client pay on the 14th of February?"
7. **Multiple simultaneous tariffs** - standard, VIP, promotional, mobile, partner.
8. **Consistent price across channels** - frontend, backend, mobile each compute slightly differently.

Each level that is addressed with if-chains instead of a model creates **hidden pricing** - logic scattered across
codebases, configs, BAs' notebooks, and the memory of developers who left.

---

## L03 - Price as a Function of Context

### The `Money` type

The foundation of any serious pricing model. `BigDecimal` carries no context; `Money` pairs an amount with a currency
and enforces arithmetic correctness.

```java
Money eur = Money.eur("100.00");
Money pln = Money.pln("450.00");

eur.add(pln); // throws: Cannot add PLN to EUR
```

### Range abstractions

A price is often constant within a segment of some axis (time, date, numeric quantity). Three immutable records capture
this:

```java
// Half-open interval [from, to) for time-of-day; handles midnight crossing
record TimeRange(LocalTime from, LocalTime to) implements CalculatorRange {
    @Override
    public boolean contains(Object value) {
        LocalTime time = (LocalTime) value;
        if (from.isBefore(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        } else {
            // cross-midnight: e.g. 22:00-06:00
            return !time.isBefore(from) || time.isBefore(to);
        }
    }
}

// Half-open interval [from, to) for calendar dates
record DateRange(LocalDate from, LocalDate to) implements CalculatorRange {
    @Override
    public boolean contains(Object value) {
        LocalDate date = (LocalDate) value;
        return !date.isBefore(from) && date.isBefore(to);
    }
}

// Half-open interval [min, max) for any numeric dimension
record NumericRange(BigDecimal min, BigDecimal max) implements CalculatorRange {
    @Override
    public boolean contains(Object value) {
        BigDecimal bd = (BigDecimal) value;
        return bd.compareTo(min) >= 0 && bd.compareTo(max) < 0;
    }
}
```

All three implement a common interface:

```java
interface CalculatorRange {
    boolean supports(Object value);
    boolean contains(Object value);
    CalculatorId calculatorId();
    boolean isCompatibleWith(CalculatorRange other);
    boolean overlaps(CalculatorRange other);
    String describe();
}
```

### The `Calculator` interface

The central abstraction of the entire pricing model. A calculator is a pure mathematical function that transforms
parameters into a monetary value.

```java
interface Calculator {
    Money calculate(Parameters parameters);
    CalculatorId getId();
    String name();
    CalculatorType getType();
    Interpretation interpretation();
    String describe();
    String formula();
}
```

`Parameters` is an immutable parameter bag:

```java
record Parameters(Map<String, Object> values) {
    Object get(String key)               { ... }
    Parameters with(String key, Object v){ ... }
    boolean contains(String key)         { ... }
    Set<String> keys()                   { ... }
}
```

`CalculatorType` is an enum that declares required creation fields and required calculation fields for each calculator
kind:

```java
public enum CalculatorType {
    SIMPLE_FIXED(
        "simple-fixed",
        "Fixed amount calculator - returns %s regardless",
        Set.of("amount"),
        Set.of()
    ),
    STEP_FUNCTION(
        "step-function",
        "Step function calculator - base price %s PLN, increments every %s units",
        Set.of("basePrice", "stepSize", "stepIncrement"),
        Set.of("quantity")
    ),
    LINEAR_DATE(
        "linear-date",
        "Linear date calculator - starts at %s, grows by %s per day",
        Set.of("startDate", "startPrice", "dailyIncrement"),
        Set.of("date")
    ),
    COMPOSITE(...),
    PERCENTAGE(...);
}
```

### Calculator implementations

**`SimpleFixedCalculator`** - constant function `f(x) = c`

```java
record SimpleFixedCalculator(CalculatorId id, String name, Money amount)
        implements Calculator {
    @Override
    public Money calculate(Parameters parameters) {
        return amount;
    }
}
```

Used for: express transfer fee (5 PLN), card issuance (15 PLN), SMS fee (0.20 PLN), EV session start fee (2 PLN).

---

**`StepFunctionCalculator`** - step function `f(q) = base + floor(q / stepSize) * stepIncrement`

```java
record StepFunctionCalculator(
        CalculatorId id,
        String name,
        Money basePrice,
        BigDecimal stepSize,
        BigDecimal stepIncrement
) implements Calculator {
    @Override
    public Money calculate(Parameters parameters) {
        BigDecimal quantity = parameters.getBigDecimal("quantity");
        BigDecimal steps = quantity.divide(stepSize, 0, RoundingMode.DOWN);
        BigDecimal incrementValue = steps.multiply(stepIncrement);
        return basePrice.add(basePrice.unit(incrementValue));
    }
}
```

Used for: parcel weight pricing (up to 5 kg = 15 PLN, +3 PLN per 5 kg tier), parking (2 PLN per 30 min), excess data (5
PLN/GB over package).

---

**`DiscretePointsCalculator`** - maps exact values to prices; throws for undefined inputs

```java
record DiscretePointsCalculator(
        CalculatorId id,
        String name,
        Map<BigDecimal, Money> points
) implements Calculator {
    @Override
    public Money calculate(Parameters parameters) {
        BigDecimal quantity = parameters.getBigDecimal("quantity");
        Money price = points.get(quantity);
        if (price == null)
            throw new IllegalArgumentException("Quantity not defined: " + quantity);
        return price;
    }
}
```

Used for: lesson bundles (5 lessons = 99 PLN, 10 = 179 PLN, 20 = 299 PLN), sports equipment transport (1 item = 49, 2 =
79, 3 = 109, 5 = 159 PLN).

---

**`DailyIncrementCalculator`** - discrete linear function over dates: `f(date) = startPrice + days * dailyIncrement`

```java
record DailyIncrementCalculator(
        CalculatorId id,
        String name,
        LocalDate startDate,
        Money startPrice,
        Money dailyIncrement
) implements Calculator {
    @Override
    public Money calculate(Parameters parameters) {
        LocalDate date = (LocalDate) parameters.get("date");
        long daysFromStart = DAYS.between(startDate, date);
        Money totalIncrement = dailyIncrement.multiply(BigDecimal.valueOf(daysFromStart));
        return startPrice.add(totalIncrement);
    }
}
```

Used for: online course presale window (price grows 100 PLN/day from 1999 PLN), event early-bird pricing.

---

**`ContinuousLinearTimeCalculator`** - continuous interpolation between two points in time

```java
record ContinuousLinearTimeCalculator(
        CalculatorId id,
        String name,
        LocalDateTime startTime,
        Money startPrice,
        LocalDateTime endTime,
        Money endPrice
) implements Calculator {
    @Override
    public Money calculate(Parameters parameters) {
        LocalDateTime queryTime = parameters.getTime("time");
        long totalSeconds   = Duration.between(startTime, endTime).getSeconds();
        long elapsedSeconds = Duration.between(startTime, queryTime).getSeconds();
        BigDecimal progress = BigDecimal.valueOf(elapsedSeconds)
                .divide(BigDecimal.valueOf(totalSeconds), 10, RoundingMode.HALF_UP);
        Money priceRange = endPrice.subtract(startPrice);
        return startPrice.add(priceRange.multiply(progress));
    }
}
```

Used for: second-by-second price changes in high-frequency sales windows.

### `CompositeFunctionCalculator` - piecewise functions

Combines multiple calculators via ranges. Given an input parameter value, it selects the matching range and delegates to
that range's calculator. This eliminates hand-written if-chains for time-of-day, seasonal, or volume-based segmentation.

Example - energy tariff (day 0.85 PLN/kWh, night 0.42 PLN/kWh):

```java
Map<TimeRange, Money> ENERGY_TARIFF = Map.of(
    new TimeRange("08:00", "22:00"), Money.pln("0.85"),
    new TimeRange("22:00", "08:00"), Money.pln("0.42")
);
```

Example - bank account maintenance fee based on monthly income:

```java
// Calculators: 20 PLN (low), 10 PLN (medium), 0 PLN (high)
// Ranges: [0, 1000), [1000, 4000), [4000, ∞)
facade.calculateComponent("account-fee",
    Parameters.of("monthlyIncome", BigDecimal.valueOf(2500)));
// → 10 PLN
```

The `Ranges` guard object ensures that all ranges within one composite function operate on the same axis and do not
overlap.

---

## L04 - Three Perspectives of Price Interpretation

The same mathematical function can mean different things depending on business context. This is captured through an
`Interpretation` type rather than multiplying calculator classes.

| Interpretation | Meaning | Example |
|---|---|---|
| `MARGINAL` | Cost of the n-th unit | 5th shirt costs 80 PLN |
| `UNIT` | Average cost per unit for n units | Average 80 PLN/shirt when buying 6 |
| `TOTAL` | Full cost for n units | 480 PLN for 6 shirts |

**Mathematical relationships:**

- `total(n) = sum of marginal(1..n)`
- `unit(n) = total(n) / n`
- `marginal(n) = total(n) - total(n-1)`

This means any perspective can be derived from any other. The module implements this with the **Adapter pattern**: a
`Unit-to-Total` adapter wraps a unit-price calculator and multiplies by quantity; a `Total-to-Marginal` adapter calls
total twice and subtracts. The facade's `calculateTotal`, `calculateUnitPrice`, and `calculateMarginal` methods select
and apply the correct adapter automatically, so the caller never needs to know which interpretation the underlying
calculator natively produces.

For multi-dimensional functions (price depends on both GB and hours), the concept of "unit" becomes ill-defined
(`gigabyte-hour`?), so `TOTAL` calculators dominate in complex billing systems.

---

## L05 - Price as Composition of Components

### The problem

A calculator produces a number. It does not know whether that number is VAT, operator margin, wholesale energy cost, or
a partner commission. Without semantic labelling, every decomposition of revenue happens manually in accounting
spreadsheets.

### The solution: two-layer architecture

```text
┌─────────────────────────────────────────┐
│  SEMANTIC LAYER  (Components)           │
│  "what this value means"                │
├─────────────────────────────────────────┤
│  MATH LAYER  (Calculators)              │
│  "how to compute a value"               │
└─────────────────────────────────────────┘
```

A **component** is a business-named slice of a price, backed by a calculator. The same `PercentageCalculator` can serve
as `vat-23`, `emsp-markup`, `referral-fee`, or `insurance-premium` depending on which component wraps it.

### `Component` interface

```java
public interface Component {
    ComponentId id();
    String name();
    Interpretation interpretation();

    Money calculate(Parameters params);
    Money calculate(Parameters params, Interpretation targetInterpretation);

    ComponentBreakdown calculateBreakdown(Parameters params);
    ComponentBreakdown calculateBreakdown(Parameters params, Interpretation targetInterpretation);
}
```

### `SimpleComponent` - the leaf

Holds a reference to exactly one calculator. Performs parameter mapping (translating business parameter names to
calculator parameter names) and interpretation adaptation.

```java
public class SimpleComponent implements Component {
    private final ComponentId id;
    private final String name;
    private final Calculator calculator;
    private final Map<String, String> parameterMappings;

    @Override
    public Money calculate(Parameters params, Interpretation target) {
        Parameters transformed = transformParameters(params);
        Calculator adapted = InterpretationAdapters.adapt(calculator, target);
        return adapted.calculate(transformed);
    }

    @Override
    public ComponentBreakdown calculateBreakdown(Parameters params, Interpretation target) {
        Money result = this.calculate(params, target);
        return new ComponentBreakdown(name, result);
    }
}
```

Example - CPO time component (parameter mapping: `time` → `quantity`):

```java
facade.addCalculator("per-minute-rate",
    CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.10), "interpretation", Interpretation.UNIT));

facade.createSimpleComponent(
    "cpo-time-component",
    "per-minute-rate",
    Map.of("time", "quantity")); // ← semantic mapping
```

### `CompositeComponent` - the tree node

Aggregates child components. Supports explicit dependency declarations so that one component's output can feed another
component's input (e.g., VAT base = sum of energy + CPO + EMSP).

```java
public class CompositeComponent implements Component {
    private final ComponentId id;
    private final String name;
    private final List<Component> children;
    private final Map<String, Map<String, ParameterValue>> parameterDependencies;

    @Override
    public ComponentBreakdown calculateBreakdown(Parameters params) {
        Map<String, Money> computed = new HashMap<>();
        List<ComponentBreakdown> breakdowns = new ArrayList<>();

        for (Component child : children) {
            Parameters enriched = enrichParameters(params, computed, child);
            ComponentBreakdown childBreakdown = child.calculateBreakdown(enriched);
            computed.put(child.name(), childBreakdown.total());
            breakdowns.add(childBreakdown);
        }

        Money total = breakdowns.stream()
                .map(ComponentBreakdown::total)
                .reduce(Money.zero(), Money::add);

        return ComponentBreakdown.composite(name, total, breakdowns);
    }
}
```

`ParameterValue` is a sealed interface (`ValueOf`, `SumOf`, `DifferenceOf`, `ProductOf`) expressing how to derive a
calculator input from previously computed child results.

### Worked example: EV charging tariff (12 kWh, 40 min = 26.57 PLN)

**Step 1 - register calculators:**

```java
// Wholesale energy: marginal step function (0.60/0.70/0.80 PLN per kWh)
facade.addCalculator("energy-wholesale", CalculatorType.STEP_FUNCTION,
    Parameters.of("basePrice", Money.pln(0.60), "stepSize", 5,
                  "stepIncrement", 0.10, "interpretation", Interpretation.MARGINAL));

// Grid fee: 0.15 PLN/kWh unit price
facade.addCalculator("energy-grid", CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.15), "interpretation", Interpretation.UNIT));

// CPO: session fee, per-kWh markup, per-minute markup
facade.addCalculator("cpo-session-fee", CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln("1.50"), "interpretation", Interpretation.TOTAL));
facade.addCalculator("cpo-per-kwh",    CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.25), "interpretation", Interpretation.UNIT));
facade.addCalculator("cpo-per-minute", CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.10), "interpretation", Interpretation.UNIT));

// EMSP: per-kWh and per-minute markup
facade.addCalculator("emsp-per-kwh",    CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.10), "interpretation", Interpretation.UNIT));
facade.addCalculator("emsp-per-minute", CalculatorType.SIMPLE_FIXED,
    Parameters.of("amount", Money.pln(0.05), "interpretation", Interpretation.UNIT));

// VAT: 23% of base amount
facade.addCalculator("vat-rate", CalculatorType.PERCENTAGE,
    Parameters.of("percentageRate", 23, "interpretation", Interpretation.TOTAL));
```

**Step 2 - create leaf components:**

```java
facade.createSimpleComponent("energy-wholesale-component", "energy-wholesale");
facade.createSimpleComponent("energy-grid-component",      "energy-grid");

facade.createSimpleComponent("cpo-session-component", "cpo-session-fee");
facade.createSimpleComponent("cpo-kwh-component",     "cpo-per-kwh");
facade.createSimpleComponent("cpo-time-component",    "cpo-per-minute",
    Map.of("time", "quantity")); // ← time → quantity mapping

facade.createSimpleComponent("emsp-kwh-component",  "emsp-per-kwh");
facade.createSimpleComponent("emsp-time-component", "emsp-per-minute",
    Map.of("time", "quantity"));

facade.createSimpleComponent("vat-component", "vat-rate");
```

**Step 3 - build the component tree:**

```java
facade.createCompositeComponent("energy-net",
    "energy-wholesale-component", "energy-grid-component");

facade.createCompositeComponent("cpo-markup",
    "cpo-session-component", "cpo-kwh-component", "cpo-time-component");

facade.createCompositeComponent("emsp-markup",
    "emsp-kwh-component", "emsp-time-component");

facade.createCompositeComponent("netto",
    "energy-net", "cpo-markup", "emsp-markup");

// VAT depends on netto: baseAmount = value of "netto"
facade.createCompositeComponent("total-session-cost",
    Map.of("vat-component", Map.of("baseAmount", new ValueOf("netto"))),
    "netto", "vat-component");
```

**Step 4 - calculate:**

```java
Parameters params = Parameters.of(
    "quantity", BigDecimal.valueOf(12),  // kWh
    "time",     BigDecimal.valueOf(40)   // minutes
);

ComponentBreakdown breakdown =
    facade.calculateComponentBreakdown("total-session-cost", params);
// total-session-cost: PLN 26.57
//   netto:            PLN 21.60
//     energy-net:     PLN 9.90
//       energy-wholesale-component: PLN 8.10
//       energy-grid-component:      PLN 1.80
//     cpo-markup:     PLN 8.50
//       cpo-session-component:      PLN 1.50
//       cpo-kwh-component:          PLN 3.00
//       cpo-time-component:         PLN 4.00
//     emsp-markup:    PLN 3.20
//       emsp-kwh-component:         PLN 1.20
//       emsp-time-component:        PLN 2.00
//   vat-component:    PLN 4.97
```

---

## L06 - Versioning and History

### Why not a plain update

Overwriting a price in the database destroys history. Without history it is impossible to:

- retroactively bill events from last month at last month's rates,
- prove to a customer or regulator what the price was on a given date,
- support overlapping billing periods with different tariffs.

### The `Validity` record

```java
record Validity(LocalDate from, LocalDate to) {
    boolean contains(LocalDate point) { ... }
    boolean overlaps(Validity other)  { ... }

    static Validity from(LocalDate from)          { ... } // open end
    static Validity between(LocalDate f, LocalDate t) { ... }
    static Validity always()                      { ... }
}
```

### `ComponentVersion` - immutable snapshot

Each version captures the component's configuration and the period it is valid for. The component itself
(`SimpleComponent`, `CompositeComponent`) retains a stable `ComponentId`; only its list of versions grows.

```java
// SimpleComponentVersion (immutable record)
record SimpleComponentVersion(
    VersionId id,
    Calculator calculator,
    Map<String, String> parameterMappings,
    Validity validity,
    Instant definedAt
) {}
```

Adding a version never modifies an existing one:

```java
// January: energy at 2.50 PLN/kWh
facade.createSimpleComponent("EnergyCharge", "energy-rate-jan",
    Validity.from(LocalDate.of(2024, 1, 1)));

// February promotion: energy at 2.00 PLN/kWh, no cascade needed
facade.createSimpleComponent("EnergyCharge", "energy-rate-feb",
    Validity.between(
        LocalDate.of(2024, 2, 1),
        LocalDate.of(2024, 3, 1)));

// After promotion, the January rate automatically applies again
facade.calculate("total-session-cost",
    params.with("timestamp", LocalDate.of(2024, 3, 10)));
// → uses January rate
```

`VersionUpdateStrategy` guards against conflicting version definitions (overlapping periods are rejected by default).

### Key principle

> "Luty mija. Nie robimy nic." (*February passes. We do nothing.*)

The promotionalversion simply expires. The system automatically selects the version whose validity period contains the
requested timestamp.

---

## L07 - Pricing Module Architecture

### Products vs. Pricing: structural similarity, different responsibility

Both the product catalog and the pricing model form versioned trees. This is an **isomorphism of mechanism**, not of
purpose:

| | Product catalog | Pricing |
|---|---|---|
| Answers | What are we selling? | How do we compute the value? |
| Changes | Rarely, contractually, with customer comms | Frequently, operationally, often invisible |
| Version means | Changed scope of the offering | Changed calculation algorithm or rate |
| Examples | Added parking to session | Raised parking fee from 5 to 8 PLN |

### When to separate them

Keep pricing inside the product when: low price variability, simple system, one team owns both.

Separate when: prices change much more often than the offer, different business teams own each,
versioning/segmentation/audit requirements differ.

### How products map to prices (three patterns)

1. **1:1 mapping** - each product element has a matching pricing component. Common in utilities, telecom, e-mobility.
2. **One product, many price components** - single product offer decomposed into fixed fees, variable fees, surcharges,
   discounts. Common in banking, logistics, cloud.
3. **Many product elements, one price** - subscription, SaaS, insurance: the pricing deliberately ignores the catalog
   decomposition.

---

## L08 - Business Rules Instead of Conditionals

### Three orthogonal axes of a pricing model

```text
┌──────────────┐   ┌─────────────────┐   ┌───────────────┐
│  Calculator  │   │  Applicability  │   │   Validity    │
│  HOW?        │   │  WHETHER?       │   │   WHEN?       │
│  math, tiers,│   │  segment, chan- │   │  versioning,  │
│  formulas    │   │  nel, context,  │   │  history      │
│              │   │  business rules │   │               │
└──────────────┘   └─────────────────┘   └───────────────┘
```

The calculator must not encode business eligibility conditions. Embedding "applies only to B2C" inside a function makes
the function context-aware, breaks reusability, and makes it impossible to answer "why did this component apply?".

### Applicability on `SimpleComponent`

```java
// Version of parking component: active from May, only for B2C,
// only if session > 10 minutes
facade.createSimpleComponent(
    "ParkingFee",
    "parking-fixed",
    Map.of("timeInMinutes", "quantity"),
    Validity.from(LocalDate.of(2024, 5, 1)),
    ApplicabilityRule.all(
        SegmentRule.is("B2C"),
        ThresholdRule.greaterThan("timeInMinutes", 10)
    )
);
```

For `CompositeComponent`, applicability uses OR semantics: the composite is applicable if its validity holds AND at
least one child is applicable. This allows breakdowns to vary by context without invalidating the entire price.

### The rule of thumb

If a condition changes once a year, embedding it in a calculator may be fine. If it changes per campaign, per season, or
per customer segment - model it as an applicability rule. The moment a condition appears in multiple calculators or
changes independently of the math, it belongs in applicability.

---

## L09 - Integration with Product, Ordering, and Accounting

### Pricing as an Open Host Service

Pricing does not belong to the ordering process. It provides a **capability**: given parameters, compute a value.
Ordering orchestrates this capability per line item.

```text
Ordering → calls → Pricing (per line)
                   Pricing does not know it is serving an order
```

### Billing as orchestrator

When billing is based on aggregated past events (monthly invoice, volume discounts, credit notes), a **Billing** module
sits between Ordering and Pricing:

```text
Events → Billing → aggregates, cycles → Pricing → component values
Billing → Accounting → journal entries, tax reporting
```

Key principle: **Billing never computes price itself. Billing always delegates to Pricing.**

### Accounting

Pricing knows where a value came from. Accounting knows where to record it. The VAT component, commission component, and
energy component in a breakdown map to accounts in the chart of accounts - but Pricing has no knowledge of account
codes, and Accounting has no knowledge of tariff algorithms.

### Pricing is the Valuation archetype

The name "pricing" implies e-commerce. The correct mental model is **value computation**:

- Interest in a mortgage
- Loyalty points earned per transaction
- Free gigabytes awarded for paying invoices on time
- Customer risk scoring
- Telecom roaming surcharge calculation

Wherever rules compute a value that depends on context, time, and parameters - the Pricing archetype applies.

---

## L10 & L11 - Pricing as Organizational Capability

### Not a shared library

A shared library of calculators creates:

- **binary coupling** - every rule change forces a library release and a full redeployment of all consumers,
- **no memory** - the library is stateless; it cannot answer "why was the result X on date Y",
- **ownership vacuum** - no one team owns pricing; it drifts toward chaos.

### Autonomous module

Pricing should be a standalone module with its own model, its own persistence, and a clear API - just like product
catalog and accounting. As an upstream provider it supplies calculation capabilities to: ordering, billing, quoting,
loyalty, analytics, risk, compliance.

### Four perspectives

| Perspective | Without the archetype | With the archetype |
|---|---|---|
| **Analytical** | Rules hidden in code, scattered, impossible to generalise | Unified value model; rules are data/config; fully versioned and queryable |
| **Developer** | Flags, if-chains, BigDecimals, copy-pasted logic, fragile tests | Calculators and components are separate testable units; process logic reduces to orchestration |
| **Architectural** | Logic scattered across domains; every change cascades | One clear capability as upstream; changes are local and predictable |
| **Business** | Every rule change is an IT project with deploy and risk | New offers, discounts, and rules are configuration; decisions are reversible without engineering effort |

### When to apply (and when not to)

The full archetype is not always necessary. A simple `price` field is the right choice when: the price is static,
changes rarely, no breakdown is needed, no promotions, no segmentation, and the system is young.

The archetype becomes necessary as soon as: rules multiply, time enters the picture, business changes arrive faster than
code, or audit/transparency requirements appear. The goal is not to implement the whole diagram, but to understand which
layer you need today and how to grow into the next one.

---

## Pattern Reference

| Pattern | Role |
|---|---|
| `Money` | Value object: amount + currency + arithmetic rules |
| `TimeRange` / `DateRange` / `NumericRange` | Half-open interval records; axis segmentation |
| `CalculatorRange` | Common interface for all range types |
| `Calculator` | Core abstraction: pure math function over `Parameters` |
| `SimpleFixedCalculator` | Constant function |
| `StepFunctionCalculator` | Stepped function with configurable step size and increment |
| `DiscretePointsCalculator` | Lookup table for exact values |
| `DailyIncrementCalculator` | Linear function over dates |
| `ContinuousLinearTimeCalculator` | Continuous interpolation over time |
| `CompositeFunctionCalculator` | Piecewise function; selects calculator by range |
| `Interpretation` | Separates math from meaning: TOTAL / UNIT / MARGINAL |
| Adapter (Unit↔Total↔Marginal) | Cross-interpretation conversion without code duplication |
| `Component` | Semantic wrapper: business name + calculator + parameter mapping |
| `SimpleComponent` | Leaf node of the price tree |
| `CompositeComponent` | Tree node; aggregates children; declares inter-child dependencies |
| `ComponentBreakdown` | Recursive result structure: full price decomposition |
| `Validity` | Immutable half-open date interval for version periods |
| `ComponentVersion` | Immutable snapshot of a component's configuration for one period |
| `VersionUpdateStrategy` | Guards against conflicting version definitions |
| `ApplicabilityRule` | Business condition (segment, threshold, channel) separate from math and validity |
