'use strict';

Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

const upperFirst = require('../_chunk/upperFirst-CorAVn.js');

function startCase(str) {
    const words = upperFirst.words(str.trim());
    let result = '';
    for (let i = 0; i < words.length; i++) {
        const word = words[i];
        if (result) {
            result += ' ';
        }
        result += word[0].toUpperCase() + word.slice(1).toLowerCase();
    }
    return result;
}

exports.camelCase = upperFirst.camelCase;
exports.capitalize = upperFirst.capitalize;
exports.constantCase = upperFirst.constantCase;
exports.deburr = upperFirst.deburr;
exports.escape = upperFirst.escape;
exports.escapeRegExp = upperFirst.escapeRegExp;
exports.kebabCase = upperFirst.kebabCase;
exports.lowerCase = upperFirst.lowerCase;
exports.lowerFirst = upperFirst.lowerFirst;
exports.pad = upperFirst.pad;
exports.pascalCase = upperFirst.pascalCase;
exports.snakeCase = upperFirst.snakeCase;
exports.trim = upperFirst.trim;
exports.trimEnd = upperFirst.trimEnd;
exports.trimStart = upperFirst.trimStart;
exports.unescape = upperFirst.unescape;
exports.upperCase = upperFirst.upperCase;
exports.upperFirst = upperFirst.upperFirst;
exports.words = upperFirst.words;
exports.startCase = startCase;
