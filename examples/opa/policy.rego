# Tool-call policy for the Wanaku OPA evaluator engine.
#
# Boolean decision:    POST /v1/data/wanaku/tool_call/allow
# Structured decision: POST /v1/data/wanaku/tool_call/decision
package wanaku.tool_call

default allow := false

allow if count(deny_reasons) == 0

decision := {"allow": true, "reason": null} if allow

decision := {"allow": false, "reason": min(deny_reasons)} if not allow

deny_reasons contains "unsupported_input_version" if {
	input.version != "wanaku.opa.input/v1"
}

deny_reasons contains "unsupported_method" if {
	input.method != "tools/call"
}

deny_reasons contains "tool_blocked" if {
	input.tool_name in data.policy.blocked_tools[input.namespace]
}

# Wanaku keeps JSON types, so a string "250" is not a number.
deny_reasons contains "amount_not_a_number" if {
	data.policy.amount_limits[input.namespace][input.tool_name]
	not is_number(input.arguments.amount)
}

deny_reasons contains "amount_over_limit" if {
	limit := data.policy.amount_limits[input.namespace][input.tool_name]
	input.arguments.amount > limit
}
