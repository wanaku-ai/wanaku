// eslint-disable-next-line import/no-extraneous-dependencies
const log = require('loglevel');
const prefix = require('../lib/loglevel-plugin-prefix');

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
