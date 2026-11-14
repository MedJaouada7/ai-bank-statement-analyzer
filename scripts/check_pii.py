
import re
import sys
from pathlib import Path


PATTERNS = {
    "Email address": re.compile(
        r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"
    ),
    "IBAN": re.compile(
        r"\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b",
        re.IGNORECASE
    ),
    "Phone number": re.compile(
        r"(?<!\w)(?:\+\d{1,3}[\s.-]?)"
        r"(?:\d[\s.-]?){7,12}\d(?!\w)"
    ),
}


def luhn_valid(number):
    digits = [int(d) for d in number]
    checksum = 0

    for i, digit in enumerate(reversed(digits)):
        if i % 2 == 1:
            digit *= 2
            if digit > 9:
                digit -= 9
        checksum += digit

    return checksum % 10 == 0


def scan_file(path):
    try:
        content = Path(path).read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return []

    findings = []

    for label, pattern in PATTERNS.items():
        if pattern.search(content):
            findings.append(label)

    # Credit/debit card candidates: 13-19 digits
    candidates = re.findall(
        r"(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)",
        content
    )

    if any(
        luhn_valid(re.sub(r"\D", "", candidate))
        for candidate in candidates
    ):
        findings.append("Possible payment card number")

    return findings


def main():
    failed = False

    for filename in sys.argv[1:]:
        findings = scan_file(filename)

        if findings:
            failed = True
            print(f"\n[PII WARNING] {filename}")

            for finding in findings:
                print(f"  - Possible {finding}")

    if failed:
        print("\nCommit blocked. Review the detected information.")
        return 1

    print("PII scan passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
