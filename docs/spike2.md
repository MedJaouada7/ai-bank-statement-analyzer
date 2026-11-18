# Spike 2: Spring AI structured output with Ollama (native vs prompt-based)

**Date:** 18/11/2026  
**Test:** `SpringAiStructuredOutputSpikeTests` (skipped unless `OLLAMA_LIVE=true`)  
**Baseline:** Spike 1 (`tools/spikes/ollama-classify.ps1`, see `docs/spike1.md`)

## Question

Can Spring AI 2.0 return a valid category from a local Ollama model reliably, with latency close to the raw HTTP call from Spike 1? Does native structured output preserve accuracy parity with the HTTP baseline when both clients use identical prompts and label casing?

If not, the plan was to implement a `RestClient` fallback behind `CategorizerPort`.

## Environment

| Item | Value |
|---|---|
| Machine | i7-8565U, 8 GB RAM, CPU-only (see Spike 1) |
| Model | `llama3.2:3b-instruct-q4_K_M` |
| Stack | Spring Boot 4.1.2, Spring AI 2.0.1 |
| Client options | `OllamaChatOptions`: temperature 0, seed 42, numCtx 2048, numPredict 40, keepAlive 10m |

## Method

- Used the same 19 frozen cases as Spike 1, with normalized input and four few-shot examples provided as chat turns.
- Tested a Java record containing one enum with the seven category labels as the response type.
- Compared two Spring AI structured-output modes:
  - **Mode A – Prompt-based:** `.entity(CategoryResult.class)`.
  - **Mode B – Native:** `.entity(CategoryResult.class, o -> o.useProviderStructuredOutput())`.
- Routed calls through a WireMock proxy to the real Ollama instance to capture the exact request bodies.
- Performed one warm-up call followed by 19 timed calls per mode, initially with one run per mode.
- Inspected the captured request bodies to verify how each mode handled structured output and prompt formatting.
- Extended the experiment with additional checks:
  - Verified that temperature, seed, context size, prediction limit and keep-alive options reached Ollama.
  - Ran native structured output twice to check repeatability.
  - Compared native Spring AI against a modified copy of the PowerShell script using the same uppercase labels, examples and prompt text.

## Results

### Initial comparison

| Mode | Valid output | Correct | Median latency |
|---|---:|---:|---:|
| HTTP baseline, original (Spike 1, capitalized labels) | 19/19 | 17/19 (89%) | 1.17 s |
| HTTP baseline, uppercase labels and examples | 19/19 | 13/19 (68%) | 1.32 s |
| A. Spring AI prompt-based (uppercase labels) | 19/19 | 13/19 (68%) | 5547 ms (4.7x baseline) |
| B. Spring AI native (uppercase labels) | 19/19 | 14/19 (74%) | 1295 ms (1.1x baseline) |

### Final native structured-output validation

| Check | Result |
|---|---|
| Options reach Ollama | Confirmed: `seed` 42, `temperature` 0.0, `num_predict` 40, `num_ctx` 2048, `keep_alive` "10m" |
| Schema constraint | `format` contains a JSON Schema with an `enum` of the seven labels |
| Repeatability | Two native runs produced identical outputs and the same four misses |
| Native run 1 | 15/19 correct, median 1324 ms |
| Native run 2 | 15/19 correct, median 1287 ms |
| Matched HTTP baseline | 13/19 correct, median 1.32 s |
| Pass rule | Passed: native (15/19) was not worse than the matched baseline (13/19), and the options were confirmed |

The two-case difference in favour of native structured output is within noise at n = 19. The request bodies also differed slightly between clients (`$schema`, `additionalProperties`, and `num_predict` 40 vs 20), so this does not establish that Spring AI is more accurate than raw HTTP.

### Misses

**Initial native mode (14/19):**

- TOTALENERGIES (expected TRANSPORT, got UTILITIES)
- ORANGE (expected UTILITIES, got OTHER)
- LE COMPTOIR DU RELAIS (expected DINING, got COFFEE)
- M DUPONT LOYER (expected OTHER, got SHOPPING)
- RETRAIT DAB (expected OTHER, got SHOPPING)

**Initial prompt-based mode (13/19):**

- BLUE BOTTLE (expected COFFEE, got DINING)
- LIDL (expected GROCERIES, got SHOPPING)
- WHOLE FOODS (expected GROCERIES, got SHOPPING)
- TOTALENERGIES
- ORANGE
- LE COMPTOIR DU RELAIS

**Final native mode (15/19):**

- TOTALENERGIES (expected TRANSPORT, got UTILITIES)
- ORANGE (expected UTILITIES, got OTHER)
- LE COMPTOIR DU RELAIS (expected DINING, got COFFEE)
- M DUPONT LOYER (expected OTHER, got SHOPPING)

Two of the final misses (fuel and rent) cannot be represented correctly by the original seven-label taxonomy. Taxonomy v1 introduces `FUEL`, `HOUSING` and `TELECOM` to cover these types of transactions.

## What the request body showed

- **Native structured output:** The request body contained a `format` field with an enum constraint, meaning Spring AI sent a real schema constraint to Ollama. Messages consisted of the system prompt, four user/assistant example turns and the bare transaction text as the final user message.
- **Prompt-based structured output:** No `format` field was present. The final user message contained the transaction text followed by `\r\n` and additional output-format instructions beginning with "Your response sho...", consistent with Spring AI appending formatting instructions to the prompt. This also made the prompt longer and introduced an operating-system-dependent line separator.
- **Final native validation:** The request body confirmed that the configured options reached Ollama: temperature 0.0, seed 42, context size 2048, prediction limit 40 and keep-alive 10m. The `format` field contained a JSON Schema with an enum of the seven labels.

## Findings

1. **Native structured output provides reliable schema-constrained responses.** It produced valid categories on every call and included an explicit enum schema in the request sent to Ollama.

2. **Native structured output has substantially better latency than prompt-based structured output.** The initial median latency was 1295 ms for native mode versus 5547 ms for prompt-based mode (approximately 4.3x faster).

3. **Native latency is close to the raw HTTP baseline.** The initial native median was 1295 ms compared with 1.32 s for the matched HTTP baseline. Subsequent native runs remained stable at 1324 ms and 1287 ms.

4. **Repeatability was confirmed for the tested configuration.** Two native runs with temperature 0 and seed 42 produced identical outputs and the same four classification misses.

5. **Accuracy was comparable to the matched HTTP baseline, but parity is not established.** Native achieved 15/19 versus 13/19 for the matched baseline. This two-case difference is within noise at such a small sample size, and the request bodies were not completely identical.

6. **Label casing has a substantial effect on classification results.** The same PowerShell script, model and cases scored 17/19 with capitalized labels in Spike 1 and 13/19 with uppercase labels. Only the 3B model was rerun, so the effect on the 1.5B model remains unknown. Spike 1's accuracy figures should therefore be interpreted with this sensitivity in mind.

7. **Prompt wording also affects classification results.** Adding "Reply with JSON only" to the Java system prompt improved native structured output from 14/19 to 15/19, making RETRAIT DAB correct.

8. **The original taxonomy limits the interpretation of accuracy.** Several classification misses involve transaction types that the original seven-label taxonomy could not express. The updated taxonomy v1 addresses this with dedicated categories.

These observations come from small, single-machine experiments on 19 cases that were also used during prompt development. They demonstrate sensitivity to prompt wording and label representation, not validated or generalizable accuracy improvements.

## Limitations

- Only 19 frozen cases were tested.
- The initial comparison used a single run per mode; repeatability was checked only for native structured output.
- The cases were also used during prompt development, so they are not an independent evaluation set.
- The matched comparison had small request-body differences, including `$schema`, `additionalProperties` and `num_predict` (40 vs 20).
- The WireMock proxy adds a small amount of overhead to each call.
- Results were obtained on one CPU-only machine with one model.
- The observed label-casing effect was tested only with the 3B model.
- The original seven-label taxonomy was insufficient for some transaction types. Real-system accuracy must be evaluated using the updated taxonomy.
- Results on these 19 cases are not representative of production accuracy. The actual evaluation will use `eval/datasets/bsa-eval-v1`.

## Decisions

1. **Will use native structured output in the Spring AI adapter.** The adapter will use `useProviderStructuredOutput()` with an explicit `OllamaChatOptions` instance. Prompt-based structured output will not be used.

2. **Will not implement the `RestClient` fallback now.** It remains a possible alternative behind `CategorizerPort`, but the validation pass rule was satisfied: native achieved 15/19 against 13/19 for the matched baseline, and the configured options were confirmed in the request body.

3. **Will not claim accuracy parity or superiority over raw HTTP.** The experiment supports the choice based on schema enforcement, valid output and latency, not a statistically meaningful accuracy comparison.

4. **Will keep the native structured-output configuration explicit.** `SpringAiCategorizer` must pass an explicit `OllamaChatOptions` instance on every call. Spring AI can ignore the native structured-output flag when the options are not a supported type, potentially falling back silently to prompt-based behavior.

5. **Will add a WireMock integration test.** Assert that the outgoing request body contains a `format` field with an enum schema and the expected options. This protects against silent fallback after Spring AI upgrades.

6. **Will version and stabilize prompts.** Store prompts in versioned files (`prompts/categorization-v1.st`). Any wording change creates a new prompt version and requires a new evaluation run. Keep prompts static and identical across operating systems.

7. **Will evaluate label representation separately.** Treat the choice between enum constants (e.g. `CASH_WITHDRAWAL`) and readable labels (e.g. `Cash withdrawal`) as an experiment in the evaluation harness, measured on the validation split.

8. **Will not tune further on the 19 spike cases.** All future accuracy claims must come from `eval/datasets/bsa-eval-v1`.


## Reproduce

```powershell
$env:OLLAMA_LIVE = "true"
.\mvnw\.cmd test "-Dtest=SpringAiStructuredOutputSpikeTests"
Remove-Item Env:OLLAMA_LIVE
```