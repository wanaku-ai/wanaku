import { block, pass, warn } from 'wanaku:evaluator/response';
import { warn as logWarn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  let level = 'green';
  let reason = '';

  try {
    const result = JSON.parse(ctx.llmResult);
    level = result.level || 'green';
    reason = result.reason || ctx.llmResult;
  } catch {
    reason = ctx.llmResult;
    // Try to detect level from raw text
    const lower = ctx.llmResult.toLowerCase();
    if (lower.includes('red')) level = 'red';
    else if (lower.includes('yellow')) level = 'yellow';
  }

  if (level === 'red') {
    logWarn(`Blocked: ${reason}`);
    block(`Tool call blocked by safety classification: ${reason}`);
  } else if (level === 'yellow') {
    logWarn(`Warning: ${reason}`);
    warn(`Safety warning: ${reason}`);
  } else {
    pass();
  }
}
