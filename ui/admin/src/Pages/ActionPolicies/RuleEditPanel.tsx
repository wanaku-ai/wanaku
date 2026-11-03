import React, {useState} from "react"
import {ActionPolicyRevision} from "../../hooks/api/use-action-policies"
import {RulesTable} from "./RulesTable"
import {Rule} from "../../models"
import {RuleForm} from "./RuleForm"


interface RuleEditPanelProps {
  policy?: ActionPolicyRevision
}


export const RuleEditPanel: React.FC<RuleEditPanelProps> = ({ policy }) => {
  
  const [openedRule, setOpenedRule] = useState<Rule>()
  
  
  function getRules(): Rule[] {
    return policy?.policy?.rules || []
  }
  
  function generateRuleId(): string {
    const ruleIds = getRules().map(rule => rule.id)
    let ruleId: string
    do {
      ruleId = crypto.randomUUID()
    } while (ruleIds.includes(ruleId))
    return ruleId
  }
  
  function newRule(): Rule {
    return {
      id: generateRuleId(),
      effect: "allow"
    }
  }
  
  
  return openedRule ? (
    <RuleForm
      rule={openedRule}
    />
  ) : (
    <RulesTable
      rules={getRules()}
      onAdd={() => setOpenedRule(newRule())}
    />
  )
}