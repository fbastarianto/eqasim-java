# Jakarta commuter/non-commuter 3.0.0 implementation report

Date: 2026-09-07 (Asia/Jakarta).

Verdict: implementation, external migration, unit/integration tests, and clean shaded JAR build complete. **69 tests passed; zero failures, errors, or skips.** No simulation, calibration, commit, or push was performed.

## 1–3. Lineage and Git state

Starting branch: `jakarta_FLM_home_side_router_2.8.0`.
Starting and final HEAD: `8cca88c75300a1dcceaf38b36a8d40ef99d17ca9`.
Verified legacy reference: `jakarta_scenario_mcOdtFeeder` at `2dfb68d26928569357aee3364fe83e65f1e2a203`.
New branch: `jakarta_commuter_noncommuter_3.0.0`, created directly from the verified production branch. Git metadata write required sandbox escalation, which was approved.
No merge, cherry-pick, legacy checkout, or legacy code import occurred. Recovered external baseline YAML supplied the non-commuter numerical values. Neither untracked audit document was used as implementation evidence.

Initial status contained only these pre-existing untracked files:

- `.DS_Store`
- `jakarta/commuter_noncommuter_3_0_0_forensic_audit.md`
- `jakarta/commuter_noncommuter_3_0_0_forensic_audit_v1_wrong_in_pricing_interpretation.md`

They remain untouched. The final status is recorded at the end of this report; all changes are uncommitted and unstaged.

## 4–6. Architecture, selector, parameter classes/providers

The existing aliases `jCarEstimator`, `jPTEstimator`, `jWalkEstimator`, `jMotorcycleEstimator`, `jMcodtEstimator`, and `jCarodtEstimator` retain their bindings in `JakartaModeChoiceModule`. Each mode estimator delegates to `JakartaBehaviour`, which selects one of two distinct formula classes:

- `JakartaCommuterFormula`: current 2.8.0 equations.
- `JakartaNonCommuterFormula`: approved generic legacy equations, with current shared costs/features.

Exact selector: `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaSubpopulation.java`, method `select(Person)`.
It uses `PopulationUtils.getSubpopulation(person)` and accepts only `commuter` and `non_commuter`. Missing, non-string, unknown, plural, whitespace, and case variants fail with person ID and invalid value. Employment never selects a population.

Physical extraction: `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaBehaviour.java`, method `features(...)`, producing `JakartaTripFeatures`. Car uses the installed core 2.0.0 `CarPredictor.predict` with zero access/search constants; each formula applies its own startup parameters afterwards. Walk uses uncached core `WalkPredictor.predict`. Direct motorcycle/ODT features retain 2.8.0 single-leg/undefined-time conventions and invoke the existing shared cost models. Non-commuters cannot consume commuter coefficients through predictors.

Parameter classes under `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/`:

- `JakartaModeParameters`: commuter startup parameters, retaining 2.8.0 Java defaults, renaming `alpha_sex` to `alpha_female`, and removing all feeder-policy fields.
- `JakartaNonCommuterParameters`: independent generic non-commuter schema/defaults from the approved recovered YAML; no latent-class fields.
- `JakartaFeederPolicyParameters`: the four shared feeder monetary settings.
- `JakartaCostParameters`: existing shared direct-cost parameters, unchanged.
- `JakartaParameterLoader`: flat dotted-key YAML and scoped CLI loading; rejects ambiguous legacy overrides.

Providers in `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java`:
`provideModeChoiceParameters`, `provideNonCommuterParameters`, `provideFeederPolicyParameters`, `provideCostParameters`.
All parameter objects are singleton startup-loaded objects. The reflection-based parameter schemas are mutable during loading; production evaluation only reads them. There is no per-person parameter mutation or switching. The core `ModeParameters` binding remains for bike/compatibility; non-commuter formula evaluation does not use it.

`JakartaBehaviourConfigGroup` is registered by `JakartaConfigurator`. It supplies explicit commuter, non-commuter, and feeder-policy paths. The existing single DMC stack, availability, MNL selection, tour aggregation, and home-finder bindings remain.

PT cache safety: `JakartaPtPredictor.predictVariables` now recomputes each candidate and invokes uncached `PtPredictor.predict`. It creates a fresh augmented `PtVariables` and never mutates the delegate result. Feeder eligibility, minutes, and fare arithmetic remain unchanged. `JakartaPersonPredictor` is also uncached and validated; its former income fallback is bypassed even when the shared PT cost model calls it. Debug printing was removed from the PT predictor.

## 7–10, 21. External files

All new XML/YAML files are in `/Users/fazafawzan/Codex/matsim_jar_rev/config`. Original 2.8.0 and recovered legacy source files were not overwritten.

Behaviour files, shared by all six scenarios:

- `UtilityParams_Commuter_3_0_0.yml`: active values from `UtilityParams_BaselineModel.yml`, minus feeder-policy settings; sex coefficients explicitly renamed female. Existing inactive waiting/line-switch calibration fields and common bike values are retained for compatibility, but the commuter PT formula does not use waiting/line-switch utility.
- `UtilityParams_NonCommuter_3_0_0.yml`: required generic fields from `Legacy_old_branch_UtilityParams_Baseline.yml`; excludes class1–4, policy, bike, unused `pt.alpha_u`, and line-switch fields. ODT sex coefficients explicitly renamed male.

Shared feeder-policy files:

- `FeederPolicy_Baseline_3_0_0.yml`: base 0, per-km 1.9, share 0, cap 5.
- `FeederPolicy_scen_mcOdt_25pct_3_0_0.yml`: base 0, per-km 1.9, share .25, cap 5.

| New XML | Shared direct-cost source | Shared feeder-policy source |
|---|---|---|
| jakarta_config_BaselineModel_3_0_0.xml | JakartaCostParameters defaults | Baseline |
| jakarta_config_scen_car_25pct_3_0_0.xml | JktCostParams_scen_car_25pct.yml | Baseline |
| jakarta_config_scen_mc_25pct_3_0_0.xml | JktCostParams_scen_mc_25pct.yml | Baseline |
| jakarta_config_scen_carMc_25pct_3_0_0.xml | JktCostParams_scen_carMc_25pct.yml | Baseline |
| jakarta_config_scen_mcOdt_25pct_3_0_0.xml | JakartaCostParameters defaults | scen_mcOdt_25pct |
| jakarta_config_scen_all_25pct_3_0_0.xml | JktCostParams_scen_carMc_25pct.yml | scen_mcOdt_25pct |

Each XML uses `jakarta_population_subpop.xml.gz`, `eqasim.modeParametersPath=null`, and the same two explicit behavioural paths. For each population it contains DMC .1 and KeepLastSelected .9, both disableAfterIteration -1. There are no newly weighted strategies. All other active config fields match their corresponding source except the version-suffixed output directory. Commented historical strategy blocks were removed from the new copies to keep strategy settings unambiguous.

**Path resolution:** the existing deployment convention is retained. Behavioural, feeder, and direct-cost paths such as `input/UtilityParams_Commuter_3_0_0.yml` are resolved by `new File(...)` relative to the JVM working directory. MATSim input paths such as the population/network/schedule are resolved against the loaded XML's context directory. For later deployment, place YAMLs in the run directory's `input/` and the XML beside its population and other existing scenario inputs, or explicitly configure absolute paths. These external source XMLs are deployment templates; they are not a complete local runnable scenario layout. No input staging or simulation was performed. Tests resolve external source filenames directly and can use `-Djakarta.release.config=/absolute/config/directory` on another machine.

## 11–12. Implemented equations

Minutes are routed travel time `T`, physical access/egress `A`, waiting `W`; `d` is Euclidean km; `C` is the shared monetary cost in KIDR. For each group's parameters:

`D = (max(0.001, d) / referenceEuclideanDistance_km)^lambdaCostEuclideanDistance`

`H = (max(0.001, hhlIncome) / avg_hhl_income)^lambda_income`

These preserve core `EstimatorUtils.interaction`, including its existing minimum clamp. Both groups use reference distance 7.67, distance exponent -.75, reference income 5327, income exponent -.06. `S` is the group's car parking-search constant; `A0` its additional car access/egress constant. Both are zero under the supplied release calibration. The commuter car predictor's search-time addition remains represented by `T+S`; non-commuter car and motorcycle access terms use their own `A0`.

With the new commuter YAML:

```text
car        = -1.370 - .054*(T+S) - .019*D*H*C + .102*d + .026*age
pt         = -1.100 - .052*A - .013*T - .019*D*H*C - .031*age + .808*I(employment="yes")
walk       = -1.070 - .054*T + 0*d - 1500*I(d*1000 > 3747)
motorcycle = -0.330 - .054*T - .019*H*C + 0*d
mcodt      = -2.820 - .054*T - .017*age + .83*I(sex="f") - .019*D*H*C + 0*d
carodt     = -4.924 - .0826*T - .017*age + .83*I(sex="f") - .019*H*C
```

The symbolic car/walk/motorcycle/mcodt distance slopes remain parameterised exactly as in 2.8.0. Commuter walk has no age; motorcycle has no age or D cost multiplier; carodt has no D multiplier. PT has no waiting or line-switch utility. Safe `"f".equals(...)` implements female dummies.

With the new non-commuter YAML:

```text
car        = -.514 - .04*(T+S) - .007*(A+A0) - .0208*D*H*C
walk       = -4.5 - .007*T + .0103*age
motorcycle = 0 - .0948*T - .007*(A+A0) - .0083*age - .0208*D*H*C
mcodt      = -2.5 - .1652*T - .0132*age - 1.15*I(sex="m") - .0208*D*H*C
carodt     = -5 - .0826*T - .0132*age - .42*I(sex="m") - .0208*D*H*C
pt         = -3.5 - .05*A - .005*T - .05*W - .0208*D*C
```

Non-commuter motorcycle's physical A is zero under the existing single-leg predictor convention, and A0 is zero in the recovered YAML. No nonzero access time is invented. Non-commuter PT uses `jPT.generic.constant=-3.5`, current feeder minutes and costs, no H, age, employment, line-switch, or generic.cost tail. Non-commuter walk has no distance penalty, including no historical 1.8-km/-1500 penalty. Non-commuter car has no age/distance slope. Non-commuter motorcycle/ODT have no linear distance slope. Safe `"m".equals(...)` implements male dummies.

## 13–15. Latent classes, infrastructure and pricing

No latent-class dispatch exists in the new behavioural architecture. Neither behavioural schema contains class1–4 fields, and such overrides fail. No legacy roadpricing/corridor surcharge was restored. The audited production branch already contains 19 excluded `jakarta/roadpricing` source files; these are untouched, still excluded by the existing compiler configuration, and absent from the clean release JAR's `org/eqasim/jakarta/roadpricing/` namespace. Existing external dependencies may still contain their own standard MATSim roadpricing classes; they were not added by this release.

Routing, home-side RAPTOR, home annotations/metadata, all trip/tour constraints, direct cost models, JakartaModeAvailability, and external core source were verified unchanged against the starting commit. JakartaConfigurator only registers the local behaviour config group. PT defensive guard bodies are unchanged. Bike is not newly available; outside/car_passenger availability and zero-utility handling are preserved.

Shared prices for identical candidates are independent of subpopulation:

```text
car C        = carCost_KIDR_km * routed car km
motorcycle C = motorcycleCost_KIDR_km * routed motorcycle km
mcodt C      = max(4 + 2.5 * routed standalone mcodt km, 6)
carodt C     = max(6 + 4.5 * routed standalone carodt km, 10)
PT base fare = existing 4 KIDR single bus/rail category or 10 KIDR mixed bus/rail fare
F            = base_mcodt * eligible mcodt leg count + per_km_mcodt * eligible mcodt route km
discount     = min(subsidyShare_mcodt * F, maxDiscountMU_mcodt)
PT C         = PT base fare + F - discount
```

Baseline car/motorcycle rates: 2.95/.59. Car scenario: 3.6875/.59. MC scenario: 2.95/.7375. CarMC/all: 3.6875/.7375. Only mcOdt/all use feeder discount `min(.25*F,5)`; all use feeder rate 1.9 and base 0. Standalone ODT fares remain unchanged in every scenario. The standalone mcodt rate 2.5 is retained solely for standalone trips, never used as the PT feeder basis.

## 16–18. Loading, CLI and validation

Each independent parameter object follows Java defaults < flat dotted-key YAML via `ParameterDefinition.applyFile` < scoped CLI via `ParameterDefinition.applyCommandLine`.

Supported examples:

```text
--commuter-mode-parameter:car.alpha_u -1.370
--non-commuter-mode-parameter:jPT.generic.constant -3.5
--commuter-mode-parameter:jMcodt.alpha_female 0.83
--non-commuter-mode-parameter:jMcodt.alpha_male -1.15
--feeder-policy-parameter:subsidyShare_mcodt 0.25
--cost-parameter:carCost_KIDR_km 3.6875
```

The old `--mode-parameter:<field>` is explicitly rejected with an explanation before scenario loading. A non-null legacy `eqasim.modeParametersPath` is rejected by the commuter provider. No ambiguous backwards-compatibility rule exists. Missing explicit paths retain their independent Java defaults; supplied release XMLs explicitly configure both group YAMLs and feeder policy. Ordinary nested YAML is not introduced.

`JakartaPersonData.read` requires numeric finite positive hhlIncome and numeric finite non-negative integral age. Sex must be m/f where needed; startup preflight requires m/f for the population because ODT modes can require it. Missing/invalid values report person ID, field and value. No income imputation occurs. Full-time employment is safe `"yes".equals(...)`, never population classification.

Startup validation in RunSimulation follows population loading and precedes scenario adjustment/routing. Mode delegates validate on evaluation as well. Read-only streaming validation of the supplied population found 297,468 persons: commuter 123,797; non_commuter 173,671; no invalid required attributes; age range 1–99. The population file was not modified.

## 19–20. Changed/new Java files

Changed Java files:

- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/JakartaConfigurator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/RunSimulation.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaModeParameters.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarodtUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMcodtUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMotorcycleUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaWalkUtilityEstimator.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPersonPredictor.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPtPredictor.java`

New production Java files:

- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaBehaviourConfigGroup.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaBehaviour.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaCommuterFormula.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaNonCommuterFormula.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaPersonData.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaSubpopulation.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaTripFeatures.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaFeederPolicyParameters.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaNonCommuterParameters.java`
- `/Users/fazafawzan/git/eqasim-java/jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaParameterLoader.java`

Only `jakarta/pom.xml` changes version (2.8.0 → 3.0.0); dependency versions and other module POMs are unchanged.

## 22–24. Tests and build

New test classes:

- `JakartaBehaviourTest` (35 cases): selector, invalid values, employment independence, attribute failures, six commuter and six non-commuter hand calculations, string content sex coding, PT excluded effects, walk thresholds, independent precedence/overrides, shared policy precedence, legacy rejection, actual aliases/interleaving, neutral access/search extraction.
- `JakartaPricingAndFeederTest` (10 cases): six pricing scenarios, population-independent costs, standalone ODT fares, feeder rate/cap, feeder eligibility/minutes, 200 concurrent evaluations, repeated candidate evaluation, original guards/constraints and availability, real core PT waiting and bus/rail cost extraction with changed candidates sharing trip identity.
- `JakartaReleaseConfigTest` (8 cases): all six MATSim config parses, per-group strategies/paths/provider loading, unsupported schema fields/legacy path rejection, production module injector and all six aliases without simulation execution.

Existing 16 routing/FLM tests retained unchanged. New tests added: 53 cases. Final total: 69.

The initial existing suite passed 16/16. Development runs exposed only test-harness issues (MATSim person factory API, ambiguous test import, omitted transit flag and temporary output directory); these were corrected. No scientific changes were made in response. The final clean build reran the entire suite successfully:

```shell
mvn -o -f jakarta/pom.xml clean package
```

Executed from `/Users/fazafawzan/git/eqasim-java`, Java 21.0.8. The Jakarta POM uses the existing external eqasim core 2.0.0 dependency; repository core is not rebuilt or modified. No skipped-test flag was used. Earlier targeted commands used `mvn -o -f jakarta/pom.xml test` and `mvn -o -f jakarta/pom.xml -Dtest=JakartaReleaseConfigTest test`.

Final suite results:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| JakartaHomeSideRaptorStopFinderTest | 8 | 0 | 0 | 0 |
| JakartaBehaviourTest | 35 | 0 | 0 | 0 |
| JakartaPricingAndFeederTest | 10 | 0 | 0 | 0 |
| JakartaReleaseConfigTest | 8 | 0 | 0 | 0 |
| NoMotorcycleEgressExceptHomeTest | 3 | 0 | 0 | 0 |
| AnnotateHomeSideTripAttributesTest | 2 | 0 | 0 | 0 |
| HomeSideTripAttributesTest | 1 | 0 | 0 | 0 |
| JakartaHomeSideRoutingModuleTest | 2 | 0 | 0 | 0 |

Full build log: `/Users/fazafawzan/git/eqasim-java/jakarta/target/commuter_noncommuter_3_0_0_build.log`.
JUnit reports: `/Users/fazafawzan/git/eqasim-java/jakarta/target/surefire-reports/`.
Population QC: `/Users/fazafawzan/git/eqasim-java/jakarta/target/commuter_noncommuter_3_0_0_population_qc.json`.
`git diff --check` passed. Active XML field comparison confirms only approved migration fields/output paths changed. Protected-source comparisons are empty. JAR inspection confirms new selector/formula classes are present and excluded Jakarta roadpricing classes are absent.

## 25–27. Artifact and SHA256

Final shaded release JAR: `/Users/fazafawzan/git/eqasim-java/jakarta/target/jakarta-3.0.0.jar`.
SHA256: `6d9f8420fe7103a0423b5fbc66e8ac6508dfc56a512e4bbacbfb13bdbb216c1d`.
The separate `original-jakarta-3.0.0.jar` is the unshaded intermediate, not the release artifact.

SHA256 manifest: `/Users/fazafawzan/git/eqasim-java/jakarta/target/commuter_noncommuter_3_0_0_sha256.txt`.

```text
6d9f8420fe7103a0423b5fbc66e8ac6508dfc56a512e4bbacbfb13bdbb216c1d  /Users/fazafawzan/git/eqasim-java/jakarta/target/jakarta-3.0.0.jar
a14bc137853ca2d4a73aa7b052da2059b6fa33fdf5da6a848b97c21290cbb761  /Users/fazafawzan/Codex/matsim_jar_rev/config/FeederPolicy_Baseline_3_0_0.yml
b44fc4fd58a9a52a1d05539ee0c7f78db72c43a4395932b0f3299f58c3eb10f4  /Users/fazafawzan/Codex/matsim_jar_rev/config/FeederPolicy_scen_mcOdt_25pct_3_0_0.yml
a8232548f50eaeee10209dc53457f12417e10600da6ddb91a977ad1fc6b86283  /Users/fazafawzan/Codex/matsim_jar_rev/config/UtilityParams_Commuter_3_0_0.yml
a95f88164aa7160d000c7784692fea1e5cf133fa02f0a1da82b3a2e34c0f9196  /Users/fazafawzan/Codex/matsim_jar_rev/config/UtilityParams_NonCommuter_3_0_0.yml
f06aa616ac680298146694dd612c75b49d0798f83d710173da733d9d01061fb4  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_BaselineModel_3_0_0.xml
188ef8c94b4bd3ee45335ece9347d94e9078564a36dbbaf01f51d27bb6a009ac  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_all_25pct_3_0_0.xml
2a880e3014eda1af949ed9fda1198b7e3600614fe705f14e3c7aef5c0d53efe2  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_carMc_25pct_3_0_0.xml
4e9fe6b2f93e528998f6d32933959a16c7af8f3751ef2c71d5b5d1df49ea0088  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_car_25pct_3_0_0.xml
0fb857b6427ae4dfd2a56cf63ee4b2659f3d842c71f0c856a418b0eb13d08f93  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_mcOdt_25pct_3_0_0.xml
65bb69ceca40b2613976406dcf4d04ca5d58895b7ec41f30fe89b6596cb292d6  /Users/fazafawzan/Codex/matsim_jar_rev/config/jakarta_config_scen_mc_25pct_3_0_0.xml
```

## 28–29. Deviations and remaining risks

No behavioural, scientific, routing, feasibility, or pricing deviation. Implementation choices within the approved scope: one shared uncached neutral extractor; startup-loaded reflective parameter objects rather than a new immutable parser; early population validation; versioned output directories in the new templates; removed PT debug prints. No new complete DMC stack or population-specific monetary cost model.

The existing dependency configuration still emits non-fatal upper-bound warnings (including GeoTools, Guava, commons-io and Jackson databind), existing deprecated API notices, and shade duplicate classes/resources warnings. Dependency versions were intentionally not changed. See the saved build log for the full warnings. Successful unit/integration/build checks do not establish full simulation or calibration validity.

Before any separately approved small smoke test, stage the deployment layout and input files, verify output-directory accessibility, and review runtime dependency warnings. Uncached extraction can increase computation relative to the former trip-only cache; performance has not been benchmarked. The existing motorcycle parking-continuity limitation remains outside this release's scope. No full Greater Jakarta model, six-scenario simulation, calibration, smoke test, Git commit, or push was performed.

The task stops at this report and the built release for researcher review.

## Final Git status

```text
 M jakarta/pom.xml
 M jakarta/src/main/java/org/eqasim/jakarta/JakartaConfigurator.java
 M jakarta/src/main/java/org/eqasim/jakarta/RunSimulation.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaModeChoiceModule.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaModeParameters.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaCarodtUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMcodtUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaMotorcycleUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaPTUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/estimators/JakartaWalkUtilityEstimator.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPersonPredictor.java
 M jakarta/src/main/java/org/eqasim/jakarta/mode_choice/utilities/predictors/JakartaPtPredictor.java
?? .DS_Store
?? jakarta/commuter_noncommuter_3_0_0_forensic_audit.md
?? jakarta/commuter_noncommuter_3_0_0_forensic_audit_v1_wrong_in_pricing_interpretation.md
?? jakarta/commuter_noncommuter_3_0_0_implementation_report.md
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/JakartaBehaviourConfigGroup.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaBehaviour.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaCommuterFormula.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaNonCommuterFormula.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaPersonData.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaSubpopulation.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaTripFeatures.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaFeederPolicyParameters.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaNonCommuterParameters.java
?? jakarta/src/main/java/org/eqasim/jakarta/mode_choice/parameters/JakartaParameterLoader.java
?? jakarta/src/test/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaBehaviourTest.java
?? jakarta/src/test/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaPricingAndFeederTest.java
?? jakarta/src/test/java/org/eqasim/jakarta/mode_choice/behaviour/JakartaReleaseConfigTest.java
```
