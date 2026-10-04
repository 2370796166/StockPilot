import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { parse, compileScript } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'

const require = createRequire(import.meta.url)
const renderer = vue.createRenderer({
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  insert() {},
  remove() {},
  setText() {},
  setElementText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null,
})

export const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
export const flush = async () => {
  for (let i = 0; i < 10; i++) await vue.nextTick()
}

export function mount(file, input = {}, mocks = {}) {
  const filename = new URL('../src/' + file, import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  const compiled = compileScript(descriptor, { id: 'acceptance', genDefaultAs: '__component' }).content
  const output = ts.transpileModule(compiled + '\nmodule.exports = __component', {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const module = { exports: {} }
  const load = (id) => {
    if (mocks[id]) return mocks[id]
    if (id === 'vue') return vue
    if (id.endsWith('.vue')) return {}
    if (id === 'element-plus') return { ElMessage: { success() {}, warning() {} }, ElMessageBox: {} }
    if (id.startsWith('@/')) {
      const source = fs.readFileSync(new URL('../src/' + id.slice(2) + '.ts', import.meta.url), 'utf8')
      const child = { exports: {} }
      const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
      vm.runInNewContext(js, { require: load, module: child, exports: child.exports, setTimeout, clearTimeout })
      return child.exports
    }
    return require(id)
  }
  vm.runInNewContext(output, { require: load, module, exports: module.exports, setTimeout, clearTimeout })
  const component = module.exports
  component.render = () => null
  const props = vue.reactive(input)
  let vnode
  const app = renderer.createApp({
    render: () => {
      vnode = vue.h(component, props)
      return vnode
    },
  })
  app.mount({})
  return { props, state: vnode.component.setupState, unmount: () => app.unmount() }
}
