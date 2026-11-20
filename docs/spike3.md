# Spike 3: PDFBox text positions on a synthetic French-style statement

**Date:** 20/11/2026 ·
**Code:** `src/test/java/.../spikes/PdfTextPositionSpike.java`
**Input:** `tools/synthetic_statements/out/statement_layout_a_2025-10.pdf`

## Question

Can transaction rows, columns and balances be reconstructed reliably from PDFBox word
positions, so that the opening and closing balances can be used to verify extraction?

## Environment

| Item | Value |
|---|---|
| PDFBox | 3.0.5 |
| Java | 25 |
| Machine | i7-8565U, 8 GB RAM, Windows |
| Input | 2-page statement, 35 transactions (34 debits, 1 credit), generated with reportlab |

## Method

`PDFTextStripper` subclass overriding `writeString`. Each text run is split into words at
blank glyphs, keeping `getXDirAdj()` as the start, that plus `getWidthDirAdj()` as the
end, and `getYDirAdj()` for the row. Position sorting is enabled. Words are then
re-sorted by (page, y, x) and grouped into rows when y differs by more than 2 pt. One
cold run. Numbers in the log are rounded to whole points and the y values use the French
locale (`50,0`).

## Answers to the seven questions

**1. Do dates, descriptions, and amounts come out as separate words with stable x positions across rows and pages? Yes.**
On all 35 transaction rows, on both pages: date word at x 40 to 63, value date at x 85
to 108, first description word at x 130, debit amounts ending at 470, credit amounts
ending at 545. Page 2 matches page 1 even though its column-header row sits at a
different y (84 vs 128). Descriptions arrive as one word per space and can be
rebuilt by joining the words in the label region with single spaces.

**2. Amounts are right-aligned, so does xEnd stay constant while x varies? (Column assignment should use the right edge, not the left.). Yes.**
Debit amounts start anywhere from 442 to 452 depending on length, and all end at 470.
Credit amounts end at 545. Column assignment must use the right edge. In this layout the
header words Débit ([448..470]) and Crédit ([519..545]) share the amounts' right edges,
but that is a property of the generator, so real layouts must not be assumed to do the
same.

**3. Is `1 234,56` split into two words? Yes, for amounts of 1000 or more.**
Examples: opening balance `1` [510..515] + `234,56` [517..545]; salary `2` + `100,00`;
total debits `2` [435..440] + `025,42` [442..470]; closing balance `1` + `309,14`. The gap
inside an amount is 2 pt, which is the same as a normal gap between words (2 to 3 pt), so
the gap alone cannot tell them apart. Consequences:
- Fragments must be merged before column assignment.

**4. Can headers, footers and the REPORT line be told apart from transactions? Yes.**
Two independent signals:
- Position: page header at y 50 and 70; on page 1 the holder, IBAN and period at y 84 to
  108; column-header row at y 128 (page 1) and 84 (page 2); footer at y 811.9 on both
  pages.
- Content: only transaction rows have a `dd/mm` word in the date column (x 40 to 63) and
  a second one in the value-date column. The REPORT row (page 2, y 98), SOLDE PRÉCÉDENT,
  TOTAL DES MOUVEMENTS and NOUVEAU SOLDE rows have no date in those columns.

Two cautions:
- Descriptions contain `dd/mm` tokens (for example `CARTE X4521 01/10`, x 192), so a
  regex over the whole row would misclassify. Date detection must be restricted to the
  date column's x range.
- The REPORT balance 159,72 is in the **debit** column. Opening balance 1,234.56 minus
  page-1 debits 1,394.28 is -159.72, so a negative running balance is encoded by column,
  not by a minus sign.

**5. Are accents correct? Yes.**
RELEVÉ, PRÉCÉDENT, Débit, Crédit, Libellé, Période and généré all came out correctly.

**6. How long does extraction take? Tens of milliseconds**
4671 ms for two pages in one cold run. That figure starts before the load and ends after
printing, so it includes JVM and class-loading warm-up, any one-time PDFBox
initialization, and console output. It is an upper bound. Import runs as an asynchronous job, so this does not block the user.
* The real cost was measured by timing `getText` alone, three runs in the same JVM, to separate warm-up from steady state. The results are : run 1: 798 ms / run 2: 29 ms / run 3: 46 ms.

## Reconciliation check from the printed output

Summed by hand from the extracted rows:

| Quantity | Computed from rows | Printed on statement |
|---|---|---|
| Page 1 debits (22 rows) | 1,394.28 | not printed |
| Opening balance minus page-1 debits | -159.72 | REPORT 159,72 in the debit column |
| Page 2 debits (12 rows) | 631.14 | not printed |
| Total debits | 2,025.42 | TOTAL DES MOUVEMENTS 2 025,42 |
| Total credits (1 row) | 2,100.00 | 2 100,00 |
| Opening + credits - debits | 1,309.14 | NOUVEAU SOLDE 1 309,14 |

Opening balance, running balance, totals and closing balance are all extractable, so a
reconciliation check is feasible on this layout.

## Limitations

- One synthetic statement from one generator: exact alignment, no y jitter, standard
  font, text emitted in drawing order, no embedded-font problems.
- Non-breaking and narrow no-break spaces were not tested. Real statements may use them
  as thousands separators, and `String.isBlank()` does not treat U+00A0 or U+202F as
  blank, so the amount would not split. The parser must normalize them first.
- The log used default-locale formatting (`50,0`), which is why numbers in logs and
  parsers must always use an explicit locale.

## Decision

Will proceed with PDFBox word positions plus per-layout profiles for digital (text-based)
PDFs. Reconstructing rows by y-clustering and column ranges works on this layout, and the
statement's own balances can verify the result. Confidence is limited to the layout
tested.

## Consequences for the parser design

1. Always sort words by (page, y, x). Do not rely on stream order.
2. Detect the column-header row on every page (words Date, Valeur, Libellé, Débit,
   Crédit). Take column ranges from it, because its y differs between pages.
3. Detect a transaction by a date inside the date column's x range only.
4. Merge adjacent numeric fragments in the amount region, then assign debit or credit by
   the merged token's right edge (tolerance about 2 pt). Chain by a gap of up to about
   4 pt, and only within the amount region.
5. Join description words with single spaces and attach continuation rows without a date
   to the previous transaction.
6. Classify non-transaction rows by keyword (SOLDE PRÉCÉDENT, REPORT, TOTAL DES
   MOUVEMENTS, NOUVEAU SOLDE) and by position (above the column header, below the
   footer line).
7. Read balance signs from the column: a balance in the debit column is negative.
8. Normalize NBSP (U+00A0) and narrow NBSP (U+202F) before tokenizing.
9. Use `Locale.ROOT` for all formatting and write explicit EU number and date parsers.


## Reproduce

```powershell
cd tools\synthetic_statements; python generate.py; cd ..\..
# then run PdfTextPositionSpike from VS Code, or:
.\mvnw.cmd test-compile
```