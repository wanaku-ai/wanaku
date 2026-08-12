# Wanaku Evaluator Action SDK — JavaScript

Write evaluator action scripts in JavaScript, compile to WASM with Javy.

## Current Limitation

Javy's WIT support only handles exports with no arguments and no return values.
This means JS action scripts cannot use the full WIT component model with typed
parameters like Rust guests can.

**Workaround**: JS actions use the stdin/stdout protocol. The host writes the
evaluation context as JSON to stdin, the action reads it, processes, and writes
the action result as JSON to stdout.

## Rust Guests (Full WIT Support)

For the full imperative API (calling `registry.listTools()`, `response.block()`,
etc. during execution), use Rust with `cargo-component`. See `actions/safety-block/`
for an example.

## JS Guest Example (stdin/stdout)

```javascript
// safety-block.js
const input = Javy.IO.readSync();
const ctx = JSON.parse(new TextDecoder().decode(input));

const result = {
  action: "block",
  reason: "Tool call blocked by safety classification: " + ctx.llmResult
};

const output = new TextEncoder().encode(JSON.stringify(result));
Javy.IO.writeSync(output);
```

Compile: `javy build safety-block.js -o safety-block.wasm`

## TypeScript Definitions

See `wanaku-actions.d.ts` for the full API surface documentation. While JS
guests currently use stdin/stdout, the type definitions document what the
host provides and expects.

## Future

When Javy adds support for WIT function exports with parameters, JS guests
will be able to use the same imperative API as Rust guests.
