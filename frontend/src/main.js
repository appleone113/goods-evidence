import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

/**
 * Element Plus 按需引入（手工清单，共 32 个组件）
 *
 * 为什么手写而不用 unplugin-vue-components：
 *   该插件在本项目 vite 5.3.3 环境下静默失效 —— 插件能被加载、出现在 plugins 数组里，
 *   但 transform 不执行、components.d.ts 不生成，最终组件没被注入、页面直接白屏。
 *   手工列清单虽然繁琐，但行为确定，且能精确控制引入范围。
 *
 * 三条必须遵守的路径规则（都是踩出来的）：
 *   1. 不能写 `import { ElButton } from 'element-plus'`（包根入口）。实测会把整个组件库
 *      连带没用到的 color-picker、slider、calendar 一起拖进包里（938 kB），
 *      tree-shaking 在这条路径上并不生效。
 *   2. 路径必须写到 `.../index.mjs` 为止。element-plus 的 package.json 里
 *      exports 规则是 `"./es/*": { "import": "./es/*.mjs" }`，
 *      写 `es/components/container` 会被解析成不存在的 `container.mjs` 而构建失败。
 *   3. 子组件（form-item / option / radio-group / table-column / descriptions-item /
 *      timeline-item / menu-item / aside / header / main）在 es 目录下没有独立入口，
 *      必须从各自的父包里一并取出，例如 ElAside 来自 container 包。
 */
// 布局（container 包同时导出 ElAside / ElHeader / ElMain）
import { ElContainer, ElAside, ElHeader, ElMain } from 'element-plus/es/components/container/index.mjs'
import { ElRow } from 'element-plus/es/components/row/index.mjs'
import { ElCol } from 'element-plus/es/components/col/index.mjs'
// 表单
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElSelect, ElOption } from 'element-plus/es/components/select/index.mjs'
import { ElRadio, ElRadioGroup } from 'element-plus/es/components/radio/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
// 数据展示
import { ElCard } from 'element-plus/es/components/card/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElDescriptions, ElDescriptionsItem } from 'element-plus/es/components/descriptions/index.mjs'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
import { ElImage } from 'element-plus/es/components/image/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
// 导航与反馈
import { ElMenu, ElMenuItem } from 'element-plus/es/components/menu/index.mjs'
import { ElLink } from 'element-plus/es/components/link/index.mjs'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElDivider } from 'element-plus/es/components/divider/index.mjs'
import { ElResult } from 'element-plus/es/components/result/index.mjs'
import { ElPopover } from 'element-plus/es/components/popover/index.mjs'

// 组件样式（子组件的样式已包含在父组件样式内，故只引父组件）
import 'element-plus/es/components/container/style/css'
import 'element-plus/es/components/aside/style/css'
import 'element-plus/es/components/header/style/css'
import 'element-plus/es/components/main/style/css'
import 'element-plus/es/components/row/style/css'
import 'element-plus/es/components/col/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/upload/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/image/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/menu/style/css'
import 'element-plus/es/components/link/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/divider/style/css'
import 'element-plus/es/components/result/style/css'
import 'element-plus/es/components/popover/style/css'
// ElMessage 是函数式调用、不出现在模板里，需单独补样式
import 'element-plus/es/components/message/style/css'

const components = [
  ElContainer, ElAside, ElHeader, ElMain, ElRow, ElCol,
  ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElRadio, ElRadioGroup, ElUpload, ElButton,
  ElCard, ElTable, ElTableColumn, ElTag, ElDescriptions, ElDescriptionsItem,
  ElTimeline, ElTimelineItem, ElImage, ElEmpty,
  ElMenu, ElMenuItem, ElLink, ElAlert, ElDivider, ElResult, ElPopover,
]

const app = createApp(App)
components.forEach((c) => app.use(c))
app.use(router)
app.mount('#app')
