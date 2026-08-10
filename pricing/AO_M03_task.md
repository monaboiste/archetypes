# SwiftShip Pricing Domain Model & Specification

## 1. Business Background

SwiftShip is a courier company that prices shipments based on their attributes (e.g., weight, cargo type, delivery
method) and additional services (e.g., cash on delivery, insurance). Some rates may change over time — therefore, the
time of valuation also matters.

The total price consists of several components that together form the "total" amount:

* Base price derived from weight,
* Fuel surcharge (dependent on the period, but always applied),
* Conditional surcharges (applied only in specific cases),
* Fees based on declared value (calculated from provided monetary amounts),
* And 23% VAT applied to the net sum.

**Final Result:**
$$\text{NET} = \text{base price} + \text{surcharges} + \text{fees}$$
$$\text{TOTAL} = \text{NET} + \text{VAT}$$

---

## 2. Task Description

Design a pricing structure using the domain archetype elements introduced in the training module:

* **`calculator`** — describes the calculation method (e.g., percentage, rate per unit, tiered thresholds),
* **`validity`** — describes the period during which a given rate is effective (time versioning),
* **`applicability`** — describes the conditions under which a given line item applies (e.g., only for shipments with a
  specific attribute),
* **`component`** — represents a pricing line item; components compose into a tree structure forming the final result.

Your goal is to build a model such that pricing in test scenarios yields values accurate to the penny (grosz) and
logically breaks down into individual components.

---

## 3. Pricing Rules

### 3.1 Base Price (by Weight)

The base price is the rate per 1 kg multiplied by the shipment weight.

| Weight Range | Base Rate |
| :--- | :--- |
| 1 – 4 kg | 7.90 PLN per kg |
| 5 – 29 kg | 6.10 PLN per kg |
| 30 – 69 kg | 5.20 PLN per kg |

**Input parameters associated with the base price:**

* `weight` — shipment weight in kilograms (number, e.g., 3, 12, 45).

---

### 3.2 Fuel Surcharge (Dependent on Valuation Date)

The fuel surcharge is calculated as a percentage of the base price and applies to every shipment. The rate depends on
when the valuation is performed.

| Validity Period | Fuel Surcharge Rate | Calculation Base |
| :--- | :--- | :--- |
| January 1 – March 31, 2025 | 4.5% | Base price |
| From April 1, 2025 | 5.0% | Base price |

**Parameter associated with rate selection:**

* `timestamp` — moment of valuation (date and time), based on which the appropriate fuel surcharge rate is selected.

---

### 3.3 Conditional Surcharges (Percentage of Base Price)

These are surcharges applied only when the shipment meets specific conditions. Each surcharge is a percentage of the
base price.

| Surcharge | Rate | Calculation Base | Applicability Condition |
| :--- | :--- | :--- | :--- |
| **ADR (Hazardous Materials)** | 50% | Base price | `cargo-type` = `"hazmat"` |
| **Oversized / Heavy** | 35% | Base price | `weight` $\ge 30$ |
| **Time Window** | 25% | Base price | `delivery-type` = `"time-window"` |

**Input parameters used in conditions:**

* `cargo-type` — cargo type (string), e.g., `"standard"` or `"hazmat"` (hazardous materials),
* `delivery-type` — delivery method (string), e.g., `"standard"` or `"time-window"` (delivery within a specific time
  slot),
* `weight` — shipment weight (as above), also used as the threshold for oversized shipments.

---

### 3.4 Fees Based on Declared Value

These line items are calculated from monetary values provided by the customer. They do not depend on the base price.

| Fee | Rate | Calculation Base | Parameter Description |
| :--- | :--- | :--- | :--- |
| **COD (Cash on Delivery)** | 2% | `cod-value` | Cash on delivery amount — how much the courier collects from the recipient |
| **Insurance** | 0.15% | `insured-value` | Insured value — the amount for which the shipment is insured |

**Input parameters:**

* `cod-value` — COD amount (monetary value), e.g., 0 PLN, 800 PLN,
* `insured-value` — insurance value (monetary value), e.g., 0 PLN, 1500 PLN.

---

### 3.5 VAT

VAT is 23% of the net sum.

| Item | Rate | Calculation Base |
| :--- | :--- | :--- |
| **VAT** | 23% | Net sum |
