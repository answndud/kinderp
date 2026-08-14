import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const javaRoot = join(process.cwd(), 'src/main/java');
const directSystemTimePattern = /(?:java\.time\.)?Local(?:Date|DateTime|Time)\.now\s*\(\s*\)/;

async function collectJavaFiles(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    const files = [];
    for (const entry of entries) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            files.push(...await collectJavaFiles(path));
        } else if (entry.isFile() && entry.name.endsWith('.java')) {
            files.push(path);
        }
    }
    return files;
}

const violations = [];
for (const file of await collectJavaFiles(javaRoot)) {
    const lines = (await readFile(file, 'utf8')).split('\n');
    lines.forEach((line, index) => {
        if (directSystemTimePattern.test(line)) {
            violations.push(`${file}:${index + 1}`);
        }
    });
}

if (violations.length > 0) {
    console.error('direct system time usage check failed; use ProductTime instead:');
    violations.forEach((violation) => console.error(`- ${violation}`));
    process.exit(1);
}

console.log(`product time usage check passed (${(await collectJavaFiles(javaRoot)).length} Java files)`);
