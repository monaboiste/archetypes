# Graph Archetypes in Software Architecture

The module is about recognizing graphs in business systems, not implementing graph algorithms from scratch.

## Bridge Words

Bridge words connect domain language to graph concepts. Hearing them in requirements is a signal to stop thinking only
in terms of sequential procedures and inspect the underlying relationships.

| Domain language                                                                                    | Likely graph meaning                         | Questions it suggests                                                                       |
|----------------------------------------------------------------------------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------------|
| depends on, dependency, relation, connection, cooperation network                                  | nodes and edges                              | What is connected to what? Does the relationship itself carry business meaning?             |
| cycle, loop, feedback                                                                              | a closed path or cycle                       | Which operations can only succeed together? Is the cycle useful or harmful?                 |
| waits for, blocks, releases, gives back, synchronizes                                              | directed dependency or resource relation     | Who blocks whom? Can a closed dependency be resolved atomically?                            |
| flow, netting, balancing                                                                           | flow graph, capacities, weights              | Can transfers be balanced? Where are the bottlenecks?                                       |
| reachable, path, route, customer journey, shortest way                                             | reachability and path finding                | Can the target state be reached? Through which paths? What is the cheapest or fastest path? |
| order, before, after, until, only when                                                             | directed acyclic graph and topological order | What must happen first? Is the dependency graph cyclic?                                     |
| in parallel, can coexist, conflicts with, mutually exclusive                                       | conflict graph and graph coloring            | What can run together? How many independent resources or environments are required?         |
| affects, spreads to, propagation, range, interference, shared supply                               | influence graph and connected components     | How far does a change propagate? Which elements must be coordinated as one zone?            |
| possible, allowed, eligible, prohibited                                                            | topology versus policy                       | What exists structurally, and what is permitted now? Should two graphs be intersected?      |
| any of these contexts, full potential, all known alternatives                                      | graph union                                  | What becomes visible after combining perspectives?                                          |
| everything flows through X, X blocks everything, X coordinates everyone, nothing happens without X | articulation point or bottleneck             | Does removing X break the system or make independent execution possible?                    |

A compact recognition rule from the module:

> If the result depends more on the arrangement of relationships than on a fixed list of steps, the system contains a
> graph.

Two additional heuristics:

1. Inspect relationships as first-class domain concepts, especially self-referencing one-to-many and many-to-many
   relations.
2. Draw the problem. A diagram often reveals cycles, paths, components, central nodes, and repeated topology that many
   user stories hide.

## Module Thesis

- Enterprise code often pretends that business behavior is linear while actually implementing a network through nested
  conditions, loops, recursion, retries, and shared flags.
- A graph archetype is a recurring way of understanding such a network across domains. The term is used by the course as
  a modeling aid, not as a formal catalog from graph-theory literature.
- The graph is valuable before any technology choice. It may be a short-lived in-memory model built from relational
  entities. A graph database is not required.
- Mature graph libraries already provide cycle detection, path finding, connected components, topological sorting,
  coloring, articulation points, unions, and related operations. The architect's main job is to translate domain meaning
  into nodes, edges, directions, weights, and predicates.
- The payoff is not only performance. An explicit graph separates topology from policy, localizes change, improves
  explainability, enables visualization and simulation, and makes new business questions affordable.
- Do not force the pattern. Three stable, unrelated cases may be clearer as ordinary conditions. A graph becomes useful
  when relationships form a repeated and growing structure.

## Lesson 1 - When a Linear Process Turns Out to Be a Dependency Network

The opening lesson reframes graphs as a modeling tool rather than merely an algorithms topic. Nodes, edges, weights,
cycles, directed and undirected graphs, and adjacency representations are assumed knowledge.

Typical enterprise layering such as Controller-Service-Repository-Entity may organize code while still failing to model
the business's actual connections. The system remains manageable until a new step, rule, or relation appears. Then a
supposedly linear process reveals a dense, accidental dependency network.

The module deliberately avoids obvious graph domains such as power grids, social networks, routing, large recommendation
systems, and biological networks of genes, proteins, and metabolic reactions. Its focus is less obvious process-heavy
enterprise systems where teams often reproduce graph behavior with services and conditionals.

Reasons hidden graphs remain unnamed include:

- a new or unstable domain;
- relationships that exist but are not visible in the model;
- lack of graph experience or fear of unfamiliar designs;
- delivery pressure that encourages another nearly identical user story rather than recognizing the repeated structure.

The central architectural question is:

> What if this is not a list of steps, but a graph?

## Lesson 2 - Cycles as a Typical Domain Pattern

### Reservation exchange problem

A user owns one reservation slot and may request another. A free target can be assigned immediately. A request for an
occupied target enters a waiting set. Several individually impossible requests may become possible together when they
form a closed exchange.

The procedural approach simulates requests, copies assignments, tracks users who lost a slot, tries further requests,
rolls back failed combinations, and repeats. It suffers from path explosion, repeated work, order-dependent outcomes,
poor readability, and policy checks spread through the traversal.

The graph translation is direct:

- node: reservation slot, or owner in the policy-aware variant;
- edge: a request from the current slot to the desired slot;
- cycle: a group whose owners can rotate slots atomically.

The stable procedure becomes:

1. Build a graph from pending requests.
2. Detect a cycle.
3. Rotate owners in one atomic operation.
4. Remove fulfilled requests and repeat while cycles exist.

### Separating desire from permission

New rules such as company boundaries, protected slots, or exchange limits should not be inserted into the cycle
algorithm. Build two graphs instead:

- request graph: what users want;
- eligibility graph: what the current rules allow.

Run cycle detection on their intersection. The topology algorithm stays stable while business policy changes locally in
graph construction.

The same archetype appears in bank-transfer netting, factory resource rotation, HR project or role swaps, mentoring-hour
exchanges, and logistics equipment rotation.

### Java examples

The presentation first shows the procedural data model and simulation outline:

```java
class Slot {
    String id;
    User owner;
}

class ChangeRequest {
    User requester;
    Slot current;
    Slot want;
}

// For every ChangeRequest startRequest:
// 1. Copy all slot assignments.
// 2. Start group with startRequest.
// 3. Simulate current -> want and track the displaced owner as deficient.
// 4. While deficient owners remain, find and simulate their requests.
// 5. Return the group if every participant ends with a slot.
// 6. Otherwise discard the simulation and try another start request.
// 7. If no start closes the group, no exchange can be made.
```

The later brute-force variant inserts policy checks into several stages of the same simulation:

```java
// 3. Before simulating current -> wantSlot, check local policies such as
//    cross-company restrictions and exchange limits.
//    Every new requirement adds another condition here.
//    Any displaced owner is added to the deficits list.
//    A failed attempt now also requires policy-aware rollback.

// 4. While deficits exist:
//      - Take the user who lost a slot.
//      - Find that user's request.
//      - Recheck pair limits and cross-company policy.
//      - Optionally verify aggregate limits for the whole candidate group.
//      - Simulate the request and add any newly displaced owner.

// 5. Return the group if everybody has a slot.
// 6. Otherwise discard it and try another start.
// 7. If no start works, no exchange is possible.
//    More filters cause more rejected paths and make the result increasingly
//    sensitive to candidate order.
```

Graph construction and first-cycle selection:

```java
class BatchReservationUseCase {

    BatchReservationResult execute(List<ReservationChangeRequest> requests) {
        Graph<SlotId, ReservationChangeRequest> graph = buildGraph(requests);
        Set<ReservationChangeRequest> dependentRequests =
                findDependentRequests(graph, requests);

        if (!dependentRequests.isEmpty()) {
            // Exchange owners atomically.
            return BatchReservationResult.success(dependentRequests);
        }
        return BatchReservationResult.none();
    }

    private Graph<SlotId, ReservationChangeRequest> buildGraph(
            List<ReservationChangeRequest> requests) {
        Graph<SlotId, ReservationChangeRequest> graph = new Graph<>();
        for (ReservationChangeRequest request : requests) {
            Node<SlotId> fromNode = new Node<>(request.fromSlot());
            Node<SlotId> toNode = new Node<>(request.toSlot());
            Edge<SlotId, ReservationChangeRequest> edge =
                    new Edge<>(fromNode, toNode, request);
            graph.addEdge(edge);
        }
        return graph;
    }

    private Set<ReservationChangeRequest> findDependentRequests(
            Graph<SlotId, ReservationChangeRequest> graph,
            List<ReservationChangeRequest> requests) {
        return graph.findFirstCycle()
                .map(path -> path.edges().stream()
                        .map(Edge::property)
                        .collect(toSet()))
                .orElseGet(Collections::emptySet);
    }
}
```

A two-user exchange test:

```java
@Test
void executesDependentReservationChangesWhenSlotsRemainValid() {
    SlotId slotA = SlotId.of("SlotA");
    SlotId slotB = SlotId.of("SlotB");
    OwnerId userX = OwnerId.of("UserX");
    OwnerId userY = OwnerId.of("UserY");

    thereIsSlotOwnedBy(slotA, userX);
    thereIsSlotOwnedBy(slotB, userY);

    BatchReservationResult result = batchReservationUseCase.execute(List.of(
            new ReservationChangeRequest(slotA, slotB, userX),
            new ReservationChangeRequest(slotB, slotA, userY)
    ));

    assertEquals(SUCCESS, result.status());
    assertEquals(2, result.executedRequests().size());
    assertEquals(userY, findSlotOwner(slotA));
    assertEquals(userX, findSlotOwner(slotB));
}
```

Policy represented independently as a graph:

```java
class Eligibility {
    private final Graph<OwnerId, Void> graph = new Graph<>();

    void markTransferEligible(OwnerId from, OwnerId to) {
        graph.addEdge(new Edge<>(new Node<>(from), new Node<>(to), null));
    }

    void markTransferIneligible(OwnerId from, OwnerId to) {
        graph.removeEdge(new Edge<>(new Node<>(from), new Node<>(to), null));
    }

    boolean isTransferEligible(OwnerId from, OwnerId to) {
        return graph.hasEdge(new Node<>(from), new Node<>(to));
    }

    Graph<OwnerId, Void> asGraph() {
        return graph;
    }
}
```

The use case then intersects intent with eligibility without changing cycle detection:

```java
BatchReservationResult execute(
        List<ReservationChangeRequest> requests,
        Eligibility eligibility) {
    Graph<OwnerId, ReservationChangeRequest> intersection =
            buildOwnerGraph(requests).intersection(eligibility.asGraph());

    Set<ReservationChangeRequest> dependentRequests =
            findDependentRequestsInOwnerGraph(intersection);

    // The remaining exchange logic stays unchanged.
}
```

## Lesson 3 - Techniques for Recognizing Hidden Graphs

A relationship is not merely a foreign key when it has semantics and changes system behavior. If objects are connected
in order to calculate, synchronize, balance, reconcile, propagate, block, limit, or retry, the arrangement of
connections may be the real model.

Warning signs include:

- entities affecting other entities through recursive relations;
- relationships affecting other relationships;
- repeated retrieval of related records followed by nested conditions;
- behavior depending on connection order, direction, or reachability;
- self-referencing one-to-many or many-to-many associations;
- logic distributed across conditionals and recursion.

A graph begins when dependencies determine behavior, not when Neo4j is installed. Naming the graph makes it possible to
inspect, visualize, sort, optimize, and test the topology directly.

### Java example

The presentation uses applications and their partner, parent, and child relations as an intentionally procedural warning
example:

```java
class Application {
    ApplicationId id;
    Status status;
    ApplicationId partnerApplicationId;
    ApplicationId parentApplicationId;
}

class PartnerChangeService {
    void onPartnerChanged(String partnerId) {
        List<Application> related = repo.findByPartnerId(partnerId);
        for (Application app : related) {
            List<Application> dependents = repo.findRelated(app.id);
            doSomething(app);
            dependents.forEach(this::recalc);
        }
    }
}

class ParentWithdrawalService {
    void onParentWithdrawn(String parentId) {
        Application parent = repo.findById(parentId);
        if (parent.isWithdrawn()) {
            List<Application> children = repo.findByParentId(parentId);
            for (Application child : children) {
                child.setStatus("WITHDRAWN_BY_PARENT");
                repo.save(child);
                // Repeat recursively for descendants.
            }
        }
    }
}
```

The next slide adds literal placeholder checks inside both traversals:

```java
for (Application app : related) {
    List<Application> dependents = repo.findRelated(app.id);
    if (app...) { ... }
    doSomething(app);
    dependents.forEach(this::recalc);
}

for (Application child : children) {
    if (app...) { ... }
    child.setStatus("WITHDRAWN_BY_PARENT");
    repo.save(child);
}
```

These placeholders are intentionally incomplete, and the second even refers to `app` inside the `child` loop. Their
point is that the code already performs graph traversal while topology and policies remain hidden inside services.

## Lesson 4 - The User-Story Archetype: Processes as States and Transitions

### From flags to a state graph

A bank loyalty system begins with simple rules: reward three on-time payments, remove a promotion after a late payment,
and restore it after improvement. Later requirements add rehabilitation paths, reduced discounts, restructuring,
campaigns, quarters, and interest thresholds.

Independent flags produce an implicit state machine with invalid or unexplained combinations. Rule order becomes
behavior, a new condition mutates state used by other rules, tests become combinatorial, and an auditor cannot easily
explain why a customer has a given discount.

Model the process as a directed graph:

- node: an explicit customer state containing products or entitlements;
- edge: a condition that permits a transition;
- path: one complete scenario or journey;
- reachability: whether a product or state can ever be achieved;
- shortest or weighted path: the fastest, cheapest, or least risky route;
- cycle: a recurring journey such as late payment -> rehabilitation -> recovery -> late payment.

Adding behavior becomes adding a transition rather than inserting another order-sensitive branch. Paths also become
natural test cases. Standard and VIP customer graphs can be compared, intersected, or differentiated.

This archetype generalizes to checkout, recruitment, treatment, loan decisions, e-learning, and CI/CD pipelines.

### Java examples

The flag-based version grows from one rehabilitation rule:

```java
class Client {
    boolean hasLatePayment;
    int onTimePaymentsCount;
    boolean promotionActive;
    boolean hadPromotionBefore;
}

void someLogic() {
    if (!c.promotionActive
            && c.hadPromotionBefore
            && !c.hasLatePayment
            && c.onTimePaymentsCount >= 5) {
        c.promotionActive = true;
        c.discountPercent = 5;
        c.rehabilitated = true;
    }
}
```

The next rule depends on state created by the previous rule and must repair order-dependent outcomes:

```java
class Client {
    boolean hasLatePayment;
    int onTimePaymentsCount;
    boolean promotionActive;
    boolean hadPromotionBefore;
    boolean isInRestructuring;
}

void someLogic() {
    if (!c.promotionActive
            && c.hadPromotionBefore
            && !c.hasLatePayment
            && c.onTimePaymentsCount >= 5) {
        c.promotionActive = true;
        c.discountPercent = 5;
        c.rehabilitated = true;
    }

    if (c.rehabilitated
            && c.hasLatePayment
            && c.isInRestructuring) {
        if (c.promotionActive) {
            c.promotionSuspended = true;
            c.auditTrail += "late + restructuring -> promotion suspended\n";
        } else {
            c.promotionActive = true;
            c.promotionSuspended = true;
            c.auditTrail += "promotion restored as suspended\n";
        }
        c.discountPercent = c.discountPercent;
    }
}
```

The graph model makes states and transition conditions explicit:

```java
record UserJourney(
        UserJourneyId userJourneyId,
        Graph<State, Condition> graph,
        State currentState) {}

record State(Set<Product> products) {}

record Condition(
        ConditionType type,
        Map<String, Object> attributes) {}
```

Finding every path to a discount:

```java
@Test
void shouldFindMultiplePathsToDiscount() {
    State newLoan = State.of(Product.newLoan());
    State afterPayments = State.of(Product.penalty());
    State discount10 = State.of(Product.discount(10));

    UserJourney journey = UserJourney.builder(UserJourneyId.of("user-4"))
            .from(newLoan).on(Condition.paymentOnTime()).goto_(discount10)
            .from(newLoan).on(Condition.latePayments(3)).goto_(afterPayments)
            .from(afterPayments).on(Condition.promotionApproved()).goto_(discount10)
            .withCurrentState(newLoan)
            .build();

    Set<CustomerPath> paths = journey.waysToAchieve(DISCOUNT);

    assertEquals(2, paths.size());
    assertContainsPath(paths, Condition.paymentOnTime());
    assertContainsPath(
            paths,
            Condition.latePayments(3),
            Condition.promotionApproved());
}
```

The corresponding path query uses JGraphT's `AllDirectedPaths`:

```java
Set<CustomerPath> waysToAchieve(Product.ProductType productType) {
    Set<State> statesWithProduct = graph.vertexSet().stream()
            .filter(state -> state.contains(productType))
            .collect(toSet());

    return statesWithProduct.stream()
            .flatMap(targetState ->
                    new AllDirectedPaths<>(graph)
                            .getAllPaths(currentState(), targetState, true, null)
                            .stream())
            .map(graphPath -> CustomerPath.of(graphPath.getEdgeList()))
            .collect(toSet());
}
```

Transitioning after a fulfilled condition:

```java
@Test
void shouldTransitionToNewStateWhenConditionFulfilled() {
    State newLoan = State.of(Product.newLoan());
    State afterPayment = State.of(Product.penalty());
    Condition paymentOnTime = Condition.paymentOnTime();

    UserJourney journey = UserJourney.builder(UserJourneyId.of("user-1"))
            .from(newLoan).on(paymentOnTime).goto_(afterPayment)
            .withCurrentState(newLoan)
            .build();

    UserJourney updatedJourney = journey.onFulfilled(paymentOnTime);

    assertEquals(afterPayment, updatedJourney.currentState());
}
```

Choosing a path by edge cost:

```java
class WeightedPathsTest {
    @Test
    void shouldFindCheapestPathByMinimizingCost() {
        State newLoan = State.of(Product.newLoan());
        State intermediate = State.of(Product.penalty());
        State discount = State.of(Product.discount(10));
        Condition directExpensive =
                Condition.withCost(PAYMENT_ON_TIME, 100.0);
        Condition cheapStep1 =
                Condition.withCost(LATE_PAYMENT, 30.0);
        Condition cheapStep2 =
                Condition.withCost(RESTRUCTURING, 20.0);

        UserJourney journey = UserJourney.builder(UserJourneyId.of("user-1"))
                .from(newLoan).on(directExpensive).goto_(discount)
                .from(newLoan).on(cheapStep1).goto_(intermediate)
                .from(intermediate).on(cheapStep2).goto_(discount)
                .withCurrentState(newLoan)
                .build();

        Optional<CustomerPath> cheapestPath =
                journey.optimizedWayToAchieve(DISCOUNT, Condition::getCost);

        assertTrue(cheapestPath.isPresent());
        CustomerPath path = cheapestPath.get();
        assertEquals(2, path.length());
        assertTrue(path.conditions().contains(cheapStep1));
        assertTrue(path.conditions().contains(cheapStep2));
    }
}
```

## Lesson 5 - The Influence-Zone Archetype

### Combining universal and local influence

The laboratory example asks whether a new reservation is safe when experiments affect one another through voltage,
cooling, temperature, vibration, acoustics, or electromagnetic interference.

Two graph layers describe different truths:

- physics graph: universal process-to-process influence;
- infrastructure graph: local influence caused by a specific building, laboratory, device, cable, wall, or defect.

Their union produces the effective influence graph. Because the physics graph uses processes as nodes while the
infrastructure graph uses process-plus-laboratory nodes, the models must first be aligned to a common `InfluenceUnit`
representation. A raw union of incompatible node types is not enough.

For reservations that overlap in time, create an edge when their process and location pair conflicts in the effective
map.

### Direct conflicts versus influence zones

- Vertex degree answers how many parties a new reservation must contact directly.
- A connected component answers how large the complete coordination zone becomes through indirect influence.
- In this domain, direction may intentionally be ignored: even one-way physical influence can require two-way
  coordination.
- In domains such as money, information, or responsibility flow, direction may remain essential and strongly connected
  or reachable sets may be more appropriate.

Connected components expose natural boundaries: reservations inside one component must be planned together, while
separate components can operate independently.

### Rich edges and simulation

An edge can carry influence type, strength, threshold, delay, cost, or attenuation. The question then changes from "does
A affect B?" to "does A affect B strongly enough to matter?" Weighted relations enable scenario simulation, isolation
investment analysis, and capacity planning without changing the component algorithm.

The archetype also applies to factory processes, microgrids, radio-spectrum allocation, software test environments, and
marketing campaigns.

### Java examples

The initial anti-example mixes specific rooms, universal physics, temporary policy, priorities, and new process types in
one method:

```java
boolean canSchedule(Reservation newOne, Room D) {
    if (A.chillerOn && B.isMeasuring) return false;
    if (B.cooling && C.isCalibrating) return false;
    if (C.isCalibrating && D.requested(Test)) return false;
    if (A.sharesPowerWith(D) && (A.isRunning || D.requestedAny())) return false;

    if (Env.vibrationRMS > VIB_THR && D.requested(Precision)) return false;
    if (Env.magneticInterference && D.requested(MagSensitive)) return false;
    if (D.requested(Acoustic) && Env.dbLevel > DB_LIMIT) return false;
    if (D.requested(Cryogenic) && !Env.power.isStable()) return false;

    if (D.hasPriority && B.isMeasuring && !C.isCalibrating) {
        preempt(B);
    } else {
        return false;
    }
    if (Policy.quietHour
            && (A.noisy() || B.noisy() || C.noisy() || D.requested(Noisy))) {
        return false;
    }

    if (D.requested(VolatileTest)
            && (A.cooling || B.cooling || C.cooling)) {
        return false;
    }
    if (D.requested(Cryogenic)
            && Env.power.reserveKW < CRYO_MIN) {
        return false;
    }

    return true;
}
```

The universal physics layer:

```java
PhysicsInfluence physics = PhysicsInfluence.builder()
        .addInfluence(CHEMICAL_ANALYSIS, SAMPLE_DRYING)
        .addInfluence(ELECTRON_MICROSCOPY, CONDUCTIVITY_MEASUREMENT)
        .addInfluence(CONDUCTIVITY_MEASUREMENT, ELECTRON_MICROSCOPY)
        .addInfluence(THERMAL_MEASUREMENT, MASS_SPECTROMETRY)
        .addInfluence(THERMAL_MEASUREMENT, CHEMICAL_ANALYSIS)
        .addInfluence(SAMPLE_DRYING, THERMAL_MEASUREMENT)
        .build();
```

The local infrastructure layer:

```java
InfrastructureInfluence infrastructureInfluence =
        InfrastructureInfluence.builder()
                .addConstraint(
                        LAB_A, THERMAL_MEASUREMENT,
                        LAB_B, CONDUCTIVITY_MEASUREMENT)
                .addConstraint(
                        LAB_B, CONDUCTIVITY_MEASUREMENT,
                        LAB_C, ELECTRON_MICROSCOPY)
                .addConstraint(
                        LAB_C, ELECTRON_MICROSCOPY,
                        LAB_D, MASS_SPECTROMETRY)
                .addConstraint(
                        LAB_D, MASS_SPECTROMETRY,
                        LAB_A, THERMAL_MEASUREMENT)
                .addConstraint(
                        LAB_E, CHEMICAL_ANALYSIS,
                        LAB_B, CONDUCTIVITY_MEASUREMENT)
                .addConstraint(
                        LAB_E, CHEMICAL_ANALYSIS,
                        LAB_C, ELECTRON_MICROSCOPY)
                .addConstraint(
                        LAB_A, THERMAL_MEASUREMENT,
                        LAB_E, CHEMICAL_ANALYSIS)
                .build();
```

The effective map is built from both perspectives. The slides show both a builder and the underlying union operation:

```java
InfluenceMap influenceMap = InfluenceMap.builder()
        .withPhysics(physics)
        .withInfrastructure(emptyInfrastructure())
        .withLaboratories(Set.of(LAB_A, LAB_B))
        .build();

GraphUnion.of(physicsGraph, infrastructureGraph);
```

Counting only direct conflicts:

```java
class InfluenceAnalyzer {
    private final InfluenceMap influenceMap;

    int countConflicts(
            Reservation newReservation,
            Set<Reservation> existingReservations) {
        int conflicts = 0;
        for (Reservation existing : existingReservations) {
            if (influenceMap.influences(newReservation, existing)) {
                conflicts++;
            }
        }
        return conflicts;
    }
}
```

Filtering an influence by edge weight:

```java
boolean influences(
        PhysicsProcess fromProcess,
        Laboratory fromLab,
        PhysicsProcess toProcess,
        Laboratory toLab) {
    var from = new InfluenceUnit(fromProcess, fromLab);
    var to = new InfluenceUnit(toProcess, toLab);

    if (graph.containsEdge(from, to)
            && graph.edgeWeight(from, to) >= THRESHOLD) {
        return true;
    }
    return false;
}

boolean influences(Reservation from, Reservation to) {
    return influences(
            from.process(),
            from.laboratory(),
            to.process(),
            to.laboratory());
}
```

## Lesson 6 - The Scheduling Archetype

One reservation may itself contain a graph of stages such as preparation, drying, calibration, measurement, analysis,
validation, and archiving.

### Ordering with a directed acyclic graph

A directed dependency edge gives temporal meaning. Topological sorting produces an order in which every prerequisite
precedes its dependents. Independent nodes can begin first, completion removes dependencies, and newly unblocked nodes
become eligible. This works only when the graph is directed and acyclic. A cycle indicates contradictory prerequisites
or a process that cannot be scheduled as stated.

The lesson's intended sequence is:

1. sample preparation and sensor calibration can start independently;
2. preparation unlocks drying;
3. drying and calibration unlock measurement;
4. measurement unlocks analysis;
5. completed prerequisites unlock validation;
6. archiving and reporting finish the process.

The direction convention must be explicit. Whether `A -> B` means "A must precede B" or "A depends on B" determines
whether zero-indegree or zero-outdegree nodes are processed first.

### Parallelism with a conflict graph

Temporal order answers "when?" but resource conflict answers "can these run together?"

Build an undirected graph where an edge means two stages cannot share an environment. Vertex coloring assigns different
colors to adjacent stages. Each color represents an independent execution environment, resource lane, team, or equipment
set. Minimizing colors minimizes required parallel capacity.

Topological sorting and coloring recur in production, CI/CD, project work, logistics, and transaction processing. A
transfer system that was serialized globally could instead separate operations that do not touch the same balances.

### Java examples

A dependency-heavy manager is shown as a warning sign:

```java
public class CalendarManager {
    private final ResourceDao resourceDao;
    private ProjectDao projectDao;
    private StageDao stageDao;
    private EmployeeDao employeeDao;
    private DeviceDao deviceDao;
    private MaterialDao materialDao;

    public CalendarManager(
            ProjectDao projectDao,
            StageDao stageDao,
            ResourceDao resourceDao,
            EmployeeDao employeeDao,
            DeviceDao deviceDao,
            MaterialDao materialDao) {
        this.projectDao = projectDao;
        this.stageDao = stageDao;
        this.resourceDao = resourceDao;
        this.employeeDao = employeeDao;
        this.deviceDao = deviceDao;
        this.materialDao = materialDao;
    }
}
```

A coloring-based concurrency example:

```java
@Test
void laboratoryStagesRequireThreeEnvironments() {
    ProcessStep measurement = new ProcessStep("Measurement");
    ProcessStep calibration = new ProcessStep("Calibration");
    ProcessStep validation = new ProcessStep("Validation");
    ProcessStep finalTest = new ProcessStep("Final test");

    ExecutionEnvironments environments = Concurrency.builder()
            .addConflict(measurement, calibration)
            .addConflict(measurement, validation)
            .addConflict(finalTest, measurement)
            .addConflict(finalTest, calibration)
            .addConflict(finalTest, validation)
            .build();

    assertEquals(3, environments.environmentCount());
    assertFalse(environments.canRunConcurrently(measurement, calibration));
    assertFalse(environments.canRunConcurrently(measurement, validation));
    assertTrue(environments.canRunConcurrently(calibration, validation));
    assertFalse(environments.canRunConcurrently(finalTest, measurement));
    assertFalse(environments.canRunConcurrently(finalTest, calibration));
    assertFalse(environments.canRunConcurrently(finalTest, validation));
}
```

## Lesson 7 - Intersections and Unions

Complex systems often contain multiple valid perspectives rather than one universal relationship model.

- Topology describes what is structurally or physically connected.
- Policy describes what is allowed, desirable, available, safe, or profitable in the current context.

A graph intersection is analogous to logical AND: retain relationships present in both views. It answers what actually
works now. Examples include requested and eligible exchanges or physically existing and currently permitted logistics
routes.

A graph union is analogous to logical OR: retain relationships present in either view. It shows the full horizon of
known influence or potential, such as universal physics plus local infrastructure, official routes plus temporarily
blocked or locally known alternatives.

Relationships may evolve at a different rate from the entities they connect. Fields such as `enabled`, `allowed`,
`limit`, `threshold`, `lastUpdated`, availability windows, and permissions often indicate that a second graph is being
hidden inside one entity model.

When graphs carry different edge meanings, union needs a conflict policy. The logistics slide illustrates blue "route
exists" and red "route closed" edges, with the more informative closure relation dominating. A union is therefore not
automatically enough when edge labels disagree.

### Java examples

The anti-example mixes topology, authorization, limits, product flags, physics, maintenance, cycles, persistence, and
auditing:

```java
class ExchangeService {
    Reservation requestSwap(
            Reservation a,
            Reservation b,
            User u,
            Calendar cal,
            Limits lim,
            Policy pol) {
        if (!a.room.equals(b.room) && pol.allowCrossRoom) {
            // Exception to an exception.
        } else if (!a.adjacentTo(b)) {
            return DENY;
        }

        if (!u.hasRole("MANAGER") && a.department != b.department)
            return FORBIDDEN;
        if (lim.daily(a.owner).reached(cal.today()))
            return THROTTLE;
        if ((a.isProtected || b.isProtected)
                && !feature("UNLOCK_PROTECTED"))
            return LOCKED;
        if (a.priority < b.priority)
            reorderQueue(a.owner);
        if (pol.needsCooling && !infraHasCooling(a.slot, b.slot))
            return PHYSICS_FAIL;
        if (overlaps(cal.maintenance(), a.slot, b.slot))
            return WINDOW_CONFLICT;
        if (causesCycle(a.owner, b.owner))
            rotateTriplet(a, b, findThird());

        audit(a, b, u, why());
        persist(a.swapWith(b));
    }
}
```

The JGraphT-based slide contrasts this with explicit composition:

```java
Graph<String, DefaultEdge> allowed = new AsSubgraph<>(
        topology,
        Sets.intersection(
                topology.vertexSet(),
                policies.vertexSet()));

Graph<String, DefaultEdge> full =
        new AsUnionGraph<>(topology, policies);
```

Important caveat: the `AsSubgraph` snippet intersects vertex sets and then takes the induced topology subgraph. A strict
graph intersection must also retain only edges present in both graphs. Use a library intersection operation or filter
both vertices and edges when edge equality matters.

## Lesson 8 - The Connector Archetype

An articulation point is a node whose removal increases the number of connected components. It identifies a single
element that connects otherwise separate parts of a network.

Its business value depends on context:

- In power, internet, supply-chain, and financial infrastructure, it is a single point of failure. Add redundancy or
  protect it.
- In organizational, informational, scheduling, or influence networks, it may be a coordination bottleneck. Removing,
  postponing, or redesigning it can split one large batch into independent parallel batches.

Examples include a clearing account through which most transfers pass, a laboratory reservation joining otherwise
separate influence zones, and a CI/CD task sharing a resource with two otherwise independent groups.

This archetype is usually discovered after a graph already exists. Ask what would happen if a node disappeared: would
the system fail, or become more independent?

### Removing edges to remove cycles

A feedback arc set is a set of directed edges whose removal makes a graph acyclic. If cycles are harmful because they
block ordering, identify the dependencies that create them and decide whether removing one loses business capability or
eliminates accidental coupling.

This reinforces a broader technique: combine simple operations such as detect, remove, add, split, and compare to
generate domain questions and new value.

### Java example

The example uses JGraphT's `BiconnectivityInspector` to find critical reservations in a chain:

```java
class BridgingReservationsAnalyzerTest {
    @Test
    void longChainHasMultipleCriticalReservations() {
        PhysicsInfluence physics = PhysicsInfluence.builder()
                .addInfluence(PROCESS_A, PROCESS_B)
                .addInfluence(PROCESS_B, PROCESS_C)
                .addInfluence(PROCESS_C, PROCESS_D)
                .build();

        InfluenceMap influenceMap = InfluenceMap.builder()
                .withPhysics(physics)
                .withLaboratories(Set.of(LAB_A, LAB_B, LAB_C))
                .build();

        Reservation r1 = new Reservation(PROCESS_A, LAB_A);
        Reservation r2 = new Reservation(PROCESS_B, LAB_B);
        Reservation r3 = new Reservation(PROCESS_C, LAB_C);
        Reservation r4 = new Reservation(PROCESS_D, LAB_A);

        BridgingReservations bridging =
                new InfluanceAnalyzer(influenceMap)
                        .identifyCriticalReservations(Set.of(r1, r2, r3, r4));

        assertEquals(2, bridging.count());
        assertTrue(bridging.isBridging(r2));
        assertTrue(bridging.isBridging(r3));
    }

    BridgingReservations identifyCriticalReservations(
            Set<Reservation> reservations) {
        Graph<Reservation, DefaultEdge> graph =
                buildInfluenceGraph(reservations);
        BiconnectivityInspector<Reservation, DefaultEdge> inspector =
                new BiconnectivityInspector<>(graph);
        Set<Reservation> criticalReservations = inspector.getCutpoints();
        return new BridgingReservations(criticalReservations);
    }
}
```

`InfluanceAnalyzer` is spelled this way in the presentation snippet. The slide does not show the owning production class
of `identifyCriticalReservations` or the implementation of `buildInfluenceGraph`.

Standard articulation-point analysis concerns undirected connectivity. A directed influence model may instead require
weak connectivity, strong articulation points, dominators, or another purpose-specific interpretation. Also, when a
financial transfer remains an edge rather than a reified node, removing that transfer is bridge-edge analysis rather
than articulation-point analysis.

## Lesson 9 - Implementation

Graph modeling has a cost. Adopt it incrementally and stop at the lowest level that solves the actual problem.

1. Local code: for one or two simple algorithms, keep a small in-memory graph implementation in one file.
2. Library: when several features need graphs or mature algorithms, use an in-memory library such as JGraphT. Build the
   graph at runtime from existing entities or persist a small graph as JSON.
3. Graph database: use one when the graph is large and relationships are dynamic, heavily queried, and first-class data.
   Examples mentioned are Neo4j, ArangoDB, and Dgraph.
4. Microservice: the rarest choice. Use it only when a shared network has its own lifecycle across domains or
   computation is sufficiently heavy or specialized to require isolation. Name its API in domain language, not `nodes`
   and `edges`.

A graph is often a temporary computational projection over relational source data. Persistence technology, algorithm
implementation, and deployment boundary are separate decisions.

The line between archetype and overengineering is repetition plus value. Use a graph when a relationship pattern grows
across cases and enables useful questions. Do not introduce one merely because two entities are related.

### Java examples

A deliberately small local graph excerpt:

```java
public class HomeMadeGraph<T, P> {
    private final Map<Node<T>, List<Edge<T, P>>> adjacencyMatrix =
            new HashMap<>();

    public HomeMadeGraph<T, P> addEdge(Edge<T, P> edge) {
        adjacencyMatrix
                .computeIfAbsent(edge.from(), key -> new ArrayList<>())
                .add(edge);
        adjacencyMatrix.putIfAbsent(edge.to(), new ArrayList<>());
        return this;
    }

    public Optional<Path<T, P>> findFirstCycle() {
        Set<Node<T>> visited = new HashSet<>();
        Set<Node<T>> inStack = new HashSet<>();

        for (Node<T> node : adjacencyMatrix.keySet()) {
            if (!visited.contains(node)) {
                Optional<Path<T, P>> cycle =
                        findCycleDFS(
                                node,
                                visited,
                                inStack,
                                new ArrayList<>());
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        return Optional.empty();
    }
}
```

The slide intentionally omits `findCycleDFS` and the supporting `Node`, `Edge`, and `Path` types. Despite its name,
`adjacencyMatrix` is an adjacency-list map. Since `HashMap` iteration order is unspecified, the "first" cycle is not
necessarily deterministic.

An in-memory library model:

```java
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Set;

class InfluenceMap {
    private final Graph<InfluenceUnit, DefaultEdge> graph;

    private InfluenceMap(Graph<InfluenceUnit, DefaultEdge> graph) {
        this.graph = graph;
    }
}
```

## Lesson 10 - Summary

The module's recurring archetypes are:

| Archetype             | Business question                                       | Graph technique                                        |
|-----------------------|---------------------------------------------------------|--------------------------------------------------------|
| interdependence       | What can only work together?                            | cycle detection with DFS or a library algorithm        |
| influence zones       | Who is affected directly or indirectly?                 | connected components using DFS, BFS, or Union-Find     |
| state journey         | Can a target state be reached, and by which route?      | reachability, all paths, shortest or weighted path     |
| order                 | What must happen before what?                           | topological sorting with Kahn's algorithm or DFS       |
| parallelism           | What can coexist, and how many environments are needed? | graph coloring such as greedy, Welsh-Powell, or DSATUR |
| critical connector    | Which node holds the network together?                  | articulation points, commonly Tarjan-style algorithms  |
| topology and policy   | What is structurally possible and currently allowed?    | graph intersection                                     |
| combined perspectives | What exists in any relevant context?                    | graph union with edge-conflict semantics where needed  |
| harmful feedback      | Which dependencies must be cut to restore order?        | feedback arc set                                       |

Benefits by perspective:

- Analyst: sees cycles, paths, components, and influence instead of reverse-engineering conditions.
- Business: can ask who is affected, where a bottleneck exists, and what a removed relation would change.
- Developer: replaces implicit traversal and fragile flags with explicit structure and established algorithms.
- Architect: gains a map for deciding whether the graph belongs in local code, a shared library, storage, or a separate
  domain capability.

The final lesson repeats the key constraint: graph thinking is not a religion. Begin with ordinary code for simple
stable cases. Introduce the archetype when cases form a network-shaped pattern and each new rule otherwise spreads
complexity through the system.

## Practical Reflection: Deliberate Algorithmic Simplifications

The practical task asks where the lessons intentionally simplify algorithms to keep attention on graph recognition. Two
clear examples are:

1. **Selecting the first cycle.** The reservation example calls `findFirstCycle()`. A production scheduler might need
   all cycles, maximal or disjoint cycle selection, priorities, fairness, starvation prevention, deterministic
   tie-breaking, and transactional handling of overlapping cycles. The lesson omits these choices because the modeling
   insight is simply that a closed exchange is a cycle.
2. **Treating coloring as an available answer.** Minimum graph coloring is computationally hard in general. Real systems
   often use greedy or domain-specific heuristics, accept a non-minimal number of colors, or exploit graph structure.
   The lesson treats coloring as one library-level operation because its purpose is to reveal that resource parallelism
   is a conflict-graph question.

Other deliberate simplifications include ignoring edge direction when coordination is mutual, using first-path or
all-simple-path queries without discussing path explosion, and presenting graph union or intersection without fully
specifying vertex identity, edge identity, label conflicts, time windows, or update consistency.
