# Label Expressions Guide

## Overview

Label expressions provide a powerful query language for filtering Wanaku entities (tools, resources, namespaces) based on their labels using logical operators and conditions.

Labels are **key-value pairs** attached to entities for categorization and filtering. Label expressions allow you to write **complex queries** that combine multiple conditions using logical operators.

## Quick Start

### Basic Filtering

List all weather-related tools:
```bash
wanaku tools list -l "category=weather"
```

List weather tools that perform forecasting:
```bash
wanaku tools list -l "category=weather & action=forecast"
```

List tools that are NOT in production:
```bash
wanaku tools list -l "environment!=production"
```

## Expression Syntax

### Comparison Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `=` | Equals | `category=weather` |
| `!=` | Not equals | `status!=deprecated` |

### Logical Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `&` | Logical AND | `category=weather & version=2.0` |
| `\|` | Logical OR | `category=weather \| category=news` |
| `!` | Logical NOT | `!deprecated=true` |
| `( )` | Grouping | `(a=1 \| b=2) & c=3` |

### Operator Precedence

Operators are evaluated in this order (highest to lowest):

1. **`!` (NOT)** - Highest precedence
2. **`&` (AND)** - Medium precedence
3. **`|` (OR)** - Lowest precedence

**Tip:** Use parentheses `( )` to override default precedence and make expressions clearer.

## Expression Examples

### Simple Equality

Match tools where category equals "weather":
```
category=weather
```

### Inequality

Match tools where environment is NOT production:
```
environment!=production
```

### AND Conditions

Match weather tools with version 2.0:
```
category=weather & version=2.0
```

Match production tools that are stable:
```
environment=production & status=stable
```

### OR Conditions

Match tools that are either weather or news:
```
category=weather | category=news
```

Match tools with beta or stable status:
```
status=beta | status=stable
```

### NOT Conditions

Match weather tools that are NOT forecasts:
```
category=weather & !action=forecast
```

Match tools that are NOT deprecated:
```
!deprecated=true
```

### Complex Expressions

Match production-ready weather or news tools:
```
(category=weather | category=news) & environment=production
```

Match non-production tools that are not deprecated:
```
environment!=production & !deprecated=true
```

Match version 2.0 tools with stable or beta status:
```
version=2.0 & (status=stable | status=beta)
```

Match tools that are either in production, or are beta weather tools:
```
environment=production | (status=beta & category=weather)
```

## Understanding Operator Precedence

### Example 1: AND before OR

Expression:
```
a=1 | b=2 & c=3
```

This is evaluated as:
```
a=1 | (b=2 & c=3)
```

**Matches when:**
- `a=1`, OR
- Both `b=2` AND `c=3`

### Example 2: NOT before AND

Expression:
```
!a=1 & b=2
```

This is evaluated as:
```
(!a=1) & b=2
```

**Matches when:**
- `a` does NOT equal `1`, AND
- `b=2`

### Example 3: Using Parentheses

Expression:
```
(a=1 | b=2) & c=3
```

**Matches when:**
- Either `a=1` OR `b=2`, AND
- `c=3`

Without parentheses, this would mean something different!

## Allowed Characters

### In Keys and Values

- **Letters:** `a-z`, `A-Z`
- **Numbers:** `0-9`
- **Hyphen:** `-` (e.g., `api-version=2.0`)
- **Underscore:** `_` (e.g., `my_category=test`)
- **Dot:** `.` (e.g., `version=1.2.3`)
- **Forward slash:** `/` (e.g., `path=/api/v1/tools`)

### Special Characters

- **Operators:** `&`, `|`, `!`, `=`, `(`, `)`
- **Whitespace:** Spaces for readability (automatically trimmed)

### Maximum Length

- **1000 characters** maximum per expression

## Common Use Cases

### By Environment

List development tools:
```
environment=development
```

List all non-production tools:
```
environment!=production
```

### By Status

List stable tools:
```
status=stable
```

List tools that are stable or beta:
```
status=stable | status=beta
```

List tools that are NOT deprecated:
```
!deprecated=true
```

### By Category

List weather and news tools:
```
category=weather | category=news
```

List finance tools that are NOT deprecated:
```
category=finance & !deprecated=true
```

### By Version

List version 2.x tools:
```
version=2.0 | version=2.1 | version=2.2
```

List tools that are NOT version 1.0:
```
version!=1.0
```

### Complex Scenarios

Production-ready, stable weather tools:
```
category=weather & environment=production & status=stable
```

Beta or alpha tools, but not deprecated:
```
(status=beta | status=alpha) & !deprecated=true
```

Weather or news tools in development or staging:
```
(category=weather | category=news) & (environment=development | environment=staging)
```

## Parser Safety

### Input Validation

All label expressions are validated for safety:

- **Character whitelist:** Only allows safe characters (alphanumeric, operators, safe punctuation)
- **Length limit:** Maximum 1000 characters (prevents DoS attacks)
- **Parser safety:** Rejects invalid characters that could cause parser errors

### Invalid Characters

These characters are **rejected** and will cause an error:

- Semicolon: `;`
- Quotes: `'`, `"`
- Backtick: `` ` ``
- Backslash: `\`
- Angle brackets: `<`, `>`
- Dollar sign: `$`
- Curly braces: `{`, `}`
- Square brackets: `[`, `]`

### Example: Rejected Input

```bash
# This will fail - contains invalid semicolon
wanaku tools list -l "category=weather; DROP TABLE"

# Error: Query contains invalid characters
```

## How It Works

Label filtering uses **in-memory evaluation** with Java streams:

1. **Parse** the expression into a predicate function
2. **Fetch** all entities from the cache
3. **Filter** using Java streams by applying the predicate to each entity's labels
4. **Return** only matching entities

This approach ensures:
- ✅ Accurate correlation of label keys and values
- ✅ Support for complex nested expressions
- ✅ No database query limitations
- ✅ Safe evaluation (no injection attacks)

## Troubleshooting

### "Query contains invalid characters"

**Cause:** Expression contains characters not in the whitelist.

**Solution:** Remove special characters. Only use alphanumeric, operators (`&`, `|`, `!`, `=`), parentheses, hyphens, underscores, dots, and forward slashes.

### "Expected ')' after expression"

**Cause:** Unmatched parentheses in expression.

**Solution:** Ensure every opening `(` has a matching closing `)`.

**Example:**
```bash
# Wrong - missing closing parenthesis
(category=weather & action=forecast

# Correct
(category=weather & action=forecast)
```

### "Expected label value after '='"

**Cause:** Missing value after `=` or `!=` operator.

**Solution:** Provide a value after the operator.

**Example:**
```bash
# Wrong - missing value
category=

# Correct
category=weather
```

### "Query string too long"

**Cause:** Expression exceeds 1000 characters.

**Solution:** Simplify the expression or split into multiple queries.

### No Results Returned

**Possible causes:**

1. **Case sensitivity:** Label matching is case-sensitive
   - `category=Weather` ≠ `category=weather`

2. **Exact matching:** Values must match exactly
   - `version=2` ≠ `version=2.0`

3. **Label not present:** Entity doesn't have the specified label
   - Use `!=` to match entities without a label

**Tips:**
- Verify labels are correctly set on entities
- Check for typos in label keys and values
- Test with simpler expressions first


## Tips and Best Practices

### Use Descriptive Labels

**Good:**
```
environment=production
status=stable
category=weather-forecast
version=2.1.0
```

**Avoid:**
```
env=prod
st=1
cat=wthr
v=2
```

### Group Related Conditions

Use parentheses to make complex expressions clearer:

**Good:**
```
(category=weather | category=news) & (status=stable | status=beta)
```

**Less clear:**
```
category=weather | category=news & status=stable | status=beta
```

### Test Incrementally

Build complex expressions step by step:

1. Start simple: `category=weather`
2. Add conditions: `category=weather & status=stable`
3. Add complexity: `(category=weather | category=news) & status=stable`

### Use Whitespace

Add spaces for readability - they're automatically trimmed:

**Good:**
```
category=weather & status=stable | environment=production
```

**Works, but harder to read:**
```
category=weather&status=stable|environment=production
```

### Examples

```bash
# List all tools with label filter
wanaku tools list -l "category=weather"

# List with multiple conditions
wanaku tools list -l "(category=weather | category=news) & environment=production"

# List without filter (show all)
wanaku tools list
```

## Additional Resources

- **Label Expression Grammar:** See `LabelQueryParser.java` class documentation
- **CLI Help:** Run `wanaku tools list --help` for command-specific help

## Summary

Label expressions provide a powerful, safe, and flexible way to filter Wanaku entities:

- **Simple syntax** with familiar logical operators
- **Complex queries** with AND, OR, NOT, and grouping
- **Safe evaluation** with input validation and in-memory filtering
- **Case-sensitive** exact matching
- **No injection risks** - safe for user input

Start with simple expressions and build up to complex queries as needed. Use parentheses to make expressions clear and test incrementally.
