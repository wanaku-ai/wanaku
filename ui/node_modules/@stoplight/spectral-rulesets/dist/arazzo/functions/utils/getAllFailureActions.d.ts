import { ArazzoSpecification, Workflow, Step, FailureAction } from '../types/arazzoTypes';
export default function getAllFailureActions(step: Step, workflow: Workflow, arazzoSpec: ArazzoSpecification): FailureAction[];
