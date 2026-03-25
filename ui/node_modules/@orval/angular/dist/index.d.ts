import { ClientDependenciesBuilder, ClientTitleBuilder, ClientHeaderBuilder, ClientFooterBuilder, ClientBuilder, ClientGeneratorsBuilder } from '@orval/core';

declare const getAngularDependencies: ClientDependenciesBuilder;
declare const generateAngularTitle: ClientTitleBuilder;
declare const generateAngularHeader: ClientHeaderBuilder;
declare const generateAngularFooter: ClientFooterBuilder;
declare const generateAngular: ClientBuilder;
declare const builder: () => () => ClientGeneratorsBuilder;

export { builder, builder as default, generateAngular, generateAngularFooter, generateAngularHeader, generateAngularTitle, getAngularDependencies };
