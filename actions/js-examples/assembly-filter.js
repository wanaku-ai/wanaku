import { copyToolToNamespace, getTool } from 'wanaku:evaluator/registry';
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

  // Assemble only from the shared catalog or the request namespace, so the
  // LLM result cannot pull in tools that belong to other namespaces.
  const assembled = [];
  for (const name of approved) {
    const tool = getTool(name);
    if (!tool) {
      continue;
    }
    const source = tool.namespace ?? 'default';
    if (source !== 'default' && source !== ctx.namespace) {
      warn(`Skipping tool '${name}' from namespace '${source}'`);
      continue;
    }
    copyToolToNamespace(name, ctx.namespace);
    assembled.push(name);
  }

  info(`Registered ${assembled.length} tools into namespace '${ctx.namespace}'`);
  filterTools(assembled);
}
