import { cp, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const vendor = resolve(root, 'src/main/resources/static/vendor');

await mkdir(vendor, { recursive: true });

const assets = [
    ['node_modules/htmx.org/dist/htmx.min.js', 'htmx.min.js'],
    ['node_modules/@alpinejs/csp/dist/cdn.min.js', 'alpine.min.js'],
    ['node_modules/sweetalert2/dist/sweetalert2.all.min.js', 'sweetalert2.all.min.js']
];

await Promise.all(assets.map(async ([source, target]) => {
    await cp(resolve(root, source), resolve(vendor, target));
}));

console.log(`Copied ${assets.length} frontend runtime assets to ${vendor}`);
