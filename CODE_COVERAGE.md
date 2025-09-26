# Code Coverage Guide

## Overview
This project uses **JaCoCo (Java Code Coverage)** to measure and report code coverage from unit tests.

## Quick Start

### Generate Code Coverage Report
```bash
./gradlew test
```
The coverage report is automatically generated after tests run.

### View Coverage Summary in Terminal
```bash
python3 show-coverage.py
```
This displays a formatted summary with visual progress bars showing coverage percentages.

### Generate Coverage Report Only
```bash
./gradlew jacocoTestReport
```

### Clean and Generate Fresh Report
```bash
./gradlew clean test
```

## Quick Coverage Commands

```bash
# Run tests and view coverage summary
./gradlew test && python3 show-coverage.py

# Just view the existing coverage report
python3 show-coverage.py

# Open HTML report in browser
xdg-open build/reports/jacoco/test/html/index.html
```

## Coverage Reports Location

### HTML Report (Human-Readable)
📊 **Location**: `build/reports/jacoco/test/html/index.html`

Open this file in your web browser to see:
- Overall coverage percentage
- Coverage by package, class, and method
- Line-by-line coverage visualization
- Branch coverage details

### XML Report (CI/CD Integration)
📄 **Location**: `build/reports/jacoco/test/jacocoTestReport.xml`

This format is used by:
- SonarQube
- Code Climate
- Codecov
- Other CI/CD tools

## View Coverage Report

### Option 1: Open HTML Report in Browser
```bash
# Linux
xdg-open build/reports/jacoco/test/html/index.html

# macOS
open build/reports/jacoco/test/html/index.html

# Windows
start build/reports/jacoco/test/html/index.html
```

### Option 2: View in IDE
Most IDEs (IntelliJ IDEA, Eclipse, VS Code) can display JaCoCo reports directly.

## Coverage Metrics Explained

JaCoCo measures several types of coverage:

1. **Instruction Coverage**: Percentage of bytecode instructions executed
2. **Branch Coverage**: Percentage of if/else branches executed
3. **Line Coverage**: Percentage of lines executed
4. **Method Coverage**: Percentage of methods called
5. **Class Coverage**: Percentage of classes instantiated

## Current Configuration

### Excluded from Coverage
The following are excluded from coverage reports (configured in `build.gradle`):
- `**/XenserverPlugin.class` - Main plugin class
- `**/util/**` - Utility classes
- `**/sync/**` - Sync classes

### Minimum Coverage Threshold
- **Target**: 70% coverage
- Run `./gradlew jacocoTestCoverageVerification` to check if coverage meets the threshold

## Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew test` | Run tests and generate coverage report |
| `./gradlew jacocoTestReport` | Generate coverage report from last test run |
| `./gradlew jacocoTestCoverageVerification` | Verify coverage meets minimum threshold (70%) |
| `./gradlew clean test jacocoTestReport` | Clean, test, and generate fresh coverage report |

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run tests with coverage
  run: ./gradlew test jacocoTestReport

- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: ./build/reports/jacoco/test/jacocoTestReport.xml
```

### SonarQube Integration
```bash
./gradlew test jacocoTestReport sonarqube \
  -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
```

## Tips for Improving Coverage

1. **Write tests for uncovered classes**: Check the HTML report to identify untested code
2. **Test edge cases**: Improve branch coverage by testing all conditional paths
3. **Mock dependencies**: Use Spock mocks for external dependencies
4. **Focus on business logic**: Prioritize testing core functionality

## Troubleshooting

### No coverage data generated
- Ensure tests are running: `./gradlew test --info`
- Check test output for failures

### Coverage report shows 0%
- Make sure tests are actually executing your code
- Verify test classes are in `src/test/groovy`
- Check that classes aren't excluded in `build.gradle`

### SLF4J warnings during tests
Already fixed! We added `testRuntimeOnly "org.slf4j:slf4j-simple:$slf4jVersion"` to suppress warnings.

## Example: Viewing Coverage for Your New Tests

After running tests:
```bash
./gradlew test
```

1. Open `build/reports/jacoco/test/html/index.html`
2. Navigate to `com.morpheusdata.xen` package
3. Click on `XenserverBackupTypeProvider` to see line-by-line coverage
4. Green lines = covered, red lines = not covered, yellow = partially covered branches

## Current Test Coverage Status

Run this command to see current coverage:
```bash
./gradlew test jacocoTestReport && \
echo "Coverage report: build/reports/jacoco/test/html/index.html"
```

---

**Note**: Coverage reports are automatically generated when you run tests. The HTML report provides the best visualization of what code is tested and what needs more tests.
