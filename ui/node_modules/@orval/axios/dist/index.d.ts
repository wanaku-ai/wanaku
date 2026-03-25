import * as _orval_core from '@orval/core';
import { ClientDependenciesBuilder, ClientTitleBuilder, ClientHeaderBuilder, ClientFooterBuilder, GeneratorVerbOptions, GeneratorOptions, ClientBuilder, ClientGeneratorsBuilder } from '@orval/core';

declare const getAxiosDependencies: ClientDependenciesBuilder;
declare const generateAxiosTitle: ClientTitleBuilder;
declare const generateAxiosHeader: ClientHeaderBuilder;
declare const generateAxiosFooter: ClientFooterBuilder;
declare const generateAxios: (verbOptions: GeneratorVerbOptions, options: GeneratorOptions) => {
    implementation: string;
    imports: _orval_core.GeneratorImport[];
};
declare const generateAxiosFunctions: ClientBuilder;
declare const builder: ({ type }?: {
    type?: "axios" | "axios-functions" | undefined;
}) => () => ClientGeneratorsBuilder;

export { builder, builder as default, generateAxios, generateAxiosFooter, generateAxiosFunctions, generateAxiosHeader, generateAxiosTitle, getAxiosDependencies };
