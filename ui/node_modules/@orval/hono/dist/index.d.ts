import { GeneratorDependency, ClientHeaderBuilder, ClientFooterBuilder, ClientBuilder, ClientExtraFilesBuilder, ClientGeneratorsBuilder } from '@orval/core';

declare const getHonoDependencies: () => GeneratorDependency[];
declare const getHonoHeader: ClientHeaderBuilder;
declare const getHonoFooter: ClientFooterBuilder;
declare const generateHono: ClientBuilder;
declare const generateExtraFiles: ClientExtraFilesBuilder;
declare const builder: () => () => ClientGeneratorsBuilder;

export { builder, builder as default, generateExtraFiles, generateHono, getHonoDependencies, getHonoFooter, getHonoHeader };
