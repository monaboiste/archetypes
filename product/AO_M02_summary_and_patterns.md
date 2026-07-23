# Software Archetypes — Module 02: Product

Lesson summary and the most important product modelling patterns.

Sources: lesson transcripts and presentations `AO_M02L01`–`AO_M02L12`.

## Core thesis of the module

A product is not just what a company sells to a customer. In architectural terms, a product is anything that has
business meaning, parameters, rules, operational or financial consequences, and that affects the state of an
organisation's resources. It can be a physical good, a service, a subscription, a medical test, a tariff, an
instalment, interest, a settlement, court costs, a service package, or an internal procedure.

The product model is the shared language of the organisation. Without it, offering rules, configurations,
dependencies, availability conditions, and billing logic spread across code, spreadsheets, databases, frontend
configuration, and people's heads. Changing the offering then becomes an IT project. With a mature product model,
changing the offering is a change in the catalog, definition, relationship, or constraints.

The Product archetype organises what an organisation offers, executes, controls, and settles. Its core is the
separation of definition from instance, explicit modelling of features, constraints, relationships, applicability
conditions, and packages. This allows the system to stop being a collection of special cases and start
understanding the language of the business.

## Lesson summaries

### M02L01 — Recognising products in a system

The first lesson shows that every organisation has products, even if it does not use that word. A bank may talk
about accounts and loans, a clinic about appointments, a logistics company about shipments, and a debt collection
agency about receivables, but in every case there is something the organisation offers, executes, settles,
controls, or delivers.

A product is the central point of reference for processes. It tells systems what can be offered, to whom, when,
under what conditions, and what should happen next. It affects operations, accounting, logistics, billing, CRM,
and analytics. The absence of an explicit product model causes data inconsistencies, duplicated logic, difficult
integrations, and slower innovation.

Key takeaway: a product catalog is not just a database of offerings. It is a map of the business and a shared
language between sales, IT, accounting, operations, and analytics.

### M02L02 — Six typical levels of product chaos

The lesson describes six stages of pain that systems go through without a consistent product model:

1. Literal modelling: separate classes and tables for goods, mortgages, shipments, appointments, etc.
2. No distinction between whether a product is unique, individually tracked, batch-tracked, or quantity-tracked.
3. Features and configurations as fields in classes or columns in tables.
4. Relationships between offerings hidden in if-statements, documentation, or additional fields.
5. Applicability rules scattered across controllers, SQL, and the frontend.
6. Packages modelled by bolting extra fields onto existing entities.

Literal modelling works in small systems but scales poorly. Every new product, variant, region, channel, or
rule requires a code change. The system begins to simulate the offering instead of understanding it.

### M02L03 — Product definition and product instance

The lesson introduces the fundamental distinction: `ProductType` and `ProductInstance`.

`ProductType` is the definition, the template, the description of what a product is. Examples: iPhone 15 Pro
256 GB, 30-minute cardiology consultation, domestic next-day shipment, savings deposit.

`ProductInstance` is a concrete occurrence of the product in the world. Examples: a phone with a specific
serial number, Kowalski's appointment on 15 March at 10:00, a specific loan agreement, a specific shipment.

This separation clarifies concepts: offering versus realisation, definition versus event, promise versus what
was actually delivered. As a result, a new product can be data rather than a new class. Systems can also
integrate around a shared language instead of translating their own concepts between each other.

### M02L04 — Product identifiability and tracking

Not all products live the same way. Some are unique, others are individually tracked, tracked by batch, tracked
both ways, or treated as interchangeable. The model introduces `ProductTrackingStrategy`:

- `UNIQUE`: a single special specimen exists, e.g. a work of art or a collector's rarity.
- `INDIVIDUALLY_TRACKED`: each specimen has its own identity, e.g. a phone, a car, a loan agreement.
- `BATCH_TRACKED`: we track the batch, not each individual unit, e.g. milk, medicine, a goods delivery.
- `INDIVIDUALLY_AND_BATCH_TRACKED`: each specimen and its batch matter, e.g. a TV with a serial number and
  production batch.
- `IDENTICAL`: specimens are interchangeable and tracked through quantity, e.g. rice or energy.

Every product is also measurable. Goods are measured in units, milk in litres, rice in kilograms, consulting
in hours, hotel stays in nights. Therefore `ProductType` should know the preferred unit of measure, and
`ProductInstance` should be created in a way that satisfies the type's requirements: serial number, batch ID,
and unit cannot be arbitrary.

The lesson also emphasises the role of identifiers: `ProductIdentifier` says what the product is,
`SerialNumber` says which specimen, `BatchId` technically identifies a batch, and `ProductInstanceId` ties a
specific instance together. A batch can additionally have a business-facing `BatchName`, e.g. `LOT-2024-001`.

### M02L05 — Variants, configurations, and differentiation

A product usually does not appear in a single rigid form. Customers choose colour, size, channel, term, amount,
segment, date, or service variant. The lesson introduces `ProductFeatureType` and `ProductFeatureInstance`.

`ProductFeatureType` defines what can be configured: the feature name, value type, and constraint. Examples:
T-shirt colour, deposit term, deposit amount, appointment date, batch code.

`ProductFeatureInstance` is a concrete feature value for a specific product instance. Examples: red colour,
size M, 12-month deposit, PLN 50,000.

`ProductFeatureInstances` validates the whole configuration against `ProductType`: mandatory features must be
present and undeclared features are rejected. For example, a deposit requires `amount` and `term`, and cannot
contain an unknown `channel=phone` value.

Constraints give features their semantics. Example types:

- `AllowedValuesConstraint`: a closed list of values, e.g. sizes S/M/L.
- `NumericRangeConstraint`: integer range, e.g. deposit term 1–60 months.
- `DecimalRangeConstraint`: range for amounts, weights, and dimensions.
- `DateRangeConstraint`: date range, e.g. appointment date or expiry date.
- `RegexConstraint`: text format, e.g. batch code.
- `Unconstrained`: free-form value, e.g. a comment.

Important distinction: `Feature` denotes potential variability, `Metadata` denotes identity. A deposit
currency of EUR or tariff G11 for households is metadata, because changing it means a different product type.
Deposit term or insurance co-payment is a feature, because the customer or sales channel can change it.

### M02L06 — Connecting definition, catalog, and inventory

The lesson separates three perspectives:

- `ProductType`: the world of definitions — what a product is.
- `CatalogEntry`: the world of sales — how and when a product is offered.
- `ProductInstance` / Inventory Item: the world of fulfilment and resources — what actually exists, has been
  reserved, sold, executed, or delivered.

`ProductType` is stable and describes the product's structure, features, units, and tracking rules. `CatalogEntry`
is more dynamic: it has a trade name, marketing description, categories, availability period, campaign and
channel metadata. The same product definition can be listed in different campaigns or catalogs without changing
the product type itself.

The relationship of `CatalogEntry` to `ProductType` is most often 1:1, because it gives simplicity and clarity.
Sometimes, however, one catalog entry can point to multiple product types if from the customer's perspective they
are a single experience, e.g. "45-minute relaxation massage" that may operationally be fulfilled by several
different types of massage.

The two entry points reflect the separation: `ProductFacade` defines product types and their features, while
`ProductCatalog` publishes, withdraws, and searches commercial offers.

### M02L07 — Relationships between products

Products rarely live in isolation. They can complement, exclude, substitute, be compatible with each other, or
form an upgrade path. The lesson introduces `ProductRelationship` as an explicit entity with its own identifier,
direction, and type.

Example relationship types:

- `UPGRADABLE_TO`: the standard plan can be upgraded to premium.
- `SUBSTITUTED_BY`: the 2023 model can be substituted by the 2024 model if unavailable.
- `REPLACED_BY`: a discontinued product is replaced by a new one.
- `COMPLEMENTED_BY`: an account can be complemented with a card and insurance.
- `COMPATIBLE_WITH`: an energy meter works with a specific tariff.
- `INCOMPATIBLE_WITH`: two plans or packages cannot coexist.

Relationships are usually directional. `Standard UPGRADABLE_TO Premium` does not automatically imply the
reverse. Even seemingly symmetric relationships such as compatibility are worth modelling explicitly and
consistently.

Important architectural pattern: relationships should not be fields in `ProductType`. They should be an
independent entity with their own repository. This keeps `ProductType` lean, and large relationship graphs can
be maintained independently. Since many relationships are catalog, recommendation, or sales-oriented,
eventual consistency often suffices.

Creating relationships should pass through a factory and domain policies, e.g. blocking reverse upgrades,
preventing graph cycles, or restricting compatibility to the same product series.

### M02L08 — Product applicability conditions

Not every product is for every customer, country, channel, season, or status. The lesson introduces
`ApplicabilityContext` and `ApplicabilityConstraint`.

`ApplicabilityContext` describes the situation: country, channel, customer age, customer type, season, status,
date, branch, available equipment, etc.

`ApplicabilityConstraint` answers whether the product makes sense in that context. Rules can be composed from
simple operators such as equals, in, greater than, less than, and then combined via `and`, `or`, `not`.

Example: travel insurance may be available only for residents of Poland or Germany, adults, in a channel other
than desktop. The product or catalog entry itself can answer whether it is applicable in the given context.

Important design decision: a constraint can belong to `ProductType` if the restriction stems from the nature of
the product, or to `CatalogEntry` if it stems from a campaign, channel, catalog, or sales approach.

### M02L09 — Composite products and packages

Businesses often sell bundles rather than individual items: an account with a card and insurance, a laptop with
a bag and antivirus, a shipment with insurance and notifications. A package is not a list of products but a
composition of options and rules.

The lesson introduces `PackageType`, `ProductSet`, `SelectionRule`, and `PackageInstance`.

`ProductSet` is a group of equivalent options, e.g. one of the tariff plans, one of the insurance options, up
to two accessories. `SelectionRule` specifies how to choose from the group: exactly one, zero or one, at least
one, up to two, or a conditional rule such as "if international transport is selected, standard insurance is
not allowed". Rules can be composed with `and`, `or`, and `not`.

The most important structural pattern is Composite. `ProductType` and `PackageType` are different
implementations of the shared contract `Product`. This allows a package to contain products and other packages,
while the catalog, relationships, search, and rules can operate on them consistently.

Definitions share one creation flow: `Product.builder(...).asProductType(...)` creates a regular product, while
`Product.builder(...).asPackage()` creates a package.

At the instance level, `ProductInstance` and `PackageInstance` implement the shared `Instance` contract.
`PackageInstance` contains selected instances and reduces `SelectedInstance` to `SelectedProduct` for validation
against `PackageType` rules.

### M02L10 — Intangible and service products

The lesson shows that products can also be things that no one intuitively treats as products.

In medicine, products are tests, consultations, procedures, results, and diagnostic packages. An ECG test has
features, metadata, required equipment, and availability conditions. A cardiology consultation is an
individually tracked service product. A blood test panel is a `PackageType`, and a full cardiac pathway is a
graph of products and relationships.

In debt collection, products are instalments, interest, notices, administrative costs, bailiff advances, and
settlements. An instalment has an amount, due date, and status. Interest has an accrual rule. A settlement is a
package of instalments, interest, and notices. A cost can also be a product, even though it represents an
outflow of money rather than revenue.

The lesson also develops the idea of a product as a graph. Nodes are products and packages, and edges are
relationships: requires, contains, recommends, substitutes, upgrades. Such a graph can be analysed: detecting
cycles, planning the shortest fulfilment path, colouring the graph on resource conflicts, simulating equipment
failures or staff unavailability, and optimising execution order.

### M02L11 — Product's place in system architecture

Product is a capability — a business ability — not a process, a library, or a helper table. The product
mechanism manages what can be offered, configured, restricted, fulfilled, and settled.

At organisational scale, a product should not be a shared library. A library causes binary coupling, versioning
problems, blurred responsibility, and no own state memory. A product model requires history, versioning,
auditing, change publication, and its own integrity.

A better solution is a separate module or service with its own API, database, and team. Product is upstream for
`Ordering`, `Billing`, `Inventory`, `Fulfillment`, CRM, promotions, and analytics. Other domains use the
product but should not independently define its truth.

Product collaborates with other archetypes: `Party` provides participant and permission context, `Pricing`
determines price and conditions, `Billing` settles specific types and instances, and `Inventory` tracks the
physical or operational availability.

### M02L12 — Product in four perspectives

The final lesson synthesises the module. A product is the language in which the system understands its own
business. A shared model removes semantic chaos, because sales, billing, fulfilment, analytics, and IT all
use the same concepts.

The entire archetype does not need to be deployed in every system. The archetype is a way of thinking and a set
of capabilities. A small massage salon can use `MassageType` and `MassageInstance` if the domain is stable and
simple. A large organisation with many services, channels, and rules needs a more generic model.

Benefits differ by perspective:

- An analyst gains a language to describe rules, parameters, and relationships.
- The business gains the ability to change the offering without an IT project every time.
- A developer gains a generic, testable model instead of special cases and if-statements.
- An architect gains a clear upstream and a separation of responsibilities between domains.

## Most important patterns and examples

### 1. Product as a capability, not a screen, table, or process

**Problem:** organisations treat a product as a UI element, price list item, order item, or marketing artefact.
As a result, different systems have their own definitions of the same concept.

**Pattern:** treat Product as a separate business capability that defines what the organisation offers, executes,
settles, and controls.

**Example:** a bank should not hold deposit logic exclusively in the sales process. A deposit is a product with
a definition, features, availability conditions, relationships, and contract instances.

**Effect:** `Ordering`, `Billing`, `Inventory`, and analytics all use the same source of truth.

### 2. Type-Instance — definition versus occurrence

**Problem:** a single class represents both the offering and a concrete realisation, e.g. `Mortgage` as both a
bank product and a customer agreement.

**Pattern:** separate `ProductType` from `ProductInstance`.

**Examples:**

- `ProductType`: "30-minute cardiology consultation".
- `ProductInstance`: Kowalski's appointment on 15 March at 10:00.
- `ProductType`: "iPhone 15 Pro 256 GB".
- `ProductInstance`: the phone with a specific serial number.

**Effect:** changing the offering definition does not mutate the history of completed services or sold units.

### 3. Explicit product tracking strategy

**Problem:** the system does not know whether to track a unit, a batch, both, or only an interchangeable quantity.

**Pattern:** assign `ProductTrackingStrategy` to `ProductType` and enforce it when creating instances.

**Examples:**

- Phone: individually tracked by serial number.
- Milk: tracked by production batch.
- TV: tracked by both serial number and production batch.
- Rice: interchangeable specimens measured by quantity.
- Collector's guitar: unique.

**Effect:** the model itself enforces whether a `SerialNumber`, `BatchId`, both identifiers, or quantity is required.

### 4. Unit of measure as part of the product definition

**Problem:** a report shows "5000" but it is unclear whether that means units, kilograms, litres, or hours.

**Pattern:** `ProductType` has a preferred unit of measure, and `ProductInstance` stores the quantity
consistent with that definition.

**Examples:**

- Avocado in wholesale: kilograms.
- Avocado in retail: units.
- ERP consulting: hours.
- Internet: billing months.

**Effect:** fewer errors in reports, warehousing, billing, and conversions.

### 5. Feature model instead of fields in every class

**Problem:** every new feature means a column, a field, a migration, and a deploy.

**Pattern:** define features via `ProductFeatureType`, concrete values via `ProductFeatureInstance`, and validate
their completeness through `ProductFeatureInstances`.

**Examples:**

- Deposit: amount, term, channel, customer segment.
- Deposit instance: mandatory amount and term, with no undeclared features.
- T-shirt: colour, size, material.
- Shipment: weight, dimensions, fragile, oversize.
- Medical test: duration, specialisation, test type.

**Effect:** products can be configured and compared without multiplying classes and columns.

### 6. Constraint as a semantics guardian

**Problem:** feature values are text or numbers with no business meaning. Validations end up in controllers.

**Pattern:** every feature has a constraint that knows the data type and validity rule.

**Examples:**

- Deposit term: integer from 1 to 60.
- Deposit amount: decimal from 1,000 to 1,000,000.
- Sales channel: mobile, web, branch.
- Batch code: regex matching the company standard.

**Effect:** the domain rejects inconsistent values by itself. Validation is in the model, not scattered
across the application.

### 7. Metadata versus Feature versus Default Value

**Problem:** everything ends up in a single bag of "attributes", making it unclear what is the product's
identity and what is configuration.

**Pattern:** separate:

- `Metadata`: immutable, definitional properties.
- `Feature`: potentially variable or configurable properties.
- `Default Value`: the typical starting value of a feature, which can be changed.

**Examples:**

- Deposit currency "EUR" as metadata, if it defines a distinct product.
- Deposit term "12 months" as a default value, if the customer can choose otherwise.
- Card colour as a feature.

**Effect:** the model communicates intent more clearly. Changing metadata means a different product;
changing a feature means a different variant or instance.

### 8. Separating ProductType, CatalogEntry, and Inventory

**Problem:** the sales offering, the technical definition, and the fulfilment state are mixed in a single object.

**Pattern:** three levels:

- `ProductType`: what something is.
- `CatalogEntry`: how we sell or display it to the customer.
- `ProductInstance` / Inventory: what exists, has been reserved, executed, or delivered.

**Example:** a premium deposit has a stable product definition but can be listed as a campaign "Premium
Deposit 7.5% Online" only until the end of March.

**Effect:** marketing can change campaigns and visibility without touching the product definition.

`ProductFacade` is the entry point for definitions; `ProductCatalog` is the entry point for publishing,
withdrawing, and searching offers.

### 9. Relationships as first-class entities

**Problem:** dependencies between products are hidden in fields, if-statements, or documentation.

**Pattern:** model relationships as `ProductRelationship` with its own ID, direction, type, and repository.

**Examples:**

- Account `COMPLEMENTED_BY` card.
- Standard plan `UPGRADABLE_TO` premium.
- Energy meter `COMPATIBLE_WITH` tariff G11.
- Business Premium plan `INCOMPATIBLE_WITH` Consumer Basic.

**Effect:** the catalog stops being a list of things and becomes a network of dependencies. Relationships can
be searched, audited, analysed, and changed without adding fields to products.

### 10. Relationship creation policies

**Problem:** the relationship structure alone is not enough, because the business has rules like "this
combination makes no sense".

**Pattern:** create relationships through a factory and `ProductRelationshipDefiningPolicy`.

**Example policies:**

- Prohibit a product relating to itself.
- Prohibit a direct reverse upgrade; arbitrary cycles require traversing the relationship graph.
- Compatibility only within the same equipment series.
- Maximum number of cross-sell add-ons.

**Effect:** business rules are always enforced in a single place.

### 11. Applicability as composable availability rules

**Problem:** a product's availability depends on country, age, channel, season, or customer status, but the
rules are scattered across the system.

**Pattern:** describe the situation via `ApplicabilityContext` and the rules via `ApplicabilityConstraint`.

**Examples:**

- Medical product for adults only.
- Offer only in the mobile channel.
- Insurance only for residents of PL or DE.
- Campaign only in December and January.

**Effect:** the system can ask the product or catalog entry: "are you applicable in this context?"

### 12. Rules in ProductType or CatalogEntry

**Problem:** it is unclear whether a restriction is a product property or a sales-approach property.

**Pattern:** if the restriction stems from the nature of the product, place it in `ProductType`. If it stems
from a campaign, channel, or catalog, place it in `CatalogEntry`.

**Examples:**

- Senior policy only for people aged 60+: most likely `ProductType`.
- The same product available online only for B2C, and through an agent only for B2B: most likely `CatalogEntry`.

**Effect:** the product definition stays stable, while the commercial offering can be dynamic.

### 13. Package as composition, not a list

**Problem:** a package is implemented as fields `creditCardId`, `insurancePolicyId`, `notificationId` bolted
onto the base product.

**Pattern:** use `PackageType`, `ProductSet`, and composable `SelectionRule` objects such as `ifThen`, `and`,
`or`, and `not`.

**Example:** `Transport Premium` consists of:

- one transport type: domestic or international,
- one insurance option: standard, extended, or partner,
- up to two add-ons,
- mandatory tracking,
- optional notifications,
- a rule: if transport is international, standard insurance is not allowed.

**Effect:** the package can be changed, validated, and analysed without rebuilding the main product class.

### 14. Composite for ProductType and PackageType

**Problem:** `PackageType extends ProductType` creates dead fields, and an optional `PackageStructure` in
`ProductType` ages poorly.

**Pattern:** `ProductType` and `PackageType` implement the shared contract `Product`.

**Example:** an office package can contain a laptop, a monitor, Office 365, and antivirus, and a larger package
can contain entire smaller packages.

Both definitions start with `Product.builder(...)`, followed by `asProductType(...)` or `asPackage()`.

**Effect:** the system can treat products and packages consistently, while each type has only the data and
behaviour that makes sense for it. Catalog and relationship APIs should therefore refer to `Product`, not only
to `ProductType`.

### 15. Shared Instance contract and package realisation

**Problem:** package validation mixes the definition level with the concrete-instance level.

**Pattern:** `ProductInstance` and `PackageInstance` implement `Instance`. A package contains `SelectedInstance`
values but reduces them to `SelectedProduct` and checks conformance with `PackageType`.

**Example:** a real logistics package contains a specific shipment, a specific policy, and a specific tracking
configuration. Validation, however, checks whether their types match the package definition.

**Effect:** no duplicated logic and no mixing of abstractions.

### 16. Product as a graph

**Problem:** a linear model does not show dependencies between products, procedures, packages, and resources.

**Pattern:** treat products and packages as graph nodes and relationships as edges.

**Example applications:**

- Detecting dependency cycles.
- Planning the shortest test fulfilment path.
- Graph colouring for doctor, equipment, or room conflicts.
- Simulating equipment failure or staff unavailability.
- Analysing how to move from the basic package to the extended one.

**Effect:** the product model becomes a planning, optimisation, and process diagnostics tool.

### 17. Intangible, financial, and operational products

**Problem:** the organisation claims it "has no products" because it does not sell boxes.

**Pattern:** look for entities with rules, parameters, consequences, and a lifecycle.

**Examples:**

- Medicine: ECG, glucose, lipid profile, echocardiography, consultation, cardiac package.
- Debt collection: instalment, interest, notice, administrative cost, settlement.
- Telecom: tariff, data package, minutes.
- Energy: capacity, tariff, grid connection.

**Effect:** the product model can be applied even where a traditional sales catalog does not exist.

### 18. Product as an upstream bounded context

**Problem:** every module defines its product in its own way.

**Pattern:** Product should be upstream relative to the processes that consume it.

**Example consumers:**

- `Ordering`: knows what can be ordered and configured.
- `Billing`: knows what to settle.
- `Inventory`: knows what to track and in what units.
- `Fulfillment`: knows what to execute or deliver.
- `Pricing`: knows the product but solves the separate problem of pricing.
- `Party`: provides participant and permission context.

**Effect:** the product is a single source of truth, and processes do not override its semantics.

### 19. Service instead of a shared library

**Problem:** a shared product library looks simple but causes coupling, version conflicts, and no persistent
model memory.

**Pattern:** at larger scale, treat Product as an autonomous module or service with an API, database, history,
versioning, and an owning team.

**Example:** a change in a product availability rule should not require updating a library in fifteen
applications. It should be a definition or catalog change published by Product.

**Effect:** lower synchronisation cost and clearer responsibility.

### 20. Pragmatic dosing of the archetype

**Problem:** a generic model can be too heavy for a simple, stable business.

**Pattern:** choose archetype elements proportional to the domain's scale and variability.

**Examples:**

- A small massage salon can use `MassageType` and `MassageInstance` if the offering is fixed.
- An organisation with hundreds of services, channels, campaigns, variants, and relationships should use the
  generic Product model.

**Effect:** the archetype is a thinking tool, not a mandatory recipe.

## Minimal conceptual model

The following set of concepts is the practical core of the Product archetype:

| Concept                   | Responsibility                | Example                          |
| ------------------------- | ----------------------------- | -------------------------------- |
| `Product`                 | Shared definition contract    | product type or package type     |
| `ProductType`             | Product definition            | Savings deposit                  |
| `Instance`                | Shared realisation contract   | product or package instance      |
| `ProductInstance`         | Concrete occurrence           | Customer deposit agreement       |
| `ProductIdentifier`       | Product type identification   | GTIN, ISBN, UUID                 |
| `SerialNumber`            | Specimen identification       | VIN, IMEI, contract number       |
| `BatchId` / `BatchName`   | Batch identity and label      | UUID / LOT-2024-001              |
| `Quantity` / `Unit`       | Quantity and unit             | 100 litres, 12 months            |
| `ProductFeatureType`      | Feature definition            | deposit term                     |
| `ProductFeatureInstance`  | Feature value                 | 12 months                        |
| `ProductFeatureInstances` | Complete feature configuration | amount + term                    |
| `FeatureValueConstraint`  | Value validity rule           | range 1–60                       |
| `ProductMetadata`         | Definitional property         | currency EUR                     |
| `CatalogEntry`            | How the product is listed     | campaign "Deposit 7.5% Online"   |
| `ProductRelationship`     | Dependency between products   | `UPGRADABLE_TO`                  |
| `ApplicabilityContext`    | Usage context                 | country PL, channel mobile       |
| `ApplicabilityConstraint` | Applicability condition       | age >= 18                        |
| `PackageType`             | Package definition            | Transport Premium                |
| `ProductSet`              | Group of options in a package | insurance options to choose from |
| `SelectionRule`           | Selection rule for groups     | exactly one                      |
| `PackageInstance`         | Concrete realised package     | selected transport + policy      |
| `SelectedInstance` / `SelectedProduct` | Instance- and type-level selection | shipment + quantity      |

## Heuristics for recognising a product

Something is worth treating as a product if it:

- has availability, configuration, or fulfilment rules;
- has parameters the business wants to change;
- causes financial, operational, logistical, or legal consequences;
- is billed, audited, tracked, or reported;
- can have variants, relationships, packages, or a lifecycle;
- other processes need to know "what exactly is this";
- the organisation uses words such as offering, service, procedure, benefit, package, plan, tariff, instalment,
  fee, limit, bonus, cost, settlement.

## Most common anti-patterns

- One class per business product, e.g. `Goods`, `Mortgage`, `Parcel`, `Appointment`.
- Mixing the product definition with its instance.
- Product features as table columns or fields in specialised classes.
- Availability rules as if-statements in controllers, SQL, or the frontend.
- Relationships as fields in the base product, e.g. `creditCardNumber` in `BankAccount`.
- Packages as additional optional fields instead of a structure with selection rules.
- Identifying the price list with the product.
- Inventory creating its own product definitions instead of consuming Product.
- A shared product library as a substitute for an autonomous bounded context.

## Practical implementation order

1. Name the products, even if the company talks about services, procedures, instalments, or tariffs.
2. Separate definition from instance.
3. Add identifiers, units of measure, and a tracking strategy.
4. Extract features into `ProductFeatureType` and validate their values through `ProductFeatureInstances`.
5. Separate metadata, features, and default values.
6. Separate `ProductType` from `CatalogEntry`.
7. Model relationships as independent entities.
8. Add `ApplicabilityConstraint` where availability rules start to scatter.
9. Model packages only once bundles become a significant part of the offering.
10. At larger scale, treat Product as an upstream module or service, not a library.

## Code snippets from presentations

The following snippets are representative code fragments from the slides, lightly tidied after extraction from
PDF. They preserve the class names, method names, and model intent from the presentations.

### ProductType and ProductInstance

```java
class ProductType {

    private final ProductIdentifier id;
    private final ProductName name;
    private final ProductDescription description;

    // constructors, getters
}
```

```java
ProductType iphone = new ProductType(
    GtinProductIdentifier.of("00123456789012"),
    ProductName.of("iPhone 15 Pro 256GB Space Black"),
    ProductDescription.of("A17 Pro chip, ProMotion display, Titanium design")
);

ProductType cardioConsultation = new ProductType(
    UuidProductIdentifier.random(),
    ProductName.of("Cardiology consultation 30min"),
    ProductDescription.of("Including ECG, requires referral")
);
```

```java
class ProductInstance {

    private final ProductInstanceId id;
    private final ProductType productType;

    // e.g. contract number, tracking number
    private final SerialNumber serialNumber;

    // if we want to group by batches
    private final BatchId batchId;

    // constructors, getters
}
```

```java
ProductType relaxingMassage = new ProductType(
    UuidProductIdentifier.random(),
    ProductName.of("Relaxing massage 60 min"),
    ProductDescription.of("Relaxation session performed by a certified masseur")
);

ProductInstance massageSession = ProductInstance.from(
    ProductInstanceId.newOne(),
    relaxingMassage,
    SerialNumber.of("MASSAGE-2024-03-15-14:00-NOWAK")
);
```

### Tracking strategy and instance creation

```java
enum ProductTrackingStrategy {
    // only one specimen exists
    UNIQUE,
    // each specimen has its own serial number
    INDIVIDUALLY_TRACKED,
    // tracked by batch
    BATCH_TRACKED,
    // both ways
    INDIVIDUALLY_AND_BATCH_TRACKED,
    // specimens completely interchangeable
    IDENTICAL
}
```

```java
class ProductType {
    private final ProductIdentifier id;
    private final ProductName name;
    private final ProductDescription description;
    private final Unit preferredUnit;
    private final ProductTrackingStrategy trackingStrategy;

    // constructors, getters
}
```

```java
ProductType.unique(id, name, description);
ProductType.individuallyTracked(id, name, description, Unit.pieces());
ProductType.batchTracked(id, name, description, Unit.liters());
ProductType.individuallyAndBatchTracked(id, name, description, Unit.pieces());
ProductType.identical(id, name, description, Unit.kilograms());
```

```java
class ProductInstance {

    private final ProductInstanceId id;
    private final ProductType productType;
    private final SerialNumber serialNumber;
    private final BatchId batchId;
    private final Quantity quantity;

    private ProductInstance(
            ProductInstanceId id,
            ProductType productType,
            SerialNumber serialNumber,
            BatchId batchId,
            Quantity quantity
    ) {
        checkArgument(id != null, "ProductInstanceId must be defined");
        checkArgument(productType != null, "ProductType must be defined");

        validateTrackingRequirements(productType, serialNumber, batchId);
        validateQuantityUnit(productType, quantity);

        this.id = id;
        this.productType = productType;
        this.serialNumber = serialNumber;
        this.batchId = batchId;
        this.quantity = quantity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductInstanceId id;
        private ProductType type;
        private SerialNumber serial;
        private BatchId batch;
        private Quantity quantity;

        public Builder id(ProductInstanceId id) { this.id = id; return this; }
        public Builder type(ProductType type) { this.type = type; return this; }
        public Builder serial(SerialNumber serial) { this.serial = serial; return this; }
        public Builder batch(BatchId batch) { this.batch = batch; return this; }
        public Builder quantity(Quantity quantity) { this.quantity = quantity; return this; }

        public ProductInstance build() {
            return new ProductInstance(id, type, serial, batch, quantity);
        }
    }
}
```

### Product features and constraints

```java
class ProductFeatureType {

    private final String name;
    private final FeatureValueConstraint constraint;

    // constructors, creator methods, getters
}
```

```java
interface FeatureValueConstraint {
    String type();
    FeatureValueType valueType();
    boolean isValid(Object value);
    String description();
}
```

```java
enum FeatureValueType {

    TEXT(String.class) {
        // casting implementation
    },

    INTEGER(Integer.class) {
        // casting implementation
    },

    DECIMAL(BigDecimal.class) {
        // casting implementation
    },

    DATE(LocalDate.class) {
        // casting implementation
    },

    BOOLEAN(Boolean.class) {
        // casting implementation
    };

    private final Class<?> type;

    FeatureValueType(Class<?> type) {
        this.type = type;
    }

    abstract Object castFrom(String value);
    abstract String castTo(Object value);
}
```

```java
ProductFeatureType color =
    ProductFeatureType.withAllowedValues("color",
        "red", "black", "white", "navy");

ProductFeatureType size =
    ProductFeatureType.withAllowedValues("size",
        "S", "M", "L", "XL", "XXL");

ProductFeatureType termMonths =
    ProductFeatureType.withNumericRange("termMonths", 1, 60);

ProductFeatureType minAmount =
    ProductFeatureType.withDecimalRange("minAmount", "1000.00", "1000000.00");

ProductFeatureType appointmentDate =
    ProductFeatureType.withDateRange("appointmentDate",
        LocalDate.now().toString(),
        LocalDate.now().plusDays(90).toString());

ProductFeatureType batchCode =
    ProductFeatureType.withRegex("batchCode", "^[A-Z]{3}-\\d{4}-[A-Z]{2}$");

ProductFeatureType comment =
    ProductFeatureType.unconstrained("comment", FeatureValueType.TEXT);
```

```java
ProductFeatureInstances depositFeatures = ProductFeatureInstances.of(
    new ProductFeatureInstance(currency, "PLN"),
    new ProductFeatureInstance(amount, BigDecimal.valueOf(10000))
);

depositFeatures.validateAgainst(depositType.featureTypes());
```

```java
class ProductType {

    private final ProductIdentifier id;
    private final ProductName name;
    private final ProductDescription description;
    private final Unit preferredUnit;
    private final ProductTrackingStrategy trackingStrategy;
    private final ProductFeatureTypes featureTypes;

    // constructor, creator methods, getters
}
```

```java
class ProductFeatureTypes {

    private final Map<String, ProductFeatureTypeDefinition> features;

    static ProductFeatureTypes empty() {
        // ...
    }

    static ProductFeatureTypes of(ProductFeatureTypeDefinition... definitions) {
        // ...
    }

    Optional<ProductFeatureTypeDefinition> get(String featureName) {
        // ...
    }

    Optional<ProductFeatureType> getFeatureType(String featureName) {
        // ...
    }

    boolean has(String featureName) {
        // ...
    }

    boolean isMandatory(String featureName) {
        // ...
    }

    Set<ProductFeatureType> mandatoryFeatures() {
        // ...
    }

    Set<ProductFeatureType> optionalFeatures() {
        // ...
    }
}

class ProductFeatureTypeDefinition {
    private final ProductFeatureType featureType;
    private final boolean mandatory;

    // constructor, creator methods, getters
}
```

### Product type builder with features

```java
static class Builder {
    private final ProductIdentifier id;
    private final ProductName name;
    private final ProductDescription description;
    private final Unit preferredUnit;
    private final ProductTrackingStrategy trackingStrategy;
    private final List<ProductFeatureTypeDefinition> featureDefinitions = new ArrayList<>();

    private Builder(
            ProductIdentifier id,
            ProductName name,
            ProductDescription description,
            Unit preferredUnit,
            ProductTrackingStrategy trackingStrategy
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.preferredUnit = preferredUnit;
        this.trackingStrategy = trackingStrategy;
    }

    Builder withMandatoryFeature(ProductFeatureType featureType) {
        this.featureDefinitions.add(ProductFeatureTypeDefinition.mandatory(featureType));
        return this;
    }

    Builder withOptionalFeature(ProductFeatureType featureType) {
        this.featureDefinitions.add(ProductFeatureTypeDefinition.optional(featureType));
        return this;
    }

    ProductType build() {
        ProductFeatureTypes features = new ProductFeatureTypes(featureDefinitions);
        return new ProductType(id, name, description, preferredUnit, trackingStrategy, features);
    }
}
```

```java
ProductFeatureType amount =
    ProductFeatureType.withDecimalRange("amountPLN", "1000.00", "1000000.00");

ProductFeatureType term =
    ProductFeatureType.withNumericRange("termMonths", 1, 60);

ProductFeatureType channel =
    ProductFeatureType.withAllowedValues("channel", "mobile", "web", "branch");

ProductFeatureType customerType =
    ProductFeatureType.withAllowedValues("customerType", "new", "existing", "premium");

ProductType depositType =
    ProductType.builder(
            new UuidProductIdentifier(),
            ProductName.of("Savings Deposit"),
            ProductDescription.of("Term deposit with configurable amount and term"),
            Unit.of("agreement"),
            ProductTrackingStrategy.INDIVIDUALLY_TRACKED
    )
    .withMandatoryFeature(amount)
    .withMandatoryFeature(term)
    .withOptionalFeature(channel)
    .withOptionalFeature(customerType)
    .build();
```

### CatalogEntry and separating definition from sales

```java
ProductType premiumDeposit =
    ProductType.builder(
            new UuidProductIdentifier(),
            ProductName.of("Premium Deposit 7.5%"),
            ProductDescription.of("12-month term account at 7.5% interest"),
            Unit.of("agreement"),
            ProductTrackingStrategy.INDIVIDUALLY_TRACKED
    )
    .withMandatoryFeature(ProductFeatureType.withAllowedValues("termInMonths", "12"))
    .withMandatoryFeature(ProductFeatureType.withAllowedValues("interestRate", "7.5"))
    .withMandatoryFeature(ProductFeatureType.withDecimalRange("amount", "10000", "10000000"))
    .withOptionalFeature(ProductFeatureType.withAllowedValues("currency", "PLN", "EUR"))
    .build();
```

```java
class CatalogEntry {

    private final CatalogEntryId id;
    // trade name
    private final String displayName;
    // marketing description
    private final String description;
    // reference to the product definition
    private final ProductType productType;
    // thematic groups
    private final Set<String> categories;
    // availability period
    private final Validity validity;
    // flexible campaign attributes
    private final Map<String, String> metadata;

    // constructors, builder, getters
}
```

```java
CatalogEntry onlineDeposit =
    CatalogEntry.builder()
        .id(CatalogEntryId.of("DEPOSIT-ONLINE-Q1"))
        .displayName("Premium Deposit 7.5% Online")
        .description("Earn 7.5% per year — available in online banking only.")
        .productType(premiumDeposit)
        .categories(Set.of("Savings", "Online"))
        .validity(Validity.between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31)))
        .metadata(Map.of(
            "badge", "7.5%",
            "campaign", "Q1-2025",
            "channel", "online"
        ))
        .build();
```

```java
productFacade.handle(new DefineProductType(/* ... */));
productFacade.findBy(new FindByFeatureCriteria(/* ... */));

productCatalog.handle(new AddToOffer(/* ... */));
productCatalog.findBy(new FindAvailableAtCriteria(/* ... */));
```

### Relationships between products

```java
public enum ProductRelationshipType {
    UPGRADABLE_TO,
    SUBSTITUTED_BY,
    REPLACED_BY,
    COMPLEMENTED_BY,
    COMPATIBLE_WITH,
    INCOMPATIBLE_WITH
}
```

```java
public record ProductRelationship(
        ProductRelationshipId id,
        ProductIdentifier from,
        ProductIdentifier to,
        ProductRelationshipType type
) {}
```

```java
interface ProductRelationshipDefiningPolicy {
    boolean canDefineFor(
            ProductIdentifier from,
            ProductIdentifier to,
            ProductRelationshipType type
    );
}
```

```java
class NoCyclicUpgradePolicy implements ProductRelationshipDefiningPolicy {
    private final ProductRelationshipRepository repository;

    boolean canDefineFor(ProductIdentifier from, ProductIdentifier to, ProductRelationshipType type) {
        if (type != ProductRelationshipType.UPGRADABLE_TO) {
            return true;
        }

        return repository.findAllRelationsFrom(to, ProductRelationshipType.UPGRADABLE_TO).stream()
            .noneMatch(rel -> rel.to().equals(from));
    }
}
```

```java
class ProductRelationshipFactory {

    Result<String, ProductRelationship> defineFor(
            ProductIdentifier from,
            ProductIdentifier to,
            ProductRelationshipType type
    ) {
        if (policy.canDefineFor(from, to, type)) {
            return Result.success(ProductRelationship.of(from, to, type));
        }

        return Result.failure("POLICIES_NOT_MET");
    }
}
```

### ApplicabilityContext and ApplicabilityConstraint

```java
public class ApplicabilityContext {
    private final Map<String, String> parameters;

    // constructor, creator methods, getters
}
```

```java
ApplicabilityContext context = ApplicabilityContext.of(
    Map.of(
        "country", "PL",
        "channel", "mobile",
        "age", "17",
        "customerType", "B2C"
    )
);
```

```java
public sealed interface ApplicabilityConstraint permits
        EqualsConstraint, InConstraint, GreaterThanConstraint,
        LessThanConstraint, BetweenConstraint, AndConstraint,
        OrConstraint, NotConstraint, AlwaysTrueConstraint {

    boolean isSatisfiedBy(ApplicabilityContext context);

    static ApplicabilityConstraint alwaysTrue() {
        return new AlwaysTrueConstraint();
    }

    static ApplicabilityConstraint equals(String parameterName, String expectedValue) {
        return new EqualsConstraint(parameterName, expectedValue);
    }

    static ApplicabilityConstraint greaterThan(String parameterName, int threshold) {
        return new GreaterThanConstraint(parameterName, threshold);
    }

    static ApplicabilityConstraint lessThan(String parameterName, int threshold) {
        return new LessThanConstraint(parameterName, threshold);
    }

    static ApplicabilityConstraint in(String parameterName, String... allowedValues) {
        return new InConstraint(parameterName, Set.of(allowedValues));
    }

    static ApplicabilityConstraint and(ApplicabilityConstraint... constraints) {
        return new AndConstraint(Arrays.asList(constraints));
    }

    static ApplicabilityConstraint or(ApplicabilityConstraint... constraints) {
        return new OrConstraint(Arrays.asList(constraints));
    }

    static ApplicabilityConstraint not(ApplicabilityConstraint constraint) {
        return new NotConstraint(constraint);
    }
}
```

```java
ApplicabilityConstraint rule = and(
    or(equals("country", "PL"), equals("country", "DE")),
    greaterThan("age", 18),
    not(equals("channel", "desktop"))
);
```

```java
class ProductType {
    private final ApplicabilityConstraint applicabilityConstraint;

    ProductType(/* other fields */, ApplicabilityConstraint applicabilityConstraint) {
        this.applicabilityConstraint = applicabilityConstraint;
    }

    public boolean isApplicableFor(ApplicabilityContext context) {
        return applicabilityConstraint.isSatisfiedBy(context);
    }
}
```

```java
ProductType travelInsurance =
    ProductType.builder(
            ProductIdentifier.of("TRAVEL_INSURANCE_STANDARD"),
            ProductName.of("Travel Insurance Standard"),
            ProductDescription.of("Standard travel insurance, available only for adult residents of PL or DE"),
            Unit.pieces(),
            ProductTrackingStrategy.INDIVIDUALLY_TRACKED
    )
    .withApplicabilityConstraint(
        and(
            in("country", "PL", "DE"),
            greaterThan("age", 18)
        )
    )
    .build();

ApplicabilityContext adultInPoland = ApplicabilityContext.of(Map.of(
    "country", "PL",
    "age", "25"
));

travelInsurance.isApplicableFor(adultInPoland); // true
```

### Packages and the Composite pattern

```java
interface Product {
    ProductIdentifier id();
    ProductName name();
    ProductDescription description();
    ProductMetadata metadata();
    ApplicabilityConstraint applicabilityConstraint();

    default boolean isApplicableFor(ApplicabilityContext context) {
        return applicabilityConstraint().isSatisfiedBy(context);
    }
}

class ProductType implements Product {
    // id, name, description, metadata, applicabilityConstraint
    // + specific: unit, trackingStrategy, featureTypes
}

class PackageType implements Product {
    // id, name, description, metadata, applicabilityConstraint
    // + specific: structure, rules, validation

    public PackageValidationResult validateSelection(List<SelectedProduct> selection) {
        // ...
    }
}
```

```java
ProductType productType = Product.builder(id, name, description)
    .asProductType(Unit.pieces(), ProductTrackingStrategy.INDIVIDUALLY_TRACKED)
    .build();

PackageType packageType = Product.builder(packageId, packageName, packageDescription)
    .asPackage()
    .withSingleChoice("Insurance", standardInsurance, premiumInsurance)
    .build();
```

```java
class PackageStructure {

    // what is included
    private final Map<String, ProductSet> productSets;

    // how to connect
    private final List<SelectionRule> selectionRules;

    public PackageValidationResult validate(List<SelectedProduct> selection) {
        var errors = new ArrayList<String>();

        for (SelectionRule rule : selectionRules) {
            if (!rule.isSatisfiedBy(selection)) {
                errors.add("Rule not satisfied: " + rule);
            }
        }

        return errors.isEmpty()
                ? PackageValidationResult.success()
                : PackageValidationResult.failure(errors);
    }
}

class ProductSet {
    private final String name;
    private final Set<ProductIdentifier> products;

    // constructor, getters
}
```

```java
interface SelectionRule {
    boolean isSatisfiedBy(List<SelectedProduct> selection);

    static SelectionRule isSubsetOf(ProductSet sourceSet, int min, int max) {
        return new IsSubsetOf(sourceSet, min, max);
    }

    static SelectionRule single(ProductSet sourceSet) {
        return new IsSubsetOf(sourceSet, 1, 1);
    }

    static SelectionRule optional(ProductSet sourceSet) {
        return new IsSubsetOf(sourceSet, 0, 1);
    }

    static SelectionRule required(ProductSet sourceSet) {
        return new IsSubsetOf(sourceSet, 1, Integer.MAX_VALUE);
    }

    static SelectionRule ifThen(SelectionRule condition, SelectionRule... thenRules) {
        return new ConditionalRule(condition, Arrays.asList(thenRules));
    }

    static SelectionRule and(SelectionRule... rules) {
        return new AndRule(Arrays.asList(rules));
    }

    static SelectionRule or(SelectionRule... rules) {
        return new OrRule(Arrays.asList(rules));
    }

    static SelectionRule not(SelectionRule rule) {
        return new NotRule(rule);
    }
}

record IsSubsetOf(ProductSet sourceSet, int min, int max) implements SelectionRule {

    @Override
    public boolean isSatisfiedBy(List<SelectedProduct> selection) {
        long count = selection.stream()
            .filter(s -> sourceSet.products().contains(s.productId()))
            .count();

        return count >= min && count <= max;
    }
}
```

```java
record ConditionalRule(
        SelectionRule condition,
        List<SelectionRule> thenRules
) implements SelectionRule {

    @Override
    public boolean isSatisfiedBy(List<SelectedProduct> selection) {
        if (condition.isSatisfiedBy(selection)) {
            return thenRules.stream()
                .allMatch(r -> r.isSatisfiedBy(selection));
        }

        return true;
    }
}
```

```java
ProductSet businessAccounts = new ProductSet(
    "BusinessAccounts",
    Set.of(businessAccountBasic.id(), businessAccountPremium.id())
);

ProductSet businessCards = new ProductSet(
    "BusinessCards",
    Set.of(businessCardStandard.id(), businessCardGold.id())
);

SelectionRule businessRule = SelectionRule.ifThen(
    SelectionRule.isSubsetOf(businessAccounts, 1, Integer.MAX_VALUE),
    SelectionRule.single(businessCards)
);
```

### Shared Instance and transitioning from instances to definitions

```java
interface Instance {

    InstanceId id();

    Product product();

    Optional<SerialNumber> serialNumber();

    Optional<BatchId> batchId();

    static InstanceBuilder builder(InstanceId id) {
        return new InstanceBuilder(id);
    }
}
```

```java
record SelectedProduct(ProductIdentifier productId, int quantity) {}

record SelectedInstance(Instance instance, int quantity) {
    SelectedProduct toSelectedProduct() {
        return new SelectedProduct(instance.product().id(), quantity);
    }
}
```

```java
class ProductInstance implements Instance {
    private final InstanceId id;
    private final ProductType productType;
    private final SerialNumber serialNumber;
    private final BatchId batchId;
    private final Quantity quantity;
    private final ProductFeatureInstances features;
}

class PackageInstance implements Instance {

    private final InstanceId id;
    private final PackageType packageType;
    private final List<SelectedInstance> selection;
    private final SerialNumber serialNumber;
    private final BatchId batchId;

    private static void validateSelection(
            PackageType packageType,
            List<SelectedInstance> selection
    ) {
        List<SelectedProduct> selectedProducts = selection.stream()
            .map(SelectedInstance::toSelectedProduct)
            .toList();

        PackageValidationResult result = packageType.validateSelection(selectedProducts);

        if (!result.isValid()) {
            throw new IllegalArgumentException(
                "Invalid package selection: " + String.join(", ", result.errors())
            );
        }
    }
}
```

```java
Instance.builder(InstanceId.newOne())
    .asPackageInstance(packageType)
    .withSelection(List.of(
        new SelectedInstance(phoneInstance, 1),
        new SelectedInstance(simInstance, 1),
        new SelectedInstance(planInstance, 1)
    ))
    .withSerial(SerialNumber.of("PKG-2025-12-15-001"))
    .build();
```
