# Module 4: Rules - Summary

This module presents rules as a reusable way to express changing business decisions. The examples start with discounts and gradually generalize to scoring, configurable rule engines, event-driven architecture, and algebraic interpretation of rule trees.

## Core ideas

- Separate stable application flow from changeable rules.
- Model the essence of a rule, not the current business vocabulary. A discount may actually be a general offer-item modifier, because a rule may increase a price, replace an item, add a free item, or modify a whole offer.
- Prefer composition of small, testable predicates and functions to large conditional methods.
- Keep rule selection separate from rule execution.
- Make the rule representation independent from the way it is interpreted. The same tree can produce a Boolean result, points, fuzzy values, or an explanation.
- Put frequently changing configuration outside the deployable code when the business really needs runtime changes.
- Do not build a rule engine by default. It is justified only when the cost of frequent changes, regression risk, or tenant-specific policies is higher than the engine's cognitive and operational cost.

## 1. Conditional logic limitations and the first abstraction

### The initial design

A common first implementation selects a discount with `if` or `switch`:

```java
OfferItemModifier createDiscountModifier(ClientStatus status) {
    return switch (status) {
        case STANDARD -> new PercentageOfferItemModifier("Standard", Percentage.of(5));
        case VIP -> new PercentageOfferItemModifier("VIP", Percentage.of(15));
        case GOLD -> new PercentageOfferItemModifier("Gold", Percentage.of(25));
    };
}
```

This works while statuses are fixed and the rules rarely change. It becomes expensive when:

- a new status requires finding and updating every switch;
- users can define statuses and rules in a database;
- several independent facts influence the decision;
- the algorithm or its parameters change at runtime;
- every change requires a release.

### Strategy, but with the right abstraction

A strategy is a function or object that transforms an offer item:

```java
public interface OfferItemModifier {
    OfferItem modify(OfferItem item);
}

public abstract class NamedOfferItemModifier implements OfferItemModifier {
    private final String name;

    protected NamedOfferItemModifier(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

public final class PercentageOfferItemModifier
        extends NamedOfferItemModifier {
    private final Percentage percentage;

    public PercentageOfferItemModifier(String name, Percentage percentage) {
        super(name);
        this.percentage = percentage;
    }

    @Override
    public OfferItem modify(OfferItem item) {
        Money change = item.getFinalPrice().multiply(percentage);
        Money newPrice = item.getFinalPrice().subtract(change);
        return item.apply(new Modification(
                newPrice, getName() + " (" + percentage + "%)"));
    }
}
```

The important refactoring is to use one parameterized implementation instead of separate `VipDiscountingStrategy`, `GoldDiscountingStrategy`, and `StandardDiscountingStrategy` classes when their algorithm is identical. The name is data, not a type. The broader name `OfferItemModifier` is preferable to `Discount` because rules can also increase prices or add products.

`OfferItem` should be an immutable value object. A modifier returns a new item when something changed and returns the original item otherwise. The item can keep the final price and a list of modifications so that the offer can explain the result.

### Enum visitor and its limits

For a closed enum, a visitor can make additions visible to the compiler:

```java
interface ClientStatusVisitor<R> {
    R visitStandard();
    R visitVip();
    R visitGold();
}

enum ClientStatus {
    STANDARD {
        @Override public <R> R accept(ClientStatusVisitor<R> visitor) {
            return visitor.visitStandard();
        }
    },
    VIP {
        @Override public <R> R accept(ClientStatusVisitor<R> visitor) {
            return visitor.visitVip();
        }
    },
    GOLD {
        @Override public <R> R accept(ClientStatusVisitor<R> visitor) {
            return visitor.visitGold();
        }
    };

    public abstract <R> R accept(ClientStatusVisitor<R> visitor);
}

final class OfferItemModifierVisitor
        implements ClientStatusVisitor<OfferItemModifier> {
    public OfferItemModifier visitStandard() {
        return new PercentageOfferItemModifier("Standard", Percentage.of(5));
    }

    public OfferItemModifier visitVip() {
        return new PercentageOfferItemModifier("VIP", Percentage.of(15));
    }

    public OfferItemModifier visitGold() {
        return new PercentageOfferItemModifier("Gold", Percentage.of(25));
    }
}
```

This is useful when the set of variants is stable. It is not a solution for user-defined statuses or database-defined rules. An elegant technique can become a constraint when the model no longer matches reality.

## 2. Bottom-up design: the user as rule author

The requirements become more variable:

- reduce the price by 5% when quantity is greater than 10;
- subtract 10 when the unit price is above 100;
- apply a surcharge to unregistered customers;
- combine multiple modifications;
- protect the margin;
- show the applied changes;
- allow authorized users to define active rules and parameters without programmers.

### Three kinds of logic

Separate the model into three areas according to source-code volatility:

1. **Stable logic** - the offer-generation service and the main business process.
2. **Stable extension points** - modifier interfaces and reusable predicates, applicators, and guards.
3. **Selection logic** - factories or configuration providers that decide which extensions apply.

The first two can remain stable while selection and parameters change. This is a practical mental model, not an excuse to create abstractions prematurely.

This maps well to a DDD large-scale model:

- **Capability**: basic business potential;
- **Operations**: stable operations built on capabilities;
- **Policy**: configurable operational closures;
- **Decision Support**: analytics and intelligence that select policies.

### Configurable modifiers

A configurable modifier separates three concerns:

- `predicate`: does the item qualify?
- `applier`: what new price should be calculated?
- `guardian`: is the proposed result allowed?

```java
public final class ConfigurableItemModifier
        extends NamedOfferItemModifier {
    private final Predicate<OfferItem> predicate;
    private final Function<OfferItem, Money> applier;
    private final Predicate<OfferItem> guardian;

    public ConfigurableItemModifier(
            String name,
            Predicate<OfferItem> predicate,
            Function<OfferItem, Money> applier,
            Predicate<OfferItem> guardian) {
        super(name);
        this.predicate = predicate;
        this.applier = applier;
        this.guardian = guardian;
    }

    @Override
    public OfferItem modify(OfferItem item) {
        if (!predicate.test(item)) {
            return item;
        }

        Money applied = applier.apply(item);
        if (applied.equals(item.getFinalPrice())) {
            return item;
        }

        OfferItem candidate = item.apply(
                new Modification(applied, getName()));
        return guardian.test(candidate) ? candidate : item;
    }
}
```

Small functors are easy to test and reuse:

```java
public final class PercentageFromFinal
        implements Function<OfferItem, Money> {
    private final Percentage percentage;

    public PercentageFromFinal(Percentage percentage) {
        this.percentage = percentage;
    }

    @Override
    public Money apply(OfferItem item) {
        Money change = item.getFinalPrice().multiply(percentage);
        return item.getFinalPrice().subtract(change);
    }
}

public final class MoreExpensiveThan
        implements Predicate<OfferItem> {
    private final Money amount;

    public MoreExpensiveThan(Money amount) {
        this.amount = amount;
    }

    @Override
    public boolean test(OfferItem item) {
        return item.getBasePrice().isGreaterThanOrEqualTo(amount);
    }
}

public final class MarginGuardian implements Predicate<OfferItem> {
    private final Percentage minimumMargin;

    public MarginGuardian(Percentage minimumMargin) {
        this.minimumMargin = minimumMargin;
    }

    @Override
    public boolean test(OfferItem item) {
        Money minimum = item.getBasePrice().multiply(minimumMargin);
        return item.getFinalPrice().isGreaterThanOrEqualTo(minimum);
    }
}
```

The guardian simulates a modification before returning it. This puts a business invariant in one shared place instead of adding emergency `if` statements to many callers.

A price percentage must have an explicit meaning: it may be calculated from the base price or from the current final price after previous modifiers. That choice belongs in the configured applicator, not in an ambiguous method name.

### Composition and ordering

Modifiers can be cumulative. A collection can be adapted to the same interface with a composite or chain:

```java
public final class ChainOfferItemModifier implements OfferItemModifier {
    private final List<OfferItemModifier> modifiers = new ArrayList<>();

    public ChainOfferItemModifier add(OfferItemModifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    @Override
    public OfferItem modify(OfferItem item) {
        for (OfferItemModifier modifier : modifiers) {
            item = modifier.modify(item);
        }
        return item;
    }
}
```

The order is business-significant. The chain is similar to Chain of Responsibility, but the collection-based composite avoids changing every reusable modifier to hold a successor. A fold over a list is an equivalent functional formulation.

Predicates can be composed into a specification tree:

```java
interface LogicalPredicate<T> extends Predicate<T> {
    default LogicalPredicate<T> and(LogicalPredicate<T> other) {
        return value -> test(value) && other.test(value);
    }

    default LogicalPredicate<T> or(LogicalPredicate<T> other) {
        return value -> test(value) || other.test(value);
    }

    default LogicalPredicate<T> not() {
        return value -> !test(value);
    }
}
```

For persistence and reflection, explicit `AndPredicate`, `OrPredicate`, and `NotPredicate` nodes are preferable to anonymous lambdas because their operands can be inspected and serialized.

### Selecting modifiers from client context

Selection rules should use only data available at selection time. For example, the factory can use status, purchase history, dates, or total expenses, while the modifier can use quantity, product attributes, base price, and the price after earlier modifiers.

```java
record ClientContext(
        UUID id,
        ClientStatus status,
        Money totalExpenses,
        LocalDate firstOrder) {}

record CandidateRule(
        OfferItemModifier modifier,
        Predicate<ClientContext> appliesToClient) {}

ChainOfferItemModifier createModifiers(ClientContext client,
                                        List<CandidateRule> rules) {
    ChainOfferItemModifier result = new ChainOfferItemModifier();
    for (CandidateRule rule : rules) {
        if (rule.appliesToClient().test(client)) {
            result.add(rule.modifier());
        }
    }
    return result;
}
```

This is a two-stage process:

1. select potential modifiers using client context;
2. run them over each offer item, where each modifier checks item context.

If loading the whole client context is expensive, split it into smaller contexts or let a rule-specific context facade load only the data required by that rule. Avoid database calls from small predicates and modifiers because they make tests slow and couple the domain logic to infrastructure.

### Static and dynamic configuration

A simple system can keep the rule map in code:

```java
List<CandidateRule> rules = List.of(
    new CandidateRule(
        new ConfigurableItemModifier(
            "Three-year VIP",
            new MoreExpensiveThan(Money.pln(50)),
            new PercentageFromBase(Percentage.of(10)),
            item -> true),
        client -> client.status() == ClientStatus.VIP
            && client.firstOrder().isBefore(LocalDate.now().minusYears(3)))
);
```

A dynamic provider can construct the same objects from current inventory, customer counts, or other data. The reusable building blocks remain in code, while selection and parameter values are calculated at runtime.

For user-authored rules, store data, not executable source code. A typical relational model contains:

- a modifier table with an ID and stable type key;
- a parameter table with modifier ID, parameter name, and parameter value;
- optionally a type-key registry for renaming, versioning, and compatibility.

A GUI presents allowed node and parameter types, preventing arbitrary strings and invalid combinations. Reflection may instantiate a type from a stable key and cast it to a known interface, but should not invoke arbitrary methods named by user input. Validate types, parameters, permissions, and versions at the configuration boundary.

The persisted logical tree uses nodes such as `AND`, `OR`, and `NOT`, with business predicates at leaves. A marker interface makes these nodes discoverable:

```java
interface LogicalExpression<T> extends Predicate<T> {}

record And<T>(LogicalExpression<T> left,
              LogicalExpression<T> right)
        implements LogicalExpression<T> {
    public boolean test(T value) {
        return left.test(value) && right.test(value);
    }
}

record Not<T>(LogicalExpression<T> child)
        implements LogicalExpression<T> {
    public boolean test(T value) {
        return !child.test(value);
    }
}
```

Visualization is essential. Without a tree view or at least a console representation, configurable rule structures become difficult to understand and maintain.

## 3. Rules as a universal decision mechanism

The same pattern applies beyond discounts:

- credit capacity;
- invoice factoring risk;
- customer or product scoring;
- tax calculation;
- eligibility decisions;
- fraud or underwriting decisions.

A Boolean decision can be a better core API than a function returning a maximum value. For example, `canGrant(data, requestedAmount)` returns true or false. The maximum allowed amount can then be found with a search, preferably binary search rather than decrementing one unit at a time.

Large conditional methods and 17,000-line SQL procedures are difficult to test and dangerous to customize. Refactor them into small leaf rules and composable trees. Each leaf tests one business condition and can have focused tests. Each customer or tenant can receive a different subset, order, or tree of rules.

The discount implementation should therefore become a plugin over a reusable rule core, just like credit scoring or factoring scoring. The reusable core should contain only the generic concepts: expressions, composition, evaluation, contexts, results, and interpreters. Domain-specific predicates and algebra implementations remain plugins.

## 4. Top-down integration with system architecture

Suppose Sales, Payments, Claims, Catalog, and CRM all influence each other. The rule is:

> If many things influence many other things, something is probably missing in between.

Do not make Sales, Payments, and Claims issue commands such as `changeScore`. That spreads scoring logic across teams. Do not make the scoring module issue commands such as `changeCRMStatus` or `changeCatalogOrdering`, because it would need to know every downstream consequence.

A better design is:

1. input modules emit domain events such as `PurchaseRegistered`, `PaymentRegistered`, and `ComplaintRegistered`;
2. a scoring context consumes and normalizes those events;
3. scoring publishes `ScoreUpdated` with old and new values, or a deliberately meaningful event such as `ScoreIncreased`;
4. CRM, Catalog, and Claims independently react to the scoring event.

The scoring component owns scoring logic, while downstream components own their reactions. This creates a source of truth and avoids a distributed monolith hidden in arrows between modules.

### Anti-corruption layer and process manager

External event models are unstable. An Anti-corruption Layer maps them to stable internal scoring events:

```java
record PurchaseRegistered(String customerId, Money amount, Instant at) {}
record ComplaintRegistered(String customerId, Instant at) {}

interface ExternalEventMapper {
    List<Object> map(Object externalEvent);
}
```

If several external events represent one meaningful internal event, a Process Manager (Saga) aggregates them and decides when the internal process is complete. For example, several instalment payments may become one internal payment event.

### Projections and time windows

Rule evaluation should normally use projections rather than repeatedly scanning raw events. Examples:

- yearly purchase amount keyed by `(customerId, year)`;
- quarterly complaint count keyed by `(customerId, year, quarter)`.

A stream processor such as Kafka Streams or Flink can maintain tumbling or sliding windows and materialize state. A small system can use ordinary tables:

```sql
CREATE TABLE yearly_purchases (
    customer_id TEXT,
    year INT,
    total_amount DECIMAL,
    PRIMARY KEY (customer_id, year)
);

CREATE TABLE quarterly_complaints (
    customer_id TEXT,
    year INT,
    quarter INT,
    complaint_count INT,
    PRIMARY KEY (customer_id, year, quarter)
);
```

On `PurchaseRegistered`, insert the event and upsert the yearly projection. On `ComplaintRegistered`, insert the event and upsert the quarterly projection.

Possible calculation modes:

- **On demand**: query projections and evaluate rules when the score is requested. It is simple and fresh enough for many systems.
- **Asynchronous**: recalculate after each relevant event and publish `ScoreUpdated`.
- **Hybrid**: recalculate asynchronously and also persist the current score for fast reads.

If business changes the time window and historical recalculation matters, retain the event log. Replaying events with a new policy allows the system to calculate the historical result under the new rules.

## 5. Scoring with ASTs and algebras

A normal specification returns only `boolean`:

```java
interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
}
```

That is insufficient when a tree must return points, a fuzzy degree, a vector, or an explanation. Separate:

- the **AST**: what the rule says;
- the **algebra/interpreter**: how the rule is evaluated;
- the **context**: data used by metric comparisons.

### Expression tree

```java
record WindowContext(
        String customerId,
        Instant from,
        Instant to,
        Map<Metric, Double> metrics) {
    double metric(Metric metric) {
        return metrics.getOrDefault(metric, 0.0);
    }
}

enum Metric {
    YEARLY_PURCHASE_AMOUNT,
    QUARTERLY_COMPLAINT_COUNT,
    LAST_PURCHASE_DAYS_AGO
}

enum CmpOp {
    GT, GTE, LT, LTE, EQ;

    boolean compare(double left, double right) {
        return switch (this) {
            case GT -> left > right;
            case GTE -> left >= right;
            case LT -> left < right;
            case LTE -> left <= right;
            case EQ -> left == right;
        };
    }
}

sealed interface Expression permits And, Or, Not, MetricCmp,
        ConstScore, Sum, IfThenElse {}

record And(Expression left, Expression right) implements Expression {}
record Or(Expression left, Expression right) implements Expression {}
record Not(Expression inner) implements Expression {}
record MetricCmp(Metric metric, CmpOp op, double value)
        implements Expression {}
record ConstScore(int value) implements Expression {}
record Sum(List<Expression> children) implements Expression {}
record IfThenElse(Expression condition, Expression thenBranch,
                  Expression elseBranch) implements Expression {}
```

For example:

```java
Expression rule = new Sum(List.of(
    new IfThenElse(
        new MetricCmp(Metric.YEARLY_PURCHASE_AMOUNT, CmpOp.GT, 10_000),
        new ConstScore(50),
        new ConstScore(0)),
    new IfThenElse(
        new MetricCmp(Metric.QUARTERLY_COMPLAINT_COUNT, CmpOp.GT, 3),
        new ConstScore(-30),
        new ConstScore(0))));
```

### Visitor and algebra

The evaluator walks the same tree regardless of the result type. A visitor can perform double dispatch and delegate actual operations to an algebra:

```java
interface Algebra<R> {
    R and(R left, R right);
    R or(R left, R right);
    R not(R value);
    R metricCmp(WindowContext context, Metric metric, CmpOp op, double value);
    R constScore(int value);
    R sum(List<R> children);
    R ifThenElse(R condition, R thenValue, R elseValue);
}

interface ExpressionVisitor<R> {
    R visit(And expression);
    R visit(Or expression);
    R visit(Not expression);
    R visit(MetricCmp expression);
    R visit(ConstScore expression);
    R visit(Sum expression);
    R visit(IfThenElse expression);
}
```

A visitor implementation recursively visits children and invokes the matching algebra operation. In production Java, each expression record can implement an `accept` method, which makes the visitor exhaustive when a new node type is added.

### Point algebra

```java
record Score(int value) {
    static final Score ZERO = new Score(0);

    Score plus(Score other) {
        return new Score(value + other.value);
    }
}

final class ScoreAlgebra implements Algebra<Score> {
    public Score and(Score left, Score right) {
        return new Score(Math.min(left.value(), right.value()));
    }

    public Score or(Score left, Score right) {
        return new Score(Math.max(left.value(), right.value()));
    }

    public Score not(Score value) {
        return new Score(value.value() > 0 ? 0 : 1);
    }

    public Score metricCmp(WindowContext context, Metric metric,
                           CmpOp op, double expected) {
        return new Score(op.compare(context.metric(metric), expected) ? 1 : 0);
    }

    public Score constScore(int value) {
        return new Score(value);
    }

    public Score sum(List<Score> children) {
        return new Score(children.stream().mapToInt(Score::value).sum());
    }

    public Score ifThenElse(Score condition, Score thenValue,
                            Score elseValue) {
        return condition.value() > 0 ? thenValue : elseValue;
    }
}
```

In this simple algebra, conditions behave as `0/1`, while constants and sums represent real points. With yearly purchases of 20,000 and five complaints, the example returns `50 - 30 = 20`.

`metricCmp` is the bridge between external data (`WindowContext.metrics`) and the logical AST. Keeping this bridge in the algebra makes the AST independent of the data representation and result type.

### Other algebras

The same expression tree can be interpreted with different algebras:

- **Boolean algebra**: reproduce ordinary specifications.
- **Fuzzy algebra**: return values from 0.0 to 1.0. A soft threshold can map values below 10,000 to 0, values above 15,000 to 1, and values in between linearly. Use `AND = min`, `OR = max`, and `NOT = 1 - value`.
- **Explained algebra**: return total points plus labeled contributions.

```java
record Contribution(String label, int value) {}
record ExplainedScore(int total, List<Contribution> contributions) {}
```

An explained result might be:

```text
Total: 20
+50  High yearly turnover > 10,000
-30  Too many complaints in quarter
```

This is more useful to users and support teams than an unexplained number. The algebra can be decorated to add logging, tracing, or explanations without changing the AST traversal.

### Event-level rules

Some rules need every event in a window, not just aggregate metrics. For example, `+3` for every purchase over 1,000:

```java
record EventWindowContext(WindowContext window,
                          CustomerEvent currentEvent) {}

record EventRule(Expression filter, Expression score) {}
```

The engine iterates over events, evaluates the filter with a Boolean algebra, and evaluates the score expression with a score algebra only when the filter is true:

```java
Score evaluate(List<EventRule> rules, WindowContext window) {
    Score total = Score.ZERO;
    for (CustomerEvent event : window.events()) {
        EventWindowContext context = new EventWindowContext(window, event);
        for (EventRule rule : rules) {
            if (evaluateBoolean(rule.filter(), context)) {
                total = total.plus(evaluateScore(rule.score(), context));
            }
        }
    }
    return total;
}
```

The filter and scoring phases should use their appropriate result types. Do not force a scoring algebra to act as a Boolean filter merely because both are trees.

### Configuration and versioning

An AST can be serialized to JSON and rebuilt at runtime:

```json
{
  "type": "Sum",
  "children": [
    {
      "type": "IfThenElse",
      "cond": {
        "type": "MetricCmp",
        "metric": "YEARLY_PURCHASE_AMOUNT",
        "op": "GT",
        "value": 10000
      },
      "then": { "type": "ConstScore", "value": 50 },
      "else": { "type": "ConstScore", "value": 0 }
    }
  ]
}
```

This makes policies easier to version, audit, validate, visualize, and roll back. Use stable type keys rather than Java class names when persistence must survive refactoring.

## 6. A universal scoring model

Customer scoring is only one use case. The same configurable component could score products, suppliers, logistics companies, or employees. Its configurable parts include:

- source events and external-to-internal mapping;
- internal events and projections;
- time windows and aggregation metrics;
- scoring expressions and algebra;
- output events emitted when the score changes.

What remains reusable is the expression-tree and rule-evaluation core. However, if only a few scoring domains exist, separate projects that reuse the abstractions and algebras may be better than prematurely building a fully generic product. Reusability has a cost: configuration, documentation, migration, observability, and support.

## 7. Four perspectives on rules and scoring

### Programming

Without a rule engine, a large conditional method creates a combinatorial explosion of test cases. With composable leaves and trees, test the small business rules and a smaller number of composition cases. The cost moves from code branching to understanding the tree, so visualization and clear naming are necessary.

### Architecture

Without a distinct scoring or policy component, logic leaks into dependencies and message flows. The logic has not disappeared; it is hidden in arrows. A dedicated component gives the rules a source of truth and supports team autonomy.

### Business

A rule engine can reduce deployment time, enable tenant-specific policies, and lower regression risk. The benefit matters most when the market or policies change frequently and the cost of a wrong decision is high. A GUI is valuable only if users can safely understand the available building blocks.

### Analysis

Rule analysis is conceptually simple but often has fuzzy boundaries. Start by identifying what changes, what data is available, when decisions are made, and which result is needed: Boolean eligibility, points, degree of satisfaction, a vector, or an explanation. Then choose the smallest model that supports those requirements.

## Practical checklist

1. Is this genuinely a rule-engine problem, or is a normal function enough?
2. What is stable, and what changes independently?
3. Is the abstraction named after the domain essence rather than today's example?
4. Can the rule be split into a predicate, an applicator, and a guard?
5. Are immutable value objects used so modifiers compose safely?
6. Is modifier ordering explicit and tested?
7. Does rule selection use only the context available at selection time?
8. Are user configurations data, not stored source code?
9. Are persisted type keys stable and versioned?
10. Can the rule tree be visualized and explained?
11. Are external events isolated with an Anti-corruption Layer?
12. Are projections and time windows used instead of repeatedly scanning raw events?
13. Can policies be replayed against historical events when required?
14. Is the result type appropriate: Boolean, score, fuzzy value, vector, or explanation?
15. Is the engine smaller and safer than the problem it solves?
