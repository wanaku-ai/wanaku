'use strict';

function capitalize(str) {
    return (str.charAt(0).toUpperCase() + str.slice(1).toLowerCase());
}

const CASE_SPLIT_PATTERN = /\p{Lu}?\p{Ll}+|[0-9]+|\p{Lu}+(?!\p{Ll})|\p{Emoji_Presentation}|\p{Extended_Pictographic}|\p{L}+/gu;
function words(str) {
    return Array.from(str.match(CASE_SPLIT_PATTERN) ?? []);
}

function camelCase(str) {
    const words$1 = words(str);
    if (words$1.length === 0) {
        return '';
    }
    const [first, ...rest] = words$1;
    return `${first.toLowerCase()}${rest.map(word => capitalize(word)).join('')}`;
}

function constantCase(str) {
    const words$1 = words(str);
    return words$1.map(word => word.toUpperCase()).join('_');
}

const deburrMap = new Map(Object.entries({
    Æ: 'Ae',
    Ð: 'D',
    Ø: 'O',
    Þ: 'Th',
    ß: 'ss',
    æ: 'ae',
    ð: 'd',
    ø: 'o',
    þ: 'th',
    Đ: 'D',
    đ: 'd',
    Ħ: 'H',
    ħ: 'h',
    ı: 'i',
    Ĳ: 'IJ',
    ĳ: 'ij',
    ĸ: 'k',
    Ŀ: 'L',
    ŀ: 'l',
    Ł: 'L',
    ł: 'l',
    ŉ: "'n",
    Ŋ: 'N',
    ŋ: 'n',
    Œ: 'Oe',
    œ: 'oe',
    Ŧ: 'T',
    ŧ: 't',
    ſ: 's',
}));
function deburr(str) {
    str = str.normalize('NFD');
    let result = '';
    for (let i = 0; i < str.length; i++) {
        const char = str[i];
        if ((char >= '\u0300' && char <= '\u036f') || (char >= '\ufe20' && char <= '\ufe23')) {
            continue;
        }
        result += deburrMap.get(char) ?? char;
    }
    return result;
}

const htmlEscapes = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
};
function escape(str) {
    return str.replace(/[&<>"']/g, match => htmlEscapes[match]);
}

function escapeRegExp(str) {
    return str.replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
}

function kebabCase(str) {
    const words$1 = words(str);
    return words$1.map(word => word.toLowerCase()).join('-');
}

function lowerCase(str) {
    const words$1 = words(str);
    return words$1.map(word => word.toLowerCase()).join(' ');
}

function lowerFirst(str) {
    return str.substring(0, 1).toLowerCase() + str.substring(1);
}

function pad(str, length, chars = ' ') {
    return str.padStart(Math.floor((length - str.length) / 2) + str.length, chars).padEnd(length, chars);
}

function pascalCase(str) {
    const words$1 = words(str);
    return words$1.map(word => capitalize(word)).join('');
}

function snakeCase(str) {
    const words$1 = words(str);
    return words$1.map(word => word.toLowerCase()).join('_');
}

function trimEnd(str, chars) {
    if (chars === undefined) {
        return str.trimEnd();
    }
    let endIndex = str.length;
    switch (typeof chars) {
        case 'string': {
            while (endIndex > 0 && str[endIndex - 1] === chars) {
                endIndex--;
            }
            break;
        }
        case 'object': {
            while (endIndex > 0 && chars.includes(str[endIndex - 1])) {
                endIndex--;
            }
        }
    }
    return str.substring(0, endIndex);
}

function trimStart(str, chars) {
    if (chars === undefined) {
        return str.trimStart();
    }
    let startIndex = 0;
    switch (typeof chars) {
        case 'string': {
            while (startIndex < str.length && str[startIndex] === chars) {
                startIndex++;
            }
            break;
        }
        case 'object': {
            while (startIndex < str.length && chars.includes(str[startIndex])) {
                startIndex++;
            }
        }
    }
    return str.substring(startIndex);
}

function trim(str, chars) {
    if (chars === undefined) {
        return str.trim();
    }
    return trimStart(trimEnd(str, chars), chars);
}

const htmlUnescapes = {
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#39;': "'",
};
function unescape(str) {
    return str.replace(/&(?:amp|lt|gt|quot|#(0+)?39);/g, match => htmlUnescapes[match] || "'");
}

function upperCase(str) {
    const words$1 = words(str);
    let result = '';
    for (let i = 0; i < words$1.length; i++) {
        result += words$1[i].toUpperCase();
        if (i < words$1.length - 1) {
            result += ' ';
        }
    }
    return result;
}

function upperFirst(str) {
    return str.substring(0, 1).toUpperCase() + str.substring(1);
}

exports.CASE_SPLIT_PATTERN = CASE_SPLIT_PATTERN;
exports.camelCase = camelCase;
exports.capitalize = capitalize;
exports.constantCase = constantCase;
exports.deburr = deburr;
exports.escape = escape;
exports.escapeRegExp = escapeRegExp;
exports.kebabCase = kebabCase;
exports.lowerCase = lowerCase;
exports.lowerFirst = lowerFirst;
exports.pad = pad;
exports.pascalCase = pascalCase;
exports.snakeCase = snakeCase;
exports.trim = trim;
exports.trimEnd = trimEnd;
exports.trimStart = trimStart;
exports.unescape = unescape;
exports.upperCase = upperCase;
exports.upperFirst = upperFirst;
exports.words = words;
