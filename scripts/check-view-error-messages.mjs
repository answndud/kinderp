import { readdir, readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

const root = resolve('src/main/java/com/kinderp/domain');
const violations = [];

async function walk(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      await walk(path);
      continue;
    }
    if (!entry.name.endsWith('ViewController.java')) continue;

    const source = await readFile(path, 'utf8');
    if (/addFlashAttribute\s*\(\s*"error"[\s\S]{0,240}?getMessage\s*\(/.test(source)) {
      violations.push(path);
    }
  }
}

await walk(root);

if (violations.length > 0) {
  console.error('view error message check failed: exception messages must not reach flash attributes');
  violations.forEach((path) => console.error(`- ${path}`));
  process.exitCode = 1;
} else {
  console.log('view error message check passed');
}
