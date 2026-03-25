import * as _orval_core from '@orval/core';
import { GlobalMockOptions, GenerateMockImports, GeneratorVerbOptions, GeneratorOptions } from '@orval/core';

declare const DEFAULT_MOCK_OPTIONS: GlobalMockOptions;
declare const generateMockImports: GenerateMockImports;
declare const generateMock: (generatorVerbOptions: GeneratorVerbOptions, generatorOptions: Omit<GeneratorOptions, 'mock'> & {
    mock: GlobalMockOptions;
}) => _orval_core.ClientMockGeneratorBuilder;

export { DEFAULT_MOCK_OPTIONS, generateMock, generateMockImports };
