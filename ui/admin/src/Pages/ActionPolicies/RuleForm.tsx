import React, {useState} from "react"
import {Form, MultiSelect, Select, SelectItem, TextArea} from "@carbon/react"
import {Rule} from "../../models"


interface RuleFormProps {
  rule: Rule
}


export const RuleForm: React.FC<RuleFormProps> = ({ rule }) => {
  
  const [description, setDescription] = useState<string>(rule.description || "")
  const [effect, setEffect] = useState<string>(rule.effect || "allow")
  const [message, setMessage] = useState(rule.message || "")
  
  
  function validate() {
  
  }
  
  return (
    <Form onSubmit={() => {validate()}}>
      <TextArea
        id="rule-description"
        labelText="Description"
        value={description}
        onChange={event => {
          const description = event.target.value
          setDescription(description)
        }}
      />
      <Select 
        id="rule-effect"
        value={effect}
        onChange={event => {
          const effect = event.target.value
          setEffect(effect)
        }}
        required
      >
        <SelectItem id="effect-allow" text="Allow" value="allow" key="allow" />
        <SelectItem id="effect-deny" text="Deny" value="deny" key="deny" />
      </Select>
      <TextArea
        id="rule-message"  
        labelText="Message"
        value={message}
        onChange={event => {
          const message = event.target.value
          setMessage(message)
        }}
      />
      <MultiSelect
        id="rule-predicates"
        label="Predicates"
        items={[
          { id: "Exists", text: "Exists" }
        ]}
      />
    </Form>
  )
}