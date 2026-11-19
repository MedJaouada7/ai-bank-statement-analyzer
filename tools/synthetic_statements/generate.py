#!/usr/bin/env python3
"""Generate a synthetic French-style monthly statement (PDF) plus ground truth (JSON)."""
import argparse
import json
import random
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

import yaml
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas


HERE = Path(__file__).parent
CENT = Decimal("0.01")
MONTHS_FR = ["JANVIER", "FEVRIER", "MARS", "AVRIL", "MAI", "JUIN", "JUILLET",
             "AOUT", "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DECEMBRE"]
BANK, HOLDER = "Banque Exemple", "M. JEAN EXEMPLE"
IBAN = "FR76 0000 0000 0000 0000 0000 000"  # obviously fake
# x positions; debit/credit are RIGHT edges (amounts are right-aligned, like real statements)
COLS = {"date": 40, "value": 85, "label": 130, "debit": 470, "credit": 545}


def fr(amount):
    """1234.5 -> '1 234,50'."""
    return f"{amount:,.2f}".replace(",", " ").replace(".", ",")


def amount_between(rng, lo, hi):
    return Decimal(str(rng.uniform(lo, hi))).quantize(CENT, ROUND_HALF_UP)


def build_transactions(rng, merchants, year, month, n_card):
    last_day = (date(year + month // 12, month % 12 + 1, 1) - timedelta(days=1)).day
    txs = []

    def add(m, day):
        d = date(year, month, min(day, last_day))
        text = rng.choice(m["templates"]).format(
            dd=f"{d.day:02d}", mm=f"{d.month:02d}", ddmm=f"{d.day:02d}{d.month:02d}",
            ref=rng.randint(1000, 9999), store=rng.randint(100, 9999),
            monthname=MONTHS_FR[month - 1])
        amount = amount_between(rng, *m["amount"])
        txs.append({"date": d, "description": text, "merchant": m["name"],
                    "category": m["category"], "channel": m["channel"], "role": m["role"],
                    "amount": amount if m.get("credit") else -amount})

    for m in merchants:
        if "recurring_day" in m:
            add(m, m["recurring_day"])
    regular = [m for m in merchants if "recurring_day" not in m]
    for _ in range(n_card):
        add(rng.choice(regular), rng.randint(1, last_day))
    txs.sort(key=lambda t: t["date"])
    return txs


def wrap(text, width=46):
    lines, current = [], ""
    for word in text.split():
        if current and len(current) + len(word) + 1 > width:
            lines.append(current)
            current = word
        else:
            current = f"{current} {word}".strip()
    return lines + [current]


def put_amount(c, y, amount):
    c.drawRightString(COLS["debit" if amount < 0 else "credit"], y, fr(abs(amount)))


def render(pdf_path, meta, txs, rows_per_page):
    c = canvas.Canvas(str(pdf_path), pagesize=A4)
    height = A4[1]
    pages = [txs[i:i + rows_per_page] for i in range(0, len(txs), rows_per_page)] or [[]]
    balance = meta["opening"]
    for number, chunk in enumerate(pages, start=1):
        y = height - 50
        c.setFont("Helvetica-Bold", 12)
        c.drawString(40, y, BANK)
        c.setFont("Helvetica", 9)
        c.drawRightString(COLS["credit"], y, f"Page {number}/{len(pages)}")
        y -= 20
        c.setFont("Helvetica-Bold", 11)
        c.drawString(40, y, "RELEVÉ DE COMPTE")
        y -= 14
        c.setFont("Helvetica", 9)
        if number == 1:
            c.drawString(40, y, f"Titulaire : {HOLDER}")
            y -= 12
            c.drawString(40, y, f"IBAN : {IBAN}")
            y -= 12
            c.drawString(40, y, f"Période : du {meta['start']:%d/%m/%Y} au {meta['end']:%d/%m/%Y}")
            y -= 20
        c.setFont("Helvetica-Bold", 9)
        c.drawString(COLS["date"], y, "Date")
        c.drawString(COLS["value"], y, "Valeur")
        c.drawString(COLS["label"], y, "Libellé")
        c.drawRightString(COLS["debit"], y, "Débit")
        c.drawRightString(COLS["credit"], y, "Crédit")
        y -= 14
        c.setFont("Helvetica", 9)
        if number == 1:
            c.drawString(COLS["label"], y, f"SOLDE PRÉCÉDENT AU {meta['previous']:%d/%m/%Y}")
        else:
            c.drawString(COLS["label"], y, "REPORT")  # a number in the amount column that is NOT a transaction
        put_amount(c, y, balance)
        y -= 14
        for t in chunk:
            lines = wrap(t["description"])
            c.drawString(COLS["date"], y, f"{t['date']:%d/%m}")
            c.drawString(COLS["value"], y, f"{t['date']:%d/%m}")
            c.drawString(COLS["label"], y, lines[0])
            put_amount(c, y, t["amount"])
            y -= 12
            for extra in lines[1:]:
                c.drawString(COLS["label"], y, extra)
                y -= 12
            y -= 2
        balance += sum((t["amount"] for t in chunk), Decimal("0"))
        if number == len(pages):
            y -= 8
            c.setFont("Helvetica-Bold", 9)
            c.drawString(COLS["label"], y, "TOTAL DES MOUVEMENTS")
            c.drawRightString(COLS["debit"], y, fr(meta["debits"]))
            c.drawRightString(COLS["credit"], y, fr(meta["credits"]))
            y -= 14
            c.drawString(COLS["label"], y, f"NOUVEAU SOLDE AU {meta['end']:%d/%m/%Y}")
            put_amount(c, y, meta["closing"])
        c.setFont("Helvetica", 7)
        c.drawCentredString(297, 30, f"{BANK} - document fictif généré pour des tests - {IBAN}")
        c.showPage()
    c.save()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--year", type=int, default=2025)
    ap.add_argument("--month", type=int, default=10)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--card-txs", type=int, default=28)
    ap.add_argument("--rows-per-page", type=int, default=22)
    ap.add_argument("--out", type=Path, default=HERE / "out")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    merchants = yaml.safe_load((HERE / "merchants.yml").read_text(encoding="utf-8"))["merchants"]
    txs = build_transactions(rng, merchants, args.year, args.month, args.card_txs)

    start = date(args.year, args.month, 1)
    end = date(args.year + args.month // 12, args.month % 12 + 1, 1) - timedelta(days=1)
    opening = Decimal("1234.56")
    debits = -sum((t["amount"] for t in txs if t["amount"] < 0), Decimal("0"))
    credits = sum((t["amount"] for t in txs if t["amount"] > 0), Decimal("0"))
    closing = opening + credits - debits
    meta = {"start": start, "end": end, "previous": start - timedelta(days=1),
            "opening": opening, "closing": closing, "debits": debits, "credits": credits}

    args.out.mkdir(parents=True, exist_ok=True)
    stem = f"statement_layout_a_{args.year}-{args.month:02d}"
    render(args.out / f"{stem}.pdf", meta, txs, args.rows_per_page)

    expected = {
        "layout": "A",
        "statement": {"bank": BANK, "iban": IBAN, "period_start": str(start), "period_end": str(end),
                      "opening_balance": str(opening), "closing_balance": str(closing),
                      "total_debits": str(debits), "total_credits": str(credits)},
        "transactions": [
            {"date": str(t["date"]), "raw_description": t["description"], "amount": str(t["amount"]),
             "currency": "EUR", "merchant_group": t["merchant"], "intended_role": t["role"],
             "expected": {"category": t["category"], "merchant": t["merchant"], "channel": t["channel"]}}
            for t in txs],
    }
    (args.out / f"{stem}.expected.json").write_text(
        json.dumps(expected, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote {stem}.pdf and .expected.json: {len(txs)} transactions, closing balance {fr(closing)}")


if __name__ == "__main__":
    main()