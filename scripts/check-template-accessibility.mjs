#!/usr/bin/env node

import { readdir, readFile } from 'node:fs/promises';
import { extname, join, relative, resolve } from 'node:path';

const root = resolve('src/main/resources/templates');
const failures = [];

const attrs = (raw) => Object.fromEntries(
  [...raw.matchAll(/([:\w-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g)]
    .map((match) => [match[1], match[2] ?? match[3] ?? match[4] ?? ''])
);

const stripAttributeValues = (raw) => raw.replace(/("[^"]*"|'[^']*')/g, '');

async function templateFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await templateFiles(path));
    else if (extname(entry.name) === '.html') files.push(path);
  }
  return files;
}

function cleanMarkup(source) {
  return source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
}

function containsLabel(labelRanges, start, end) {
  return labelRanges.some((range) => range.start < start && range.end > end);
}

function inspect(path, source) {
  const markup = cleanMarkup(source);
  const relativePath = relative(process.cwd(), path);
  const labelFor = new Set(
    [...markup.matchAll(/<label\b([^>]*)>/gi)]
      .map((match) => attrs(match[1]).for)
      .filter(Boolean)
  );
  const labelRanges = [...markup.matchAll(/<label\b[^>]*>[\s\S]*?<\/label>/gi)]
    .map((match) => ({ start: match.index, end: match.index + match[0].length }));

  for (const match of markup.matchAll(/<(input|select|textarea)\b([^>]*)>/gi)) {
    const attributes = attrs(match[2]);
    if (attributes.type?.toLowerCase() === 'hidden') continue;
    const hasName = attributes['aria-label'] || attributes['aria-labelledby']
      || (attributes.id && labelFor.has(attributes.id))
      || containsLabel(labelRanges, match.index, match.index + match[0].length);
    if (!hasName) {
      failures.push(`${relativePath}:${markup.slice(0, match.index).split('\n').length} form control has no accessible name`);
    }
  }

  for (const match of markup.matchAll(/<button\b([^>]*)>/gi)) {
    const directAttributes = stripAttributeValues(match[1]);
    const typeMatches = directAttributes.match(/(?:^|\s)type\s*=/gi) ?? [];
    if (!typeMatches.length) {
      failures.push(`${relativePath}:${markup.slice(0, match.index).split('\n').length} button must declare type`);
    }
    if (typeMatches.length > 1) {
      failures.push(`${relativePath}:${markup.slice(0, match.index).split('\n').length} button has duplicate type attributes`);
    }
  }

  for (const match of markup.matchAll(/<img\b([^>]*)>/gi)) {
    if (!Object.hasOwn(attrs(match[1]), 'alt')) {
      failures.push(`${relativePath}:${markup.slice(0, match.index).split('\n').length} image must declare alt`);
    }
  }
}

for (const path of await templateFiles(root)) {
  inspect(path, await readFile(path, 'utf8'));
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`template accessibility check passed (${(await templateFiles(root)).length} templates)`);
