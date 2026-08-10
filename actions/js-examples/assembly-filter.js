import { copyToolToNamespace } from 'wanaku:evaluator/registry';
import { filterTools } from 'wanaku:evaluator/response';
import { info, warn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  let approved;
  try {
    approved = JSON.parse(ctx.llmResult);
  } catch (e) {
    warn('Failed to parse LLM result as tool name array, returning all tools');
    return;
  }

  if (!Array.isArray(approved) || approved.length === 0) {
    info('LLM returned empty tool list, returning all tools (fail-open)');
    return;
  }

  for (const name of approved) {
    copyToolToNamespace(name, ctx.namespace);
  }

  info(`Registered ${approved.length} tools into namespace '${ctx.namespace}'`);
  filterTools(approved);
}
