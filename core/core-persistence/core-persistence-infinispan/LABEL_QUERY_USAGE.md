# Label Query Filtering - Usage Guide

## Overview

This document explains how to use the label query filtering system to filter Wanaku entities (Tools, Resources, Namespaces, etc.) by their labels using logical expressions.

The filtering is done **in-memory** using Java streams, which provides accurate results and avoids the limitations of Infinispan Ickle queries with map fields.

## Quick Start

### Repository Usage

```java
// Use the repository's built-in filtering method
List<ToolReference> results = toolReferenceRepository
    .findFilterByLabelExpression("category=weather & !action=forecast");
```

### CLI Example

```bash
# List weather tools that are not forecasts
wanaku tools list -l "category=weather & !action=forecast"

# List production-ready tools
wanaku tools list -l "environment=production & status=stable"

# List weather or news tools
wanaku tools list -l "category=weather | category=news"
```

## Expression Syntax

### Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `=` | Equals | `category=weather` |
| `!=` | Not equals | `status!=deprecated` |
| `&` | Logical AND | `category=weather & version=2.0` |
| `\|` | Logical OR | `category=weather \| category=news` |
| `!` | Logical NOT | `!deprecated=true` |
| `()` | Grouping | `(a=1 \| b=2) & c=3` |

### Operator Precedence

1. `!` (NOT) - highest precedence
2. `&` (AND)
3. `|` (OR) - lowest precedence

Use parentheses to override precedence.

## Examples

### Simple Equality

```java
// Find all weather tools
String query = "category=weather";
List<ToolReference> results = repository.findFilterByLabelExpression(query);
```

### AND Expression

```java
// Find weather forecasting tools
String query = "category=weather & action=forecast";
```

### NOT Expression

```java
// Find non-deprecated tools
String query = "!deprecated=true";
```

### Complex Expression

```java
// Find weather or news tools that are production-ready and not deprecated
String query = "(category=weather | category=news) & environment=production & !deprecated=true";
```

### Not Equals

```java
// Find tools not in production
String query = "environment!=production";
```

## How It Works

### Architecture

```
Query String → LabelQueryParser → Predicate<Map<String, String>> → In-Memory Evaluation
```

1. **LabelQueryParser**: Parses the query string and builds a `Predicate<Map<String, String>>`
2. **Predicate**: A functional interface that tests if a label map matches the query
3. **In-Memory Evaluation**: Evaluates the predicate against each entity's label map using Java streams

### Implementation Details

The filtering process:
1. Parses the query string into a predicate during parsing (no intermediate AST)
2. Fetches all entities from the cache (`listAll()`)
3. Filters using Java streams and the predicate
4. Returns only matching entities

This approach ensures correct evaluation of complex label expressions, as it properly correlates label keys and values from the same map entry.

## Parser Safety and Input Validation

### Input Validation

The parser includes built-in safety features to ensure reliable parsing:

1. **Character Whitelist**: Only allows valid label query syntax characters
   - Letters, numbers, operators, parentheses
   - Hyphens, underscores, dots, forward slashes
   - Rejects unexpected characters that could cause parser errors

2. **Length Limit**: Maximum 1000 characters (prevents DoS attacks)

3. **In-Memory Evaluation**: Since filtering uses Java streams (not database queries), there is no risk of injection attacks

### Safe Characters

```
Allowed: a-z A-Z 0-9 & | ! ( ) = _ - . /
Rejected: ; ' " ` \ < > $ { } [ ]
```

### Example: Invalid Characters Rejected

```java
// This will throw LabelQueryParseException due to invalid characters
String invalidQuery = "category=weather'; DROP TABLE tools; --";
repository.findFilterByLabelExpression(invalidQuery); // THROWS EXCEPTION
```

## Performance Considerations

### When to Use In-Memory Filtering

- ✅ Small to medium datasets (< 100,000 entities)
- ✅ All expression complexities supported
- ✅ Accurate correlation of label keys and values
- ✅ No database query limitations

### Optimization Tips

1. **Cache Size**: Keep entity caches reasonably sized
2. **Indexing**: Consider adding indexes to the underlying cache
3. **Filter Early**: Apply other filters before label filtering when possible
4. **Label Design**: Use consistent, well-defined label schemas

### When to Denormalize

For very large datasets (> 100,000 entities) with frequently queried labels:

```protobuf
message ToolReference {
  // ... existing fields
  map<string, string> labels = 10;

  // Denormalized for fast queries
  string category = 11;
  string environment = 12;
}
```

## Testing

### Unit Tests

```bash
mvn test -Dtest=LabelQueryParserTest
mvn test -Dtest=LabelFilteringIntegrationTest
```

### Manual Testing

```java
// Test parser
LabelQueryParser parser = new LabelQueryParser();
Predicate<Map<String, String>> predicate = parser.parse("category=weather & !action=forecast");

// Test filtering
List<ToolReference> results = repository.findFilterByLabelExpression(
    "category=weather & version=2.0"
);
```

## Troubleshooting

### Query Parsing Errors

```
LabelQueryParseException: Query contains invalid characters
```
**Solution**: Remove special characters, use only allowed syntax

### No Results Returned

**Check:**
1. Labels are correctly set on entities (use `Map<String, String>`)
2. Query syntax is correct
3. Label keys and values match exactly (case-sensitive)

### Performance Issues

**Solutions:**
1. Reduce cache size if possible
2. Consider denormalizing frequently queried labels
3. Add database-level indexes

## API Reference

### AbstractLabelAwareInfinispanRepository

```java
/**
 * Finds entities matching a label expression.
 *
 * @param labelExpression the filter expression (null returns all)
 * @return filtered list of entities
 */
List<A> findFilterByLabelExpression(String labelExpression);
```

### LabelQueryParser

```java
/**
 * Parses a label query string into a predicate.
 *
 * @param query the query string
 * @return a predicate that tests if a label map matches the query
 * @throws LabelQueryParseException if syntax is invalid
 */
Predicate<Map<String, String>> parse(String query);
```

## References

- Label Expression Grammar: See `LabelQueryParser.java` class documentation
- In-Memory Filtering Implementation: See `AbstractLabelAwareInfinispanRepository.java`
