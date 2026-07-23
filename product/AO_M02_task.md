# Product and Catalog

## Goal

Apply the knowledge from the module in practice: find products where "they don't exist"
and model them according to the learned patterns.

## Scenario: Escape Room

### Part 1: Modeling a New Domain

You run a network of escape rooms in three cities. Your offer includes:

### Rooms

- "Mad Scientist's Laboratory" (60 min, difficulty: medium, 2–5 people)
- "Alcatraz Prison" (75 min, difficulty: hard, 3–6 people)
- "Egyptian Tomb" (45 min, difficulty: easy, 2–4 people)
- "Cyberpunk 2077" (90 min, difficulty: extreme, 4–6 people, requires VR)

### Add-ons

- Actor in the room (+30% to the price)
- Photo and video package
- Catering after the game (pizza / sushi / vegetarian)
- Dedicated game master (instead of standard)

### Packages

- "Birthday" = any room + catering + photo package
- "Team Building" = 2 rooms (sequentially) + catering + dedicated GM
- "Hardcore" = Cyberpunk 2077 + actor + dedicated GM (18+ only)

### Constraints

- "Cyberpunk 2077" available only in Warsaw (that's where the VR equipment is)
- Actor available only on weekends
- "Hardcore" package for adults only
- "Alcatraz Prison" not available for people with claustrophobia (self-declaration)

### Tasks

1) Define ProductType for selected rooms (min. 2)
   - What features (ProductFeatureType) should they have?
   - What metadata?
   - What tracking strategy?
2) Define ProductType for add-ons (min. 2)
   - Is catering one product with variants, or three separate ones?
   - Justify the decision.
3) Design PackageType for the "Team Building" package
   - What ProductSets?
   - What SelectionRules?
   - What dependencies between elements?
4) Define ApplicabilityConstraints
   - For the "Cyberpunk 2077" room
   - For the "Hardcore" package
5) Propose ProductRelationships
   - What relationships between rooms make sense?
   - Are there complementary products? Mutually exclusive ones?

## Part 2: Reflection on Your Own Domain

Find "hidden products" in your organization

1) What mask-words do you hear in your company?
   - (procedure, benefit, order, fee, package, plan...)
   - Which of them are actually products?
2) Choose one "non-obvious product" from your domain
   - What do people call it?
   - What features does it have?
   - What rules/constraints does it have?
   - What is it related to?
3) Where does chaos live in your system?
   - Do you see classes like Invoice, Contract, Order with dozens of fields?
   - Do you see if-statements scattered across controllers?
   - How could a product model simplify this?
4) If you were to introduce the Product archetype in one place in your
   organization — where would it be and why?

## Part 3: Discussion Questions (for the meeting)

Prepare for a conversation about:

1) The boundary between ProductType and PackageType
   - When should a "large product with many features" become a package?
2) Metadata vs Feature
   - How do you decide what is a feature (configurable) and what is metadata (fixed)?
3) Eventual consistency in relationships
   - Do you accept that "stale" relationships may exist momentarily?
   - What are the limits of this tolerance in your domain?
4) Product graph
   - What business questions could you ask a product graph in your organization?

---

*Final tip:*
There is no single "correct" solution.
The most important question is:
"Does your model allow the business to change its offer without changing the code?"
If yes — you are on the right track.
