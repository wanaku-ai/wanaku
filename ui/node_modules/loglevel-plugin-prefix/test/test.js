const expect = require('chai').expect;
const loglevel = require('loglevel');
const other = require('loglevel-plugin-mock');
const sinon = require('sinon');
const prefix = require('../lib/loglevel-plugin-prefix');

const spy = sinon.spy();

loglevel.enableAll();

describe('API', () => {
  it('Methods', () => {
    expect(prefix).to.have.property('apply').with.be.a('function');
    expect(prefix).to.have.property('reg').with.be.a('function');
    expect(prefix).not.to.have.property('noConflict');
  });

  it('Reg: empty arguments', () => {
    expect(prefix.reg).to.throw(TypeError, 'Argument is not a root logger');
  });

  it('Reg: incorrect argument', () => {
    expect(() => prefix.reg('logger')).to.throw(TypeError, 'Argument is not a root logger');
  });

  it('Apply: empty arguments', () => {
    expect(prefix.apply).to.throw(TypeError, 'Argument is not a logger');
  });

  it('Apply: incorrect argument', () => {
    expect(() => prefix.apply('logger')).to.throw(TypeError, 'Argument is not a logger');
  });
});

describe('Prefix', () => {
  other.apply(loglevel, { method: spy });

  beforeEach(() => {
    spy.reset();
  });

  it('Root applying', () => {
    expect(() => prefix.apply(loglevel)).to.not.throw();
  });

  it('Root reapplyng', () => {
    prefix.apply(loglevel, { template: '%l (%n):' });
    loglevel.info('test');
    expect(spy.calledWith('INFO (root): test')).to.be.true;
  });

  it('Format', () => {
    prefix.apply(loglevel, {
      format: (level, logger) => `${level} (${logger}):`
    });

    loglevel.info('test');

    expect(spy.calledWith('INFO (root): test')).to.be.true;
  });

  it('Unformat', () => {
    prefix.apply(loglevel, { template: '%l:' });

    loglevel.info('test');

    expect(spy.calledWith('INFO: test')).to.be.true;
  });

  it('The prefix must be combined with the first argument, if it is a string', () => {
    prefix.apply(loglevel, { template: '%l:' });

    loglevel.info('foo %s', 'bar');
    expect(spy.calledWith('INFO: foo %s', 'bar')).to.be.true;
  });

  it('All methods of the previous plugin should be called', () => {
    prefix.apply(loglevel);

    // for warn in apply
    spy.reset();

    loglevel.trace();
    loglevel.debug();
    loglevel.info();
    loglevel.warn();
    loglevel.error();
    expect(spy.callCount).to.equal(5);
  });

  it('Child applying', () => {
    const child = loglevel.getLogger('child');
    prefix.apply(child, { template: '%l (%n):' });
    child.info('test');
    expect(spy.calledWith('INFO (child): test')).to.be.true;
  });

  it('Child reapplyng', () => {
    const child = loglevel.getLogger('child');
    prefix.apply(child, {
      levelFormatter(level) {
        return level;
      }
    });
    child.info('test');
    expect(spy.calledWith('info (child): test')).to.be.true;
  });
});
