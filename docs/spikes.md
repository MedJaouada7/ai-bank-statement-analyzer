# Spike 1: local LLM classification of bank transactions (CPU-only, 8 GB RAM)

**Date:** 15/11/2026 , 
**Script:** `tools/spikes/ollama-classify.ps1`

## Question

Can a small local model (1.5B to 3B parameters, Q4 quantization) classify bank
transaction descriptors into a fixed category list accurately and fast enough to serve
as the LLM stage of the enrichment pipeline, on a laptop with 8 GB of RAM and no
usable GPU?

## Environment

| Item | Value |
|---|---|
| CPU | Intel Core i7-8565U (4 cores), 1.80 GHz base |
| RAM | 8 GB installed, 7.88 GB usable |
| GPU | Radeon 520 (2 GB) and Intel UHD 620; inference ran on CPU |
| OS / runtime | Windows, native Ollama install, version 0.40.16|
| Chat models | `qwen2.5:1.5b-instruct-q4_K_M` (ID : 65ec06548149), `llama3.2:3b-instruct-q4_K_M` (ID : a80c4f17acd5) |

## Method

- 19 hand-written test cases (7 categories: Coffee, Groceries, Dining, Transport,
  Shopping, Utilities, Other). Each case has a raw descriptor, a hand-normalized
  merchant name, and an expected label. French and US descriptor styles are mixed.
- Two input forms per model: **raw** descriptor and **normalized** merchant name.
- Constrained call via `POST /api/chat`:
  - JSON schema with an `enum` of the 7 categories passed in `format`
  - system prompt with one-line category definitions
  - 4 few-shot examples as real user/assistant turns (none of them are test cases)
  - `temperature: 0`, `seed: 42`, `num_ctx: 2048`, `num_predict: 20`, `keep_alive: 10m`
- One warm-up call per run (loads the model, excluded from timing). Latency is the
  per-call wall time of the HTTP request; the table reports the median.
- Single run per configuration. Temperature 0 with a fixed seed makes it repeatable,
  but I did not repeat runs to measure variance.

## Iteration log (what failed first)

1. **Unconstrained prompt** ("Categorize this bank transaction into one word") sent to
   the 1.5B model returned a paragraph guessing it was a "check transaction". The model
   needs an explicit label set.
2. **Few-shot as one text block via `/api/generate`**, with Ollama's default sampling,
   gave unstable output: the same prompt returned different categories between runs,
   sometimes several lines (the model continued the example pattern), once a label not in
   the list ("Transfer"), and once with a "Category:" prefix. Wall times of 7 to 12 s
   were model load or swapping, not inference. Tokens/s ranged from about 8 to 26 and
   were not meaningful with only 3 to 8 output tokens.
3. **Fix:** `/api/chat`, few-shot as chat turns, schema-constrained `enum` output,
   temperature 0, fixed seed, category definitions, and a warmed-up model for timing.

## Results

| Model | Input | Correct | Approx. 95% interval | Median latency (warm) |
|---|---|---|---|---|
| qwen2.5:1.5b | raw | 9/19 (47%) | 27 to 68% | 0.61 s |
| qwen2.5:1.5b | normalized | 12/19 (63%) | 41 to 81% | 0.52 s |
| llama3.2:3b | raw | 15/19 (79%) | 57 to 91% | 1.30 s |
| llama3.2:3b | normalized | 17/19 (89%) | 69 to 97% | 1.17 s |

Intervals are Wilson 95% intervals for a binomial proportion with n = 19. Max latency
was between 0.62 s and 1.66 s across the four runs.

### Misses (llama3.2:3b, normalized)

| Input | Expected | Got | Diagnosis |
|---|---|---|---|
| `TOTALENERGIES` | Transport | Utilities | Ambiguous merchant (fuel stations and energy supply). Normalization dropped the word `STATION`. The payment channel (card vs SEPA direct debit) is the real signal. |
| `M DUPONT LOYER` | Other | Shopping | No Rent category in the spike taxonomy, and "Other" is a weak catch-all. `LOYER` is a keyword rule; a SEPA transfer to a person is a transfer. |

Both remaining errors fall in territory that deterministic rules should own, so in the
real pipeline neither would reach the LLM.

## Findings

- Constraining the output (schema enum, temperature 0, chat-turn examples) turned
  unusable outputs into valid labels. Prompt format and decoding settings mattered more
  than the model choice in the first iteration.
- The 3B model is clearly better than the 1.5B on both input forms (+6 and +5 cases).
- Normalization helped both models (+3 and +2 cases). With n = 19 the 3B gain is within
  noise.
- Warm latency is about 1.2 s per call for the 3B model, so a 300-item benchmark is
  roughly 6 minutes with no caching. Cold loads cost several seconds, so process
  transactions in batches.

## Limitations

- **Sample size:** 19 cases; the intervals above overlap for neighbouring rows.
- **Easy inputs:** most merchants are well-known chains, and normalization was done by
  hand. Unseen local merchants and an imperfect normalizer will lower accuracy.
- **Not held out:** the prompt was iterated on while looking at these cases, so they are
  development data. The real evaluation uses a separate set split by merchant.
- **Single run, single machine.**


## Memory observation

- Peak RAM during runs : 7.57 GB (96%) of 7.88 GB
- `ollama ps` while the 3B model was loaded: size=2.3 GB, processor=100% CPU.
- Swapping observed: yes 
- Settings used: `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`

## Decisions

1. **Default evaluation model:** `llama3.2:3b-instruct-q4_K_M`. The 1.5B model stays
   only as the small-model baseline for experiments.
2. **The LLM is the fallback, not the classifier.** Even at about 89% on easy inputs,
   rules, alias memory, and kNN should resolve most transactions first.
3. **The normalizer extracts the payment channel** (card, SEPA debit, SEPA transfer,
   ATM, cheque, fee) as a separate field, used by rules and passed to the prompt.
4. **Add rules for:** rent keywords (`LOYER`, `RENT`), SEPA transfers to individuals,
   ATM withdrawals, energy providers on SEPA debit, fuel brands on card payments.
5. **Taxonomy needs explicit classes** for Rent, Transfers, Cash withdrawal, Income, and
   Fees. "Other" is not a reliable catch-all for small models.
6. **Do not tune the prompt on these 19 cases.** They stay frozen so this spike remains
   reproducible.
7. **If the LLM underperforms on the real set,** restrict the schema `enum` to the
   kNN's top 2 to 3 candidates.
8. **Spring AI** should reproduce the same constrained call (schema enum, temperature 0,
   fixed seed).

## Reproduce

```powershell
foreach ($m in "qwen2.5:1.5b-instruct-q4_K_M","llama3.2:3b-instruct-q4_K_M") {
  foreach ($f in "raw","norm") {
    .\tools\spikes\ollama-classify.ps1 -Model $m -Form $f | Select-Object -Last 1
  }
}
```

## Appendix: script

<details>
<summary>tools/spikes/ollama-classify.ps1</summary>

```powershell
param(
  [string]$Model   = "llama3.2:3b-instruct-q4_K_M",
  [ValidateSet("raw","norm")][string]$Form = "norm",
  [string]$BaseUrl = "http://localhost:11434"
)

$categories = "Coffee","Groceries","Dining","Transport","Shopping","Utilities","Other"

$system = @"
You classify bank transactions into exactly one category.
- Coffee: cafes and coffee shops
- Groceries: supermarkets and food stores
- Dining: restaurants, fast food, bars
- Transport: rides, fuel, trains, transit, parking
- Shopping: retail stores and online marketplaces
- Utilities: electricity, gas, water, internet, phone
- Other: anything else, or unclear
Reply with JSON only.
"@

# Few-shot examples (must NOT appear in the test cases below)
$shots = @(
  @{ t = "AMZN MKTP"; c = "Shopping" },
  @{ t = "UBER TRIP"; c = "Transport" },
  @{ t = "MCDONALDS"; c = "Dining" },
  @{ t = "EDF";       c = "Utilities" }
)

# normalized | raw | expected
$cases = @(
  "BLUE BOTTLE|SQ *BLUE BOTTLE 0412 SAN FRANCISCO CA|Coffee",
  "STARBUCKS|PAIEMENT CB 1411 STARBUCKS PARIS 75002|Coffee",
  "CARREFOUR MARKET|CARTE X4521 14/11 CARREFOUR MARKET LYON|Groceries",
  "LIDL|PAIEMENT CB LIDL 0345 MARSEILLE|Groceries",
  "WHOLE FOODS|WHOLEFDS 10234 AUSTIN TX|Groceries",
  "SNCF|PAIEMENT CB SNCF INTERNET 1411|Transport",
  "TOTALENERGIES|CARTE 1411 TOTALENERGIES STATION NICE|Transport",
  "LYFT|LYFT *RIDE SAT 8PM SAN FRANCISCO CA|Transport",
  "ORANGE|PRLV SEPA ORANGE SA REF 8823|Utilities",
  "ENGIE|PRLV SEPA ENGIE ENERGIE|Utilities",
  "COMCAST|COMCAST CABLE COMM 800-266-2278|Utilities",
  "ZARA|CARTE X4521 ZARA FRANCE PARIS|Shopping",
  "IKEA|PAIEMENT CB IKEA SAINT-HERBLAIN|Shopping",
  "DECATHLON|CB DECATHLON LILLE 1411|Shopping",
  "PIZZA HUT|PAIEMENT CB PIZZA HUT NANTES|Dining",
  "CHIPOTLE|CHIPOTLE 2154 DENVER CO|Dining",
  "LE COMPTOIR DU RELAIS|CARTE X4521 LE COMPTOIR DU RELAIS PARIS|Dining",
  "M DUPONT LOYER|VIR SEPA M DUPONT LOYER NOVEMBRE|Other",
  "RETRAIT DAB|RETRAIT DAB 1411 BNP PARIS|Other"
)

$schema = @{
  type       = "object"
  properties = @{ category = @{ type = "string"; enum = $categories } }
  required   = @("category")
}

function Invoke-Classify([string]$text) {
  $messages = @(@{ role = "system"; content = $system })
  foreach ($s in $shots) {
    $messages += @{ role = "user";      content = $s.t }
    $messages += @{ role = "assistant"; content = (@{ category = $s.c } | ConvertTo-Json -Compress) }
  }
  $messages += @{ role = "user"; content = $text }

  $body = @{
    model      = $Model
    stream     = $false
    format     = $schema
    messages   = $messages
    keep_alive = "10m"
    options    = @{ temperature = 0; seed = 42; num_predict = 20; num_ctx = 2048 }
  } | ConvertTo-Json -Depth 10

  Invoke-RestMethod -Uri "$BaseUrl/api/chat" -Method Post -Body $body -ContentType "application/json"
}

$null = Invoke-Classify "WARM UP"          # loads the model; excluded from timing

$ok = 0
$times = @()
foreach ($line in $cases) {
  $norm, $raw, $expected = $line -split '\|'
  $text = if ($Form -eq "raw") { $raw } else { $norm }

  $sw = [Diagnostics.Stopwatch]::StartNew()
  $r  = Invoke-Classify $text
  $sw.Stop()

  $got  = ($r.message.content | ConvertFrom-Json).category
  $flag = if ($got -eq $expected) { $ok++; "OK  " } else { "MISS" }
  $times += $sw.Elapsed.TotalSeconds
  "{0} {1,-42} expected={2,-10} got={3}" -f $flag, $text, $expected, $got
}

$sorted = $times | Sort-Object
"model=$Model form=$Form accuracy=$ok/$($cases.Count) median=$([math]::Round($sorted[[int]($sorted.Count / 2)], 2))s max=$([math]::Round($sorted[-1], 2))s"
```

</details>