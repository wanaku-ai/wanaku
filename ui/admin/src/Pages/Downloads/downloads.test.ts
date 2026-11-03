import assert from 'node:assert/strict';
import { test } from 'node:test';

for (const [version, channel, tag, artifact] of [
  ['0.4.0', 'stable', 'v0.3.0', '0.3.0'],
  ['0.4.0+build.1', 'stable', 'v0.3.0', '0.3.0'],
  ['0.4.0+build-with-hyphens', 'stable', 'v0.3.0', '0.3.0'],
  ['0.4.0-dev', 'early-access', 'early-access', '0.3.0-SNAPSHOT'],
  ['0.4.0-alpha.1', 'early-access', 'early-access', '0.3.0-SNAPSHOT'],
  ['0.4.0-beta.1', 'early-access', 'early-access', '0.3.0-SNAPSHOT'],
  ['0.4.0-rc.1+build.1', 'early-access', 'early-access', '0.3.0-SNAPSHOT'],
]) {
  test(`server ${version} selects ${channel} CLI links independently of its version`, async () => {
    Object.assign(globalThis, { WANAKU_VERSION: version });
    const release: typeof import('./downloads.ts') = await import(
      `./downloads.ts?version=${version}`
    );
    assert.equal(release.CLI_RELEASE_CHANNEL, channel);
    assert.equal(release.CLI_RELEASE_TAG, tag);
    assert.equal(
      release.CLI_RELEASE_PAGE,
      `https://github.com/wanaku-ai/wanaku-barn/releases/tag/${tag}`,
    );
    assert.equal(
      release.CLI_CHECKSUM_URL,
      `https://github.com/wanaku-ai/wanaku-barn/releases/download/${tag}/checksums_sha256.txt`,
    );
    assert.deepEqual(
      release.CLI_DOWNLOADS.map(({ fileName }) => fileName),
      [`wanaku-cli-${artifact}.zip`, `wanaku-cli-${artifact}-linux-x86_64.zip`],
    );
    for (const download of release.CLI_DOWNLOADS) {
      assert.equal(
        download.downloadUrl,
        `https://github.com/wanaku-ai/wanaku-barn/releases/download/${tag}/${download.fileName}`,
      );
    }
  });
}
