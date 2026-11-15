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