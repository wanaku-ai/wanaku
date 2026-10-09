package wanaku.tool_call_test

import data.wanaku.tool_call

transfer(arguments) := {
	"version": "wanaku.opa.input/v1",
	"method": "tools/call",
	"namespace": "finance",
	"tool_name": "transfer",
	"arguments": arguments,
}

test_allows_transfer_under_limit if {
	tool_call.allow with input as transfer({"amount": 250})
	tool_call.decision == {"allow": true, "reason": null} with input as transfer({"amount": 250})
}

test_denies_transfer_over_limit if {
	not tool_call.allow with input as transfer({"amount": 5000})
	tool_call.decision == {"allow": false, "reason": "amount_over_limit"} with input as transfer({"amount": 5000})
}

test_denies_amount_with_wrong_type if {
	tool_call.decision.reason == "amount_not_a_number" with input as transfer({"amount": "250"})
}

test_denies_blocked_tool if {
	tool_call.decision.reason == "tool_blocked" with input as object.union(transfer({}), {"tool_name": "delete_ledger"})
}

test_denies_unknown_input_version if {
	tool_call.decision.reason == "unsupported_input_version" with input as object.union(transfer({"amount": 1}), {"version": "v0"})
}

test_limit_comes_from_policy_data if {
	tool_call.allow with input as transfer({"amount": 5000})
		with data.policy.amount_limits as {"finance": {"transfer": 10000}}
}
