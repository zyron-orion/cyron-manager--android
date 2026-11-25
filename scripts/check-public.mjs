import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const tracked = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
  cwd: root,
  encoding: 'utf8'
})
  .split('\0')
  .filter(Boolean)
  .sort();

const failures = [];
const forbiddenPath = /(^|\/)(?:\.gradle|\.idea|\.kotlin|\.cxx|\.externalNativeBuild|build)(?:\/|$)|(^|\/)(?:local\.properties|keystore\.properties(?:\..*)?|release\.keystore|secrets\.properties|google-services\.json|service-account[^/]*\.json|\.env)$|\.(?:apk|aab|apks|jks|keystore|pem|key|p12|pfx|hprof|log)$/i;
const forbiddenEnv = /(^|\/)\.env\./i;
const binaryExtensions = new Set(['.png', '.jpg', '.jpeg', '.webp', '.gif', '.ttf', '.otf', '.so', '.jar', '.aar', '.whl', '.zip']);
const textExtensions = new Set(['.kt', '.kts', '.java', '.xml', '.md', '.txt', '.yml', '.yaml', '.json', '.toml', '.properties', '.sh', '.mjs', '.js', '.html', '.css', '.pro']);
const secretRules = [
  ['private key', /-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----/],
  ['AWS access key', /\bAKIA[0-9A-Z]{16}\b/],
  ['GitHub token', /\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{50,255})\b/],
  ['Google API key', /\bAIza[0-9A-Za-z_-]{35}\b/],
  ['Slack token', /\bxox[baprs]-[A-Za-z0-9-]{20,}\b/],
  ['Stripe live key', /\bsk_live_[0-9A-Za-z]{16,}\b/]
];

for (const relative of tracked) {
  if ((forbiddenPath.test(relative) && relative !== 'keystore.properties.example') || (forbiddenEnv.test(relative) && !relative.endsWith('.env.example'))) {
    failures.push(`Sensitive or generated file is not ignored: ${relative}`);
    continue;
  }
  const extension = path.extname(relative).toLowerCase();
  if (binaryExtensions.has(extension) || !textExtensions.has(extension)) continue;
  const size = statSync(path.join(root, relative)).size;
  if (size > 2 * 1024 * 1024) continue;
  const lines = readFileSync(path.join(root, relative), 'utf8').split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const [name, pattern] of secretRules) {
      if (pattern.test(line)) failures.push(`Possible ${name} in ${relative}:${index + 1}`);
    }
  });
}

const required = [
  '.gitattributes',
  '.gitignore',
  'LICENSE',
  'README.md',
  'SECURITY.md',
  'THIRD_PARTY_NOTICES.md',
  'SBOM.json',
  'app/src/main/assets/THIRD_PARTY_NOTICES.md',
  'gradle/verification-metadata.xml',
  'keystore.properties.example'
];
for (const relative of required) {
  if (!tracked.includes(relative)) failures.push(`Required public-release file is missing or ignored: ${relative}`);
}

const sbomPath = path.join(root, 'SBOM.json');
if (existsSync(sbomPath)) {
  try {
    const sbom = JSON.parse(readFileSync(sbomPath, 'utf8'));
    if (sbom.bomFormat !== 'CycloneDX' || sbom.specVersion !== '1.5') failures.push('SBOM.json is not a CycloneDX 1.5 document.');
    const names = new Set((sbom.components ?? []).map(component => component.name));
    for (const name of ['yt-dlp-android', 'ffmpeg-kit-full', 'libtorrent4j', 'libtorrent4j-android-arm64', 'libtorrent4j-android-x86_64', 'yt-dlp', 'certifi', 'setuptools', 'Python', 'FFmpeg', 'libtorrent']) {
      if (!names.has(name)) failures.push(`SBOM.json is missing required component: ${name}`);
    }
  } catch (error) {
    failures.push(`SBOM.json is invalid: ${error.message}`);
  }
}

const noticePath = path.join(root, 'THIRD_PARTY_NOTICES.md');
const assetNoticePath = path.join(root, 'app/src/main/assets/THIRD_PARTY_NOTICES.md');
if (existsSync(noticePath) && existsSync(assetNoticePath)) {
  const notice = readFileSync(noticePath, 'utf8');
  if (notice !== readFileSync(assetNoticePath, 'utf8')) failures.push('Root and in-app third-party notices differ.');
  if (/starting inventory|not a complete|not complete/i.test(notice)) failures.push('Third-party notice still contains preliminary inventory wording.');
  for (const marker of ['SBOM.json', 'docs/THIRD_PARTY_SOURCES.md', 'BUILD-AND-RELINK.txt', 'native-libs.sha256', 'ALL_DEPENDENCY_LICENSES.txt']) {
    if (!notice.includes(marker)) failures.push(`Third-party notice is missing release reference: ${marker}`);
  }
}

const wrapperPath = path.join(root, 'gradle/wrapper/gradle-wrapper.properties');
if (existsSync(wrapperPath)) {
  const wrapper = readFileSync(wrapperPath, 'utf8');
  if (!/^distributionSha256Sum=[a-f0-9]{64}$/m.test(wrapper)) {
    failures.push('Gradle wrapper distributionSha256Sum is missing.');
  }
}

const workflowPath = path.join(root, '.github/workflows/android.yml');
if (existsSync(workflowPath)) {
  const workflow = readFileSync(workflowPath, 'utf8');
  for (const match of workflow.matchAll(/^\s*uses:\s*([^\s#]+)/gm)) {
    if (!/@[a-f0-9]{40}$/.test(match[1])) failures.push(`GitHub Action is not pinned to a commit SHA: ${match[1]}`);
  }
}

const licenseDirectory = path.join(root, 'app/src/main/assets/licenses');
if (!existsSync(licenseDirectory) || readdirSync(licenseDirectory).length === 0) failures.push('In-app license asset directory is empty.');

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`Public-source check passed for ${tracked.length} files.`);
