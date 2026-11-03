import React, { useState } from "react"
import {
  Button,
  ComposedModal,
  Form,
  ModalBody, ModalHeader,
  ProgressIndicator,
  ProgressStep,
  Select,
  SelectItem,
  TextArea
} from "@carbon/react"
import {Effect, Rule} from "../../models"
import {ModalFooter} from "@carbon/react"
import {RulePredicatesPanel} from "./RulePredicatesPanel.tsx";
import {RuleSelectorsPanel} from "./RuleSelectorsPanel.tsx";


interface ActionPolicyModalProps {
  onSubmit: (rule: Rule) => void
  onClose: () => void
}


export const AddRuleModal: React.FC<ActionPolicyModalProps> = ({ onSubmit, onClose }) => {
  
  const [currentStep, setCurrentStep] = useState(0)
  const [step1Invalid, setStep1Invalid] = useState(false)
  const [step2Invalid, setStep2Invalid] = useState(false)
  const [step3Invalid, setStep3Invalid] = useState(false)
  const [step4Invalid, setStep4Invalid] = useState(false)
  
  const [step1Complete, setStep1Complete] = useState(false)
  const [step2Complete, setStep2Complete] = useState(false)
  const [step3Complete, setStep3Complete] = useState(false)
  
  const [ruleDescription, setRuleDescription] = useState("")
  const [ruleEffect, setRuleEffect] = useState("allow")
  const [ruleMessage, setRuleMessage] = useState("")
  const [ruleMetadata, setRuleMetadata] = useState("")
  
  const FINAL_STEP = 3

  
  
  function isFinalStep(): boolean {
    return currentStep === FINAL_STEP
  }
  
  function isFormInvalid(): boolean {
    return step1Invalid || step2Invalid || step3Invalid || step4Invalid
  }
  
  function isFormValid(): boolean {
    return !isFormInvalid()
  }
  
  function primaryButtonText(): string {
    return isFinalStep() ? "Save" : "Next"
  }
  
  function handleSubmit() {
    const rule = { id: generateRuleId(), effect: ruleEffect as Effect }
    onSubmit(rule)
  }
  
  function generateRuleId(): string {
    return crypto.randomUUID()
  }
  
  function validateStep(step: number): boolean {
    switch (step) {
      case 0:
        setStep1Invalid(false)
        return true
      case 1:
        setStep2Invalid(false)
        return true
      case 2:
        setStep3Invalid(false)
        return true
      case 3:
        setStep4Invalid(false)
        return true
      default:
        throw RangeError(`Unknown step to validate: ${step}`)
    }
  }
  
  function completeStep(step: number) {
    switch (step) {
      case 0:
        setStep1Complete(true)
        break
      case 1:
        setStep2Complete(true)
        break
      case 2:
        setStep3Complete(true)
        break
      default:
        throw RangeError(`Unknown step to complete: ${step}`)
    }
  }
  
  
  return (
    <ComposedModal
      open={true}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <ModalHeader title="Create New Rule">
        <ProgressIndicator currentIndex={0}>
          <ProgressStep
            label="1. General"
            description="Step 1: New rule general information"
            current={currentStep === 0}
            complete={step1Complete}
            invalid={step1Invalid}
          />
          <ProgressStep
            label="2. Metadata"
            description="Step 2: Enter metadata for the new rule"
            current={currentStep === 1}
            complete={step2Complete}
            invalid={step1Invalid}
          />
          <ProgressStep
            label="3. Predicates"
            description="Step 3: Specify rule predicates"
            current={currentStep === 2}
            complete={step3Complete}
            invalid={step3Invalid}
          />
          <ProgressStep
            label="4. Selectors"
            description="Step 4: Specify rule selectors"
            current={currentStep === 3}
            invalid={step4Invalid}
          />
        </ProgressIndicator>
      </ModalHeader>
      
      <ModalBody>
      {currentStep === 0 && (
        <Form>
          <TextArea
            id="rule-description"
            labelText="Description"
            value={ruleDescription}
            onChange={event => {
              const description = event.target.value
              setRuleDescription(description)
            }}
          />
          
          <Select
            id="rule-effect"
            value={ruleEffect}
            onChange={event => {
              const effect = event.target.value
              setRuleEffect(effect)
            }}
            required
          >
            <SelectItem id="effect-allow" text="Allow" value="allow" key="allow" />
            <SelectItem id="effect-deny" text="Deny" value="deny" key="deny" />
          </Select>
          
          <TextArea
            id="rule-message"
            labelText="Message"
            value={ruleMessage}
            onChange={event => {
              const message = event.target.value
              setRuleMessage(message)
            }}
          />
        </Form>
      )}
      {currentStep === 1 && (
        <Form>
          <TextArea
            id="rule-metadata"
            labelText="Metadata"
            value={ruleMetadata}
            onChange={event => {
              const metadata = event.target.value
              setRuleMetadata(metadata)
            }}
          />
        </Form>
      )}
      {currentStep === 2 && (
        <Form>
          <RulePredicatesPanel />
        </Form>
      )}
      {currentStep === 3 && (
        <Form>
          <RuleSelectorsPanel />
        </Form>
      )}
      </ModalBody>
      
      <ModalFooter>
        <Button
          kind="tertiary"
          onClick={onClose}>
          Cancel
        </Button>
        <Button
          kind="secondary"
          disabled={currentStep === 0}
          onClick={() => setCurrentStep(currentStep - 1)}>
          Previous
        </Button>
        <Button
          kind="primary"
          disabled={isFinalStep() && isFormInvalid()}
          onClick={() => {
            if (isFinalStep()) {
              validateStep(FINAL_STEP)
              if (isFormValid()) {
                handleSubmit()
              }
            } else {
              validateStep(currentStep)
              completeStep(currentStep)
              setCurrentStep(currentStep + 1)
            }
          }}>
          {primaryButtonText()}
        </Button>
      </ModalFooter>
    </ComposedModal>
  )
}