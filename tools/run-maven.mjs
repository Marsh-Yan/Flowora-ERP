import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const repositoryRoot = fileURLToPath(new URL('../', import.meta.url))
const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
const result = spawnSync(wrapper, process.argv.slice(2), {
  cwd: repositoryRoot,
  stdio: 'inherit',
  shell: process.platform === 'win32',
})

if (result.error) {
  console.error(result.error.message)
}

process.exit(result.status ?? 1)
