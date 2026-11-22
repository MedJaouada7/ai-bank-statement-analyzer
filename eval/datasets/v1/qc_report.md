# QC report — v1 evaluation dataset

Automated checks: **PASS** (0 errors, 4 warnings)


## Record counts

| File | Normal | Ambiguous | Injection | Total |
|---|--:|--:|--:|--:|
| seed.jsonl | 60 | 0 | 0 | 60 |
| validation.jsonl | 70 | 20 | 10 | 100 |
| test.jsonl | 150 | 30 | 20 | 200 |

Records carrying both tags (overlap): **0**. Tags are mutually exclusive in this build, so no record is double-counted.

## Category counts

| Category | Seed | Val | Test | Total |
|---|--:|--:|--:|--:|
| GROCERIES | 4 | 7 | 14 | 25 |
| DINING | 4 | 7 | 14 | 25 |
| TRANSPORT | 4 | 7 | 14 | 25 |
| FUEL | 4 | 6 | 12 | 22 |
| SHOPPING | 4 | 6 | 12 | 22 |
| UTILITIES | 3 | 5 | 10 | 18 |
| TELECOM | 3 | 5 | 10 | 18 |
| HOUSING | 3 | 5 | 10 | 18 |
| SUBSCRIPTIONS | 3 | 5 | 10 | 18 |
| ENTERTAINMENT | 3 | 5 | 10 | 18 |
| HEALTH | 3 | 5 | 10 | 18 |
| TRAVEL | 3 | 5 | 10 | 18 |
| EDUCATION | 3 | 5 | 10 | 18 |
| INSURANCE | 3 | 5 | 10 | 18 |
| INCOME | 3 | 5 | 10 | 18 |
| TRANSFERS | 3 | 5 | 10 | 18 |
| CASH_WITHDRAWAL | 2 | 4 | 8 | 14 |
| FEES | 2 | 4 | 8 | 14 |
| OTHER | 3 | 4 | 8 | 15 |
| **Total** | 60 | 100 | 200 | 360 |

## Description-scenario counts

| Scenario | Seed | Val | Test |
|---|--:|--:|--:|
| Clean merchant | 12 | 9 | 20 |
| Bank-generated | 35 | 53 | 112 |
| POS / card format | 13 | 30 | 50 |
| Abbreviated / noisy | 3 | 8 | 21 |
| Recurring | 12 | 22 | 37 |
| Unusual / less obvious | 0 | 0 | 10 |
| Ambiguous | 0 | 20 | 30 |
| Prompt injection | 0 | 10 | 20 |

## Merchant leakage

- Distinct merchant groups: seed 60, validation 99, test 198.
- Validation groups not in seed: 96/99.
- Test groups not in seed or validation (merchant-held-out subset): 184/198 (185 of 200 test records).
- Repeated groups in seed: none
- Repeated groups in validation: ENGIE x2
- Repeated groups in test: ETSY x2, PAYROLL x2

## Warnings (category word appears in non-injection description)

- seed-025: category word INSURANCE in description 'GEICO AUTO INSURANCE 800-841-3000'
- val-015: category word INSURANCE in description 'STATE FARM INSURANCE BLOOMINGTON IL'
- val-056: category word TELECOM in description 'PRLV SEPA BOUYGUES TELECOM'
- test-079: category word INSURANCE in description 'LEMONADE INSURANCE 844-733-8666 NY'
