import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const templatesRoot = join(process.cwd(), 'src/main/resources/templates');
const inlineHandlerPattern = /\s(?:on(?:click|change|submit|input|keyup|keydown|load|error)|th:on(?:click|change|submit|input|keyup|keydown|load|error))\s*=/i;

async function collectHtmlFiles(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    const files = [];
    for (const entry of entries) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            files.push(...await collectHtmlFiles(path));
        } else if (entry.isFile() && entry.name.endsWith('.html')) {
            files.push(path);
        }
    }
    return files;
}

const violations = [];
for (const file of await collectHtmlFiles(templatesRoot)) {
    const lines = (await readFile(file, 'utf8')).split('\n');
    lines.forEach((line, index) => {
        if (inlineHandlerPattern.test(line)) {
            violations.push(`${file}:${index + 1}`);
        }
    });
}

if (violations.length > 0) {
    console.error('inline event handler check failed:');
    violations.forEach((violation) => console.error(`- ${violation}`));
    process.exit(1);
}

console.log(`inline event handler check passed (${(await collectHtmlFiles(templatesRoot)).length} templates)`);
