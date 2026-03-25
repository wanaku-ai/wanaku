# loglevel-plugin-prefix

Plugin for [loglevel](https://github.com/pimterry/loglevel) message prefixing.

[![NPM version](https://img.shields.io/npm/v/loglevel-plugin-prefix.svg?style=flat-square)](https://www.npmjs.com/package/loglevel-plugin-prefix)[![Build Status](https://img.shields.io/travis/kutuluk/loglevel-plugin-prefix/master.svg?style=flat-square)](https://travis-ci.org/kutuluk/loglevel-plugin-prefix)

## Installation

```sh
npm install loglevel-plugin-prefix
```

## API

**This plugin is under active development and should be considered as an unstable. No guarantees regarding API stability are made. Backward compatibility is guaranteed only by path releases.**

#### `reg(loglevel)`

This method registers plugin for loglevel. This method must be called at least once before any call to the apply method. Repeated calls to this method are ignored.

#### Parameters

`loglevel` - the root logger, imported from loglevel module

#### `apply(logger, options)`

This method applies the plugin to the logger. Before using this method, the `reg` method must be called, otherwise a warning will be logged. **From the next release, the call apply before reg will throw an error.**

#### Parameters

`logger` - any logger of loglevel

`options` - an optional configuration object

```javascript
var defaults = {
  template: '[%t] %l:',
  levelFormatter: function (level) {
    return level.toUpperCase();
  },
  nameFormatter: function (name) {
    return name || 'root';
  },
  timestampFormatter: function (date) {
    return date.toTimeString().replace(/.*(\d{2}:\d{2}:\d{2}).*/, '$1');
  },
  format: undefined
};
```

Plugin formats the prefix using `template` option as a printf-like format. The `template` is a string containing zero or more placeholder tokens. Each placeholder token is replaced with the value from loglevel messages parameters. Supported placeholders are:

- `%l` - level of message
- `%n` - name of logger
- `%t` - timestamp of message

The `levelFormatter`, `nameFormatter` and `timestampFormatter` is a functions for formatting corresponding values.

Alternatively, you can use `format` option. This is a function that receives formatted values (level, name, timestamp) and should returns a prefix string.

If both `format` and` template` are present in the configuration, the `template` parameter is ignored. When both these parameters are missing in the configuration, the inherited behavior is used.

## Usage

### Browser directly
```html
<script src="https://unpkg.com/loglevel/dist/loglevel.min.js"></script>
<script src="https://unpkg.com/loglevel-plugin-prefix@^0.8/dist/loglevel-plugin-prefix.min.js"></script>

<script>
  var logger = log.noConflict();
  var prefixer = prefix.noConflict();
  prefixer.reg(logger);
  prefixer.apply(logger);
  logger.warn('prefixed message');
</script>
```

Output
```
[16:53:46] WARN: prefixed message
```

### Node
```javascript
const chalk = require('chalk');
const log = require('loglevel');
const prefix = require('loglevel-plugin-prefix');

const colors = {
  TRACE: chalk.magenta,
  DEBUG: chalk.cyan,
  INFO: chalk.blue,
  WARN: chalk.yellow,
  ERROR: chalk.red,
};

prefix.reg(log);
log.enableAll();

prefix.apply(log, {
  format(level, name, timestamp) {
    return `${chalk.gray(`[${timestamp}]`)} ${colors[level.toUpperCase()](level)} ${chalk.green(`${name}:`)}`;
  },
});

prefix.apply(log.getLogger('critical'), {
  format(level, name, timestamp) {
    return chalk.red.bold(`[${timestamp}] ${level} ${name}:`);
  },
});

log.trace('trace');
log.debug('debug');
log.getLogger('critical').info('Something significant happened');
log.log('log');
log.info('info');
log.warn('warn');
log.error('error');
```

Output

![output](https://raw.githubusercontent.com/kutuluk/loglevel-plugin-prefix/master/colored.png "output")

## Custom options

```javascript
const log = require('loglevel');
const prefix = require('loglevel-plugin-prefix');

prefix.reg(log);
log.enableAll();

prefix.apply(log, {
  template: '[%t] %l (%n) static text:',
  levelFormatter(level) {
    return level.toUpperCase();
  },
  nameFormatter(name) {
    return name || 'global';
  },
  timestampFormatter(date) {
    return date.toISOString();
  },
});

log.info('%s prefix', 'template');

const fn = (level, name, timestamp) => `[${timestamp}] ${level} (${name}) static text:`;

prefix.apply(log, { format: fn });

log.info('%s prefix', 'functional');

prefix.apply(log, { template: '[%t] %l (%n) static text:' });

log.info('again %s prefix', 'template');
```

Output
```
[2017-05-29T12:53:46.000Z] INFO (global) static text: template prefix
[2017-05-29T12:53:46.000Z] INFO (global) static text: functional prefix
[2017-05-29T12:53:46.000Z] INFO (global) static text: again template prefix
```

## Option inheritance

```javascript
const log = require('loglevel');
const prefix = require('loglevel-plugin-prefix');

prefix.reg(log);
log.enableAll();

log.info('root');

const chicken = log.getLogger('chicken');
chicken.info('chicken');

prefix.apply(chicken, { template: '%l (%n):' });
chicken.info('chicken');

prefix.apply(log);
log.info('root');

const egg = log.getLogger('egg');
egg.info('egg');

const fn = (level, name) => `${level} (${name}):`;

prefix.apply(egg, { format: fn });
egg.info('egg');

prefix.apply(egg, {
  levelFormatter(level) {
    return level.toLowerCase();
  },
});
egg.info('egg');

chicken.info('chicken');
log.info('root');
```

Output
```
root
chicken
INFO (chicken): chicken
[16:53:46] INFO: root
[16:53:46] INFO: egg
INFO (egg): egg
info (egg): egg
INFO (chicken): chicken
[16:53:46] INFO: root
```
