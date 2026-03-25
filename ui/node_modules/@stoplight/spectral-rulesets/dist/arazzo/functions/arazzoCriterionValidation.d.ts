import { IFunctionResult } from '@stoplight/spectral-core';
import { Criterion, ArazzoSpecification } from './types/arazzoTypes';
export default function arazzoCriterionValidation(criterion: Criterion, contextPath: (string | number)[], arazzoSpec: ArazzoSpecification): IFunctionResult[];
