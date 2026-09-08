# Jakarta commuter/non-commuter 3.0.0 forensic audit and architecture design

Audit date: 2026-09-07 (Asia/Jakarta). **AUDIT ONLY — no implementation or release approval.**

Repository: `/Users/fazafawzan/git/eqasim-java`. External files: `/Users/fazafawzan/Codex/matsim_jar_rev`.

## 1. Executive verdict

**The endpoint utility functional forms genuinely differ. Changing only coefficients in the existing 2.8.0 estimators cannot reproduce the legacy reference.** Differences include walk age versus distance, car age/distance additions, motorcycle age removal and cost-distance interaction removal, carodt cost-distance interaction removal, PT ASC/income/employment/age/waiting treatment, and active legacy PT latent-class branching. Some other textual changes have no numerical effect because predictors supply zero.

Recommend one future JAR based on 2.8.0, containing **separate, externally parameterised commuter and approved non-commuter formula paths**, selected through narrow per-mode delegating estimators. Share routing, feasibility, person/trip feature extraction where safe, and monetary policy calculations. Load distinct behavioural objects and one shared policy object. A common enlarged superset formula is mathematically possible, but that is a new implementation with mode-specific interactions and structural switches, not a coefficient-only use of current estimators.

The research hypothesis is **partially verified**: source history explicitly documents alignment with revised MNL/R functions, and 2.8.0 contains those revisions. It does **not** prove their estimation sample was commuters, or that the old specification was estimated for daily non-commuters. The baseline actually selects `MultinomialLogit`; no commuter/non-commuter NL nesting is implemented here. Legacy PT has a generic branch labelled non-commuters, but that label alone cannot establish scientific validity. Those interpretations require researcher approval.

**No historical Jakarta behavioural YAML was found** in the inspected reachable Git history or supplied external files. Legacy Java defaults are the strongest available parameter reference, **not proven historical runtime calibration**. The existing loader permitted YAML and command-line overrides even on the legacy branch.

Critical release-design findings:

- Legacy PT recognises `non_commuters` (plural) or a missing attribute, plus four class labels. Both new labels, `commuter` and `non_commuter`, would throw in that old PT estimator. Current PT reads the attribute but does not use it.
- 2.8.0 car, PT and mcodt still multiply monetary utility by Euclidean-distance interaction **and**, for these current paths, income interaction. Motorcycle and carodt use income interaction only. Comments about R alignment do not remove inherited terms.
- Legacy generic PT uses `jPT.generic.constant` (default 0), **not** `pt.alpha_u` (default -3.5), and returns before the income multiplier. Legacy PT feeder time is omitted and feeder fare is not added to PT cost, although a feeder-derived discount can be subtracted.
- The supplied baseline XML references `input/UtilityParams_FLM_rev_260_calib3.yml`, not the supplied `UtilityParams_BaselineModel.yml`. It still references `jakarta_population.xml.gz`. Runtime loading of the supplied file/new population is not established by the XML.
- Only DMC weight 0.1 and KeepLastSelected weight 0.9 are active strategy settings, both for null subpopulation. Explicit commuter/non-commuter populations need matching settings; null is not a wildcard.
- `scen_mcOdt_25pct` changes **mcodt-as-PT-feeder cost**, not standalone mcodt fares. Its discount is `min(0.25 × feeder cost, 5.0 KIDR)` for valid finite nonnegative inputs. The cap is finite.
- Behavioural and policy fields currently coexist in `JakartaModeParameters`: `jPT.odt.*` are policy-cost fields despite living in a utility YAML. Blindly duplicating that object per group risks unequal policy treatment.

The counted difference register in §9 contains **26 grouped findings: A=10, B=3, C=5, D=3, REQUIRES DECISION=5**. The approval register in §25 has **12 distinct REQUIRES RESEARCHER DECISION items**. Repeated references in other tables do not increase either count.

## 2. Exact branch and commit provenance

| Reference | Exact commit | Role |
|---|---|---|
| `jakarta_FLM_home_side_router_2.8.0` | `8cca88c75300a1dcceaf38b36a8d40ef99d17ca9` | Current commuter-lineage reference; future production base |
| `jakarta_scenario_mcOdtFeeder` | `2dfb68d26928569357aee3364fe83e65f1e2a203` | Read-only historical reference; POM version at this endpoint is 2.4.0 |
| HEAD during audit | `8cca88c75300a1dcceaf38b36a8d40ef99d17ca9` | Tracked source working tree had no changes |

In this report **C** means the exact 2.8.0 commit above, **L** means the exact legacy commit above. Classification C in a classification column instead means routing/feasibility infrastructure.

Initial pre-existing untracked files were `.DS_Store` and `jakarta/commuter_noncommuter_3_0_0_forensic_audit_v1_wrong_in_pricing_interpretation.md`. Neither was modified or used as authority. No branch/worktree was created. No source/config/population/POM changes, builds, simulations, calibration, staging, commits or pushes were performed. The only created file is this report.

## 3. Merge-base and lineage

`git merge-base C L` returned `2dfb68d26928569357aee3364fe83e65f1e2a203`.
`git merge-base --is-ancestor L C` returned exit status 0. **Legacy is an ancestor of 2.8.0.** There is no need to merge the old branch to access its content; its code can be examined with `git show L:path`.

The comparison used endpoint source reads, `git diff L C`, `git log`, `git grep`, and all-reachable history/path searches. Endpoint diff comprises 30 files, including utilities, parameters, PT predictor, motorcycle cost model, routing/constraints, tests, documentation, POM and housekeeping. Most non-PT predictors and cost models are unchanged at the endpoints.

Dependency provenance matters: both Jakarta POMs depend on **`org.eqasim:core:2.0.0`** and MATSim **`2026.0-2025w19`**. The repository's sibling `core/` source still contains older `ch.ethz.matsim.discrete_mode_choice` imports; it is not automatically the Jakarta dependency implementation. The audit therefore also read the locally cached dependency **source JARs**, without extracting files or executing the model:

- `/Users/fazafawzan/.m2/repository/org/eqasim/core/2.0.0/core-2.0.0-sources.jar`
- `/Users/fazafawzan/.m2/repository/org/matsim/matsim/2026.0-2025w19/matsim-2026.0-2025w19-sources.jar`

Inherited equations, multi-stage car prediction, parameter reflection, MATSim subpopulation API and strategy failure behaviour below refer to those pinned dependency sources. This is source/dependency evidence, not a claim that an unspecified historical shaded JAR has been executed or authenticated.

## 4. Parameter-loading call path and precedence

### 4.1 Entry point and bindings, both branches

`RunSimulation.main` constructs `CommandLine` allowing `mode-parameter` and `cost-parameter` prefixes, loads the config, invokes `JakartaConfigurator.updateConfig`, and applies command-line configuration with `cmd.applyConfiguration(config)`. It then sets six mode estimator names to `jCarEstimator`, `jPTEstimator`, `jWalkEstimator`, `jMotorcycleEstimator`, `jMcodtEstimator`, and `jCarodtEstimator`. Therefore the XML class-name-style entries are not the final aliases used by this entry point.

`JakartaConfigurator` registers `JakartaModeChoiceModule`; `RunSimulation` also adds it as an overriding module near the end. `installEqasimExtension` binds the named utility estimators, cost models, Jakarta predictors and constraints; it binds `ModeParameters.class` to `JakartaModeParameters.class`. The final injector's provider is `@Provides @Singleton provideModeChoiceParameters(EqasimConfigGroup)`:

```text
parameters = JakartaModeParameters.buildDefault()
if config.getModeParametersPath() != null:
    ParameterDefinition.applyFile(new File(config.getModeParametersPath()), parameters)
ParameterDefinition.applyCommandLine("mode-parameter", commandLine, parameters)
return parameters
```

This provider body is identical in L and C. The cost provider has the same sequence using `JakartaCostParameters`, `costParametersPath`, and prefix `cost-parameter`.

**Exact precedence: Java field initialisers + buildDefault assignments < supplied YAML < command-line field overrides.** Example existing syntax is `--mode-parameter:car.alpha_u VALUE`; it is a colon-prefixed option family, not an arbitrary free-form `--mode-parameter` payload.

All six Jakarta mode estimators receive the same singleton Jakarta behavioural object. The base `ModeParameters` link supplies that same object to inherited estimators and applicable predictors, including bike. `JakartaPtPredictor` is separately singleton-scoped and receives the same object. Cost models receive the separate singleton `JakartaCostParameters`.

**Exception to the shared-parameter story:** `JakartaPersonPredictor.predict` calls `JakartaModeParameters.buildDefault()` anew, rather than using the injected singleton. `JakartaPredictorUtils.hhlIncome` uses that fresh default reference income (5327) if the attribute is absent. Thus externally changing reference income does not change this missing-income fallback. This is present in both branches and must be addressed explicitly in a future design; it must not be mistaken for YAML precedence working everywhere.

### 4.2 Resolution, parsing and failure semantics

`EqasimConfigGroup.getModeParametersPath` returns the stored string; its setter does not resolve paths. `provideModeChoiceParameters` uses `new File(path)`, not MATSim's config-context URL resolver. A relative `input/...` path is therefore relative to the **process working directory**, not automatically the directory holding the XML. `costParametersPath` behaves the same. MATSim `ReflectiveConfigGroup.fromString` converts XML string `"null"` to Java null. With null path the file step is skipped; with a non-null missing path reading fails, rather than silently falling back to defaults.

`ParameterDefinition.applyFile` reads YAML through Jackson into **`HashMap<String,String>`**, then `applyMap` splits each key on `.` and traverses existing public fields with reflection (`getField`). A key such as `jPT.odt.per_km_mcodt` reaches the existing `JakartaPTParameters.ODT` object and sets its numeric leaf. Public nested objects must already be initialised; this loader does not construct a nested object graph from ordinary YAML mappings.

- Missing keys leave Java defaults unchanged; there is no required-field completeness validation.
- Ordinary indented nested YAML maps are **not supported by this flat string-map loader**. A proposed nested object architecture can use flat dotted keys, e.g. `commuter.car.alpha_u`, or add a separately specified flattening/typed loader later.
- Unknown keys throw `IllegalStateException("Parameter ... does not exist")`; they are not ignored. Commented YAML lines are not keys.
- Numeric conversion uses Java parsers. Malformed types/values fail; no domain validation enforces positive reference income, valid subsidy range, nonnegative cap, or finite coefficients. Object-valued leaves are unsupported. The pinned 2.0.0 loader supports booleans as well as numbers/strings/enums; the sibling repository core version lacks that boolean branch.
- Loading is a startup operation. External calibration means changing files and starting a new run without rebuilding; it does not mean safely hot-reloading a running simulation.

For two groups, create independently initialised, externally overlaid objects and never mutate a shared singleton on each person call. Qualifiers or an explicit registry are required to prevent Guice injecting one object into both paths by accident. Keep policy fields outside either behavioural object, or make both paths reference exactly the same shared policy instance.

## 5. Historical YAML and legacy parameter provenance

Searches covered both branch trees, all reachable YAML/YML paths (`git log --all --name-only -- '*.yml' '*.yaml' '*UtilityParams*'`), `git rev-list --objects --all` path inventory, Jakarta history containing parameter-file references (`git log --all -G ...`), legacy `git grep` for `modeParametersPath`, `UtilityParams`, and `mode-parameter`, and the supplied external config/scripts/documentation inventory.

Results:

- No Jakarta behavioural/calibration YAML exists in the inspected reachable Git history. YAML history entries are CI/Dependabot/Travis files, not calibration files. No tracked Jakarta scenario XML was found; the relevant tracked XML is its POM.
- Legacy Jakarta matches establish the loader and CLI support, not a named historical YAML. The available external YAMLs document revised MNL/calibration iterations; none establishes a run of L. The external scripts directory contains `add_matsim_subpopulation.py`, not a historical run launcher.
- No evidence proves the old model **normally** relied on defaults. No evidence proves a particular external YAML overrode them in a historical run. If supplied, the code would have applied it, then CLI overrides.
- Consequently **legacy Java defaults are the strongest available parameter reference**. There is no historical YAML-to-default numerical comparison to perform. Treat §6–8 legacy defaults as a reproducible source reference, not a recovered runtime calibration.
- The current XML's `input/UtilityParams_FLM_rev_260_calib3.yml` was not found in the two supplied project folder inventories. `UtilityParams_BaselineModel.yml` may be intended as its renamed counterpart, but equivalence needs confirmation or a future explicit path correction.

Search scope is reachable local refs and supplied local files, not an unavailable HPC filesystem, deleted/unreachable objects, private estimation material, or an unknown original launch command. Those absences cannot prove no YAML ever existed.

## 6. Baseline YAML → Java → utility mapping

All **37 active YAML fields** are enumerated below. Every listed key is accepted by C's parameter object and overlays its Java default if this file is actually selected; equality of values still counts as an override. `inactive` means loaded but not consumed by the final C utility, not ignored by the loader. `zero` means a live expression currently multiplies by zero.

Object notation: `P` = singleton `JakartaModeParameters`; dotted YAML keys are exactly the field paths below. `car`, `pt`, `walk`, `bike` are inherited `ModeParameters` nested objects; `jCar`, `jPT`, `jWalk`, `jMotorcycle`, `jMcodt`, `jCarodt`, `jIncomeElasticity`, `jAvgHHLIncome` are Jakarta nested objects. `jPT.odt` is the ODT policy object. Defaults below mean **after buildDefault**, not just declaration initialisers.

Method notation: Car/PT protected methods are in pinned core `CarUtilityEstimator`/`PtUtilityEstimator`; Walk/Bike inherited methods are in their pinned core estimators; MC/MO/CO mean JakartaMotorcycle/Mcodt/CarodtUtilityEstimator. Their caller is each Jakarta `estimateUtility`. All symbols and predictor scaling are defined in §7.

| YAML key → Java field | L default | C default | Baseline YAML (overrides: YES) | Estimator/predictor → term | Units/scaling | C runtime status |
|---|---|---|---|---|---|---|
| `betaCost_u_MU` → `P.betaCost_u_MU` | -0.0208 | -0.0208 | -0.019 | Car/PT/MO estimateMonetaryCostUtility: b·D·C; MC/CO: b·C; income multiplied by caller | utility/KIDR | active |
| `lambdaCostEuclideanDistance` → `P.lambdaCostEuclideanDistance` | -0.75 | -0.75 | -0.75 | EstimatorUtils.interaction exponent in Car/PT/MO monetary methods | dimensionless | active; MC/CO no longer consume |
| `referenceEuclideanDistance_km` → `P.referenceEuclideanDistance_km` | 7.67 | 7.67 | 7.67 | EstimatorUtils.interaction reference d0 in Car/PT/MO | km | active |
| `jIncomeElasticity.lambda_income` → `P.jIncomeElasticity.lambda_income` | -0.06 | -0.06 | -0.06 | Jakarta paid-mode estimateUtility: H exponent | dimensionless | active |
| `jAvgHHLIncome.avg_hhl_income` → `P.jAvgHHLIncome.avg_hhl_income` | 5327 | 5327 | 5327 | Jakarta paid-mode estimateUtility: H denominator; person fallback separately uses fresh default | same unit as hhlIncome | active |
| `car.alpha_u` → `P.car.alpha_u` | -0.5 | -0.5 | -1.370 | Car estimateConstantUtility: ASC | utility | active |
| `car.betaTravelTime_u_min` → `P.car.betaTravelTime_u_min` | -0.0124 | -0.0124 | -0.054 | Car estimateTravelTimeUtility: Tcar including parking-search minutes | utility/min | active |
| `jCar.betaTravelDistance_km` → `P.jCar.betaTravelDistance_km` | absent | 0 | 0.102 | JakartaCarUtilityEstimator.estimateUtility: Euclidean d | utility/km | active |
| `jCar.alpha_age` → `P.jCar.alpha_age` | absent | 0 | 0.026 | JakartaCarUtilityEstimator.estimateUtility: age | utility/year | active |
| `pt.alpha_u` → `P.pt.alpha_u` | -3.5 (not called by legacy generic/class PT) | -3.5 | -1.100 | PT estimateConstantUtility: ASC | utility | active |
| `pt.betaLineSwitch_u` → `P.pt.betaLineSwitch_u` | 0 (not called by legacy PT) | 0 | 0.0 | PT estimateLineSwitchUtility: number of switches; caller omitted | utility/switch | inactive |
| `pt.betaInVehicleTime_u_min` → `P.pt.betaInVehicleTime_u_min` | -0.0149 | -0.0149 | -0.013 | PT estimateInVehicleTimeUtility: IVT | utility/min | active |
| `pt.betaWaitingTime_u_min` → `P.pt.betaWaitingTime_u_min` | -0.0149 | -0.0149 | -0.05 | PT estimateWaitingTimeUtility: W; caller omitted in C | utility/min | inactive |
| `pt.betaAccessEgressTime_u_min` → `P.pt.betaAccessEgressTime_u_min` | -0.0149 | -0.0149 | -0.052 | PT estimateAccessEgressTimeUtility: A from JakartaPtPredictor | utility/min | active |
| `jPT.alpha_age` → `P.jPT.alpha_age` | 0 (unused helper) | -0.031 | -0.031 | JakartaPTUtilityEstimator.estimateUtility: continuous age | utility/year | active |
| `jPT.alpha_fulltime` → `P.jPT.alpha_fulltime` | absent | 0.808 | 0.808 | JakartaPTUtilityEstimator.estimateUtility: employment==1 | utility/indicator | active; employment proxy |
| `jPT.odt.base_mcodt` → `P.jPT.odt.base_mcodt` | 0 | 0 | 0 | JakartaPtPredictor.predict: F flat fare × eligible feeder leg count | KIDR/feeder leg | policy; zero baseline base |
| `jPT.odt.per_km_mcodt` → `P.jPT.odt.per_km_mcodt` | 0 | 0 | 1.9 | JakartaPtPredictor.predict: F distance fare × eligible route km | KIDR/km | policy; active |
| `jPT.odt.subsidyShare_mcodt` → `P.jPT.odt.subsidyShare_mcodt` | 0 | 0 | 0 | JakartaPtPredictor.predict: discount=min(s·F,cap) | fraction | policy; zero baseline share |
| `jPT.odt.maxDiscountMU_mcodt` → `P.jPT.odt.maxDiscountMU_mcodt` | +Infinity | +Infinity | 5.0 | JakartaPtPredictor.predict: aggregate discount ceiling | KIDR/PT candidate | policy; finite YAML cap |
| `bike.alpha_u` → `P.bike.alpha_u` | -4.44 | -4.44 | -4.44 | BikeUtilityEstimator.estimateConstantUtility: ASC | utility | bound; bike absent from Jakarta availability |
| `bike.betaTravelTime_u_min` → `P.bike.betaTravelTime_u_min` | -0.0905 | -0.0905 | -0.0905 | BikeUtilityEstimator.estimateTravelTimeUtility: bike leg minutes | utility/min | bound; bike absent from Jakarta availability |
| `walk.alpha_u` → `P.walk.alpha_u` | -2.5 | -2.5 | -1.070 | WalkUtilityEstimator.estimateConstantUtility: ASC | utility | active |
| `walk.betaTravelTime_u_min` → `P.walk.betaTravelTime_u_min` | -0.0052 | -0.0052 | -0.054 | Walk estimateTravelTimeUtility; L also Car/MC access penalty | utility/min | active walk; C no Car/MC access call |
| `jWalk.betaTravelDistance_km` → `P.jWalk.betaTravelDistance_km` | absent | -0.35 | 0.0 | JakartaWalkUtilityEstimator.estimateUtility: Euclidean d | utility/km | active expression, zero YAML |
| `jCarodt.beta_TravelTime_u_min` → `P.jCarodt.beta_TravelTime_u_min` | -0.0626 | -0.0626 | -0.0826 | CO estimateTravelTimeUtility: leg time/60 | utility/min | active |
| `jCarodt.alpha_u` → `P.jCarodt.alpha_u` | -1.23 | -1.23 | -4.924 | CO estimateConstantUtility: ASC | utility | active |
| `jCarodt.alpha_sex` → `P.jCarodt.alpha_sex` | -0.42 | -0.42 | 0.83 | CO estimateUtility: female indicator (L unsafe reference comparison) | utility/indicator | active |
| `jCarodt.alpha_age` → `P.jCarodt.alpha_age` | -0.0132 | -0.0132 | -0.017 | CO estimateUtility: age | utility/year | active |
| `jMcodt.beta_TravelTime_u_min` → `P.jMcodt.beta_TravelTime_u_min` | -0.0626 | -0.0626 | -0.054 | MO estimateTravelTimeUtility: leg time/60 | utility/min | active |
| `jMcodt.alpha_u` → `P.jMcodt.alpha_u` | -1.15 | -1.15 | -2.820 | MO estimateConstantUtility: ASC | utility | active |
| `jMcodt.alpha_sex` → `P.jMcodt.alpha_sex` | -0.42 | 0.83 | 0.83 | MO estimateUtility: female indicator (L unsafe reference comparison) | utility/indicator | active |
| `jMcodt.alpha_age` → `P.jMcodt.alpha_age` | -0.0132 | -0.0132 | -0.017 | MO estimateUtility: age | utility/year | active |
| `jMcodt.betaShortDistance_km` → `P.jMcodt.betaShortDistance_km` | absent | -0.03 | 0.0 | MO estimateUtility: Euclidean d, no short-distance threshold | utility/km | active expression, zero YAML |
| `jMotorcycle.beta_TravelTime_u_min` → `P.jMotorcycle.beta_TravelTime_u_min` | -0.0332 | -0.0332 | -0.054 | MC estimateTravelTimeUtility: leg time/60 | utility/min | active |
| `jMotorcycle.alpha_u` → `P.jMotorcycle.alpha_u` | 0 | 0 | -0.330 | MC estimateConstantUtility: ASC | utility | active |
| `jMotorcycle.betaShortDistance_km` → `P.jMotorcycle.betaShortDistance_km` | absent | -0.03 | 0.0 | MC estimateUtility: Euclidean d, no short-distance threshold | utility/km | active expression, zero YAML |

### 6.1 Fields not supplied by baseline YAML and declared-versus-used distinctions

| Parameter/field | L default | C default | Actual contribution / external status |
|---|---|---|---|
| `car.additionalAccessEgressWalkTime_min` | 0 min | 0 min | L: Car access time plus actual walk-leg minutes, and MC access time; weighted by `walk.betaTravelTime_u_min`. C: still predicted but those utility calls are removed. Commented baseline entry is not loaded. |
| `car.constantParkingSearchPenalty_min` | 0 min | 0 min | **Still active through core CarPredictor travel time in both branches**; changing externally changes car time utility. C removal of access utility did not remove this travel-time component. MC predictor does not add it. |
| `bike.betaAgeOver18_u_a` | 0 utility/year | 0 utility/year | Core BikeUtilityEstimator calls coefficient × max(0,age−18); zero by default; bike unavailable under JakartaModeAvailability. |
| `jWalk.alpha_age` | 0.0103 utility/year | absent/commented | L actively multiplies age. Supplying this old YAML key to C fails as unknown. |
| `jMotorcycle.alpha_age` | -0.0083 utility/year | -0.0083 utility/year | L active; C declaration/default survives but utility call is removed. |
| `jMotorcycle.betaAccessEgressWalkTime_min` | 0 | 0 | Never used by actual MC access helper; helper uses walk beta instead. C helper itself is not called. |
| `jMotorcycle.betaWaitingTime_u_min` | 0 | 0 | No called waiting expression. |
| `jMcodt.betaAccessEgressWalkTime_min` | 0 | 0 | L helper called but predictor supplies exactly zero access time; C helper not called. |
| `jCarodt.betaAccessEgressWalkTime_min` | 0 | 0 | Same zero-predictor/no-called-helper distinction as mcodt. |
| `jMcodt.betaWaitingTime_u_min`, `jCarodt.betaWaitingTime_u_min` | 0 each | 0 each | Never called; no separate waiting term. |
| `jPT.generic.constant` | 0 utility | absent | L generic PT ASC, **active**. No baseline YAML field. |
| `jPT.generic.cost` | 0 utility/KIDR | absent | L class-only extra `cost×C×H`; bypassed by early generic return. |
| `jPT.generic.accessTime`, `.inVehicleTime`, `.egressTime` | 0 each | absent | Declared but never used; generic branch uses inherited `pt.*` betas. |
| `jPT.class1`–`.class4` leaves | see §11 | absent | Active class-specific subset in L only. All baseline class keys absent. |
| inherited `drt.alpha_u`, `.betaTravelTime_u_min`, `.betaWaitingTime_u_min`, `.betaAccessEgressTime_u_min` | 0 each in pinned core | 0 each | Declared by dependency, no Jakarta drt estimator/mode active in these configs. |
| city/spatial terms `alpha_*_city`, `home_jakarta`, subscription, taxi fields | commented/absent | commented/absent | No active spatial, subscription, taxi, entrepreneur or city-residence systematic utility. Commented examples are not valid active YAML declarations. |

Waiting and line-switch fields are the two active baseline YAML entries loaded but unused by C's PT utility. The two bike entries are consumed only if bike is made available by a different availability setup. Zero distance coefficients remain live expressions; do not classify them as dead code. Policy base/subsidy/cap terms remain operative even when the baseline share is zero.

Every behavioural key becomes group-ambiguous if copied without a namespace. The four `jPT.odt.*` keys must instead remain shared policy keys. Legacy class/generic keys and `jWalk.alpha_age` cannot simply be applied to an unmodified C object.

## 7. Exact effective equations and predictor semantics

These are **systematic mode-estimator utilities for a fixed person and routed candidate**, before mode availability and trip/tour constraints remove candidates. Guards can return negative infinity. They are not the MATSim experienced-plan score.

### 7.1 Notation and units

- `d` = Euclidean origin–destination distance in km (`PredictorUtils.calculateEuclideanDistance_km`, `CoordUtils` distance × 0.001). This is **not** route distance.
- `r_m` = sum of route distance in km for legs whose mode matches cost model m (`AbstractCostModel.getInVehicleDistance_km`).
- `H = (max(0.001,hhlIncome)/jAvgHHLIncome.avg_hhl_income) ^ jIncomeElasticity.lambda_income`.
- `D = (max(0.001,d)/referenceEuclideanDistance_km) ^ lambdaCostEuclideanDistance`.
- `b` = `betaCost_u_MU`. `EstimatorUtils.interaction` clamps only the numerator, not the reference. Zero/negative reference values are not validated. Monetary values are KIDR (1,000 IDR) in Jakarta cost defaults/YAML; income is used without conversion, so the reference must have the same units as person `hhlIncome`. The source does not independently certify the survey income unit.
- `a` = integer person age in years. `f = 1` exactly when `"f".equals(sex)`, else 0. `e = 1` exactly when attribute string `employment` equals lowercase `"yes"`, else 0. This **does not establish full-time status** or daily commuting status.
- `G_L(sex) = 0 if Java reference identity (sex == "f") is true, else 1`. This intentionally describes the legacy bug. A separately allocated string containing `f` can receive the coefficient; it is not a reliable male/female indicator.
- `Tcar = sum(car-leg seconds)/60 + car.constantParkingSearchPenalty_min`; `Acar = sum(walk-leg seconds)/60 + car.additionalAccessEgressWalkTime_min`, from pinned core CarPredictor. The latter is omitted from C utility. No `JakartaCarPredictor.java` exists at either endpoint; the effective predictor is this core class.
- MC/MO/CO predictors require a single plan element, cast it to Leg, and use `leg.getTravelTime().orElse(0)/60`. MC access time is `car.additionalAccessEgressWalkTime_min`; both ODT access times are hard-coded zero. These predictors are unchanged between L and C. Waiting parameters do not produce separate waiting variables.
- Walk and bike use their core leg-time predictors in minutes. Bike age comes from core PersonPredictor. Car/MO/CO Jakarta estimators call the predictors' public `predict` directly; MC and Jakarta person/PT calls use `predictVariables`.
- `JakartaPersonPredictor` and helpers cast `hhlIncome` to Double, age to Integer/int, sex/employment to String. Missing income falls back to fresh Java default 5327; missing/wrong age can fail. No age centring, age bins or income scaling conversion occurs in the active C expressions.

### 7.2 PT feature and cost construction, including material legacy differences

`JakartaPtPredictor.predict` first filters `elements` to **PT legs only** and sends those to core PtPredictor. Core computes IVT as `(leg travel time − (boarding time − departure time))/60`, skips waiting before the **first** PT stage, counts later boarding waits in `W`, and computes `N=max(0,PT-leg-count−1)`. Because only PT legs are delegated, core access/egress `A0=0` and transit_walk transfer-walk input is absent. The actual base cost `P` is JakartaPtCostModel's 4 or 10 KIDR rule (§15).

From the original elements the Jakarta predictor counts an eligible feeder if:

```text
mode is walk, non_network_walk, motorcycle, or mcodt
AND immediately adjacent activity type startsWith("pt interaction")
AND leg travelTime.seconds() is neither NaN nor <= 0
```

This is adjacency to a stage activity, **not** verification of a complete feeder chain or an actual neighbouring PT leg. `carodt` and `transit_walk` are not eligible. Undefined travel time can fail on `.seconds()` before the numeric test. Eligible route distance defaults to zero if route is absent or its distance is NaN. No negative-distance or infinity check is implemented. Those are limits of the existing feature definition, not instructions to change it during this audit.

Let `AF` be the sum of eligible feeder travel minutes, `n` the eligible mcodt leg count, and `rF` their aggregate route km. Then:

```text
F = jPT.odt.base_mcodt * n + jPT.odt.per_km_mcodt * rF
rawDiscount = jPT.odt.subsidyShare_mcodt * F
discount = maxDiscountMU_mcodt if rawDiscount > maxDiscountMU_mcodt else rawDiscount

L: A = A0 = 0;       CPT = P - discount
C: A = A0 + AF = AF; CPT = P + F - discount
```

For normal finite nonnegative policy values, discount is exactly `min(share*F,cap)`. There is no final nonnegative-cost clamp. F and discount aggregate eligible **both-side** feeder legs before applying **one cap per PT candidate**, not one cap per feeder leg. Private motorcycle contributes feeder time but **no** private-motorcycle monetary operating cost here. Standalone ODT pickup/minimum-fare rules are not consulted.

C's PT estimator also retains simple immediate-Leg adjacency motorcycle/home guards returning `-Infinity`. They are defensive and do not cover every stage-separated chain. The authoritative routing boundary and broader DMC constraint must remain active (§18).

### 7.3 Endpoint equations by mode

Use the parameter fields below with defaults/YAML in §6. `ASCm`, `ttm`, etc. refer to those exact fields, not an extra coefficient source.

| Mode | L effective utility | C effective utility | Source class/method |
|---|---|---|---|
| car | `car.alpha_u + car.betaTravelTime_u_min*Tcar + walk.betaTravelTime_u_min*Acar + b*D*H*Ccar` | `car.alpha_u + car.betaTravelTime_u_min*Tcar + b*D*H*Ccar + jCar.betaTravelDistance_km*d + jCar.alpha_age*a` | JakartaCarUtilityEstimator.estimateUtility; core CarUtilityEstimator monetary/time/access helpers and CarPredictor.predict |
| walk | `walk.alpha_u + walk.betaTravelTime_u_min*Tw + jWalk.alpha_age*a - 1500*1[d>1.8]` | `walk.alpha_u + walk.betaTravelTime_u_min*Tw + jWalk.betaTravelDistance_km*d - 1500*1[d>3.747]` | JakartaWalkUtilityEstimator.estimateUtility and core WalkUtilityEstimator |
| motorcycle | `jMotorcycle.alpha_u + jMotorcycle.beta_TravelTime_u_min*Tmc + walk.betaTravelTime_u_min*Amc + jMotorcycle.alpha_age*a + b*D*H*Cmc_L` | `jMotorcycle.alpha_u + jMotorcycle.beta_TravelTime_u_min*Tmc + b*H*Cmc_C + jMotorcycle.betaShortDistance_km*d` | JakartaMotorcycleUtilityEstimator.estimateUtility and monetary/access helpers; JakartaMotorcyclePredictor |
| mcodt | `jMcodt.alpha_u + jMcodt.beta_TravelTime_u_min*Tmo + jMcodt.betaAccessEgressWalkTime_min*0 + jMcodt.alpha_age*a + jMcodt.alpha_sex*G_L(sex) + b*D*H*Cmo` | `jMcodt.alpha_u + jMcodt.beta_TravelTime_u_min*Tmo + jMcodt.alpha_age*a + jMcodt.alpha_sex*f + b*D*H*Cmo + jMcodt.betaShortDistance_km*d` | JakartaMcodtUtilityEstimator.estimateUtility and private/protected helpers; JakartaMcodtPredictor |
| carodt | `jCarodt.alpha_u + jCarodt.beta_TravelTime_u_min*Tco + jCarodt.betaAccessEgressWalkTime_min*0 + jCarodt.alpha_age*a + jCarodt.alpha_sex*G_L(sex) + b*D*H*Cco` | `jCarodt.alpha_u + jCarodt.beta_TravelTime_u_min*Tco + jCarodt.alpha_age*a + jCarodt.alpha_sex*f + b*H*Cco` | JakartaCarodtUtilityEstimator.estimateUtility and private/protected helpers; JakartaCarodtPredictor |
| PT, generic | `jPT.generic.constant + pt.betaAccessEgressTime_u_min*0 + pt.betaInVehicleTime_u_min*IVT + pt.betaWaitingTime_u_min*W + b*D*CPT_L` | `pt.alpha_u + pt.betaAccessEgressTime_u_min*AF + pt.betaInVehicleTime_u_min*IVT + b*D*H*CPT_C + jPT.alpha_age*a + jPT.alpha_fulltime*e` | JakartaPTUtilityEstimator.estimateUtility; core PtUtilityEstimator helpers; JakartaPtPredictor.predict |
| bike | `bike.alpha_u + bike.betaTravelTime_u_min*Tb + bike.betaAgeOver18_u_a*max(0,a-18)` | same | Core BikeUtilityEstimator.estimateUtility; BikePredictor and PersonPredictor |

`Cmc_L` contains a historical corridor/time charge (§15); using a common C cost surface for future non-commuters intentionally differs from whole-L numerical output. L PT generic is selected only for null or `non_commuters`, returns immediately, has **no income elasticity**, **no age**, **no employment**, and **no line-switch term**. The `estimateAgeUtility` method at L is declared but never called. Class PT paths are separately reconstructed in §11.

These equations distinguish absent terms from terms multiplied by zero. In particular, legacy ODT access code is called but its predictor is zero; legacy PT access coefficients are called but receive A0=0; current short-distance terms have no threshold and are zero only due to YAML.

### 7.4 Numerical C utilities if the supplied baseline YAML is actually loaded

With `D=(max(.001,d)/7.67)^(-.75)` and `H=(max(.001,income)/5327)^(-.06)`:

```text
car        = -1.370 - .054*Tcar - .019*D*H*Ccar + .102*d + .026*a
PT         = -1.100 - .052*AF - .013*IVT - .019*D*H*CPT - .031*a + .808*e
walk       = -1.070 - .054*Tw - 1500*1[d>3.747]
motorcycle = - .330 - .054*Tmc - .019*H*Cmc
mcodt      = -2.820 - .054*Tmo - .017*a + .83*f - .019*D*H*Cmo
carodt     = -4.924 - .0826*Tco - .017*a + .83*f - .019*H*Cco
bike       = -4.440 - .0905*Tb
```

CPT baseline is `P+1.9*rF`; parking-search default is zero. These are conditional loaded-file equations, not proof the XML currently selects that file.

With **L Java defaults** and no external/CLI overrides: replace coefficients with §6 L defaults, walk age with +.0103/year, MC age with -.0083/year, ODT sex with -.42×G_L, and generic PT ASC with 0. L feeder default fare/share are zero, so CPT=P. In particular L generic PT becomes `-.0149*IVT -.0149*W -.0208*D*P`, while its declared `pt.alpha_u=-3.5` contributes nothing.

### 7.5 Integration and availability

Pinned core `EqasimUtilityEstimator.estimateTrip` gets the mode's estimator, calls `estimateUtility(person,trip,elements)`, then adds epsilon and subtracts policy utility penalty. `usePseudoRandomErrors` defaults false and is not set in supplied XML; EpsilonModule binds NoopEpsilonProvider. No eqasim policies module is supplied, so PolicyModule produces an empty policy collection. The 25% files use Jakarta monetary parameters, not those generic policy utilities. Tour `Cumulative` and `MultinomialLogit` operate outside these per-mode equations.

`JakartaModeAvailability.getAvailableModes` (same L/C) always offers walk/PT/mcodt/carodt; offers car for age>=18 unless carAvailability is `never`; offers motorcycle for age>=18 without motorcycle-ownership checking; adds outside/isPassenger modes when their attributes request them. Bike addition is commented out. XML mode-availability parameter sets listing bike do not override this custom Java implementation. Outside and car_passenger have ZeroUtilityEstimator. Age thus also affects feasibility independently of age coefficients.

`WalkDurationConstraint.validateBeforeEstimation` rejects walk when `Euclidean metres * walkFactor / walkSpeed > 3600 seconds`, despite a stale 40-minute comment. Baseline factor 1.3/speed 1.2 implies approximately **3.323 km** maximum. This makes C's 3.747-km utility penalty unreachable under that constraint, while L's 1.8-km penalty could affect otherwise admissible trips. Keep feasibility separate from preference coefficients.

## 8. Side-by-side interpretation: form versus values

| Mode | Functional-form change | Coefficient-only changes/default caveats | Implication |
|---|---|---|---|
| car | Remove explicit access penalty; add age and linear Euclidean distance | New age/distance defaults 0; supplied YAML activates them. ASC/time defaults unchanged L→C | Current estimator cannot restore legacy access merely through existing C coefficients |
| PT | Generic/class branching replaced by unified ASC, income, continuous age and employment; waiting removed; feeder inputs repaired | Most inherited defaults unchanged; new age -.031/fulltime .808; old pt ASC was unused | Largest structural difference; generic legacy scientific target needs approval |
| walk | Age removed; distance added; feasibility-style soft cutoff changed | New distance default -.35 but YAML 0; ASC/time Java defaults unchanged | Separate form needed to retain approved old age effect |
| motorcycle | Age/access removed, distance-cost elasticity removed, linear distance added | Age default remains dead; distance -.03→YAML 0 | Cost sensitivity differs structurally, not just numerically |
| mcodt | Gender code corrected; linear distance added; zero-valued access removed | Sex Java default changes -.42→+.83; distance -.03→YAML 0; distance-cost multiplier retained | Do not infer all ODT distance interactions were removed |
| carodt | Gender corrected; distance-cost multiplier removed; zero-valued access removed | Java sex default still -.42, YAML +.83; most defaults unchanged | Legacy calibration/indicator correspondence cannot be guessed |
| bike | No change | Age-over-18 default 0; same time/ASC | Not ordinarily offered by current availability |

The difference between C Java defaults and the supplied baseline YAML is calibration, not branch code change. Retaining the C branch alone does not retain the user's calibrated behaviour unless the correct external parameters are loaded.

## 9. Classified substantive-difference register

A = demonstrable intended change in behavioural formulation (not proof of group-specific empirical validity). B = coding correction with source/history evidence. C = FLM/routing/feasibility/PT infrastructure. D = non-behavioural housekeeping/build/infrastructure. REQUIRES DECISION = intent insufficient to decide bug versus legitimate legacy specification/policy. A classification can still require approval before use for non-commuters. Each F row is one grouped finding; history table and final decision table cross-reference rather than recount it.

| ID | Class | Change | Evidence | Disposition / ambiguity |
|---|---|---|---|---|
| F01 | A | Walk age replaced by distance | L age active; C linear d, age absent; f02b0b56, ef9ee69b, afd35798 | Non-commuter age/distance form needs R1/R3; not inherently a bug. |
| F02 | A | Car age and distance added | a0e5c518, 5cc76df5, 03bdddab; C estimateUtility | Adopt current commuter form; legacy non-commuter exclusions need R4. |
| F03 | A | Motorcycle age removed | 2fa53585; old age call removed but field survives | Old age variable may reflect original specification; R1/R4. |
| F04 | A | Motorcycle cost-distance elasticity removed | c8e6543c; C monetary helper b*C, caller H remains | Review group-specific D matrix R5. |
| F05 | A | Carodt cost-distance elasticity removed | Final operative change 923a5f20; 5a769ce8 only added a commented duplicate | Review group-specific D matrix R5; do not trust commit title alone. |
| F06 | A | Motorcycle linear distance added | 80cc6116, c31a79ea, 2ab0885a/95c56a3b; no threshold | Default -.03, YAML zero; R1/R4. |
| F07 | A | Mcodt linear distance added | 6547b671 and C estimateUtility; default -.03/YAML zero | Not a short-trip dummy; old absence may be retained if approved. |
| F08 | B | ODT Java string identity corrected | 0eaa0d72 and 052eda48; == replaced by equals | Never reproduce reference-identity bug. |
| F09 | REQUIRES DECISION | ODT indicator orientation and coefficient provenance | L applies -.42 to non-identical-f strings; C applies female coefficient; MO default +.83, CO default -.42; YAML both +.83 | R6: safe equality is clear; scientific indicator, sign and ASC transformation need approved estimated model. |
| F10 | A | Generic PT ASC source and income interaction change | 8b0b27f7; L generic.constant and early return; C pt.alpha_u plus H | R5/R7: cannot silently use old pt.alpha as historical active ASC. |
| F11 | B | PT age helper corrected to continuous age during revised lineage | e8e35b0c replaces <=16 helper call on revised baseline lineage; L endpoint helper is inactive | Do not claim L had active child-age effect; continuous current effect supported by final call. |
| F12 | A | PT employment/fulltime-labelled term added | e8e35b0c, d61d99bc, b40775b9; employment==1 | R9: yes employment proxy is not proof of full-time worker coding. |
| F13 | REQUIRES DECISION | Car/private MC access utility removed | a0e5c518, 2fa53585 state consistency with R | R4: could be intended changed specification or correction relative to same target; no original estimation equations supplied. |
| F14 | REQUIRES DECISION | PT waiting/line-switch calls removed in later revision | dfeb2cef; L generic has waiting but no line-switch; intermediate revised path had both | R8: preserve revised commuter baseline, decide approved non-commuter waiting; do not reintroduce an imaginary L transfer term. |
| F15 | B | ODT access helper calls removed (numerical no-op) | 0eaa0d72, 9d8a071f; both ODT predictors return access=0 | No need to recreate dead call; future nonzero ODT access would be new specification. |
| F16 | C | PT feeder time actually included | caf5bcc2, 29ea4ba5, 49f8efbd; AF accumulator and return changed | Share C feature infrastructure; do not restore omitted feeder time. |
| F17 | C | PT feeder fare actually added | 367ede0e; P-discount becomes P+F-discount | Share C cost infrastructure; preserve finite capped voucher rule from L 2dfb68d2. |
| F18 | C | Startup/general home-side RAPTOR restriction | 8490bae4; Configurator, routing module, annotation, stop finder | Share for both groups; no separate routing stack. |
| F19 | C | Defensive DMC PT constraint strengthened | abe12dbf, 4a4a8b86; inspect all legs before/after actual PT, throw on uninspectable candidates, allow no-PT fallback through | Retain for both groups; do not restore weak legacy chain detection. |
| F20 | C | Home semantics consolidated | 8490bae4; exact case-insensitive home via HomeSideTripAttributes vs legacy startsWith(home) | Keep shared current endpoint contract; not a subpopulation utility choice. |
| F21 | REQUIRES DECISION | Walk soft cutoff 1800→3747 metres | 923a5f20; -1500 unchanged; active hard duration constraint limits baseline to ~3323m | R3: scientific/availability scope of old cutoff not established; do not change silently. |
| F22 | REQUIRES DECISION | Historical private motorcycle corridor/time monetary charge removed | 0a1b4aad; L adds 2.5*charged route km by start link/peak time; C removes it | R11: a policy-cost difference, not a non-commuter coefficient; never restore only for one group. |
| F23 | D | Obsolete cost fields removed/commented | f88f273d, ef5f7aa0, 2769d468, b3765b0f; carCharging and per-minute ODT already unused; active MC charge separately F22 | Old YAML keys now fail; preserve current schema or document migration. |
| F24 | A | Active PT latent-class framework removed | 8b0b27f7 and C endpoint; class/generic structures removed | No class framework in requested future model; extract only researcher-approved generic behaviour. |
| F25 | D | Unused JakartaFeederModule removed; effective main binding retained | Endpoint module deleted; RunSimulation installation already commented; JakartaModeChoiceModule endpoint diff only import movement | Do not re-add obsolete alternative binding. |
| F26 | D | Build/version/documentation/tests/housekeeping | Endpoint POM version/comments/dependency/build changes, .gitignore, .DS_Store removal, forensic docs and routing test additions | Keep base infrastructure; no build/version/branch action in this audit. |

## 10. Dedicated historical-fixes table

“Reintroduce” means deliberately carrying the historical mechanism into future non-commuter behaviour. It does not authorise implementation. A source mismatch corrected for a revised commuter R model is not automatically proof the older non-commuter equation was a bug.

| Change | Legacy implementation | 2.8.0 implementation | Classification | Reintroduce for non-commuters? | Evidence |
|---|---|---|---|---|---|
| Walk age/distance | +.0103×age; no linear d | no age; betaDistance×d | A (F01) | REQUIRES DECISION | ef9ee69b, f02b0b56, afd35798; estimateUtility |
| Walk distance feasibility penalty | -1500 above 1.8 km | -1500 above 3.747 km | REQUIRES DECISION (F21) | REQUIRES DECISION | 923a5f20; WalkDurationConstraint unchanged |
| Car age/distance | absent | both active expressions | A (F02) | REQUIRES DECISION | a0e5c518, 5cc76df5, 03bdddab |
| Car access/egress | walk beta × Acar | omitted | REQUIRES DECISION (F13) | REQUIRES DECISION | a0e5c518; core CarPredictor still produces Acar |
| Car parking search | part of Tcar, default 0 | still part of Tcar | unchanged, not a removal | YES | core CarPredictor.predict; no endpoint core dependency change |
| Motorcycle age | active -.0083/year default | unused surviving field | A (F03) | REQUIRES DECISION | 2fa53585 |
| Motorcycle access | walk beta × car additional-access minutes | omitted | REQUIRES DECISION (F13) | REQUIRES DECISION | 2fa53585; JakartaMotorcyclePredictor |
| Motorcycle income-vs-distance | b×D×H×cost | b×H×cost | A (F04) | REQUIRES DECISION | c8e6543c; not income newly replacing absent income |
| Carodt income-vs-distance | b×D×H×cost | b×H×cost | A (F05) | REQUIRES DECISION | operative change 923a5f20; 5a769ce8 title exceeds actual diff |
| Car/PT/mcodt residual D | D present | D remains | unchanged but specification question R5 | REQUIRES DECISION | inherited Car/PT monetary helpers; MO monetary helper |
| ODT Java sex comparison | `sex == "f"` | `"f".equals(sex)` | B (F08) | NO | 0eaa0d72 explicitly describes unsafe/reversed code; 052eda48 |
| ODT sex sign/indicator calibration | coefficient on else branch | coefficient on female | REQUIRES DECISION (F09) | REQUIRES DECISION | Java default/YAML discrepancy; indicator/ASC may need joint conversion |
| ODT access helper | called with hard-coded zero | not called | B (F15) | NO | 9d8a071f, 0eaa0d72 and predictors |
| Motorcycle “short distance” | absent | linear Euclidean km, no cutoff | A (F06) | REQUIRES DECISION | 80cc6116, c31a79ea; YAML zero |
| Mcodt “short distance” | absent | linear Euclidean km, no cutoff | A (F07) | REQUIRES DECISION | 6547b671; YAML zero |
| PT age | helper <=16 declared but inactive at L | continuous age | B (F11), with endpoint activation | NO | e8e35b0c fixes intermediate call; L endpoint no age term |
| PT full-time-labelled term | absent | `employment==1`, from `employment="yes"` | A (F12) | REQUIRES DECISION | e8e35b0c; JakartaPredictorUtils.employment |
| PT waiting | active generic post-first waiting | omitted | REQUIRES DECISION (F14) | REQUIRES DECISION | dfeb2cef; old generic early return |
| PT line switches | no call in L generic/class paths | no call in C; intermediate revised call removed | REQUIRES DECISION (F14) | NO | L and C estimateUtility; dfeb2cef |
| PT ASC/income | generic.constant; no H | pt.alpha_u and H | A (F10) | REQUIRES DECISION | 8b0b27f7, final source |
| PT feeder time omission | AF discarded, A0=0 | AF added | C (F16) | NO | caf5bcc2, 29ea4ba5, 49f8efbd |
| PT feeder fare omission | subtract discount without adding fare | add fare then subtract discount | C (F17) | NO | 367ede0e |
| PT voucher cap | configurable, default infinite | same algorithm; supplied YAML cap 5 | shared policy, no endpoint algorithm change | YES | 2dfb68d2; JakartaPtPredictor; scenario YAML |
| Legacy motorcycle corridor charge | extra term by start-link/time | removed | REQUIRES DECISION (F22) | NO | 0a1b4aad; restore only via separately approved shared policy if ever needed |
| Weak PT feasibility/home guards | incomplete legacy handling | routing + broad constraint + defensive estimator guard | C (F18–F20) | NO | abe12dbf, 4a4a8b86, 8490bae4 |
| Latent-class dispatcher | active for four old labels | removed | A (F24) | NO | 8b0b27f7; user explicitly excludes prior latent-class framework |

## 11. Latent-class assessment

L's `JakartaModeParameters.JakartaPTParameters.LatentClassParameters` declares `constant`, `accessTime`, `inVehicleTime`, `egressTime`, `cost`, instantiated as `class1`…`class4` and `generic`. These are **not merely dormant compatibility fields at L**. `JakartaPTUtilityEstimator.estimateUtility` dispatches on the person subpopulation string:

| Accepted label | Equation after feeder/home guard | Default coefficients `(constant, accessTime, IVT, egressTime, cost)` |
|---|---|---|
| null or `non_commuters` | `generic.constant + pt.betaAccessEgressTime*A0 + pt.betaInVehicleTime*IVT + pt.betaWaitingTime*W + b*D*CPT_L`; early return | generic all 0; generic time/cost fields not used here |
| `Class1_non_private_motorised_commuters` | `k1.constant + (k1.accessTime+k1.egressTime)*A0 + k1.inVehicleTime*IVT + k1.cost*CPT_L + generic.cost*CPT_L*H` | `(0,-.013,-.023,-.069,-.006)` |
| `Class2_young_cost_sensitive_commuters` | same shape with k2 | `(0,-.461,+.156,-.450,-.183)` |
| `Class3_affluent_car_dependent_commuters` | `k3.constant + k3.cost*CPT_L + generic.cost*CPT_L*H` | `(0,0,0,0,-.019)`; class3 time fields not read |
| `Class4_young_time_sensitive_commuters` | same shape as class1 with k4 | `(0,-.052,-.010,-.056,+.030)` |

Constants are utility units; time coefficients utility/minute; cost coefficients utility/KIDR; class cost has **no** global b or D multiplier. `generic.cost=0` makes the appended class income term zero by default. Both access and egress coefficients multiply the same combined A variable, not separately identified sides; at the L endpoint that A is in fact zero. Positive class2 IVT and class4 cost are actual defaults, not corrected during this audit. No baseline YAML supplies these fields.

No probability-weighted latent-class mixture is computed in this estimator; it selects one label. An earlier `JakartaLatentClassPredictor` introduced in `f9093682af0cfa50e757bccc30b62a1fcd7cb995` has employment/age/vehicle/sex rules and a class3 fallback. It is absent from both inspected endpoint trees and has no active endpoint binding. Its historical classification is not the new daily work-activity rule.

C removes the class structures/dispatcher. A remaining local `subpopulation` read and commented generic/age snippets have no behavioural effect. **The L generic PT branch can be reconstructed without any latent-class functionality.** Whether that branch's zero ASC, no income multiplier and old waiting formula are an acceptable non-commuter research specification is **REQUIRES RESEARCHER DECISION (R1, R5, R7, R8)**. Do not carry class fields, positive class coefficients, double use of combined access/egress, class labels, or old employment classifier into 3.0.0.

## 12. Functional-form conclusion and selector feasibility

A single unmodified C estimator plus two copies of current coefficients is insufficient: it cannot turn on deleted walk/MC age and access calls, remove the PT income interaction independently of other modes through current global income fields, or recreate legacy generic PT's separate ASC semantics. A newly designed superset could express these with independent per-mode exponents, zero-able coefficients and explicit structural flags, but that has its own complexity and is not Option 1 “coefficients only” as currently implemented.

Use **`PopulationUtils.getSubpopulation(person)`**, from pinned MATSim `org.matsim.core.population.PopulationUtils`, which returns the String `subpopulation` person attribute. Person is already present in `EqasimUtilityEstimator.estimateTrip`, every mode `estimateUtility`, predictors, cost models and availability. No population transformation is necessary for selection.

The narrowest recommended point is **a per-mode wrapper/delegating utility estimator** registered under existing mode aliases. It validates exactly `commuter` or `non_commuter` and selects one pure behavioural formula path after shared feasibility/feature preparation. Keep EqasimUtilityEstimator's epsilon/policy integration outside the wrapper and applied once. Unknown/missing values should fail clearly with person ID/value under the proposed strict release contract; any fallback requires explicit scientific justification.

Do not select by employment, do not rescan a candidate trip for work, and do not classify per trip: the person attribute represents **daily selected-plan commuting status**. A commuter's shopping trip still uses commuter behaviour. Keep the user-supplied classification fixed for this release; adding strategies that change daily activity participation would require a separately specified reclassification policy.

Predictor-only selection cannot express differing formulas safely. Parameter-provider-only selection cannot dynamically choose a per-person object through a singleton Guice provider. Modifying the core EqasimUtilityEstimator is broader than needed. Wrappers must not duplicate PT guards in only one branch or accidentally call the same monetary correction twice.

## 13. Baseline strategysettings audit

Complete XML inspection found exactly these active settings:

| Strategy | Subpopulation | Weight | disableAfterIteration | executionPath |
|---|---|---|---|---|
| DiscreteModeChoice | null | 0.1 | -1 | null |
| KeepLastSelected | null | 0.9 | -1 | null |

No active ReRoute, TimeAllocationMutator, combined TimeAllocationMutator_ReRoute, ChangeTripMode or SubtourModeChoice strategy exists. Their module parameters or mentions in comments do not activate strategies. DMC `performReroute=true` is routing within DMC, not an independently sampled ReRoute strategy.

Other relevant baseline settings: `fractionOfIterationsToDisableInnovation=Infinity`, `maxAgentPlanMemorySize=1`, `planSelectorForRemoval=NonSelectedPlanSelector`, DMC `enforceSinglePlan=true`, `fallbackBehaviour=EXCEPTION`, `modelType=Tour`, cumulative tour estimator, MultinomialLogit selector, home-based tour finder, and current trip/tour constraints. Keep them unchanged in the initial heterogeneity experiment.

MATSim `ReplanningConfigGroup.StrategySettings` defines null as the default population without explicit membership. `GenericStrategyManagerImpl.run` obtains `PopulationUtils.getSubpopulation(person)` and chooses from that group's weights. There is no fallback to null weights. If no strategy is selected it throws: `No strategy found! Have you defined at least one replanning strategy per subpopulation? Current subpopulation = ...`. Thus the revised explicitly labelled population would not inherit the two null settings; it would fail at replanning absent matching settings.

The XML still selects `jakarta_population.xml.gz`. The new input is `/Users/fazafawzan/Codex/matsim_jar_rev/data/jakarta_population_subpop.xml.gz`. Supplied QC is accepted as prior evidence: 297,468 persons, 123,797 commuter, 173,671 non_commuter, zero selected-plan/duplicate-attribute/classification errors, person-ID SHA256 before/after `ee7a7e27a898f94121fedddffde094ea3f905c34b1297e5004f66e08b30bc0a1`. This audit did not rerun the full population QC or alter the file. A read-only first-person schema check confirmed String subpopulation and the expected typed age/income/employment attributes; it is not a new population-wide validation.

## 14. Proposed future strategysettings structure — report only

Replace the two null-group settings with the following four in the future approved config; retain the surrounding strategy module parameters listed above. This is an example in Markdown, **not an edited XML file**.

```xml
<parameterset type="strategysettings">
  <param name="disableAfterIteration" value="-1" />
  <param name="executionPath" value="null" />
  <param name="strategyName" value="DiscreteModeChoice" />
  <param name="subpopulation" value="commuter" />
  <param name="weight" value="0.1" />
</parameterset>
<parameterset type="strategysettings">
  <param name="disableAfterIteration" value="-1" />
  <param name="executionPath" value="null" />
  <param name="strategyName" value="KeepLastSelected" />
  <param name="subpopulation" value="commuter" />
  <param name="weight" value="0.9" />
</parameterset>
<parameterset type="strategysettings">
  <param name="disableAfterIteration" value="-1" />
  <param name="executionPath" value="null" />
  <param name="strategyName" value="DiscreteModeChoice" />
  <param name="subpopulation" value="non_commuter" />
  <param name="weight" value="0.1" />
</parameterset>
<parameterset type="strategysettings">
  <param name="disableAfterIteration" value="-1" />
  <param name="executionPath" value="null" />
  <param name="strategyName" value="KeepLastSelected" />
  <param name="subpopulation" value="non_commuter" />
  <param name="weight" value="0.9" />
</parameterset>
```

Weights normalise within each group: both retain 10% DMC/90% KeepLastSelected. Do not split the 0.1 between groups or multiply by group population shares. No new mutation/reroute frequency is proposed. Apply corresponding settings and the approved population path consistently to all pricing configs.

## 15. Pricing implementation and equations

### 15.1 Shared monetary models

Let `r` be matching-mode route km, not Euclidean d. Current defaults, all absent from baseline cost YAML because baseline costParametersPath is null:

| Cost-model method | Active parameter/default | Current calculated KIDR |
|---|---|---|
| JakartaCarCostModel.calculateCost_MU | carCost_KIDR_km=2.95 | `2.95*r_car` |
| JakartaMotorcycleCostModel.calculateCost_MU | motorcycleCost_KIDR_km=.59 | `.59*r_motorcycle` |
| JakartaMcodtCostModel.calculateCost_MU | pickup=4, perkm=2.5, minimum=6 | `max(4+2.5*r_mcodt,6)` |
| JakartaCarodtCostModel.calculateCost_MU | pickup=6, perkm=4.5, minimum=10 | `max(6+4.5*r_carodt,10)` |
| JakartaPtCostModel.calculateCost_MU | ptCostPerTrip_0Transfers_KIDR=4; ptCostPerTrip_3Transfers_KIDR=10 | `P=10` if at least one bus/angkot leg AND at least one train/rail leg, otherwise `P=4` |
| JakartaPtPredictor.predict | base_mcodt=0, per_km_mcodt=1.9 in supplied utility YAML | `CPT=P+F-min(s*F,cap)`, F=`1.9*rF` |

The PT field names do not implement literal 0-versus-3 transfer counting. Multiple buses alone or multiple rail legs alone still cost 4; a bus+rail combination costs 10. Subway/other route transport-mode labels not matching these strings do not increment those counters. A no-PT delegated list also falls through to 4. No subscription discount is active; person variables are predicted but do not condition this fare.

Standalone ODT fares have pickup fees and minima but no active per-minute fare. The removed per-minute declarations had default zero and were unused. PT feeder F has **no standalone pickup fee or minimum fare**: its distinct base is 0. The direct fare breakpoints are 0.8 km for mcodt and 4/4.5 km for carodt; they do not affect the feeder voucher formula.

L car cost is the same. L motorcycle cost differs: `.59*r_motorcycle + 2.5*rCharged`. `getkmLink` loops all Legs, checks whether their route **start link** belongs to a hard-coded link set and departure is strictly between 07:00–10:00 or 16:00–19:00; if so it adds that leg's **whole route distance** in km. It does not sum only tolled network links. L `carCharging_KIDR_km=5` is declared but not called by CarCostModel. C removes the motorcycle charge call. These are shared monetary-policy differences, not evidence of different non-commuter preferences.

### 15.2 Independent scenario verification

The following refers to C source plus supplied XML/YAML content, assuming the referenced `input/...` files are staged at those process-relative paths and no CLI override intervenes. Scalar XML comparison found only parameter-file/output-directory changes, plus mcOdt output intervals (events/plans 10→40; snapshots 1→10). DMC, routing and strategy settings otherwise match baseline.

| Scenario | XML-selected cost YAML / utility YAML | Exact changed policy values from baseline | Cost equation before → after | Scope and cap/minimum implications |
|---|---|---|---|---|
| scen_car_25pct | `JktCostParams_scen_car_25pct.yml` / `UtilityParams_Scenario_rev.yml` | car 2.95→3.6875; MC .59→.59; feeder share 0→0 | `2.95*r_car → 3.6875*r_car` | Exactly +25% standalone car calculated cost for fixed route; no min/pickup. PT feeders, MC, direct ODT unchanged. |
| scen_mc_25pct | `JktCostParams_scen_mc_25pct.yml` / `UtilityParams_Scenario_rev.yml` | car 2.95→2.95; MC .59→.7375; share 0→0 | `.59*r_mc → .7375*r_mc` | Exactly +25% standalone private MC cost for fixed route. **Private-MC PT feeder operating cost is not calculated by JakartaPtPredictor**, so that component is not increased here. |
| scen_carMc_25pct | `JktCostParams_scen_carMc_25pct.yml` / `UtilityParams_Scenario_rev.yml` | car 2.95→3.6875; MC .59→.7375; share unchanged 0 | both preceding equations | +25% for both direct modes; no feeder/direct ODT policy change. |
| scen_mcOdt_25pct | null cost path / `UtilityParams_scen_mcOdt_25pct_rev.yml` | feeder share 0→.25; base 0→0; per-km 1.9→1.9; cap 5→5; car/MC/direct ODT defaults unchanged | `P+1.9*rF → P+1.9*rF-min(.475*rF,5)` | Only PT mcodt feeder-derived component discounted, one aggregate cap; standalone `max(4+2.5*r,6)` unchanged. Not 25% off total PT cost. |
| scen_all_25pct | `JktCostParams_scen_carMc_25pct.yml` / `UtilityParams_scen_mcOdt_25pct_rev.yml` | car→3.6875; MC→.7375; feeder share→.25; base/per-km/cap unchanged | car and MC +25% equations plus `CPT=P+1.9*rF-min(.475*rF,5)` | Combined direct car/MC surcharge and capped mcodt feeder discount; direct mcodt/carodt unchanged. |

All utility YAML behavioural scalar values match the supplied baseline. `UtilityParams_Scenario_rev.yml` has exactly the same active map. `UtilityParams_scen_mcOdt_25pct_rev.yml` differs in exactly one active key: `jPT.odt.subsidyShare_mcodt: 0.25` instead of 0. All three cost YAMLs provide only car and motorcycle cost/km keys; PT and direct ODT defaults remain intact. Thus these files implement **monetary cost interventions**, not changed beta-cost or other behavioural calibration, subject to the baseline path caveat.

### 15.3 Cap interpretation and examples

For baseline feeder base=0 and per-km=1.9, the 5-KIDR cap starts to bind above aggregate eligible mcodt feeder distance `5/(.25*1.9)=10.526315789... km` (at that distance it is exactly reached). Equivalently feeder cost F must exceed 20 KIDR. At 2 km, F=3.8 and discount=.95; at 12 km, F=22.8 and discount=5, so the effective feeder reduction is about 21.93%, not 25%.

The current feeder search maxRadius=5000 m is a spatial stop-search radius, not a mathematical 5-km limit on routed feeder distance; both sides can be counted. It may be reasonable to hypothesise that the cap is **effectively non-binding in typical cases**, but no feeder-distance distribution was analysed here, so that prevalence is unverified. It is **not mathematically unlimited**. Java default +Infinity is overridden by the supplied 5.0 YAML in both baseline and subsidy scenarios.

The comments call this a PT voucher, but C's effective calculation is total PT+feeder monetary cost minus the feeder-derived discount. For valid inputs this gives exactly the requested reduction to the feeder component. There is no independently reduced standalone mcodt CostModel fare and no separate payment/refund event proven by this code.

## 16. Pricing compatibility with two behavioural paths

All current cost-model formulas above are independent of `subpopulation`. PT CostModel constructs person variables but does not use them to determine fare; JakartaPtPredictor uses its shared params without group selection. **For the same eligible routed candidate, the same calculated monetary policy currently applies to every person.** Different routes can of course produce different actual total costs.

Preserve this property: calculate monetary cost once through shared C cost/feeder infrastructure, then apply the selected group's utility response. Do not recreate L's omitted feeder fare/time or L-only motorcycle charge as part of the non-commuter formula. Otherwise the experiment would vary both the cost intervention and behavioural response.

Specific risks:

- Duplicating entire JakartaModeParameters duplicates `jPT.odt`; one file may retain subsidy 0 while another gets .25, producing accidental group eligibility.
- Keeping the singleton JakartaPtPredictor injected with a commuter object while assuming it uses a non-commuter object silently mixes behavioural and policy provenance.
- Copying old PT predictor loses F from total cost and AF from time; copying old motorcycle CostModel restores a corridor charge. Both invalidate the intended controlled comparison.
- Updating `betaCost_u_MU` by 25% instead of cost/km changes sensitivity and potentially all paid modes. It does not preserve the requested policy definition.
- Direct ODT pickup/minimum rates and PT feeder rates are intentionally separate mechanisms. Do not apply the feeder scenario to jMcodt utility coefficients or direct mcodt fares.
- Current private-MC feeder monetary operating cost is absent. If the researcher intends “private motorcycle +25%” to include that component, adding a base operating cost and pricing it would be a separately approved shared policy/model extension, not already implemented behaviour.

## 17. Cost model versus utility model

`JakartaCostParameters` holds calculated monetary charges and is loaded with `--cost-parameter:*` overrides. `JakartaModeParameters` holds behavioural coefficients and also, historically, PT feeder cost-policy fields. `CostModel.calculateCost_MU` supplies monetary values; `UtilityEstimator` multiplies these by marginal utility and interactions. `JakartaPtPredictor` is the current location of additional monetary feeder accounting.

For fixed route and person, +25% standalone car cost changes car's monetary utility component from `b*D*H*C` to `b*D*H*1.25*C`, leaving ASC/time/age/distance unchanged. Although a corresponding beta multiplication could equal that one term in a fixed example, changing global b also changes other modes, obscures monetary accounting, and does not represent the policy. MC uses `b*H*C`; direct mcodt uses `b*D*H*C`; direct carodt uses `b*H*C`; PT uses `b*D*H*(P+F-discount)` in C. Different non-commuter formulas may give different responses to the **same** monetary amounts.

Keep the scientific monetary interventions explicit in a shared policy file/object. A future move of `jPT.odt` into a cost/policy object should preserve F/cap equations and provide documented external-key migration. Do not change actual charged-cost formulas and calibration simultaneously.

## 18. 2.8.0 FLM and routing protections to preserve

| Shared component | Runtime role/dependency | Refactor hazard |
|---|---|---|
| JakartaConfigurator | Registers JakartaModeChoiceModule and **then** JakartaHomeSideRoutingModule after super's SwissRailRaptor registration | Reordering modules may lose authoritative RaptorStopFinder binding |
| JakartaHomeSideRoutingModule.install | Registers person prepare-for-sim annotation, diagnostics listener, explicit DefaultRaptorStopFinder delegate, RaptorStopFinder→JakartaHomeSideRaptorStopFinder | Copying a legacy configurator drops startup/general routing restrictions |
| AnnotateHomeSideTripAttributes.run | Annotates substantive trip endpoint types for every plan using TripStructureUtils stage filtering | Rebuilding trips/attributes can omit metadata consumed by routing |
| HomeSideTripAttributes | Shared namespaced attribute keys and exact case-insensitive `home` matcher | Do not replace with work classification or legacy home-prefix match |
| JakartaHomeSideRaptorStopFinder.findStops | Delegates candidate construction; inspects candidate mode/legs; removes private-motorcycle candidates for non-home origin access or non-home destination egress before RAPTOR chooses; preserves remaining candidate identity | Behaviour selection must not reconstruct routes/candidates or bypass filter |
| Stop-finder validation/diagnostics | Throws for uninspectable feeder candidate or missing endpoint metadata when motorcycle candidates need checking | Unknown-group fallback must not suppress routing failures |
| NoMotorcycleEgressExceptHome.validateAfterEstimation | Checks all private-MC legs before first/after last actual PT; throws for uninspectable routed PT; no-PT-leg fallback returns true | A wrapper cannot replace the registered TripConstraintFactory with only estimator adjacency checks |
| JakartaPTUtilityEstimator guards | Retained immediate-Leg adjacency defensive `-Infinity` checks | Both future formula paths must pass through equivalent shared guard semantics |
| JakartaPtPredictor | Shared eligible feeder extraction, time and capped monetary augmentation | Duplicating old predictor omits cost/time; applying twice double counts |
| HomeFinder bindings and standard constraints | Named trip/tour FirstActivityHomeFinder, VehicleContinuity, TransitWalk and other configured constraints | Preserve bindings and constraints while replacing only behavioural dispatch |

Baseline SwissRailRaptor uses intermodal access/egress, CalcLeastCostModePerStop, and feeder modes walk/motorcycle/mcodt (not carodt). Their max radii are 3747/4050/5000 m. Network modes include car, car_passenger, mcodt, carodt, motorcycle. These are shared infrastructure, not group behavioural coefficients.

The route-level rule is a home-side feasibility restriction; it does not establish full daily physical motorcycle parking continuity. Do not claim a behavioural refactor solves the separate parking-continuity problem or redesign it here.

Pinned `CachedVariablePredictor` caches only the last `DiscreteModeChoiceTrip` identity, not person/group/parameters/elements. JakartaPtPredictor is singleton-scoped and inherits mutable caching; its delegate also caches. This existing design needs targeted cache isolation/concurrency regression evidence before sharing extracted variables across new paths. Never toggle parameter values on a shared predictor; do not promise its cache distinguishes counterfactual candidates automatically.

## 19. Viable architecture options

Options about file layout and options about formula layout are orthogonal and can be combined. In all cases both formula paths must compile into the future **same 2.8.0-based JAR**. No runtime Java-class loading from different branches.

| Option | applyFile compatibility and Guice | Functional-form support | Calibration / pricing | Mixing / duplication / tests / maintenance / thesis transparency |
|---|---|---|---|---|
| 1. One common estimator + two sets in one nested YAML | Current parser accepts dotted namespaces only; actual indented YAML needs new loader. Provider must expose both sets | **Insufficient with unchanged C equations**; possible only by designing expanded superset terms and per-mode exponents/flags | One calibration file is convenient; shared policy must be separated | Many structural zeroes/flags can obscure intent; modest code duplication; regression matrix large; thesis must document every switch |
| 2. Separate parameter objects from one file | A root ParameterDefinition with initialised public commuter/non_commuter leaves works with dotted keys. Qualified bindings or registry needed | Parameter separation alone does not supply missing terms; combine with separate formulas or expanded superset | Easy single-file reproducibility; keep shared policy separately | Low mixing with typed objects; moderate schema work; straightforward overlay/isolation tests; clear thesis tables |
| 3. Separate commuter/non-commuter YAMLs | Existing applyFile usable twice on independent defaults; config/provider needs explicit two-path support, plus scoped CLI prefixes | Orthogonal to forms; combine with Option 4 | Independent calibration convenient; manifest must pin both files and shared policy | Low mixing with qualified instances; file drift risk; minimal loader change; strong testability and clear calibration provenance |
| 4. Separate estimators/formula implementations, externally parameterised | Register wrappers under current aliases; delegate to qualified formula/parameter objects; loader 2 or 3 | **Directly supports differing forms** and corrected legacy decisions | Distinct coefficients remain external; one shared monetary layer | Some arithmetic duplication, reduced by shared feature DTOs/helpers; highest equation traceability and direct regression testing; avoid duplicating routing/predictors |
| 5. Recommended hybrid: wrappers + pure formula strategies + shared feature/policy services | Same external alias registry; independent singleton parameter objects; Option 3 files initially, optionally Option 2 dotted root later | Explicit commuter and approved generic non-commuter formula functions per mode | Two behavioural files plus one shared cost/feeder policy source; consistent precedence | Low policy mixing, contained equation duplication; excellent unit-testability; clear thesis equations; modest upfront boundary design and cache validation |

## 20. Advantages, disadvantages and design boundaries

Option 1 is attractive only if the approved non-commuter specification is reduced to the same form; the present code evidence does not justify that assumption. Mathematical expressibility via a superset is not evidence that a coefficient-only patch is sufficient. Non-commuter PT income must be controllable independently of its car income response; per-group copies of a single global exponent alone cannot do that while reusing unchanged C estimators.

Option 2 gives a coherent one-file snapshot but requires explicit object construction and namespaces; `applyFile` cannot consume ordinary nested YAML without adaptation. Option 3 minimises changes to the loader but needs path validation and a reproducibility manifest to avoid accidentally pairing files from different calibrations. Neither grants two Guice instances merely by adding another YAML.

Option 4 is the clearest formula comparison but wholesale class copying would duplicate constraints, prediction and cost bugs. Option 5 isolates only arithmetic and parameter ownership, retaining C infrastructure and externally accessible calibration. Its costs are new wrapper/feature interfaces, a migration policy for old keys, and cache/concurrency validation. This is preferred over copying whole old modules.

## 21. Recommended architecture — no implementation

Proposed flow:

```text
C routing + availability + trip/tour constraints
  → routed person/trip candidate
  → existing mode alias → per-mode delegating estimator
  → validate PopulationUtils.getSubpopulation(person)
  → shared C feasible-candidate checks + trip features + monetary policy result
  → commuter formula(commuter parameters) OR approved non-commuter formula(non-commuter parameters)
  → existing EqasimUtilityEstimator epsilon/policy integration once
  → existing cumulative tour utility and MNL selector
```

Initial file arrangement: one commuter behavioural YAML, one non-commuter behavioural YAML, and a shared monetary policy file covering car/MC/PT/direct ODT and feeder fare/subsidy/cap. Each behavioural file uses that object's ordinary dotted fields and is independently overlaid on its approved defaults; use explicit namespaced CLI overrides, e.g. a future `--mode-parameter:commuter.car.alpha_u ...` contract. Those example new CLI namespaces **do not yet exist**. Current unscoped overrides should fail or require explicit documented migration when ambiguous, not silently apply to one or both groups.

Maintain current commuter equations initially, including inherited residual D, unless the researcher approves a correction as a separately documented change. Build a corrected generic non-commuter specification from approved legacy terms, not the old latent-class dispatcher. Shared F/AF repairs, home-side routing, safe string comparisons and shared policy costs are mandatory preservation boundaries. Exact non-commuter coefficients/terms remain gated by §25.

Reference-income fallback should be deterministic and owned by the selected behavioural specification or an explicitly agreed shared imputation rule; do not retain the fresh-default predictor override accidentally. Construct immutable/read-only parameter views after startup loading, log final values and source precedence, and record hashes of external files. No rebuilding should be needed for either group's future calibration.

## 22. Exact Java files likely to require future changes

Paths below are relative to repository `/Users/fazafawzan/git/eqasim-java`; nothing in this list was modified. The appendix provides clickable current-source links.

**Existing likely touchpoints:**

- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java` — qualified parameter providers, scoped overlays, wrappers/aliases, shared cost bindings.
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaModeParameters.java` — behavioural schema split/migration, approved defaults.
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaCostParameters.java` — shared feeder policy ownership if moved here or to a separate policy object.
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarUtilityEstimator.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaWalkUtilityEstimator.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMotorcycleUtilityEstimator.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMcodtUtilityEstimator.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarodtUtilityEstimator.java` — retain/extract commuter arithmetic and introduce delegation without copying infrastructure.
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPtPredictor.java` — inject shared policy ownership; retain current feature/equation semantics unless separately approved.
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPersonPredictor.java`
- `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPredictorUtils.java` — explicit reference-income fallback/feature semantics.
- `jakarta/src/main/java/org/eqasim/jakarta/RunSimulation.java` — future CLI/path support if not entirely handled by extension config; preserve final alias registration.

**Potential new files (architecture proposals, do not exist by this audit):**
`jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/CommuterModeParameters.java`, `NonCommuterModeParameters.java`, `JakartaPolicyCostParameters.java` in that parameters directory; `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/SubpopulationUtilityEstimator.java`; `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/SubpopulationBehaviourSelector.java`; and approved per-mode formula classes under `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/formulas/`. Final names are a design choice, not an implemented API.

**Expected preserved existing files:** all five `mode_choice/costs/Jakarta*CostModel.java` calculations (except separately approved policy extraction), `JakartaModeAvailability.java`, ODT/MC predictors, person-variable types if unchanged, `JakartaConfigurator.java`, routing classes and `NoMotorcycleEgressExceptHome.java`. They are regression dependencies, not invitations to redesign.

No edit to sibling `core/ParameterDefinition.java` alone would modify the published `org.eqasim:core:2.0.0` dependency. Prefer a Jakarta-local configuration/overlay adapter if needed. No changes to core EqasimUtilityEstimator, routing stop finder, POM/version are required merely to decide architecture in this audit.

## 23. Exact XML/YAML files likely to require future changes

All existing files below are under `/Users/fazafawzan/Codex/matsim_jar_rev/config/`:

| File | Future approved action |
|---|---|
| `jakarta_config_BaselineModel.xml` | Select revised population; explicit group strategy settings; correct behavioural file paths/provider schema |
| `jakarta_config_scen_car_25pct_rev.xml` | Same population/groups/behavioural paths; retain shared car policy |
| `jakarta_config_scen_mc_25pct_rev.xml` | Same; retain shared private-MC policy |
| `jakarta_config_scen_carMc_25pct_rev.xml` | Same; retain shared paired policy |
| `jakarta_config_scen_mcOdt_25pct_rev.xml` | Same; migrate shared feeder-discount policy explicitly |
| `jakarta_config_scen_all_25pct_rev.xml` | Same; combined shared monetary policy |
| `UtilityParams_BaselineModel.yml` | Become commuter behavioural source or migrate to scoped schema; move feeder policy fields once |
| `UtilityParams_Scenario_rev.yml` | Avoid divergent copy of behavioural calibration across scenarios; migrate consistently |
| `UtilityParams_scen_mcOdt_25pct_rev.yml` | Separate the sole subsidy policy delta from behavioural coefficients |
| `JktCostParams_scen_car_25pct.yml` | Numerical rates can remain; schema update only if shared policy file is consolidated |
| `JktCostParams_scen_mc_25pct.yml` | Same |
| `JktCostParams_scen_carMc_25pct.yml` | Same |

Proposed new external files could be `UtilityParams_Commuter_3_0_0.yml`, `UtilityParams_NonCommuter_3_0_0.yml`, and a shared `JakartaPolicyCosts_Baseline_3_0_0.yml` plus scenario counterparts, all in that config directory. No such files were created. Resolve process-relative versus config-relative path policy explicitly. No population conversion is proposed, and no existing XML/YAML needs to be changed until the design is approved.

## 24. Required future test plan — designed, not implemented or run

Tests should compare independent hand-computed expected components, not merely mirror new implementation. Retain separate acceptance oracles: exact approved C behaviour; raw L diagnostic equations; corrected/approved non-commuter target. Record approved deviations from raw L rather than forcing equality with its bugs or different policy costs.

| Area | Required cases and assertions |
|---|---|
| Parameter loading | Verify every default; partial commuter overlay changes only commuter; partial non-commuter overlay changes only non-commuter; highest-precedence scoped CLI wins; unchanged missing values deterministic; unknown keys/malformed numbers fail; actual nested maps rejected or explicitly flattened by approved loader; missing file fails; file paths tested under different process working directories. Check final-file hashes and effective values. |
| Ownership/isolation | Guice obtains independent immutable behavioural objects and one shared policy object; repeated interleaved group evaluations never mutate defaults/other group; named aliases all resolve; inherited ModeParameters consumers use intended features, not accidental commuter-only fallback. |
| Subpopulation selection | Exact commuter/non_commuter labels select correct path for all six modes; unknown, plural old label, null fail with clear person context under proposed strict contract. An employed person with no daily work uses non-commuter; a commuter on a non-work trip still uses commuter. |
| Feature units | Seconds→minutes; metres→km; route versus Euclidean distance independently varied; reference-income units matched; zero income .001 clamp; missing income approved fallback; valid typed age/sex/employment and wrong-type failure cases. No accidental entrepreneur/full-time inference. |
| Car utility | Independent ASC/time/parking/cost×D×H/age/distance increments; direct access-walk term present only in approved path. Hold route cost fixed while varying d to detect inherited D. |
| PT utility | IVT, post-first waiting, first-wait exclusion, line-switch call absence, AF, age, employment proxy, ASC source, H matrix all tested individually. Raw L generic early-return oracle has no H or pt ASC. Approved non-commuter expected deviations documented. Test guards before both formulas. |
| Walk utility | Age/distance matrix; thresholds just below/equal/above 1800 and 3747 m for direct estimator diagnostic tests; hard duration limit around 3600 sec in integrated feasibility tests; distinguish inactive zero coefficient from removed term. |
| Motorcycle utility | Age/access matrix, D absent for commuter, H active, linear “short distance” no threshold; current route cost separate from raw L corridor charge. |
| Mcodt utility | Direct fare min/pickup, age, safe female indicator with `new String("f")`, male/null strings, D and H retained for commuter, linear distance zero/nonzero overrides; no standalone fare subsidy. |
| Carodt utility | Same gender safety; current D absent and H active; fare min/pickup; zero ODT access; no accidental use of mcodt feeder parameters. |
| Bike/other modes | Bound bike formula unchanged if invoked, availability normally excludes it; outside/car_passenger zero-utility handling remains. |
| Legacy regression | Fixed synthetic persons/trips compare raw L pseudoequations with approved non-commuter formula. Explicit exceptions: reference-identity gender bug, omitted feeder cost/time, L motorcycle policy surcharge, and approved ASC/waiting/age changes. No class dispatcher should be required. |
| Commuter regression | Fixed fixtures reproduce exact C equation including residual Car/PT/MO D and no waiting/line-switch calls, with supplied approved commuter YAML; then integration fixture validates named alias/feature/constraint path. |
| Pricing: car | For fixed positive r, 2.95→3.6875 gives ratio 1.25 for both group persons; b and every behavioural coefficient unchanged; other costs unchanged. |
| Pricing: MC | .59→.7375 ratio 1.25 for direct private MC in both groups; no restoration of corridor charge. Explicitly test current absence of private-MC feeder monetary increment. |
| Pricing: car+MC | Both exact ratios together; all other direct/feeder rates unchanged. |
| Pricing: mcodt feeder | Share 0→.25 with base0/perkm1.9/cap5. Test no feeder, one/both sides, F=0, F below/at/above20; example 2km discount .95, 12km discount5. Direct mcodt still max(4+2.5r,6); total PT cost reduction is not assumed 25%. Cap is aggregate per candidate. |
| Pricing: all | Combine both direct rate increments and identical feeder discount; no behavioural differences between scenario files; both group persons get identical monetary costs for identical route. |
| PT fare | Bus-only, rail-only, mixed bus+rail, multiple same-family transfers, unknown transit mode, no-PT fallback; expected 4/10 rule separately asserted. Direct carodt/mcodt minima and pickup unchanged in every scenario. |
| Feeder construction | Eligible mode/stage adjacency; walk/non_network_walk/MC/mcodt; carodt/transit_walk exclusion; positive/zero/undefined time; missing/NaN route distance. Guard approved failure semantics; ensure F and AF added once. |
| Cache/concurrency | Re-evaluate same trip with different routed elements and interleave groups/trips; test shared predictor cache assumptions and parallel DMC contexts. Ensure no stale monetary policy result or cross-group parameter leakage. |
| FLM | Preserve existing JakartaHomeSideRaptorStopFinderTest, JakartaHomeSideRoutingModuleTest, AnnotateHomeSideTripAttributesTest, HomeSideTripAttributesTest, NoMotorcycleEgressExceptHomeTest expectations. Cover startup/general and DMC routing, home→nonhome/nonhome→home/nonhome→nonhome, motorcycle hidden behind other feeder legs, missing metadata, uninspectable and no-actual-PT candidates. Verify both groups identically. |
| Strategy configuration | Parse every future XML; each of the two labels has exactly DMC .1 and KeepLastSelected .9 with original disabling/memory settings; no new ReRoute or time mutation; no accidental reliance on null group. Population path and behavioural/policy paths exist under documented resolution. |

No tests were added or executed in this audit. No MATSim, Jakarta simulation, Maven build/package, or calibration run was performed. Future execution should follow researcher approval and the project's release validation process.

## 25. Open researcher decisions (12 distinct items)

Every row below has status **REQUIRES RESEARCHER DECISION**. Engineering recommendations do not substitute for the missing scientific evidence.

| ID | Decision needing approval | Why source evidence is insufficient / proposed disposition |
|---|---|---|
| R1 | Validate the population/estimation meaning of both specifications | Revised MNL comments establish lineage, not commuter sample validity; old generic PT label does not establish a non-commuter estimation. Approve the non-commuter behavioural target before coding. |
| R2 | Approve legacy parameter provenance and runtime baseline | No historical YAML/launch record found. Decide whether legacy defaults are an acceptable starting reference; supply original calibration if available. Confirm whether the current differently named YAML is the intended baseline. |
| R3 | Non-commuter walk age/distance and soft cutoff | Old age may be genuine original preference; 1.8-km cutoff may be feasibility or calibration. Prefer shared C feasibility; retain old age only if justified, and explicitly decide any soft-cutoff exception. |
| R4 | Non-commuter car/MC variables and access treatment | Old car lacks age/distance; old MC has age/access; R-alignment commits do not prove all older terms were mistakes. Approve an explicit variable matrix, keeping shared prediction and no ad hoc new access penalty. |
| R5 | Mode-specific income/distance-cost interaction matrix | C Car/PT/MO retain D, MC/CO remove it; L generic PT has no H. Verify intended R equations and distance definition before changing residual interactions or importing old ones. |
| R6 | Corrected ODT gender coefficients and ASC | Equality fix is mandatory; old indicator orientation and -.42 coefficients cannot be blindly mapped to a female +.83 model. Approve non-commuter coding/sign/ASC from the estimated specification. |
| R7 | Non-commuter PT ASC | Raw L active generic.constant is 0, not declared pt.alpha=-3.5. Decide intentional generic behaviour versus historical omission. Keep external non-commuter ASC regardless. |
| R8 | Non-commuter PT waiting/transfer specification | L generic waiting active; L transfer utility absent; later removal aligns revised R. Approve desired non-commuter waiting and no invented transfer coefficient. |
| R9 | Employment versus full-time predictor semantics | Code tests employment=yes; no distinct full-time, entrepreneur, or work-today predicate. Confirm correspondence to estimated full-time variable; do not alter daily group membership to match employment. |
| R10 | PT feeder measurement edge cases and experiment scope | Shared C F/AF inclusion and cap are required; adjacency/zero-time/no-PT limitations are real. Decide whether later robustness refinements are separate work; do not mix them silently into the initial behavioural comparison. |
| R11 | Shared monetary reference versus raw L corridor charge | Raw L MC charge differs from current policy. Recommend C cost surface for both groups; approve this documented non-commuter regression deviation. If private-MC feeder pricing is intended, it requires a separate shared-cost extension. |
| R12 | Final external schema/ownership/failure contract | Approve Option5+Option3, shared feeder policy migration, scoped CLI precedence, strict unknown-group rejection, and deterministic missing-income handling. These determine reproducibility and compatibility; no implementation has begun. |

## 26. Final decision table

“Approved legacy” below means only the generic behavioural target after the R decisions, corrected for known coding errors and evaluated using shared C monetary/routing features. Proposed current commuter preservation remains subject to separately approved research corrections; it does not certify every residual current term matches an unavailable R model.

| Component | 2.8.0 commuter behaviour | Legacy behaviour | Difference type | Proposed commuter 3.0.0 | Proposed non-commuter 3.0.0 | Decision status |
|---|---|---|---|---|---|---|
| car utility | ASC,time,D×H cost,age,d | ASC,time,access,D×H cost | A / REQUIRES DECISION | Preserve C arithmetic initially | Approved old variable form on shared costs; R4/R5 | REQUIRES RESEARCHER DECISION |
| PT utility | ASC,AF,IVT,D×H cost,age,employment | generic ASC0,IVT,waiting,D cost; active class alternatives | A/B/C / REQUIRES DECISION | Preserve C formula/guards | Approved generic formula; shared corrected F/AF, no classes | REQUIRES RESEARCHER DECISION |
| walk utility | ASC,time,d; cutoff3.747km | ASC,time,age; cutoff1.8km | A / REQUIRES DECISION | Preserve C | Decide old age and cutoff independently | REQUIRES RESEARCHER DECISION |
| motorcycle utility | ASC,time,H cost,d | ASC,time,age,access,D×H cost | A / REQUIRES DECISION | Preserve C | Approved variable/interaction matrix, C costs | REQUIRES RESEARCHER DECISION |
| mcodt utility | ASC,time,age,female,D×H cost,d | age,unsafe sex,D×H cost,zero access | A/B / REQUIRES DECISION | Preserve C with safe equality | Corrected approved old form and coefficients | REQUIRES RESEARCHER DECISION |
| carodt utility | ASC,time,age,female,H cost | unsafe sex,D×H cost,zero access | A/B / REQUIRES DECISION | Preserve C | Corrected approved old form and coefficients | REQUIRES RESEARCHER DECISION |
| bike | core formula, unavailable normally | same | unchanged | Preserve | Shared until group evidence says otherwise | CLEAR |
| cost sensitivity | b plus mode-specific D/H | b,D/H except generic PT no H; class cost separate | A | Separate external commuter b | Separate external non-commuter b; no class b | REQUIRES RESEARCHER DECISION |
| income interaction | H all paid modes | H road modes, not generic PT | A | Preserve actual C matrix pending R5 | Approve per-mode H matrix | REQUIRES RESEARCHER DECISION |
| age | car,PT,ODT active; MC/walk absent | walk,MC,ODT active; PT helper dead | A/B | Preserve C | Approve variable matrix, never resurrect dead threshold blindly | REQUIRES RESEARCHER DECISION |
| sex/gender | safe female indicator | unsafe identity/else | B plus coefficient uncertainty | Keep safe coding | Safe coding, approve sign/ASC | REQUIRES RESEARCHER DECISION |
| employment/full-time effect | employment=yes PT effect | generic PT none | A | Confirm proxy against R9 | None unless approved estimated term | REQUIRES RESEARCHER DECISION |
| distance | linear Euclidean terms + residual mode-specific D | D for road/generic PT; no linear d | A / REQUIRES DECISION | Preserve actual C pending R5 | Approved matrix/units | REQUIRES RESEARCHER DECISION |
| access/egress | PT AF active; car/MC/ODT calls removed | car/MC active, ODT zero; PT A0 zero | C/B / REQUIRES DECISION | Preserve C | Share PT AF; decide old road access preferences | REQUIRES RESEARCHER DECISION |
| waiting | no PT waiting call | generic post-first waiting | REQUIRES DECISION | Preserve no waiting | Approve generic waiting term | REQUIRES RESEARCHER DECISION |
| PT transfers | no switch utility; fare uses bus/rail composition | generic no switch utility | no endpoint behavioural transfer change | No switch utility | No switch utility unless new approved specification | CLEAR |
| PT feeder cost | P+F−capped discount | P−capped discount | C | Share current corrected calculation | Same calculation before utility response | CLEAR |
| PT feeder time | AF added | AF omitted | C | Share current feature | Same feature with approved non-commuter beta | CLEAR |
| FLM routing rules | authoritative stop finder + constraints/guards | weaker legacy protection | C | Preserve C | Preserve same C rules | CLEAR |
| pricing cost models | shared current monetary policy | MC corridor charge additionally active | policy / REQUIRES DECISION | Shared C rates and cap | Shared C rates and cap; document deviation R11 | REQUIRES RESEARCHER DECISION |
| latent class | removed | active four-class PT dispatcher | A | None | None; use approved generic target | CLEAR |
| parameter-loading architecture | one object; defaults<YAML<CLI | same loader plus old fields | architecture / REQUIRES DECISION | Independent external object | Independent external object; shared policy | REQUIRES RESEARCHER DECISION |

## 27. Evidence index and reproducibility boundaries

The following source index identifies actual inspected files, endpoint ownership and representative method/field lines. C links resolve to the current checkout. L entries must be read with `git show 2dfb68d26928569357aee3364fe83e65f1e2a203:<path>`; a current checkout hyperlink must not be mistaken for old source.

| C source link | Repository path for git show | Representative C declaration lines | Inspected reference |
|---|---|---|---|
| [RunSimulation.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/RunSimulation.java:37) | `jakarta/src/main/java/org/eqasim/jakarta/RunSimulation.java` | 37 | L and C |
| [JakartaConfigurator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/JakartaConfigurator.java:1) | `jakarta/src/main/java/org/eqasim/jakarta/JakartaConfigurator.java` | class body | L and C |
| [JakartaModeChoiceModule.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java:112) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java` | 112, 126 | L and C |
| [JakartaModeAvailability.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeAvailability.java:17) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeAvailability.java` | 17 | L and C |
| [JakartaModeParameters.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaModeParameters.java:123) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaModeParameters.java` | 123 | L and C |
| [JakartaCostParameters.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaCostParameters.java:35) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaCostParameters.java` | 35 | L and C |
| [JakartaCarUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarUtilityEstimator.java:40) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarUtilityEstimator.java` | 40 | L and C |
| [JakartaPTUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java:106) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java` | 106 | L and C |
| [JakartaWalkUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaWalkUtilityEstimator.java:39) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaWalkUtilityEstimator.java` | 39 | L and C |
| [JakartaMotorcycleUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMotorcycleUtilityEstimator.java:57) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMotorcycleUtilityEstimator.java` | 57 | L and C |
| [JakartaMcodtUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMcodtUtilityEstimator.java:36) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMcodtUtilityEstimator.java` | 36 | L and C |
| [JakartaCarodtUtilityEstimator.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarodtUtilityEstimator.java:38) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarodtUtilityEstimator.java` | 38 | L and C |
| [JakartaPersonPredictor.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPersonPredictor.java:17) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPersonPredictor.java` | 17 | L and C |
| [JakartaPredictorUtils.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPredictorUtils.java:20) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPredictorUtils.java` | 20, 44 | L and C |
| [JakartaPtPredictor.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPtPredictor.java:54) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPtPredictor.java` | 54 | L and C |
| [JakartaMotorcyclePredictor.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaMotorcyclePredictor.java:30) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaMotorcyclePredictor.java` | 30 | L and C |
| [JakartaMcodtPredictor.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaMcodtPredictor.java:31) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaMcodtPredictor.java` | 31 | L and C |
| [JakartaCarodtPredictor.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaCarodtPredictor.java:31) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaCarodtPredictor.java` | 31 | L and C |
| [JakartaCarCostModel.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaCarCostModel.java:589) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaCarCostModel.java` | 589, 595 | L and C |
| [JakartaPtCostModel.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaPtCostModel.java:79) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaPtCostModel.java` | 79 | L and C |
| [JakartaMotorcycleCostModel.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaMotorcycleCostModel.java:566) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaMotorcycleCostModel.java` | 566 | L and C |
| [JakartaMcodtCostModel.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaMcodtCostModel.java:45) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaMcodtCostModel.java` | 45 | L and C |
| [JakartaCarodtCostModel.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaCarodtCostModel.java:45) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/costs/JakartaCarodtCostModel.java` | 45 | L and C |
| [NoMotorcycleEgressExceptHome.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java:56) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/constraints/NoMotorcycleEgressExceptHome.java` | 56, 62 | L and C |
| [WalkDurationConstraint.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/constraints/WalkDurationConstraint.java:28) | `jakarta/src/main/java/org/eqasim/jakarta/mode_choice/constraints/WalkDurationConstraint.java` | 28 | L and C |
| [JakartaHomeSideRaptorStopFinder.java](/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/ch/sbb/matsim/routing/pt/raptor/JakartaHomeSideRaptorStopFinder.java:47) | `jakarta/src/main/java/ch/sbb/matsim/routing/pt/raptor/JakartaHomeSideRaptorStopFinder.java` | 47 | C only |

### Dependency-source reference methods

In `core-2.0.0-sources.jar`: `ParameterDefinition.applyFile/applyMap/applyCommandLine`; `EqasimConfigGroup` path getters/setters and pseudo-random-error default; `EqasimConfigurator` registration order; `EqasimUtilityEstimator.estimateTrip`; `ModeParameters` nested defaults; `EstimatorUtils.interaction`; `CarUtilityEstimator` and `PtUtilityEstimator` protected monetary helpers; `WalkUtilityEstimator`, `BikeUtilityEstimator`; `CarPredictor.predict`, `PtPredictor.predict`, `PredictorUtils.calculateEuclideanDistance_km`, `CachedVariablePredictor.predictVariables`, `AbstractCostModel.getInVehicleDistance_km`, `EpsilonModule`, `PolicyModule`.

In `matsim-2026.0-2025w19-sources.jar`: `PopulationUtils.getSubpopulation` lines 1272–1278, `ReflectiveConfigGroup.fromString` null conversion, `ReplanningConfigGroup.StrategySettings`, `GenericStrategyManagerImpl.run/chooseStrategy` (subpopulation lookup and no-strategy failure at lines 233–237).

### External file hashes (SHA256)

These identify the inspected bytes, not a historical runtime linkage. Every baseline XML module and the full baseline YAML were read; all pricing XMLs were parsed and compared, and all active YAML maps compared. No launched command or output-config manifest was available to establish actual loaded filenames on an external machine.

| Inspected file | SHA256 |
|---|---|
| [JktCostParams_scen_carMc_25pct.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/JktCostParams_scen_carMc_25pct.yml) | `32a8cba383e009870861fb1a0c53802fc1b174f8da63b3e85f9c1ad1a402170c` |
| [JktCostParams_scen_car_25pct.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/JktCostParams_scen_car_25pct.yml) | `99abe266674e615c5fcba5405df986640168116f40fca0aa53ca5014b8f55544` |
| [JktCostParams_scen_mc_25pct.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/JktCostParams_scen_mc_25pct.yml) | `94bc6ffcf5d834128d2012b1ac637ddc3f53257e522f703d2019d4760b8b01d1` |
| [UtilityParams_BaselineModel.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/UtilityParams_BaselineModel.yml) | `1c535ed8a57ff7d1669e7241bc1892b3f456624bbab56c1303668e871a668b2f` |
| [UtilityParams_Scenario_rev.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/UtilityParams_Scenario_rev.yml) | `99c9f63c99b106a7b7930f04fa7233fbb2e77f0c37289d12a1ef9833d5bce932` |
| [UtilityParams_scen_mcOdt_25pct_rev.yml](/Users/fazafawzan/Codex/matsim_jar_rev/config/UtilityParams_scen_mcOdt_25pct_rev.yml) | `4c7a45535b677f1fd104cdb5a8289d3010f4031ca1a82647f6345261098e3c73` |
| [jakarta_config_BaselineModel.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_BaselineModel.xml) | `8eb14bddc1220e5929333a23ad96b1074036ff8765702ac334ab61d80919cb8d` |
| [jakarta_config_scen_all_25pct_rev.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_all_25pct_rev.xml) | `f6e937ed1bb85f89c52afe8f2f34c7641d91e77f67b0ef67fa2b58bb4fb6c87f` |
| [jakarta_config_scen_carMc_25pct_rev.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_carMc_25pct_rev.xml) | `e3335b0db97c0fd8438d86603ed576ac1794611bdd1310a041931ad5d02aa6f8` |
| [jakarta_config_scen_car_25pct_rev.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_car_25pct_rev.xml) | `c81514ceb61fdf1ce399be3063d86b8ed358dc87e40dd20988ad21deb5a4c2de` |
| [jakarta_config_scen_mcOdt_25pct_rev.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_mcOdt_25pct_rev.xml) | `456ac6df6b7b2177a27ff5c9697ae327a2cb109fea00a8613474ca56aba8070b` |
| [jakarta_config_scen_mc_25pct_rev.xml](/Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_mc_25pct_rev.xml) | `376d3ecf5c2bc42b0b779fb90d6f45313f2b057aaec606f5bfdaec9c5c307d46` |
| `/Users/fazafawzan/.m2/repository/org/eqasim/core/2.0.0/core-2.0.0-sources.jar` | `a7fb5142b63d062308178722f8ed4889dc9881ee2e017cc22b15556665bc4d72` |
| `/Users/fazafawzan/.m2/repository/org/matsim/matsim/2026.0-2025w19/matsim-2026.0-2025w19-sources.jar` | `1a7c8cd3a1e7c5ce4c45387a28ac8fbdc4788489488b48ae3e0078d8f27bf2ff` |

### Audit completion boundary

This report is the complete authorised artifact. Its proposed Java/config/file names, XML snippet and test plan are design material only. No production `jakarta_commuter_noncommuter_3.0.0` branch was created. Implementation must await researcher approval of the specification and architecture decisions.

