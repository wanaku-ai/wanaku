import { warn as warnResponse } from 'wanaku:evaluator/response';
import { warn as logWarn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  const message = `Safety warning: ${ctx.llmResult}`;
  logWarn(message);
  warnResponse(message);
}
