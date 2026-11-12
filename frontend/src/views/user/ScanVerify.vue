<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>扫码验真</span>
        <el-tag v-if="scannerReady" type="success" size="small">摄像头已就绪</el-tag>
      </div>
    </template>

    <!-- 权限被拒时给出可执行的排查步骤（常驻，不用一闪而过的 toast） -->
    <el-alert v-if="permissionError" type="error" :closable="false" show-icon
              title="浏览器拒绝了摄像头权限" style="margin-bottom:12px">
      <template #default>
        <div style="line-height:1.8">
          ① 点地址栏左侧的 <b>锁形 / 摄像头图标</b> → 把「摄像头」改为<b>允许</b>，再点下方按钮重试；<br>
          ② 或在<b>系统自带的 Edge / Chrome</b> 里打开 <code>http://localhost:5173</code>（内置预览窗口可能无法弹出授权）；<br>
          ③ Windows「设置 → 隐私和安全性 → 摄像头」开启「允许应用访问摄像头」，并关闭正在占用摄像头的其它软件；<br>
          ④ 也可以直接用<b>「上传二维码图片」</b>，不需要摄像头权限。
        </div>
      </template>
    </el-alert>

    <!-- 扫码入口：摄像头优先，图片上传 + 手输双兜底 -->
    <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
      <el-button v-if="!scanning" type="primary" :icon="Camera" @click="startScan">打开摄像头扫码</el-button>
      <el-button v-else type="danger" plain @click="stopScan">停止扫描</el-button>
      <el-button :icon="Upload" @click="pickFile">上传二维码图片</el-button>
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFile" />
      <el-input v-model="code" placeholder="也可直接粘贴二维码内容（支持 P123 或完整验真链接）"
                style="max-width:420px" @keyup.enter="onVerify">
        <template #append><el-button @click="onVerify">验真</el-button></template>
      </el-input>
    </div>

    <!-- 摄像头取景框：由 html5-qrcode 注入 video 元素 -->
    <div v-show="scanning" style="margin-top:12px">
      <div :id="readerId" style="width:320px;max-width:100%"></div>
      <div style="margin-top:6px;font-size:12px;color:#909399">
        把二维码对准取景框，识别成功后会自动验真
      </div>
    </div>
    <!-- 图片识别用的隐藏容器（scanFile 需要一个 DOM 节点，但不需要摄像头） -->
    <div :id="fileReaderId" style="width:1px;height:1px;overflow:hidden"></div>

    <div v-if="!scanning" style="margin-top:8px;font-size:12px;color:#909399">
      二维码扫出来是一整条链接（形如 .../verify/view?code=P2115...），粘贴或扫码后会自动提取其中的码。
      浏览器调用摄像头需通过 localhost 或 https 访问。
    </div>

    <div v-if="result" style="margin-top:20px">
      <el-result :icon="result.authentic ? 'success' : 'error'"
                 :title="result.authentic ? '验真通过' : '验真未通过'"
                 :sub-title="result.message">
        <template #extra>
          <el-descriptions border :column="1" v-if="result.productName">
            <el-descriptions-item label="商品名称">{{ result.productName }}</el-descriptions-item>
            <el-descriptions-item label="商品ID">{{ result.productId }}</el-descriptions-item>
          </el-descriptions>
          <el-button type="primary" @click="goTrace(result.productId)">查看溯源履历</el-button>
        </template>
      </el-result>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Camera, Upload } from '@element-plus/icons-vue'
import { scanVerify } from '../../api/verify'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'

const code = ref('')
const result = ref(null)
const scanning = ref(false)
const scannerReady = ref(false)
const permissionError = ref('')
const fileInput = ref(null)
const readerId = 'qr-reader-box'
const fileReaderId = 'qr-file-reader-box'
const router = useRouter()

let cameraScanner = null
let fileScanner = null

/**
 * 提取商品码，兼容三种输入：
 *   1) 纯码：P2115... 或 2115...
 *   2) 扫码得到的完整链接：http://.../api/verify/view?code=P2115...
 *   3) 粘贴时带空格 / 换行 / URL 转义的脏数据
 * 二维码里存的是完整链接（product_code.qr_content），不做这步解析后端会
 * Long.parseLong("http://...") 直接抛 NumberFormatException。
 */
function extractCode(raw) {
  let s = (raw || '').trim()
  if (s.includes('code=')) {
    try {
      const q = new URL(s).searchParams.get('code')
      if (q) return q.trim()
    } catch (ignored) { /* 不是合法 URL 时走下面的正则兜底 */ }
    const m = s.match(/[?&]code=([^&#\s]+)/)
    if (m) {
      try { return decodeURIComponent(m[1]).trim() } catch (ignored) { return m[1] }
    }
  }
  return s
}

/** 打开摄像头并持续识别，识别成功即自动验真 */
async function startScan() {
  if (scanning.value) return
  permissionError.value = ''
  // 非 https / localhost 访问时浏览器会直接禁用 getUserMedia，提前给出可读提示
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    permissionError.value = '当前环境不支持调用摄像头（需用 localhost 或 https 访问）'
    ElMessage.error(permissionError.value)
    return
  }
  scanning.value = true
  await nextTick()   // 等 v-show 的容器渲染出来，html5-qrcode 需要真实 DOM 节点
  try {
    const { Html5Qrcode } = await import('html5-qrcode')
    cameraScanner = new Html5Qrcode(readerId, { verbose: false })
    const config = { fps: 10, qrbox: { width: 240, height: 240 } }
    const onDecodedText = (text) => onDecoded(text)
    const onFrameError = () => { /* 每帧识别失败是常态，静默忽略 */ }
    try {
      await cameraScanner.start({ facingMode: 'environment' }, config, onDecodedText, onFrameError)
    } catch (firstError) {
      const msg = String(firstError?.message || firstError)
      // 权限被拒就不必再试；设备/约束类错误则回落到默认摄像头（笔记本常只有前置）
      if (/NotAllowed|Permission|Security/i.test(msg)) {
        throw firstError
      }
      await cameraScanner.start({}, config, onDecodedText, onFrameError)
    }
    scannerReady.value = true
  } catch (e) {
    const msg = String(e?.message || e)
    scanning.value = false
    scannerReady.value = false
    cameraScanner = null
    if (/NotAllowed|Permission|Security/i.test(msg)) {
      permissionError.value = msg
      ElMessage.error('摄像头权限被拒绝，按上方提示排查，或改用「上传二维码图片」')
    } else {
      ElMessage.error('无法打开摄像头：' + msg + '（可改用「上传二维码图片」或输入框粘贴）')
    }
  }
}

/** 停止扫描并释放摄像头，避免指示灯常亮 */
async function stopScan() {
  if (cameraScanner) {
    try {
      await cameraScanner.stop()
      await cameraScanner.clear()
    } catch (ignored) { /* 已停止时 stop 会抛错，忽略 */ }
    cameraScanner = null
  }
  scanning.value = false
  scannerReady.value = false
}

/** 摄像头/图片识别成功后的统一处理 */
function onDecoded(text) {
  const pure = extractCode(text)
  if (!pure) return
  code.value = text
  stopScan()
  ElMessage.success('已识别二维码：' + pure)
  onVerify()
}

/** 兜底：直接选一张二维码图片识别，完全不需要摄像头权限 */
function pickFile() {
  permissionError.value = ''
  if (fileInput.value) fileInput.value.click()
}

async function onFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  try {
    const { Html5Qrcode } = await import('html5-qrcode')
    fileScanner = new Html5Qrcode(fileReaderId, { verbose: false })
    const text = await fileScanner.scanFile(file, false)
    try { await fileScanner.clear() } catch (ignored) { /* ignore */ }
    fileScanner = null
    ElMessage.success('图片识别成功')
    onDecoded(text)
  } catch (err) {
    if (fileScanner) {
      try { await fileScanner.clear() } catch (ignored) { /* ignore */ }
      fileScanner = null
    }
    ElMessage.error('这张图片里没识别出二维码，请换一张清晰的二维码截图或 PNG')
  }
  e.target.value = ''   // 允许重复选同一张图
}

async function onVerify() {
  if (!code.value) { ElMessage.warning('请输入或扫描二维码内容'); return }
  const pure = extractCode(code.value)
  if (!pure) { ElMessage.warning('没能从内容里识别出商品码'); return }
  try {
    const res = await scanVerify(pure)
    result.value = res.data || res
  } catch (e) {
    ElMessage.error('验真失败：' + (e?.message || '请确认码是否正确'))
    result.value = null
  }
}

function goTrace(id) {
  router.push({ path: '/user/trace', query: { productId: id } })
}

onBeforeUnmount(stopScan)
</script>
