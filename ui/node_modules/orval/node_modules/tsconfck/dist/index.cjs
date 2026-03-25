"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
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
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// src/index.ts
var src_exports = {};
__export(src_exports, {
  TSConfckParseError: () => TSConfckParseError,
  TSConfckParseNativeError: () => TSConfckParseNativeError,
  find: () => find,
  findAll: () => findAll,
  findNative: () => findNative,
  parse: () => parse,
  parseNative: () => parseNative,
  toJson: () => toJson
});
module.exports = __toCommonJS(src_exports);

// src/find.ts
var import_path = __toESM(require("path"), 1);
var import_fs = require("fs");
async function find(filename, options) {
  let dir = import_path.default.dirname(import_path.default.resolve(filename));
  const root = (options == null ? void 0 : options.root) ? import_path.default.resolve(options.root) : null;
  while (dir) {
    const tsconfig = await tsconfigInDir(dir, options);
    if (tsconfig) {
      return tsconfig;
    } else {
      if (root === dir) {
        break;
      }
      const parent = import_path.default.dirname(dir);
      if (parent === dir) {
        break;
      } else {
        dir = parent;
      }
    }
  }
  throw new Error(`no tsconfig file found for ${filename}`);
}
async function tsconfigInDir(dir, options) {
  const tsconfig = import_path.default.join(dir, "tsconfig.json");
  if (options == null ? void 0 : options.tsConfigPaths) {
    return options.tsConfigPaths.has(tsconfig) ? tsconfig : void 0;
  }
  try {
    const stat = await import_fs.promises.stat(tsconfig);
    if (stat.isFile() || stat.isFIFO()) {
      return tsconfig;
    }
  } catch (e) {
    if (e.code !== "ENOENT") {
      throw e;
    }
  }
}

// src/find-all.ts
var import_path2 = __toESM(require("path"), 1);
var import_fs2 = require("fs");
var sep = import_path2.default.sep;
async function findAll(dir, options) {
  const state = {
    files: [],
    calls: 0,
    skip: options == null ? void 0 : options.skip,
    err: false
  };
  return new Promise((resolve, reject) => {
    walk(import_path2.default.resolve(dir), state, (err, files) => err ? reject(err) : resolve(files));
  });
}
function walk(dir, state, done) {
  if (state.err) {
    return;
  }
  state.calls++;
  (0, import_fs2.readdir)(dir, { withFileTypes: true }, (err, entries = []) => {
    var _a;
    if (state.err) {
      return;
    }
    if (err && !(err.code === "ENOENT" || err.code === "EACCES" || err.code === "EPERM")) {
      state.err = true;
      done(err);
    } else {
      for (const ent of entries) {
        if (ent.isDirectory() && !((_a = state.skip) == null ? void 0 : _a.call(state, ent.name))) {
          walk(`${dir}${sep}${ent.name}`, state, done);
        } else if (ent.isFile() && ent.name === "tsconfig.json") {
          state.files.push(`${dir}${sep}tsconfig.json`);
        }
      }
      if (--state.calls === 0) {
        if (!state.err) {
          done(null, state.files);
        }
      }
    }
  });
}

// src/to-json.ts
function toJson(tsconfigJson) {
  const stripped = stripDanglingComma(stripJsonComments(stripBom(tsconfigJson)));
  if (stripped.trim() === "") {
    return "{}";
  } else {
    return stripped;
  }
}
function stripDanglingComma(pseudoJson) {
  let insideString = false;
  let offset = 0;
  let result = "";
  let danglingCommaPos = null;
  for (let i = 0; i < pseudoJson.length; i++) {
    const currentCharacter = pseudoJson[i];
    if (currentCharacter === '"') {
      const escaped = isEscaped(pseudoJson, i);
      if (!escaped) {
        insideString = !insideString;
      }
    }
    if (insideString) {
      danglingCommaPos = null;
      continue;
    }
    if (currentCharacter === ",") {
      danglingCommaPos = i;
      continue;
    }
    if (danglingCommaPos) {
      if (currentCharacter === "}" || currentCharacter === "]") {
        result += pseudoJson.slice(offset, danglingCommaPos) + " ";
        offset = danglingCommaPos + 1;
        danglingCommaPos = null;
      } else if (!currentCharacter.match(/\s/)) {
        danglingCommaPos = null;
      }
    }
  }
  return result + pseudoJson.substring(offset);
}
function isEscaped(jsonString, quotePosition) {
  let index = quotePosition - 1;
  let backslashCount = 0;
  while (jsonString[index] === "\\") {
    index -= 1;
    backslashCount += 1;
  }
  return Boolean(backslashCount % 2);
}
function strip(string, start, end) {
  return string.slice(start, end).replace(/\S/g, " ");
}
var singleComment = Symbol("singleComment");
var multiComment = Symbol("multiComment");
function stripJsonComments(jsonString) {
  let isInsideString = false;
  let isInsideComment = false;
  let offset = 0;
  let result = "";
  for (let index = 0; index < jsonString.length; index++) {
    const currentCharacter = jsonString[index];
    const nextCharacter = jsonString[index + 1];
    if (!isInsideComment && currentCharacter === '"') {
      const escaped = isEscaped(jsonString, index);
      if (!escaped) {
        isInsideString = !isInsideString;
      }
    }
    if (isInsideString) {
      continue;
    }
    if (!isInsideComment && currentCharacter + nextCharacter === "//") {
      result += jsonString.slice(offset, index);
      offset = index;
      isInsideComment = singleComment;
      index++;
    } else if (isInsideComment === singleComment && currentCharacter + nextCharacter === "\r\n") {
      index++;
      isInsideComment = false;
      result += strip(jsonString, offset, index);
      offset = index;
    } else if (isInsideComment === singleComment && currentCharacter === "\n") {
      isInsideComment = false;
      result += strip(jsonString, offset, index);
      offset = index;
    } else if (!isInsideComment && currentCharacter + nextCharacter === "/*") {
      result += jsonString.slice(offset, index);
      offset = index;
      isInsideComment = multiComment;
      index++;
    } else if (isInsideComment === multiComment && currentCharacter + nextCharacter === "*/") {
      index++;
      isInsideComment = false;
      result += strip(jsonString, offset, index + 1);
      offset = index + 1;
    }
  }
  return result + (isInsideComment ? strip(jsonString.slice(offset)) : jsonString.slice(offset));
}
function stripBom(string) {
  if (string.charCodeAt(0) === 65279) {
    return string.slice(1);
  }
  return string;
}

// src/parse.ts
var import_path4 = __toESM(require("path"), 1);
var import_fs4 = require("fs");
var import_module = require("module");

// src/util.ts
var import_path3 = __toESM(require("path"), 1);
var import_fs3 = require("fs");
var POSIX_SEP_RE = new RegExp("\\" + import_path3.default.posix.sep, "g");
var NATIVE_SEP_RE = new RegExp("\\" + import_path3.default.sep, "g");
var PATTERN_REGEX_CACHE = /* @__PURE__ */ new Map();
var GLOB_ALL_PATTERN = `**/*`;
var DEFAULT_EXTENSIONS = [".ts", ".tsx", ".mts", ".cts"];
var DEFAULT_EXTENSIONS_RE_GROUP = `\\.(?:${DEFAULT_EXTENSIONS.map((ext) => ext.substring(1)).join(
  "|"
)})`;
var dynamicImportDefault = new Function("path", "return import(path).then(m => m.default)");
async function loadTS() {
  try {
    return dynamicImportDefault("typescript");
  } catch (e) {
    console.error('typescript must be installed to use "native" functions');
    throw e;
  }
}
async function resolveTSConfig(filename) {
  if (import_path3.default.extname(filename) !== ".json") {
    return;
  }
  const tsconfig = import_path3.default.resolve(filename);
  try {
    const stat = await import_fs3.promises.stat(tsconfig);
    if (stat.isFile() || stat.isFIFO()) {
      return tsconfig;
    }
  } catch (e) {
    if (e.code !== "ENOENT") {
      throw e;
    }
  }
  throw new Error(`no tsconfig file found for ${filename}`);
}
function posix2native(filename) {
  return import_path3.default.posix.sep !== import_path3.default.sep && filename.includes(import_path3.default.posix.sep) ? filename.replace(POSIX_SEP_RE, import_path3.default.sep) : filename;
}
function native2posix(filename) {
  return import_path3.default.posix.sep !== import_path3.default.sep && filename.includes(import_path3.default.sep) ? filename.replace(NATIVE_SEP_RE, import_path3.default.posix.sep) : filename;
}
function resolve2posix(dir, filename) {
  if (import_path3.default.sep === import_path3.default.posix.sep) {
    return dir ? import_path3.default.resolve(dir, filename) : import_path3.default.resolve(filename);
  }
  return native2posix(
    dir ? import_path3.default.resolve(posix2native(dir), posix2native(filename)) : import_path3.default.resolve(posix2native(filename))
  );
}
function resolveReferencedTSConfigFiles(result) {
  const dir = import_path3.default.dirname(result.tsconfigFile);
  return result.tsconfig.references.map((ref) => {
    const refPath = ref.path.endsWith(".json") ? ref.path : import_path3.default.join(ref.path, "tsconfig.json");
    return resolve2posix(dir, refPath);
  });
}
function resolveSolutionTSConfig(filename, result) {
  if (result.referenced && DEFAULT_EXTENSIONS.some((ext) => filename.endsWith(ext)) && !isIncluded(filename, result)) {
    const solutionTSConfig = result.referenced.find(
      (referenced) => isIncluded(filename, referenced)
    );
    if (solutionTSConfig) {
      return {
        ...solutionTSConfig,
        solution: result
      };
    }
  }
  return result;
}
function isIncluded(filename, result) {
  const dir = native2posix(import_path3.default.dirname(result.tsconfigFile));
  const files = (result.tsconfig.files || []).map((file) => resolve2posix(dir, file));
  const absoluteFilename = resolve2posix(null, filename);
  if (files.includes(filename)) {
    return true;
  }
  const isIncluded2 = isGlobMatch(
    absoluteFilename,
    dir,
    result.tsconfig.include || (result.tsconfig.files ? [] : [GLOB_ALL_PATTERN])
  );
  if (isIncluded2) {
    const isExcluded = isGlobMatch(absoluteFilename, dir, result.tsconfig.exclude || []);
    return !isExcluded;
  }
  return false;
}
function isGlobMatch(filename, dir, patterns) {
  return patterns.some((pattern) => {
    let lastWildcardIndex = pattern.length;
    let hasWildcard = false;
    for (let i = pattern.length - 1; i > -1; i--) {
      if (pattern[i] === "*" || pattern[i] === "?") {
        lastWildcardIndex = i;
        hasWildcard = true;
        break;
      }
    }
    if (lastWildcardIndex < pattern.length - 1 && !filename.endsWith(pattern.slice(lastWildcardIndex + 1))) {
      return false;
    }
    if (pattern.endsWith("*") && !DEFAULT_EXTENSIONS.some((ext) => filename.endsWith(ext))) {
      return false;
    }
    if (pattern === GLOB_ALL_PATTERN) {
      return filename.startsWith(`${dir}/`);
    }
    const resolvedPattern = resolve2posix(dir, pattern);
    let firstWildcardIndex = -1;
    for (let i = 0; i < resolvedPattern.length; i++) {
      if (resolvedPattern[i] === "*" || resolvedPattern[i] === "?") {
        firstWildcardIndex = i;
        hasWildcard = true;
        break;
      }
    }
    if (firstWildcardIndex > 1 && !filename.startsWith(resolvedPattern.slice(0, firstWildcardIndex - 1))) {
      return false;
    }
    if (!hasWildcard) {
      return filename === resolvedPattern;
    }
    if (PATTERN_REGEX_CACHE.has(resolvedPattern)) {
      return PATTERN_REGEX_CACHE.get(resolvedPattern).test(filename);
    }
    const regex = pattern2regex(resolvedPattern);
    PATTERN_REGEX_CACHE.set(resolvedPattern, regex);
    return regex.test(filename);
  });
}
function pattern2regex(resolvedPattern) {
  let regexStr = "^";
  for (let i = 0; i < resolvedPattern.length; i++) {
    const char = resolvedPattern[i];
    if (char === "?") {
      regexStr += "[^\\/]";
      continue;
    }
    if (char === "*") {
      if (resolvedPattern[i + 1] === "*" && resolvedPattern[i + 2] === "/") {
        i += 2;
        regexStr += "(?:[^\\/]*\\/)*";
        continue;
      }
      regexStr += "[^\\/]*";
      continue;
    }
    if ("/.+^${}()|[]\\".includes(char)) {
      regexStr += `\\`;
    }
    regexStr += char;
  }
  if (resolvedPattern.endsWith("*")) {
    regexStr += DEFAULT_EXTENSIONS_RE_GROUP;
  }
  regexStr += "$";
  return new RegExp(regexStr);
}

// src/parse.ts
async function parse(filename, options) {
  const cache = options == null ? void 0 : options.cache;
  if (cache == null ? void 0 : cache.has(filename)) {
    return cache.get(filename);
  }
  let tsconfigFile;
  if (options == null ? void 0 : options.resolveWithEmptyIfConfigNotFound) {
    try {
      tsconfigFile = await resolveTSConfig(filename) || await find(filename, options);
    } catch (e) {
      const notFoundResult = {
        tsconfigFile: "no_tsconfig_file_found",
        tsconfig: {}
      };
      cache == null ? void 0 : cache.set(filename, notFoundResult);
      return notFoundResult;
    }
  } else {
    tsconfigFile = await resolveTSConfig(filename) || await find(filename, options);
  }
  let result;
  if (cache == null ? void 0 : cache.has(tsconfigFile)) {
    result = cache.get(tsconfigFile);
  } else {
    result = await parseFile(tsconfigFile, cache);
    await Promise.all([parseExtends(result, cache), parseReferences(result, cache)]);
    cache == null ? void 0 : cache.set(tsconfigFile, result);
  }
  result = resolveSolutionTSConfig(filename, result);
  cache == null ? void 0 : cache.set(filename, result);
  return result;
}
async function parseFile(tsconfigFile, cache) {
  if (cache == null ? void 0 : cache.has(tsconfigFile)) {
    return cache.get(tsconfigFile);
  }
  try {
    const tsconfigJson = await import_fs4.promises.readFile(tsconfigFile, "utf-8");
    const json = toJson(tsconfigJson);
    const result = {
      tsconfigFile,
      tsconfig: normalizeTSConfig(JSON.parse(json), import_path4.default.dirname(tsconfigFile))
    };
    cache == null ? void 0 : cache.set(tsconfigFile, result);
    return result;
  } catch (e) {
    throw new TSConfckParseError(
      `parsing ${tsconfigFile} failed: ${e}`,
      "PARSE_FILE",
      tsconfigFile,
      e
    );
  }
}
function normalizeTSConfig(tsconfig, dir) {
  var _a;
  if (((_a = tsconfig.compilerOptions) == null ? void 0 : _a.baseUrl) && !import_path4.default.isAbsolute(tsconfig.compilerOptions.baseUrl)) {
    tsconfig.compilerOptions.baseUrl = resolve2posix(dir, tsconfig.compilerOptions.baseUrl);
  }
  return tsconfig;
}
async function parseReferences(result, cache) {
  if (!result.tsconfig.references) {
    return;
  }
  const referencedFiles = resolveReferencedTSConfigFiles(result);
  const referenced = await Promise.all(referencedFiles.map((file) => parseFile(file, cache)));
  await Promise.all(referenced.map((ref) => parseExtends(ref, cache)));
  result.referenced = referenced;
}
async function parseExtends(result, cache) {
  if (!result.tsconfig.extends) {
    return;
  }
  const extended = [
    { tsconfigFile: result.tsconfigFile, tsconfig: JSON.parse(JSON.stringify(result.tsconfig)) }
  ];
  let pos = 0;
  const extendsPath = [];
  let currentBranchDepth = 0;
  while (pos < extended.length) {
    const extending = extended[pos];
    extendsPath.push(extending.tsconfigFile);
    if (extending.tsconfig.extends) {
      currentBranchDepth += 1;
      let resolvedExtends;
      if (!Array.isArray(extending.tsconfig.extends)) {
        resolvedExtends = [resolveExtends(extending.tsconfig.extends, extending.tsconfigFile)];
      } else {
        resolvedExtends = extending.tsconfig.extends.reverse().map((ex) => resolveExtends(ex, extending.tsconfigFile));
      }
      const circularExtends = resolvedExtends.find(
        (tsconfigFile) => extendsPath.includes(tsconfigFile)
      );
      if (circularExtends) {
        const circle = extendsPath.concat([circularExtends]).join(" -> ");
        throw new TSConfckParseError(
          `Circular dependency in "extends": ${circle}`,
          "EXTENDS_CIRCULAR",
          result.tsconfigFile
        );
      }
      extended.splice(
        pos + 1,
        0,
        ...await Promise.all(resolvedExtends.map((file) => parseFile(file, cache)))
      );
    } else {
      extendsPath.splice(-currentBranchDepth);
      currentBranchDepth = 0;
    }
    pos = pos + 1;
  }
  result.extended = extended;
  for (const ext of result.extended.slice(1)) {
    extendTSConfig(result, ext);
  }
}
function resolveExtends(extended, from) {
  let error;
  try {
    return (0, import_module.createRequire)(from).resolve(extended);
  } catch (e) {
    error = e;
  }
  if (!import_path4.default.isAbsolute(extended) && !extended.startsWith("./") && !extended.startsWith("../")) {
    try {
      const fallbackExtended = import_path4.default.join(extended, "tsconfig.json");
      return (0, import_module.createRequire)(from).resolve(fallbackExtended);
    } catch (e) {
      error = e;
    }
  }
  throw new TSConfckParseError(
    `failed to resolve "extends":"${extended}" in ${from}`,
    "EXTENDS_RESOLVE",
    from,
    error
  );
}
var EXTENDABLE_KEYS = [
  "compilerOptions",
  "files",
  "include",
  "exclude",
  "watchOptions",
  "compileOnSave",
  "typeAcquisition",
  "buildOptions"
];
function extendTSConfig(extending, extended) {
  const extendingConfig = extending.tsconfig;
  const extendedConfig = extended.tsconfig;
  const relativePath = native2posix(
    import_path4.default.relative(import_path4.default.dirname(extending.tsconfigFile), import_path4.default.dirname(extended.tsconfigFile))
  );
  for (const key of Object.keys(extendedConfig).filter((key2) => EXTENDABLE_KEYS.includes(key2))) {
    if (key === "compilerOptions") {
      if (!extendingConfig.compilerOptions) {
        extendingConfig.compilerOptions = {};
      }
      for (const option of Object.keys(extendedConfig.compilerOptions)) {
        if (Object.prototype.hasOwnProperty.call(extendingConfig.compilerOptions, option)) {
          continue;
        }
        extendingConfig.compilerOptions[option] = rebaseRelative(
          option,
          extendedConfig.compilerOptions[option],
          relativePath
        );
      }
    } else if (extendingConfig[key] === void 0) {
      if (key === "watchOptions") {
        extendingConfig.watchOptions = {};
        for (const option of Object.keys(extendedConfig.watchOptions)) {
          extendingConfig.watchOptions[option] = rebaseRelative(
            option,
            extendedConfig.watchOptions[option],
            relativePath
          );
        }
      } else {
        extendingConfig[key] = rebaseRelative(key, extendedConfig[key], relativePath);
      }
    }
  }
}
var REBASE_KEYS = [
  // root
  "files",
  "include",
  "exclude",
  // compilerOptions
  "baseUrl",
  "rootDir",
  "rootDirs",
  "typeRoots",
  "outDir",
  "outFile",
  "declarationDir",
  // watchOptions
  "excludeDirectories",
  "excludeFiles"
];
function rebaseRelative(key, value, prependPath) {
  if (!REBASE_KEYS.includes(key)) {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map((x) => rebasePath(x, prependPath));
  } else {
    return rebasePath(value, prependPath);
  }
}
function rebasePath(value, prependPath) {
  if (import_path4.default.isAbsolute(value)) {
    return value;
  } else {
    return import_path4.default.posix.normalize(import_path4.default.posix.join(prependPath, value));
  }
}
var TSConfckParseError = class _TSConfckParseError extends Error {
  constructor(message, code, tsconfigFile, cause) {
    super(message);
    Object.setPrototypeOf(this, _TSConfckParseError.prototype);
    this.name = _TSConfckParseError.name;
    this.code = code;
    this.cause = cause;
    this.tsconfigFile = tsconfigFile;
  }
};

// src/find-native.ts
var import_path5 = __toESM(require("path"), 1);
async function findNative(filename) {
  const ts = await loadTS();
  const { findConfigFile, sys } = ts;
  const tsconfigFile = findConfigFile(import_path5.default.dirname(import_path5.default.resolve(filename)), sys.fileExists);
  if (!tsconfigFile) {
    throw new Error(`no tsconfig file found for ${filename}`);
  }
  return tsconfigFile;
}

// src/parse-native.ts
var import_path6 = __toESM(require("path"), 1);
async function parseNative(filename, options) {
  const cache = options == null ? void 0 : options.cache;
  if (cache == null ? void 0 : cache.has(filename)) {
    return cache.get(filename);
  }
  let tsconfigFile;
  if (options == null ? void 0 : options.resolveWithEmptyIfConfigNotFound) {
    try {
      tsconfigFile = await resolveTSConfig(filename);
      if (!tsconfigFile) {
        tsconfigFile = await findNative(filename);
      }
    } catch (e) {
      const notFoundResult = {
        tsconfigFile: "no_tsconfig_file_found",
        tsconfig: {},
        result: null
      };
      cache == null ? void 0 : cache.set(filename, notFoundResult);
      return notFoundResult;
    }
  } else {
    tsconfigFile = await resolveTSConfig(filename);
    if (!tsconfigFile) {
      tsconfigFile = await findNative(filename);
    }
  }
  let result;
  if (cache == null ? void 0 : cache.has(tsconfigFile)) {
    result = cache.get(tsconfigFile);
  } else {
    const ts = await loadTS();
    result = await parseFile2(tsconfigFile, ts, options);
    await parseReferences2(result, ts, options);
    cache == null ? void 0 : cache.set(tsconfigFile, result);
  }
  result = resolveSolutionTSConfig(filename, result);
  cache == null ? void 0 : cache.set(filename, result);
  return result;
}
async function parseFile2(tsconfigFile, ts, options) {
  const cache = options == null ? void 0 : options.cache;
  if (cache == null ? void 0 : cache.has(tsconfigFile)) {
    return cache.get(tsconfigFile);
  }
  const posixTSConfigFile = native2posix(tsconfigFile);
  const { parseJsonConfigFileContent, readConfigFile, sys } = ts;
  const { config, error } = readConfigFile(posixTSConfigFile, sys.readFile);
  if (error) {
    throw new TSConfckParseNativeError(error, tsconfigFile, null);
  }
  const host = {
    useCaseSensitiveFileNames: false,
    readDirectory: sys.readDirectory,
    fileExists: sys.fileExists,
    readFile: sys.readFile
  };
  if (options == null ? void 0 : options.ignoreSourceFiles) {
    config.files = [];
    config.include = [];
  }
  const nativeResult = parseJsonConfigFileContent(
    config,
    host,
    import_path6.default.dirname(posixTSConfigFile),
    void 0,
    posixTSConfigFile
  );
  checkErrors(nativeResult, tsconfigFile);
  const result = {
    tsconfigFile,
    tsconfig: result2tsconfig(nativeResult, ts),
    result: nativeResult
  };
  cache == null ? void 0 : cache.set(tsconfigFile, result);
  return result;
}
async function parseReferences2(result, ts, options) {
  if (!result.tsconfig.references) {
    return;
  }
  const referencedFiles = resolveReferencedTSConfigFiles(result);
  result.referenced = await Promise.all(
    referencedFiles.map((file) => parseFile2(file, ts, options))
  );
}
function checkErrors(nativeResult, tsconfigFile) {
  var _a;
  const ignoredErrorCodes = [
    // see https://github.com/microsoft/TypeScript/blob/main/src/compiler/diagnosticMessages.json
    18002,
    // empty files list
    18003
    // no inputs
  ];
  const criticalError = (_a = nativeResult.errors) == null ? void 0 : _a.find(
    (error) => error.category === 1 && !ignoredErrorCodes.includes(error.code)
  );
  if (criticalError) {
    throw new TSConfckParseNativeError(criticalError, tsconfigFile, nativeResult);
  }
}
function result2tsconfig(result, ts) {
  const tsconfig = JSON.parse(JSON.stringify(result.raw));
  const ignoredOptions = ["configFilePath", "pathsBasePath"];
  if (result.options && Object.keys(result.options).some((o) => !ignoredOptions.includes(o))) {
    tsconfig.compilerOptions = {
      ...result.options
    };
    for (const ignored of ignoredOptions) {
      delete tsconfig.compilerOptions[ignored];
    }
  }
  const compilerOptions = tsconfig.compilerOptions;
  if (compilerOptions) {
    if (compilerOptions.lib != null) {
      compilerOptions.lib = compilerOptions.lib.map(
        (x) => x.replace(/^lib\./, "").replace(/\.d\.ts$/, "")
      );
    }
    const enumProperties = [
      { name: "importsNotUsedAsValues", enumeration: ts.ImportsNotUsedAsValues },
      { name: "module", enumeration: ts.ModuleKind },
      {
        name: "moduleResolution",
        enumeration: {
          ...ts.ModuleResolutionKind,
          2: "node"
          /*ts.ModuleResolutionKind uses "Node10" but in tsconfig it is just node"*/
        }
      },
      {
        name: "newLine",
        enumeration: { 0: "crlf", 1: "lf" }
        /*ts.NewLineKind uses different names*/
      },
      { name: "target", enumeration: ts.ScriptTarget }
    ];
    for (const prop of enumProperties) {
      if (compilerOptions[prop.name] != null && typeof compilerOptions[prop.name] === "number") {
        compilerOptions[prop.name] = prop.enumeration[compilerOptions[prop.name]].toLowerCase();
      }
    }
    if (compilerOptions.target === "latest") {
      compilerOptions.target = "esnext";
    }
  }
  if (result.watchOptions) {
    tsconfig.watchOptions = {
      ...result.watchOptions
    };
  }
  const watchOptions = tsconfig.watchOptions;
  if (watchOptions) {
    const enumProperties = [
      { name: "watchFile", enumeration: ts.WatchFileKind },
      { name: "watchDirectory", enumeration: ts.WatchDirectoryKind },
      { name: "fallbackPolling", enumeration: ts.PollingWatchKind }
    ];
    for (const prop of enumProperties) {
      if (watchOptions[prop.name] != null && typeof watchOptions[prop.name] === "number") {
        const enumVal = prop.enumeration[watchOptions[prop.name]];
        watchOptions[prop.name] = enumVal.charAt(0).toLowerCase() + enumVal.slice(1);
      }
    }
  }
  if (tsconfig.compileOnSave === false) {
    delete tsconfig.compileOnSave;
  }
  return tsconfig;
}
var TSConfckParseNativeError = class _TSConfckParseNativeError extends Error {
  constructor(diagnostic, tsconfigFile, result) {
    super(diagnostic.messageText);
    Object.setPrototypeOf(this, _TSConfckParseNativeError.prototype);
    this.name = _TSConfckParseNativeError.name;
    this.code = `TS ${diagnostic.code}`;
    this.diagnostic = diagnostic;
    this.result = result;
    this.tsconfigFile = tsconfigFile;
  }
};
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  TSConfckParseError,
  TSConfckParseNativeError,
  find,
  findAll,
  findNative,
  parse,
  parseNative,
  toJson
});
//# sourceMappingURL=index.cjs.map