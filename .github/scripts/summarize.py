"""Résume tests et erreurs de compilation en annotations GitHub (lisibles sans ouvrir les journaux)."""

import glob
import sys
import xml.etree.ElementTree as ET


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def tests() -> None:
    total = failures = errors = skipped = 0
    failed = []
    for path in glob.glob("app/build/test-results/**/*.xml", recursive=True):
        root = ET.parse(path).getroot()
        total += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
        for case in root.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                message = (problem.get("message") or problem.text or "").strip()[:400]
                failed.append(f"{case.get('classname')}.{case.get('name')} : {message}")
    print(f"::notice title=Tests::{total} tests, {failures} échecs, {errors} erreurs, {skipped} ignorés")
    if failed:
        print(f"::error title=Tests échoués::{escape(chr(10).join(failed[:15]))}")


def compile_errors(log_path: str) -> None:
    try:
        lines = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
    except FileNotFoundError:
        return
    keep = []
    for i, line in enumerate(lines):
        # Redémarrage du compilateur Kotlin : message interne sans conséquence.
        if "daemon" in line.lower():
            continue
        if line.startswith("e: ") or "error:" in line or line.startswith("* What went wrong"):
            keep.extend(lines[i : i + 4] if line.startswith("* What went wrong") else [line])
    if keep:
        print(f"::error title=Erreurs de compilation::{escape(chr(10).join(keep[:40]))[:3500]}")


if __name__ == "__main__":
    tests()
    if len(sys.argv) > 1:
        compile_errors(sys.argv[1])
