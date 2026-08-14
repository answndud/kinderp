import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

const repositoryRoot = process.cwd();
const markdownRoots = [
  'README.md',
  'docs/README.md',
  'docs/guides',
  'docs/architecture',
  'docs/resume',
];

function markdownFiles(root) {
  const absoluteRoot = path.join(repositoryRoot, root);
  if (root.endsWith('.md')) {
    return [absoluteRoot];
  }

  return readdirSync(absoluteRoot, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(absoluteRoot, entry.name);
    if (entry.isDirectory()) {
      return markdownFiles(path.relative(repositoryRoot, entryPath));
    }
    return entry.name.endsWith('.md') ? [entryPath] : [];
  });
}

function isExternalTarget(target) {
  return target.startsWith('#')
    || target.startsWith('http://')
    || target.startsWith('https://')
    || target.startsWith('mailto:');
}

const missing = [];
const linkPattern = /!?\[[^\]]*\]\(([^)]+)\)/g;

for (const file of markdownRoots.flatMap(markdownFiles)) {
  const source = readFileSync(file, 'utf8');
  for (const match of source.matchAll(linkPattern)) {
    const rawTarget = match[1].trim().replace(/^<|>$/g, '');
    const target = rawTarget.split('#', 1)[0];
    if (!target || isExternalTarget(rawTarget)) {
      continue;
    }

    const resolvedTarget = path.resolve(path.dirname(file), target);
    if (!existsSync(resolvedTarget)) {
      missing.push(`${path.relative(repositoryRoot, file)} -> ${rawTarget}`);
    }
  }
}

if (missing.length > 0) {
  console.error(`markdown link check failed (${missing.length} missing target(s))`);
  for (const item of missing) {
    console.error(`- ${item}`);
  }
  process.exit(1);
}

console.log('markdown link check passed');
