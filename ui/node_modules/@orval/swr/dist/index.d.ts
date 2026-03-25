import { ClientDependenciesBuilder, ClientHeaderBuilder, ClientBuilder, ClientGeneratorsBuilder } from '@orval/core';

declare const getSwrDependencies: ClientDependenciesBuilder;
declare const generateSwrHeader: ClientHeaderBuilder;
declare const generateSwr: ClientBuilder;
declare const builder: () => () => ClientGeneratorsBuilder;

export { builder, builder as default, generateSwr, generateSwrHeader, getSwrDependencies };
