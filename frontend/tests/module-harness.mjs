import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import ts from 'typescript'

const require = createRequire(import.meta.url)

// Each test owns a module graph; imports within it share real module state.
export function moduleLoader(mocks = {}, globals = {}) {
  const modules = new Map()
  const load = (id) => {
    if (Object.hasOwn(mocks, id)) return mocks[id]
    if (!id.startsWith('@/')) return require(id)
    if (modules.has(id)) return modules.get(id).exports
    const module = { exports: {} }
    modules.set(id, module)
    const source = fs.readFileSync(new URL('../src/' + id.slice(2) + '.ts', import.meta.url), 'utf8')
    const js = ts.transpileModule(source.replaceAll('import.meta.env', '({})'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText
    vm.runInNewContext(js, { require: load, module, exports: module.exports, setTimeout, clearTimeout, ...globals })
    return module.exports
  }
  return load
}
