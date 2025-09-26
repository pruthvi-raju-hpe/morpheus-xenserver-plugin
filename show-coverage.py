#!/usr/bin/env python3
"""
Code Coverage Summary Tool
Displays JaCoCo test coverage in a readable format
"""

import xml.etree.ElementTree as ET
import sys
import os

def main():
    report_file = 'build/reports/jacoco/test/jacocoTestReport.xml'

    if not os.path.exists(report_file):
        print("❌ Coverage report not found!")
        print("Run: ./gradlew test")
        sys.exit(1)

    try:
        tree = ET.parse(report_file)
        root = tree.getroot()
    except Exception as e:
        print(f"❌ Error parsing coverage report: {e}")
        sys.exit(1)

    print("=" * 80)
    print("                        CODE COVERAGE SUMMARY")
    print("=" * 80)

    # Overall coverage
    for counter in root.findall('./counter'):
        ctype = counter.get('type')
        covered = int(counter.get('covered'))
        missed = int(counter.get('missed'))
        total = covered + missed
        percentage = (covered / total * 100) if total > 0 else 0

        bar_length = 40
        covered_bars = int((covered / total) * bar_length) if total > 0 else 0
        bar = '█' * covered_bars + '░' * (bar_length - covered_bars)

        print(f"\n{ctype:15} | {bar} | {percentage:6.2f}% ({covered}/{total})")

    print("\n" + "=" * 80)
    print("                       COVERAGE BY PACKAGE")
    print("=" * 80)

    for package in root.findall('./package'):
        pkg_name = package.get('name').replace('/', '.')

        line_counter = package.find("./counter[@type='LINE']")
        if line_counter is not None:
            covered = int(line_counter.get('covered'))
            missed = int(line_counter.get('missed'))
            total = covered + missed
            percentage = (covered / total * 100) if total > 0 else 0

            bar_length = 30
            covered_bars = int((percentage / 100) * bar_length)
            bar = '█' * covered_bars + '░' * (bar_length - covered_bars)

            print(f"\n{pkg_name:50} | {bar} | {percentage:6.2f}%")

    print("\n" + "=" * 80)
    print("                     TOP 10 MOST COVERED CLASSES")
    print("=" * 80)

    # Collect class coverage data
    classes_data = []
    for package in root.findall('./package'):
        pkg_name = package.get('name').replace('/', '.')
        for sourcefile in package.findall('./sourcefile'):
            class_name = sourcefile.get('name')
            line_counter = sourcefile.find("./counter[@type='LINE']")
            if line_counter is not None:
                covered = int(line_counter.get('covered'))
                missed = int(line_counter.get('missed'))
                total = covered + missed
                if total > 0:
                    percentage = (covered / total * 100)
                    classes_data.append((f"{pkg_name}.{class_name}", percentage, covered, total))

    # Sort by coverage percentage (descending)
    classes_data.sort(key=lambda x: x[1], reverse=True)

    for i, (class_name, percentage, covered, total) in enumerate(classes_data[:10], 1):
        bar_length = 20
        covered_bars = int((percentage / 100) * bar_length)
        bar = '█' * covered_bars + '░' * (bar_length - covered_bars)

        # Truncate long class names
        display_name = class_name[-55:] if len(class_name) > 55 else class_name
        print(f"{i:2}. {display_name:55} | {bar} | {percentage:6.2f}%")

    print("\n" + "=" * 80)
    print("                     TOP 10 LEAST COVERED CLASSES")
    print("=" * 80)

    # Show least covered (excluding 0% coverage)
    least_covered = [c for c in classes_data if c[1] > 0 and c[1] < 100]
    least_covered.sort(key=lambda x: x[1])

    for i, (class_name, percentage, covered, total) in enumerate(least_covered[:10], 1):
        bar_length = 20
        covered_bars = int((percentage / 100) * bar_length)
        bar = '█' * covered_bars + '░' * (bar_length - covered_bars)

        display_name = class_name[-55:] if len(class_name) > 55 else class_name
        print(f"{i:2}. {display_name:55} | {bar} | {percentage:6.2f}%")

    print("\n" + "=" * 80)
    print("\n📊 Full HTML Report: build/reports/jacoco/test/html/index.html")
    print("📄 XML Report:       build/reports/jacoco/test/jacocoTestReport.xml")
    print("\n💡 To open HTML report: xdg-open build/reports/jacoco/test/html/index.html")
    print("\n" + "=" * 80)

if __name__ == '__main__':
    main()

