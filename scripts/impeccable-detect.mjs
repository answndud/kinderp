#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '..');
const userArgs = process.argv.slice(2);
const defaultTargets = [
  'src/main/resources/templates',
  'src/main/resources/static/js',
];

const hasExplicitTarget = userArgs.some((arg) => arg && !arg.startsWith('-'));
const detectorArgs = [
  'detect',
  ...userArgs,
  ...(hasExplicitTarget ? [] : defaultTargets),
];

const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const npmArgs = [
  'exec',
  '--yes',
  '--package',
  'impeccable@2.1.7',
  '--',
  'impeccable',
  ...detectorArgs,
];

const child = spawn(npmCommand, npmArgs, {
  cwd: repoRoot,
  stdio: 'inherit',
  env: {
    ...process.env,
    npm_config_cache: resolve(repoRoot, '.cache/npm'),
    npm_config_audit: 'false',
    npm_config_fund: 'false',
    npm_config_update_notifier: 'false',
  },
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
