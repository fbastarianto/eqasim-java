# Jakarta PT first/last-mile home-side routing: source-level forensic report

Date: 2026-09-05  
Repository: `eqasim-java`, module `jakarta/`  
Inspected commit: `4a4a8b869d0f4dc96a06d3f3cc4b7e145d5a569f`  
Scope: diagnosis and implementation design only; no source/configuration changes and no simulation/build execution

## A. Executive verdict

The zero-DMC intermodal chains are created by MATSim's **controller-startup initial routing**, not by DiscreteModeChoice (DMC). The responsible caller is `PrepareForSimImpl.run()`. It runs before the iteration loop and constructs `PersonPrepareForSim(new PlanRouter(...))`. When any activity or leg in an input plan requires preparation, `PersonPrepareForSim` invokes `PlanRouter` for the **whole plan**. `PlanRouter` sends the substantive direct `pt` trip to `TripRouter`; the `pt` routing module in this installation is `SwissRailRaptorRoutingModule`. That module calls `SwissRailRaptor`, which generates access and egress candidates for every configured intermodal feeder mode—including `motorcycle`—and may return a multi-leg chain. `TripRouter.insertTrip` then replaces the direct input leg with that returned chain.

The conclusions are:

| Question | Source-level verdict |
|---|---|
| Does population loading expand the direct PT leg? | No. `ScenarioLoaderImpl` parses the population; it does not route plans. |
| Is `PrepareForSim` responsible? | **Yes.** It is the pre-iteration caller that conditionally invokes whole-plan routing. |
| Is SwissRailRaptor responsible for constructing the intermodal chain? | **Yes.** `TripRouter` dispatches mode `pt` to `SwissRailRaptorRoutingModule`, and `SwissRailRaptor` obtains intermodal access/egress candidates from `DefaultRaptorStopFinder`. |
| Is DMC required? | **No.** At iteration 0, no replanning event occurs at all. The 0.0 DMC weight is therefore corroborative but not the decisive source fact. |
| Can `NoMotorcycleEgressExceptHome` intercept startup routing? | **No.** It is a DMC `TripConstraint`, not a `TripRouter`, `RoutingModule`, or RAPTOR hook. |
| Does `performReroute=false` affect startup routing? | **No.** It changes only the composition of the DMC replanning strategy. |
| Is another pre-mobsim stage implicated? | `PrepareForMobsimImpl` performs the same route-validity check before each mobsim, but normally makes no change after startup routing has supplied valid routes. It is a possible rerouting caller only if a route is still missing/invalid. It is not needed to explain this control result. |

The current RAPTOR intermodal implementation has no trip-purpose or endpoint-activity-type filter. Access and egress candidate sets are generated separately, using the same configured intermodal mode parameter sets. A motorcycle parameter set that passes its optional **person-level** filter is consequently available on either side whenever its feeder routing module can return a route. The final PT path choice jointly minimizes access, transit, and egress cost; there is no home-side coupling.

The cleanest low-risk correction in this pinned version is a routing-level restriction at the `RaptorStopFinder` boundary, supplied with immutable origin/destination activity-type metadata through `Trip.getTripAttributes()`. It must discard motorcycle-bearing initial-stop candidates on the forbidden side **before** the RAPTOR path is selected. This single binding is used by both startup/general routing and DMC candidate routing. The strengthened DMC constraint should remain as a defensive invariant, with `performReroute=false` and post-run QC.

## Evidence and version provenance

This report inspected repository source and locally cached source JARs; it did not infer behavior from class names.

| Component | Exact inspected version/source |
|---|---|
| Jakarta module | `org.eqasim:jakarta:2.7.0`; `pom.xml:17-34` |
| MATSim | `org.matsim:matsim:2026.0-2025w19`; `pom.xml:9-15,73-78` |
| MATSim DMC | `org.matsim.contrib:discrete_mode_choice:2026.0-2025w19`; `pom.xml:80-85` |
| eqasim core | `org.eqasim:core:2.0.0`; `pom.xml:51-71` |
| Java | 21, inherited from the eqasim parent POM |

The pre-existing shaded `target/jakarta-2.7.0.jar` embeds Maven metadata with these same versions. Its `PrepareForSimImpl.class` and `SwissRailRaptor.class` are byte-for-byte equal to those in the corresponding cached binary dependency JARs. The repository's `dependency-reduced-pom.xml` is stale and is not the build authority; the shade plugin has `createDependencyReducedPom=false` in the active `pom.xml`.

The runtime configuration is supplied externally: `src/main/java/org/eqasim/jakarta/RunSimulation.java:37-53` requires `--config-path`, loads it, creates the scenario, and calls `ScenarioUtils.loadScenario`. No run configuration XML is committed in this module. Consequently, the stated runtime choices (`useIntermodalAccessEgress=true`, `CalcLeastCostModePerStop`, feeder modes, DMC weights, and `performReroute=false`) are treated as audited run evidence, but their exact XML lines cannot be cited from this checkout.

There is one configuration caveat worth resolving before a future production run. `NoMotorcycleEgressExceptHome` is bound in `JakartaModeChoiceModule.java:89-94`, but a factory binding alone does not activate it. DMC's `ConstraintModule.provideTripConstraintFactory()` instantiates only names present in `DiscreteModeChoiceConfigGroup.getTripConstraints()` (`discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/modules/ConstraintModule.java:107-116`). The repository helper `RunAdaptConfig.java:78-84` adds only `WalkDurationConstraint`. If the real external config does not explicitly include `NoMotorcycleEgressExceptHome`—and, for a tour-based model, `FromTripBased`—the constraint is not active. This caveat does not alter the zero-DMC diagnosis.

### Control evidence used in the source attribution

The supplied exact-population audit and deterministic control establish the before/after facts below. They are experimental inputs to this report, not numbers recomputed during the source inspection.

| Measure | Clean input | Zero-DMC iteration-0 output |
|---|---:|---:|
| Persons | 297,468 | — |
| Substantive trips | 544,414 | — |
| Realised PT trips (at least one actual `pt` leg) | 27,298 | 17,943 |
| Motorcycle anywhere on access | 0 | 10,363 |
| Access motorcycle with origin != home | 0 | 5,235 |
| Motorcycle anywhere on egress | 0 | 8,261 |
| Egress motorcycle with destination != home | 0 | 4,321 |
| Unique violating trips | 0 | 9,425 |
| Violating persons | 0 | 6,537 |
| Motorcycle on both PT sides with an infeasible endpoint | 0 | 5,147 |
| PT-labelled but no actual PT leg, all walk | — | 9,201 |

All 9,425 violating trips had direct `pt` input counterparts and changed structurally. The both-side endpoint breakdown—home→home 0, home→non-home 2,527, non-home→home 2,489, non-home→non-home 131—has the exact directional signature expected when the same globally available feeder mode is offered independently to ACCESS and EGRESS without endpoint-purpose eligibility.

## B1. Call flow: clean direct-PT input to the iteration-0 selected plan

```text
RunSimulation.main
  -> ConfigUtils.loadConfig(...)
  -> ScenarioUtils.loadScenario(scenario)
     -> ScenarioLoaderImpl.loadScenario
        -> ScenarioLoaderImpl.loadPopulation
           -> PopulationReader.parse                         [read only; no routing]
  -> new Controler(scenario)
  -> configurator.configureController(controler)
     -> SwissRailRaptorModule installed
     -> pt RoutingModule bound to SwissRailRaptorRoutingModule.Provider
  -> controler.run()
     -> AbstractController.run
        -> startup listeners
        -> NewControler.prepareForSim
           -> PrepareForSimImpl.run
              -> adaptOutdatedPlansForRoutingMode
                 direct Leg(mode="pt") gets routingMode="pt" if absent
              -> PersonPrepareForSim.run(person)
                 if any activity/leg in that plan needs preparation:
                 -> PlanRouter.run(whole plan)
                    -> identifyMainMode(oldTrip) == "pt"
                    -> TripRouter.calcRoute("pt", origin, destination,
                                            departure, person, tripAttributes)
                       -> SwissRailRaptorRoutingModule.calcRoute(request)
                          -> SwissRailRaptor.calcRoute(...)
                             -> DefaultRaptorStopFinder.findStops(ACCESS)
                                -> candidates for walk, motorcycle, mcodt, ...
                             -> DefaultRaptorStopFinder.findStops(EGRESS)
                                -> candidates for walk, motorcycle, mcodt, ...
                             -> SwissRailRaptorCore.calcLeastCostRoute(...)
                             -> RaptorUtils.convertRouteToLegs(...)
                          -> fillWithActivities(...)
                       -> every returned leg gets routingMode="pt"
                    -> TripRouter.insertTrip(...)
                       direct input pt leg is removed and returned chain inserted
        -> doIterations
           -> iteration 0: NO replanning event because iteration == firstIteration
           -> before-mobsim listeners
           -> PrepareForMobsimImpl.run
              -> route-validity preparation again; normally no-op now
           -> PopulationAgentSource
              -> DefaultAgentFactory.createMobsimAgentFromPerson
                 -> person.getSelectedPlan()
           -> QSim runs the expanded selected plan
```

### Population load does not route

`RunSimulation.java:52` calls `ScenarioUtils.loadScenario`. `ScenarioLoaderImpl.loadScenario()` loads network, facilities, population, households, transit, and vehicles (`matsim-2026.0-2025w19-sources.jar!/org/matsim/core/scenario/ScenarioLoaderImpl.java:108-123`). `loadPopulation()` constructs `PopulationReader` and calls `parse` (`ScenarioLoaderImpl.java:193-205`). There is no `TripRouter` call in that loading path.

### Controller startup does route

`AbstractController.run()` fires startup listeners and then calls `prepareForSim()` before `doIterations()` (`matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/AbstractController.java:75-85`). `NewControlerModule` binds the preparation interface to `PrepareForSimImpl` (`.../core/controler/NewControlerModule.java:43-47`), and `NewControler.prepareForSim()` delegates to it (`.../core/controler/NewControler.java:113-116`).

`PrepareForSimImpl.run()` first calls `adaptOutdatedPlansForRoutingMode()` (`.../core/controler/PrepareForSimImpl.java:167-176`). For a one-leg legacy trip without a routing-mode attribute, that method copies the leg's actual mode—`pt` here—into its routing mode (`PrepareForSimImpl.java:336-382`). It then runs any registered `PersonPrepareForSimAlgorithm`s and, crucially, invokes in parallel:

```java
new PersonPrepareForSim(
    new PlanRouter(tripRouterProvider.get(), activityFacilities, timeInterpretation),
    scenario,
    carOnlyNetwork)
```

at `PrepareForSimImpl.java:178-196`.

`PersonPrepareForSim.run()` checks every plan, not only the selected plan. A missing/invalid activity location sets `needsReRoute`; a leg with `route == null` also sets it (`.../core/population/algorithms/PersonPrepareForSim.java:117-146,151-170`). If the flag is set, it invokes the supplied `PlanRouter` on the whole plan. Thus a defect on one plan element causes all substantive trips in that plan to be routed again.

This condition is important for a precise claim: a fully valid direct `pt` leg with a non-null route and valid endpoints would survive this check. The observed structural replacement therefore means the affected input plans met one of `PersonPrepareForSim`'s explicit rerouting conditions. The most direct condition for an unrouted input leg is `leg.getRoute() == null`. The control comparison did not report the route object's state, so the source alone cannot distinguish that from a plan-level invalid activity-location trigger. It can, however, identify the only active pre-iteration whole-plan router that performs the observed replacement.

`PlanRouter.run()` enumerates every `TripStructureUtils.Trip`, obtains its routing mode, and calls `TripRouter.calcRoute` with facilities, person, time, and `oldTrip.getTripAttributes()` (`.../core/router/PlanRouter.java:74-97`). It then calls `TripRouter.insertTrip`, which clears the old plan elements between the two substantive activities and inserts the returned plan elements (`.../core/router/TripRouter.java:207-270`). This is the exact destructive replacement of the direct `pt` leg by an expanded chain.

### Why there is no DMC replanning in iteration 0

`AbstractController.iteration()` fires replanning only when `iteration > firstIteration` (`AbstractController.java:141-157`). MATSim's default `firstIteration` is 0 (`.../core/config/groups/ControllerConfigGroup.java:90,135`); no contrary external setting was supplied. With first and last iteration 0, the condition is false. Therefore neither the DMC 0.0 weight nor `KeepLastSelected=1.0` needs to be invoked to explain the result: there is no strategy-selection/replanning phase at all. Startup `PrepareForSim` remains active regardless of replanning weights. If the external config deliberately set `firstIteration < 0`, that config would need separate inspection, but it would not remove startup routing.

### The selected expanded plan reaches mobsim

Immediately before each mobsim, `AbstractController.mobsim()` calls `prepareForMobsim()` (`AbstractController.java:188-208`). `PrepareForMobsimImpl.run()` again uses `PersonPrepareForSim` with `PlanRouter` (`.../core/controler/PrepareForMobsimImpl.java:75-107`). After successful startup routing, all returned legs have routes, so this is normally a validation/no-op; it becomes a second rerouting opportunity only if an intervening operation has left missing/invalid routes.

QSim's `PopulationAgentSource` sends each person to the agent factory (`.../core/mobsim/qsim/agents/PopulationAgentSource.java:71-80`), and `DefaultAgentFactory` explicitly passes `person.getSelectedPlan()` to the plan agent (`.../core/mobsim/qsim/agents/DefaultAgentFactory.java:46-48`). Thus the chain inserted at startup is the selected plan used in mobsim.

No active Jakarta startup listener or module in this checkout contains another general plan-replacement call. The only other `PlanRouter` path relevant here is `PrepareForMobsimImpl`, as just described. The disabled/commented road-pricing code in `RunSimulation` is not part of the run.

## C. PT binding and SwissRailRaptor expansion

### Exact Jakarta/eqasim installation path

`JakartaConfigurator` extends `EqasimConfigurator` and registers `JakartaModeChoiceModule` (`src/main/java/org/eqasim/jakarta/JakartaConfigurator.java:7-12`). `EqasimConfigurator` adds `SwissRailRaptorConfigGroup`, `DiscreteModeChoiceConfigGroup`, and `EqasimRaptorConfigGroup`, then registers `SwissRailRaptorModule`, `EqasimTransitModule`, DMC, `EqasimRaptorModule`, and eqasim mode choice (`core-2.0.0-sources.jar!/org/eqasim/core/simulation/EqasimConfigurator.java:71-86,256-270`).

For `TransitRoutingAlgorithmType.SwissRailRaptor`, MATSim's normal `TripRouterModule` deliberately does not install the standard transit router (`matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripRouterModule.java:71-76`). `SwissRailRaptorModule.install()` instead binds every configured transit mode's `RoutingModule` to `SwissRailRaptorRoutingModule.Provider`; it also binds `RaptorStopFinder` to `DefaultRaptorStopFinder` and `RaptorIntermodalAccessEgress` to `DefaultRaptorIntermodalAccessEgress` (`matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorModule.java:39-77`). The provider constructs `SwissRailRaptorRoutingModule` (`.../ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorRoutingModule.java:42-52`).

### Exact routing call

`TripRouter.calcRoute()` looks up `routingModules.get(mainMode)`, creates a `DefaultRoutingRequest`, calls `module.calcRoute(request)`, uses the generic fallback module only if that returns `null`, and stamps the original requested main mode as `routingMode` on every returned leg (`.../core/router/TripRouter.java:146-179`). For the direct input trip, `mainMode` is `pt`.

`SwissRailRaptorRoutingModule.calcRoute()` invokes `raptor.calcRoute(...)`; if a RAPTOR result is returned it inserts stage activities between adjacent legs, otherwise it returns the walk-router fallback (`.../ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorRoutingModule.java:68-95`).

`SwissRailRaptor.calcRoute()` obtains parameters, calls `getAccessStops` and `getEgressStops`, asks `SwissRailRaptorCore` for the least-cost route, constructs a direct-walk alternative, applies the configured intermodal-leg-only policy, and converts the chosen route to MATSim legs (`.../ch/sbb/matsim/routing/pt/raptor/SwissRailRaptor.java:81-124`). Its access and egress helpers call the `RaptorStopFinder` separately with `Direction.ACCESS` and `Direction.EGRESS` (`SwissRailRaptor.java:292-298`).

`RaptorUtils.convertRouteToLegs()` copies the access/egress plan elements held by initial stops, creates real `pt` legs for transit route parts, and makes walk legs for non-transit transfers (`.../ch/sbb/matsim/routing/pt/raptor/RaptorUtils.java:139-198`). `SwissRailRaptorRoutingModule.fillWithActivities()` inserts `pt interaction` activities between adjacent legs (`SwissRailRaptorRoutingModule.java:76-95`). This is sufficient to produce all observed forms, including:

- `motorcycle-pt-motorcycle`
- `motorcycle-pt-mcodt`
- `mcodt-pt-motorcycle`
- `motorcycle-pt-walk-pt-motorcycle`
- `motorcycle-walk-pt-mcodt`

## Intermodal access/egress selection

The inspected RAPTOR configuration class defines `IntermodalAccessEgressModeSelection.CalcLeastCostModePerStop` and one parameter set per intermodal mode (`matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/config/SwissRailRaptorConfigGroup.java:70-95,495-650`). Each set can define mode, search radius, link-ID attribute, a person filter, a stop filter, and shared trip search. It has no activity-type, trip-purpose, origin-type, destination-type, or direction-specific mode-availability field.

`DefaultRaptorStopFinder` constructs a routing module for every configured intermodal mode parameter set (`matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/DefaultRaptorStopFinder.java:69-80`). Its `findStops()` branches on `Direction.ACCESS` versus `Direction.EGRESS` (`DefaultRaptorStopFinder.java:88-133`). With `CalcLeastCostModePerStop`, `findIntermodalStops()` iterates **all** configured intermodal parameter sets for either direction and calls `addInitialStopsForParamSet()` for each (`DefaultRaptorStopFinder.java:136-163`).

For every parameter set, `addInitialStopsForParamSet()`:

1. applies only the configured person-attribute and stop filters;
2. finds stops inside the configured radius;
3. calls the feeder `RoutingModule` from origin to stop for ACCESS, or stop to destination for EGRESS;
4. skips a feeder only if that routing call returns `null`;
5. optionally adds walk transfer elements needed by the link/stop setup;
6. computes disutility and creates an `InitialStop` carrying those plan elements.

These operations are at `DefaultRaptorStopFinder.java:165-273`.

### What the selector knows

The stop finder receives both endpoint `Facility` objects, the person, direction, and routing attributes. However, the `Facility` API exposes id, coordinate, custom attributes, and link id—not an activity type (`matsim-2026.0-2025w19-sources.jar!/org/matsim/facilities/Facility.java:32-37`). `FacilitiesUtils.toFacility(Activity, ...)` may wrap an activity, but the private wrapper likewise exposes only facility properties, not activity type (`.../facilities/FacilitiesUtils.java:149-157` and `.../facilities/ActivityWrapperFacility.java:36-66`). `DefaultRaptorStopFinder` never attempts to inspect either substantive activity type. Its optional person filter is person-global and symmetrical, so it cannot express “this person's motorcycle is allowed only on the home end of this particular trip.”

`TripStructureUtils.Trip.getTripAttributes()` returns the substantive origin activity's attributes (`.../core/router/TripStructureUtils.java:425-430`). `PlanRouter` already forwards those attributes to `TripRouter`, and DMC does the same. This is the clean data channel through which a Jakarta extension can expose both endpoint types without depending on private facility wrappers.

### Independent sides, joint least-cost route

Access and egress **candidate generation** is side-independent: two calls, two directions, the same mode parameter sets. For `CalcLeastCostModePerStop`, `SwissRailRaptorCore` first retains the least access-cost candidate for each stop and handles egress candidates by stop, then evaluates total access + transit + egress cost (`matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorCore.java:114-145,940-981`). Therefore it is slightly imprecise to say that two final modes are chosen in complete isolation. The candidate pools and per-stop mode choices are independent by side, while the final route is chosen jointly on total generalized cost. Nothing couples either side to home purpose.

Accordingly, with the audited intermodal configuration, motorcycle is globally eligible on both sides whenever:

- its parameter set passes any person/stop filters, and
- the motorcycle feeder `RoutingModule` returns a route.

There is no current routing-level home-side availability rule.

## B2. Call flow: a DMC PT candidate to the selected routed plan

```text
replanning event in an iteration after firstIteration
  -> strategy selection chooses DiscreteModeChoice
     -> DiscreteModeChoiceAlgorithm.run(plan)
        -> TripListConverter.convert(plan)
        -> ModeChoiceModel.chooseModes(...)
           [trip-based route]
           -> TripBasedModel.generate candidates
              -> validateBeforeEstimation(...)
              -> AbstractTripRouterEstimator.estimateTrip(pt, trip, ...)
                 -> TripRouter.calcRoute("pt", substantive facilities,
                                         person, tripAttributes)
                    -> SwissRailRaptorRoutingModule
                    -> SwissRailRaptor
                    -> DefaultRaptorStopFinder for ACCESS and EGRESS
                    -> routed intermodal candidate
              -> utility must be finite
              -> validateAfterEstimation(...)
                 -> NoMotorcycleEgressExceptHome, if configured

           [usual tour-based Jakarta route]
           -> TourBasedModel generates a tour candidate
              -> CumulativeTourEstimator
                 -> AbstractTripRouterEstimator.estimateTrip(...) per trip
                 -> same TripRouter -> SwissRailRaptor call as above
              -> utility must be finite
              -> TourFromTripConstraint.validateAfterEstimation(...)
                 -> configured TripConstraints, including home-side constraint

        -> selected RoutedTripCandidate.getRoutedPlanElements()
        -> TripRouter.insertTrip(...) for each substantive trip
        -> strategy modules after DMC:
           performReroute=false -> CheckConsistentRoutingReplanningModule
                                  [assert routes exist; no mutation]
           performReroute=true  -> ReRoute -> PlanRouter
                                  [generic re-route; no DMC revalidation]
  -> PrepareForMobsimImpl route-validity check
  -> selected plan enters mobsim
```

### Exact DMC route and constraint calls

`EqasimModeChoiceModule` provides `EqasimUtilityEstimator` with an empty `preroutedModes` set (`core-2.0.0-sources.jar!/org/eqasim/core/simulation/mode_choice/EqasimModeChoiceModule.java:110-130`). Therefore a DMC `pt` candidate is not reused from the current plan: it is routed through `TripRouter`.

`AbstractTripRouterEstimator.estimateTrip()` converts the DMC trip's actual origin and destination activities to facilities and, unless the mode is pre-routed, calls `TripRouter.calcRoute`; it returns `DefaultRoutedTripCandidate` with the resulting elements (`discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/components/estimators/AbstractTripRouterEstimator.java:60-101`).

For a trip-based model, `TripBasedModel` validates before estimation, routes/estimates the candidate, skips non-finite utility, then validates after estimation (`.../model/trip_based/TripBasedModel.java:73-89`). For the eqasim-style cumulative tour model, `CumulativeTourEstimator` estimates each trip (`discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/components/estimators/CumulativeTourEstimator.java:37-61`); `TourBasedModel` checks the resulting candidate and its constraint (`discrete_mode_choice...!/model/tour_based/TourBasedModel.java:86-105`); and `TourFromTripConstraint.validateAfterEstimation()` invokes its configured trip constraints (`.../model/constraints/TourFromTripConstraint.java:48-65`).

The local `NoMotorcycleEgressExceptHome.validateAfterEstimation()` examines only routed PT candidates. It gets routed plan elements, finds the first and last actual legs whose mode is exactly `pt`, scans all access legs before the first PT and all egress legs after the last PT, and rejects the appropriate non-home side (`src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java:67-198`). This is correctly placed as a DMC post-routing validator, but it is not registered with `TripRouter` or RAPTOR and is never called from `PrepareForSimImpl`.

`DiscreteModeChoiceAlgorithm.run()` inserts the exact `RoutedTripCandidate.getRoutedPlanElements()` selected by the model (`discrete_mode_choice...!/replanning/DiscreteModeChoiceAlgorithm.java:46-72`). `DiscreteModeChoiceStrategyProvider` appends MATSim's generic `ReRoute` module only when `performReroute=true`; otherwise it appends `CheckConsistentRoutingReplanningModule` (`.../replanning/DiscreteModeChoiceStrategyProvider.java:59-71`). The consistency module only verifies that every leg has a route (`.../replanning/CheckConsistentRoutingReplanningModule.java:23-40`).

There is also a Jakarta PT-utility guard in `src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java:21-114`. It is DMC-only and weaker structurally: it tests only the leg immediately adjacent to a PT leg, so it misses `motorcycle-walk-pt` and `pt-walk-motorcycle`. It also accepts activity types starting with `home`, while the constraint uses exact case-insensitive equality. It cannot protect startup routing and should not be treated as the primary invariant.

## D. Why the DMC constraint cannot prevent the zero-DMC violations

The two routing invocations share `TripRouter` and SwissRailRaptor but have different callers and validation layers:

| Property | Startup/general plan routing | DMC candidate routing |
|---|---|---|
| Caller | `PrepareForSimImpl` → `PersonPrepareForSim` → `PlanRouter` | DMC estimator → `TripRouter` |
| Timing | Controller startup, before iteration 0 | Replanning iterations only |
| Uses SwissRailRaptor for `pt` | Yes | Yes |
| Has DMC `TripConstraint`s | No | Yes, if configured |
| Invokes `NoMotorcycleEgressExceptHome` | No | Yes, after estimation, if activated |
| Inserts route | `PlanRouter`/`TripRouter.insertTrip` | `DiscreteModeChoiceAlgorithm`/`TripRouter.insertTrip` |
| Affected by `performReroute` | No | Yes, only after DMC insertion |

This is why the zero-DMC result is not paradoxical. The constraint does not fail to reject a candidate it saw; it never sees the startup candidate.

## E. Why `performReroute=false` does not solve startup routing

`performReroute` is read by `DiscreteModeChoiceStrategyProvider` only when assembling the **DMC replanning strategy**. With `false`, the provider follows DMC with a route-consistency check, preserving the routed candidate that DMC validated. With `true`, it follows DMC with MATSim `ReRoute`, whose `ReRoute` module constructs a `PlanRouter` (`matsim-2026.0-2025w19-sources.jar!/org/matsim/core/replanning/modules/ReRoute.java:58-65`) and can replace the validated route without re-invoking DMC constraints.

`PrepareForSimImpl`, by contrast, is a controller service executed before `doIterations()`. It neither reads `performReroute` nor passes through DMC. Thus `false` is the right protection for a DMC-selected route but is outside the call graph that creates iteration-0 intermodal chains.

## F. PT-labelled walk-only routes

A `pt` routing request can normally return only walk legs through either of two source-defined branches:

1. `SwissRailRaptor.calcRoute()` explicitly constructs a direct-walk alternative and may choose it when its weighted cost is lower than the RAPTOR PT path, subject to `intermodalLegOnlyHandling` (`SwissRailRaptor.java:96-119`).
2. If RAPTOR returns `null`, `SwissRailRaptorRoutingModule.calcRoute()` returns its walk-router fallback (`SwissRailRaptorRoutingModule.java:68-74`).

The external config is not in the repository, so the 9,201 records cannot be apportioned between those two branches from source alone. The default `intermodalLegOnlyHandling=forbid` would tend to turn a no-PT RAPTOR answer into `null` and therefore the wrapper's walk fallback, but that must not be asserted for an unseen runtime config.

Whichever branch supplies the walk route, `TripRouter.calcRoute("pt", ...)` stamps `routingMode="pt"` on **every returned leg** (`TripRouter.java:171-178`). Eqasim's population trip writer identifies the exported mode from the routing mode on the first leg (`core-2.0.0-sources.jar!/org/eqasim/core/analysis/trips/TripReaderFromPopulation.java:115-120`; MATSim routing-mode main-mode logic is at `TripStructureUtils.java:611-621`). This explains a record labelled `main_mode=pt` whose physical legs are all `walk`.

This is normal fallback semantics, not a realised transit journey. Excluding routes with no actual `Leg.mode == "pt"` from the realised-PT denominator is correct. The phenomenon is relevant diagnostically because it proves the same `pt` routing request/fallback path ran, but it is not a cause of motorcycle home-side violations.

## G. Ranked routing-level implementation options

### 1. Custom `RaptorStopFinder` plus trip endpoint metadata — recommended

**Mechanism.** Register a `PersonPrepareForSimAlgorithm` that annotates each substantive origin activity's trip-attribute map with namespaced origin and destination activity types. Bind a Jakarta `RaptorStopFinder` that delegates to `DefaultRaptorStopFinder` and removes an `InitialStop` before RAPTOR selection when its package-visible `mode` is motorcycle or any of its feeder plan elements is a motorcycle leg, and:

- direction is ACCESS and annotated origin type is not exactly `home`; or
- direction is EGRESS and annotated destination type is not exactly `home`.

Walk and mcodt candidates pass unchanged. Missing, contradictory, or uninspectable endpoint metadata should fail closed with a diagnostic exception, not silently allow motorcycle.

**Correctness.** High. Filtering happens before stop/path choice, so RAPTOR re-optimizes over the remaining feasible feeders and stops. No selected leg is edited afterward; generalized cost, transit stop choice, time, scoring, and feeder routes remain internally consistent.

**Visibility.** `RaptorStopFinder.findStops(...)` receives `Direction`; routing attributes arrive in the same call. `Trip.getTripAttributes()` provides a stable channel for both endpoint types.

**Coverage.** Both startup/general routing and DMC call the same bound `pt` module and stop finder. It also covers later generic `PlanRouter` reroutes.

**Complexity/risk.** Low-to-medium and lowest behavioral risk because all allowed alternatives retain the calibrated default search and disutility. There is one version-specific implementation issue: `InitialStop` exposes no public getters for its mode/plan elements (`matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/InitialStop.java:33-58`). A small compatibility class in the exact `ch.sbb.matsim.routing.pt.raptor` package can inspect and filter a delegated result. This is a split-package dependency on pinned internals, so it must have an explicit version-lock test. It is nevertheless much smaller and easier to audit under resubmission time pressure than copying the full stop finder.

**PhD resubmission suitability.** Best option for this exact pinned build, provided it is documented as a narrow compatibility bridge and tested at the router boundary.

### 2. Reimplement/derive the default stop-finder algorithm with a pre-generation mode guard

Copy the behavior of `DefaultRaptorStopFinder` into a Jakarta implementation and skip the motorcycle parameter set before `addInitialStopsForParamSet()` on a forbidden side.

- Correctness: high; restriction is even earlier than option 1.
- Endpoint visibility/coverage: same as option 1.
- Complexity: high; approximately the whole default stop-search implementation becomes local maintenance code.
- Risk: more opportunity to drift from calibrated/default RAPTOR behavior and from future dependency changes.
- Suitability: technically clean but less attractive under time pressure.

### 3. Dedicated feeder `RoutingModule` wrapper selected by a custom stop finder

A stop finder could call a motorcycle feeder wrapper that returns `null` when endpoint metadata/direction forbids it. `DefaultRaptorStopFinder` already skips null feeder routes.

- Correctness: high only if direction and substantive endpoint metadata are explicitly carried to the wrapper.
- Endpoint visibility: a plain `RoutingRequest` has no direction enum; inferring direction from stop identity or coordinates is fragile.
- Coverage: both pathways if installed under the shared RAPTOR binding.
- Complexity/risk: medium-to-high due to context propagation and possible thread-safety problems.
- Suitability: inferior to filtering at the interface that already has `Direction`.

### 4. Custom/wrapped PT `RoutingModule`

Wrap `SwissRailRaptorRoutingModule` and run a separately configured RAPTOR instance, or choose among side-specific instances using endpoint metadata.

- Correctness: achievable.
- Endpoint visibility: routing attributes are available.
- Coverage: both pathways if it replaces the `pt` binding.
- Complexity/risk: high; easy to duplicate or diverge from eqasim/MATSim binding, fallback, stage-activity, and parameter behavior.
- Suitability: unnecessary surface area for one feeder-availability rule.

### 5. Custom `RaptorIntermodalAccessEgress`

This extension sees direction while calculating disutility, but not the full substantive trip attributes/facilities needed for a robust purpose rule. Thread-local context or infinite disutility would be needed.

- Correctness: weaker; “infinite cost” is not as clear as removing an infeasible alternative.
- Coverage: potentially both pathways.
- Complexity/risk: high and thread-sensitive.
- Suitability: not recommended.

### 6. Existing person/stop filter configuration or `RaptorParametersForPerson`

These are useful for person-level or stop-level eligibility, not for a rule that changes by trip and by side. A worker with a motorcycle can have both home-origin and work-origin trips; a single person attribute cannot distinguish them.

- Behavioral correctness: insufficient.
- Endpoint visibility: absent.
- Coverage: global but semantically wrong.
- Suitability: not viable for this rule.

### 7. DMC-only, utility-only, or post-routing mutation

The DMC constraint and PT-utility estimator cannot cover startup/general routing. Deleting or converting selected legs after routing leaves the optimized stop/path and cost inconsistent. Output editing and analysis filters do not change behavior. None is a primary fix.

## Recommended minimal robust implementation

Implement option 1 in four narrowly separated responsibilities:

1. Define one Jakarta helper for the canonical rule: `isHome(type)` should match the study definition exactly. At present the constraint uses exact case-insensitive `home`, while `JakartaPTUtilityEstimator` also accepts prefixes; this inconsistency should be removed.
2. Before MATSim's initial route-validation pass, annotate every substantive trip's origin-activity attributes with immutable, namespaced origin and destination types. `PrepareForSimImpl` executes registered `PersonPrepareForSimAlgorithm`s immediately before initial routing (`PrepareForSimImpl.java:178-196`), making this the natural hook. Do this for every plan, because startup preparation routes every plan.
3. Bind a Jakarta `RaptorStopFinder` that delegates to the default finder and filters forbidden motorcycle-containing `InitialStop`s using `Direction` and the two attributes. Check the package-visible mode and scan **all** feeder legs in each initial stop, not only the adjacent leg, to cover motorcycle-plus-walk chains.
4. Fail closed and count/report an error if metadata is absent or feeder elements cannot be inspected. Do not silently fall back to the DMC constraint.

The restriction is then evaluated before the RAPTOR route is selected:

```text
trip endpoint types
  -> routing attributes
     -> RaptorStopFinder(direction)
        -> default candidates
        -> discard only directionally invalid motorcycle candidates
        -> RAPTOR optimizes over valid motorcycle/walk/mcodt alternatives
        -> internally consistent routed trip
```

This changes no costs or behavior for allowed candidates. It only removes infeasible motorcycle alternatives. It therefore has a smaller calibration footprint than a custom PT router or any post-routing substitution.

### Future files/classes to add or modify (not changed by this report)

Suggested names are illustrative; the responsibilities are normative.

| Action | File/class | Purpose |
|---|---|---|
| Add | `src/main/java/org/eqasim/jakarta/routing/HomeSideTripAttributes.java` | Attribute keys and canonical home predicate |
| Add | `src/main/java/org/eqasim/jakarta/routing/AnnotateHomeSideTripAttributes.java` | `PersonPrepareForSimAlgorithm` that annotates every substantive trip |
| Add | `src/main/java/ch/sbb/matsim/routing/pt/raptor/JakartaHomeSideRaptorStopFinder.java` | Small pinned-version bridge/delegate that filters `InitialStop`s before RAPTOR |
| Add | `src/main/java/org/eqasim/jakarta/routing/JakartaHomeSideRoutingModule.java` | Guice bindings for annotator and `RaptorStopFinder` |
| Modify | `src/main/java/org/eqasim/jakarta/JakartaConfigurator.java` | Register the routing module |
| Modify | `src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java` | Reuse canonical home predicate; preferably use `RoutedTripCandidate.getRoutedPlanElements()` directly rather than reflection |
| Modify | `src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java` | Reuse canonical predicate or remove reliance on its incomplete adjacency guard |
| Verify external config | Actual run config | Constraint name activated; tour wrapper activated if required; `performReroute=false` retained |
| Add | `src/test/java/...` | Unit and integration tests below |

If split-package policy is unacceptable, replace the small bridge with option 2 and document the copied upstream version. Do not use Java reflection against `InitialStop` in production routing; a compile-time-pinned bridge fails more visibly on upgrades.

## H. Role of `NoMotorcycleEgressExceptHome` after the routing fix

The proposed layered policy is sensible:

1. **Routing-level home-side restriction:** authoritative feasibility rule for every `pt` routing call.
2. **`NoMotorcycleEgressExceptHome`:** defensive DMC invariant after candidate routing.
3. **`performReroute=false`:** prevents a generic, unvalidated `PlanRouter` pass after DMC selection.
4. **Post-run zero-violation QC:** detects regression, missing metadata, configuration mistakes, or new routing paths.

Keep the constraint. It is cheap insurance against a future binding mistake or an alternative DMC router. It should use the same canonical `home` predicate as the routing filter and continue scanning all access/egress legs around the first/last actual PT leg. It should fail—not allow—an uninspectable routed PT candidate so that the `uninspectable routed PT candidates = 0` requirement is enforceable.

The current reflection in `extractElements()` is unnecessary for the inspected DMC API because `RoutedTripCandidate` exposes `getRoutedPlanElements()` (`discrete_mode_choice...!/model/trip_based/candidates/RoutedTripCandidate.java:12-14`). A future implementation should make that contract direct and testable. This is a hardening change, not the fix for startup routing.

## I. Proposed tests before any simulation

### Constraint unit tests

Construct routed candidates containing real leg modes and any intervening stage activities. Assert:

| Case | Candidate | Expected |
|---|---|---|
| 1 | `home -> motorcycle -> walk -> pt -> mcodt -> work` | ALLOWED |
| 2 | `work -> motorcycle -> walk -> pt -> mcodt -> home` | FORBIDDEN: motorcycle on access from non-home |
| 3 | `home -> mcodt -> pt -> walk -> motorcycle -> work` | FORBIDDEN: motorcycle on egress toward non-home |
| 4 | `work -> mcodt -> pt -> walk -> motorcycle -> home` | ALLOWED |
| 5 | `home -> motorcycle -> pt -> motorcycle -> work` | FORBIDDEN: egress motorcycle toward non-home |
| 6 | `work -> motorcycle -> pt -> motorcycle -> home` | FORBIDDEN: access motorcycle from non-home |
| 7 | walk and mcodt on either/both sides, for all endpoint combinations | ALLOWED |

Add cases for:

- multiple PT legs and transfer walks, proving the first/last actual PT boundary is used;
- a candidate with routing mode `pt` but no actual PT leg (walk-only fallback), classified separately from realised PT;
- null/missing routed elements, which must be rejected/raise a diagnostic rather than pass;
- case and exactness of the canonical home predicate;
- consistency between the routing filter, DMC constraint, and any remaining utility guard.

### Stop-finder/filter unit tests

Use a deterministic fake delegate producing `InitialStop`s with known feeder plan elements and costs. Test ACCESS and EGRESS independently for all four endpoint combinations:

| Origin → destination | Access MC | Egress MC |
|---|---:|---:|
| home → home | keep | keep |
| home → non-home | keep | remove |
| non-home → home | remove | keep |
| non-home → non-home | remove | remove |

Also assert:

- a feeder containing `motorcycle -> walk` is treated as motorcycle-bearing;
- `walk`, `mcodt`, and mixed non-motorcycle feeders remain byte-for-byte unchanged;
- the lowest-cost invalid motorcycle stop is removed and the next valid alternative remains available;
- missing endpoint attributes fail closed with person/trip/direction context;
- the pinned `InitialStop` compatibility bridge still compiles and inspects the intended fields.

### Trip-attribute annotation unit tests

- Every plan and every substantive trip receives origin and destination types on the origin activity's trip attributes.
- Stage activities are not mistaken for substantive endpoints.
- Existing unrelated activity attributes are preserved.
- Repeated preparation is idempotent.
- Copied/replanned plans retain the attributes used by DMC.

### Integration/smoke tests

These should use tiny synthetic networks/schedules and should run before any Jakarta production simulation:

1. **Startup routing test:** load a direct `activity -> Leg(pt, route=null) -> activity` plan, invoke the actual `PrepareForSimImpl` stack, and prove it expands the route while enforcing the four endpoint combinations above.
2. **Whole-plan trigger test:** make one plan element require rerouting and verify every trip routed by `PlanRouter` still receives correct endpoint metadata.
3. **DMC routing test:** generate a DMC PT candidate through the actual `AbstractTripRouterEstimator`; prove it uses the same stop-finder restriction and the defensive constraint agrees.
4. **Cost dominance test:** make motorcycle deliberately cheapest on both sides. Confirm forbidden candidates are absent—not merely outscored—and allowed home-side motorcycle remains selected.
5. **Feeder preservation test:** prove walk and mcodt candidates and their generalized costs are unchanged by the filter.
6. **Walk-only fallback test:** make PT infeasible, verify the `pt` request may return walk with routing mode `pt`, and ensure realised-PT analysis requires an actual PT leg.
7. **Lifecycle test:** run only a tiny iteration-0 synthetic controller. Assert startup preparation, pre-mobsim preparation, and selected-plan consumption do not reintroduce invalid motorcycle.
8. **Reduced Jakarta smoke test:** on a documented small deterministic population subset, compare input, post-`PrepareForSim`, post-DMC, and output chains and emit side-specific counters.

Before a full simulation, inspect the external config dump and assert programmatically that:

- the intermodal feeder modes and selection setting are the intended ones;
- the DMC constraint list actually contains `NoMotorcycleEgressExceptHome`;
- `FromTripBased` is present if the active model is tour-based;
- `performReroute=false`;
- the Jakarta `RaptorStopFinder` binding is the active binding.

### Hard post-run acceptance criteria

For realised PT trips, where realised means at least one actual leg with `mode == "pt"`:

```text
motorcycle on PT access side AND substantive origin != home       = 0
motorcycle on PT egress side AND substantive destination != home = 0
uninspectable routed PT candidates                                = 0
valid home-side motorcycle feeders                                > 0
walk feeders                                                      > 0
mcodt feeders                                                     > 0
```

The QC must scan **all** legs before the first actual PT leg and after the last actual PT leg, mirroring the routing rule and DMC invariant. PT-labelled walk-only fallbacks must be reported separately and excluded from the realised-PT denominator.

## Appendix: exact source map

All dependency references below are to the source JAR versions listed in the provenance table, not to current upstream source.

| Responsibility | Exact class/method and source location |
|---|---|
| Jakarta entry and external config load | `RunSimulation.main`; `src/main/java/org/eqasim/jakarta/RunSimulation.java:37-53,71-98` |
| Jakarta module registration | `JakartaConfigurator.<init>`; `src/main/java/org/eqasim/jakarta/JakartaConfigurator.java:7-12` |
| Jakarta DMC constraint/estimator bindings | `JakartaModeChoiceModule.install`; `src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java:89-106` |
| Jakarta DMC constraint logic | `NoMotorcycleEgressExceptHome.validateAfterEstimation/extractElements`; `src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java:67-233` |
| Jakarta DMC utility guard | `JakartaPTUtilityEstimator.isHomeType/hasIllegalMotorcycleAccess/hasIllegalMotorcycleEgress/estimateUtility`; `src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java:51-114` |
| Repository config helper's trip-constraint list | `RunAdaptConfig.main`; `src/main/java/org/eqasim/jakarta/scenario/RunAdaptConfig.java:78-84` |
| Eqasim modules/configuration | `EqasimConfigurator.<init>/configureController`; `core-2.0.0-sources.jar!/org/eqasim/core/simulation/EqasimConfigurator.java:71-86,256-270` |
| Population parsing | `ScenarioLoaderImpl.loadScenario/loadPopulation`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/scenario/ScenarioLoaderImpl.java:108-123,193-205` |
| Startup precedes iterations | `AbstractController.run`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/AbstractController.java:75-85` |
| Iteration-0 replanning condition | `AbstractController.iteration`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/AbstractController.java:138-157` |
| Default first iteration | `ControllerConfigGroup`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/config/groups/ControllerConfigGroup.java:90,135` |
| Pre-mobsim preparation call | `AbstractController.mobsim`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/AbstractController.java:188-208` |
| Prepare bindings/delegation | `NewControlerModule.install` and `NewControler.prepareForSim/prepareForMobsim`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/NewControlerModule.java:43-47`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/NewControler.java:113-125` |
| Initial route preparation | `PrepareForSimImpl.run/adaptOutdatedPlansForRoutingMode`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/PrepareForSimImpl.java:167-196,336-390` |
| Route-validity trigger | `PersonPrepareForSim.run/needsReRoute`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/population/algorithms/PersonPrepareForSim.java:104-170` |
| Whole-plan routing | `PlanRouter.run`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/PlanRouter.java:74-97` |
| Routing dispatch and routing-mode stamp | `TripRouter.calcRoute`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripRouter.java:146-179` |
| Physical trip replacement | `TripRouter.insertTrip`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripRouter.java:207-270` |
| Repeated pre-mobsim validity pass | `PrepareForMobsimImpl.run`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/controler/PrepareForMobsimImpl.java:75-107` |
| Selected plan enters QSim | `PopulationAgentSource.insertAgentsIntoMobsim` and `DefaultAgentFactory.createMobsimAgentFromPerson`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/mobsim/qsim/agents/PopulationAgentSource.java:71-80`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/mobsim/qsim/agents/DefaultAgentFactory.java:46-48` |
| Default transit binding suppression | `TripRouterModule.install`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripRouterModule.java:71-76` |
| SwissRailRaptor bindings | `SwissRailRaptorModule.install`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorModule.java:39-77` |
| PT module/fallback/stage activities | `SwissRailRaptorRoutingModule.calcRoute/fillWithActivities`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorRoutingModule.java:68-95` |
| RAPTOR top-level route and side calls | `SwissRailRaptor.calcRoute/findAccessStops/findEgressStops`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptor.java:81-124,292-310` |
| Stop-finder extension interface | `RaptorStopFinder.findStops`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/RaptorStopFinder.java:33-38` |
| Default intermodal mode candidate creation | `DefaultRaptorStopFinder.findStops/findIntermodalStops/addInitialStopsForParamSet`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/DefaultRaptorStopFinder.java:88-273` |
| Intermodal config/available filters | `SwissRailRaptorConfigGroup` and `IntermodalAccessEgressParameterSet`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/config/SwissRailRaptorConfigGroup.java:70-109,495-650` |
| Candidate holder visibility | `InitialStop`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/InitialStop.java:33-58` |
| Joint least-cost path | `SwissRailRaptorCore.calcLeastCostRoute`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/SwissRailRaptorCore.java:114-145,940-981` |
| RAPTOR result-to-leg conversion | `RaptorUtils.convertRouteToLegs`; `matsim-2026.0-2025w19-sources.jar!/ch/sbb/matsim/routing/pt/raptor/RaptorUtils.java:139-198` |
| Facility type is unavailable | `Facility`, `FacilitiesUtils.toFacility`, `ActivityWrapperFacility`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/facilities/Facility.java:32-37`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/facilities/FacilitiesUtils.java:149-157`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/facilities/ActivityWrapperFacility.java:36-66` |
| Trip-attribute transport channel | `TripStructureUtils.Trip.getTripAttributes`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripStructureUtils.java:425-430` |
| Routing-mode main-mode semantics | `TripStructureUtils.identifyMainMode`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/router/TripStructureUtils.java:598-621` |
| DMC factory activation | `ConstraintModule.provideTripConstraintFactory`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/modules/ConstraintModule.java:107-116` |
| DMC PT candidate routing | `AbstractTripRouterEstimator.estimateTrip/estimateTripCandidate`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/components/estimators/AbstractTripRouterEstimator.java:57-102` |
| DMC trip-based validation order | `TripBasedModel`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/model/trip_based/TripBasedModel.java:73-89` |
| DMC tour estimation | `CumulativeTourEstimator.estimateTour`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/components/estimators/CumulativeTourEstimator.java:36-62` |
| DMC tour validation order | `TourBasedModel`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/model/tour_based/TourBasedModel.java:86-105` |
| Trip constraints inside a tour | `TourFromTripConstraint.validateAfterEstimation`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/model/constraints/TourFromTripConstraint.java:48-65` |
| DMC chosen-route insertion | `DiscreteModeChoiceAlgorithm.run`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/replanning/DiscreteModeChoiceAlgorithm.java:46-72` |
| `performReroute` branch | `DiscreteModeChoiceStrategyProvider.get`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/replanning/DiscreteModeChoiceStrategyProvider.java:59-71` |
| False-branch consistency only | `CheckConsistentRoutingReplanningModule`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/replanning/CheckConsistentRoutingReplanningModule.java:23-40` |
| True-branch generic router | `ReRoute`; `matsim-2026.0-2025w19-sources.jar!/org/matsim/core/replanning/modules/ReRoute.java:58-65` |
| Routed-candidate public accessor | `RoutedTripCandidate.getRoutedPlanElements`; `discrete_mode_choice-2026.0-2025w19-sources.jar!/org/matsim/contribs/discrete_mode_choice/model/trip_based/candidates/RoutedTripCandidate.java:12-14` |
| Eqasim exported trip mode | `TripReaderFromPopulation`; `core-2.0.0-sources.jar!/org/eqasim/core/analysis/trips/TripReaderFromPopulation.java:115-120` |

## Final forensic conclusion

The control experiment and the pinned source agree exactly. Clean direct-PT plans are not structurally expanded while parsing the population. They are expanded when controller startup preparation determines that a plan requires routing, then sends every trip in that plan through `PlanRouter`. For routing mode `pt`, the installed module is SwissRailRaptor. Its default intermodal stop finder makes each configured feeder mode available in both directions and has no substantive activity-purpose rule, so a least-cost motorcycle feeder can be selected on either side. This pathway runs before iteration 0 and has no access to DMC constraints.

The correct intervention is therefore not another post-routing check. It is a trip-aware, direction-aware feasibility filter at the RAPTOR access/egress candidate boundary, shared by initial/general routing and DMC routing. Retaining the strengthened DMC constraint, `performReroute=false`, and strict post-run QC provides a coherent four-layer defense without changing valid walk, mcodt, or home-side motorcycle behavior.
