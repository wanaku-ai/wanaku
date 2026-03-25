"use strict";
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// src/index.ts
var src_exports = {};
__export(src_exports, {
  builder: () => builder,
  default: () => src_default,
  generateAngular: () => generateAngular,
  generateAngularFooter: () => generateAngularFooter,
  generateAngularHeader: () => generateAngularHeader,
  generateAngularTitle: () => generateAngularTitle,
  getAngularDependencies: () => getAngularDependencies
});
module.exports = __toCommonJS(src_exports);
var import_core = require("@orval/core");
var ANGULAR_DEPENDENCIES = [
  {
    exports: [
      { name: "HttpClient", values: true },
      { name: "HttpHeaders" },
      { name: "HttpParams" },
      { name: "HttpContext" },
      { name: "HttpResponse", alias: "AngularHttpResponse" },
      // alias to prevent naming conflict with msw
      { name: "HttpEvent" }
    ],
    dependency: "@angular/common/http"
  },
  {
    exports: [{ name: "Injectable", values: true }],
    dependency: "@angular/core"
  },
  {
    exports: [{ name: "Observable", values: true }],
    dependency: "rxjs"
  }
];
var returnTypesToWrite = /* @__PURE__ */ new Map();
var getAngularDependencies = () => ANGULAR_DEPENDENCIES;
var generateAngularTitle = (title) => {
  const sanTitle = (0, import_core.sanitize)(title);
  return `${(0, import_core.pascal)(sanTitle)}Service`;
};
var generateAngularHeader = ({
  title,
  isRequestOptions,
  isMutator,
  isGlobalMutator,
  provideIn
}) => `
${isRequestOptions && !isGlobalMutator ? `type HttpClientOptions = {
  headers?: HttpHeaders | {
      [header: string]: string | string[];
  };
  context?: HttpContext;
  observe?: any;
  params?: HttpParams | {
    [param: string]: string | number | boolean | ReadonlyArray<string | number | boolean>;
  };
  reportProgress?: boolean;
  responseType?: any;
  withCredentials?: boolean;
};` : ""}

${isRequestOptions && isMutator ? `// eslint-disable-next-line
    type ThirdParameter<T extends (...args: any) => any> = T extends (
  config: any,
  httpClient: any,
  args: infer P,
) => any
  ? P
  : never;` : ""}

@Injectable(${provideIn ? `{ providedIn: '${(0, import_core.isBoolean)(provideIn) ? "root" : provideIn}' }` : ""})
export class ${title} {
  constructor(
    private http: HttpClient,
  ) {}`;
var generateAngularFooter = ({
  operationNames
}) => {
  let footer = "};\n\n";
  operationNames.forEach((operationName) => {
    if (returnTypesToWrite.has(operationName)) {
      footer += returnTypesToWrite.get(operationName) + "\n";
    }
  });
  return footer;
};
var generateImplementation = ({
  headers,
  queryParams,
  operationName,
  response,
  mutator,
  body,
  props,
  verb,
  override,
  formData,
  formUrlEncoded,
  paramsSerializer
}, { route, context }) => {
  var _a, _b;
  const isRequestOptions = (override == null ? void 0 : override.requestOptions) !== false;
  const isFormData = (override == null ? void 0 : override.formData) !== false;
  const isFormUrlEncoded = (override == null ? void 0 : override.formUrlEncoded) !== false;
  const isExactOptionalPropertyTypes = !!((_b = (_a = context.output.tsconfig) == null ? void 0 : _a.compilerOptions) == null ? void 0 : _b.exactOptionalPropertyTypes);
  const bodyForm = (0, import_core.generateFormDataAndUrlEncodedFunction)({
    formData,
    formUrlEncoded,
    body,
    isFormData,
    isFormUrlEncoded
  });
  const dataType = response.definition.success || "unknown";
  returnTypesToWrite.set(
    operationName,
    `export type ${(0, import_core.pascal)(
      operationName
    )}ClientResult = NonNullable<${dataType}>`
  );
  if (mutator) {
    const mutatorConfig = (0, import_core.generateMutatorConfig)({
      route,
      body,
      headers,
      queryParams,
      response,
      verb,
      isFormData,
      isFormUrlEncoded,
      hasSignal: false,
      isExactOptionalPropertyTypes
    });
    const requestOptions = isRequestOptions ? (0, import_core.generateMutatorRequestOptions)(
      override == null ? void 0 : override.requestOptions,
      mutator.hasThirdArg
    ) : "";
    const propsImplementation = mutator.bodyTypeName && body.definition ? (0, import_core.toObjectString)(props, "implementation").replace(
      new RegExp(`(\\w*):\\s?${body.definition}`),
      `$1: ${mutator.bodyTypeName}<${body.definition}>`
    ) : (0, import_core.toObjectString)(props, "implementation");
    return ` ${operationName}<TData = ${dataType}>(
    ${propsImplementation}
 ${isRequestOptions && mutator.hasThirdArg ? `options?: ThirdParameter<typeof ${mutator.name}>` : ""}) {${bodyForm}
      return ${mutator.name}<TData>(
      ${mutatorConfig},
      this.http,
      ${requestOptions});
    }
  `;
  }
  const options = (0, import_core.generateOptions)({
    route,
    body,
    headers,
    queryParams,
    response,
    verb,
    requestOptions: override == null ? void 0 : override.requestOptions,
    isFormData,
    isFormUrlEncoded,
    paramsSerializer,
    paramsSerializerOptions: override == null ? void 0 : override.paramsSerializerOptions,
    isAngular: true,
    isExactOptionalPropertyTypes,
    hasSignal: false
  });
  const propsDefinition = (0, import_core.toObjectString)(props, "definition");
  const overloads = isRequestOptions ? `${operationName}<TData = ${dataType}>(
    ${propsDefinition} options?: Omit<HttpClientOptions, 'observe'> & { observe?: 'body' }
  ): Observable<TData>;
    ${operationName}<TData = ${dataType}>(
    ${propsDefinition} options?: Omit<HttpClientOptions, 'observe'> & { observe?: 'response' }
  ): Observable<AngularHttpResponse<TData>>;
    ${operationName}<TData = ${dataType}>(
    ${propsDefinition} options?: Omit<HttpClientOptions, 'observe'> & { observe?: 'events' }
  ): Observable<HttpEvent<TData>>;` : "";
  return ` ${overloads}${operationName}<TData = ${dataType}>(
    ${(0, import_core.toObjectString)(
    props,
    "implementation"
  )} ${isRequestOptions ? `options?: HttpClientOptions
` : ""}  ): Observable<TData>  {${bodyForm}
    return this.http.${verb}<TData>(${options});
  }
`;
};
var generateAngular = (verbOptions, options) => {
  const imports = (0, import_core.generateVerbImports)(verbOptions);
  const implementation = generateImplementation(verbOptions, options);
  return { implementation, imports };
};
var angularClientBuilder = {
  client: generateAngular,
  header: generateAngularHeader,
  dependencies: getAngularDependencies,
  footer: generateAngularFooter,
  title: generateAngularTitle
};
var builder = () => () => angularClientBuilder;
var src_default = builder;
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  builder,
  generateAngular,
  generateAngularFooter,
  generateAngularHeader,
  generateAngularTitle,
  getAngularDependencies
});
//# sourceMappingURL=index.js.map