// eslint-disable-next-line import/no-extraneous-dependencies
const log = require('loglevel');
const prefix = require('../lib/loglevel-plugin-prefix');

prefix.reg(log);
log.enableAll();

prefix.apply(log, {
  template: '[%t] %l (%n) static text:',
  timestampFormatter(date) {
    return date.toISOString();
  },
  levelFormatter(level) {
    return level.toUpperCase();
  },
  nameFormatter(name) {
    return name || 'global';
  },
});

log.info('%s prefix', 'template');

const fn = (level, name, timestamp) => `[${timestamp}] ${level} (${name}) static text:`;

prefix.apply(log, { format: fn });

log.info('%s prefix', 'functional');

prefix.apply(log, { template: '[%t] %l (%n) static text:' });

log.info('again %s prefix', 'template');
