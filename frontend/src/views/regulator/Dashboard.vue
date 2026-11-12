<template>
  <div>
    <!-- 核心指标卡片 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>商品总数</template>
          <div class="metric">{{ data.productCount ?? 0 }}</div>
          <div class="metric-sub">已登记上链主体</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>交易总数</template>
          <div class="metric" style="color:#E6A23C">{{ data.tradeCount ?? 0 }}</div>
          <div class="metric-sub">权属转移次数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>验真次数</template>
          <div class="metric" style="color:#409EFF">{{ data.verifyCount ?? 0 }}</div>
          <div class="metric-sub">扫码验真累计</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>上链存证数</template>
          <div class="metric" style="color:#67C23A">{{ data.chainRecordCount ?? 0 }}</div>
          <div class="metric-sub">链上存证回执</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据完整性巡检 + 告警 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>数据完整性巡检</span>
              <div>
                <el-tag :type="integrity.healthy ? 'success' : 'danger'" size="small" style="margin-right:8px">
                  {{ integrity.checkedCount ? (integrity.healthy ? '链上链下一致' : '发现异常') : '尚未巡检' }}
                </el-tag>
                <el-button size="small" type="primary" :loading="checking" @click="check">立即巡检</el-button>
              </div>
            </div>
          </template>
          <div v-if="integrity.checkedCount">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="存证比对数">{{ integrity.checkedCount }}</el-descriptions-item>
              <el-descriptions-item label="一致">
                <span style="color:#67C23A">{{ integrity.passCount }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="不一致">
                <span :style="integrity.mismatchCount ? 'color:#F56C6C;font-weight:bold' : ''">{{ integrity.mismatchCount }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="应上链未上链">
                <span :style="integrity.missingCount ? 'color:#E6A23C;font-weight:bold' : ''">{{ integrity.missingCount }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="巡检时间" :span="2">{{ fmtTime(integrity.checkedAt) }}</el-descriptions-item>
            </el-descriptions>
            <el-alert v-if="integrity.healthy" type="success" :closable="false"
                      title="全部存证与链下业务数据一致，未发现篡改痕迹" style="margin-top:10px" />
            <el-table v-else :data="integrity.items || []" size="small" style="margin-top:10px">
              <el-table-column prop="type" label="类型" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'MISMATCH' ? 'danger' : 'warning'" size="small">
                    {{ row.type === 'MISMATCH' ? '不一致' : '漏上链' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="bizType" label="业务" width="70">
                <template #default="{ row }">{{ cnType(row.bizType) }}</template>
              </el-table-column>
              <el-table-column prop="productId" label="商品ID" width="150" show-overflow-tooltip />
              <el-table-column prop="detail" label="定位说明" show-overflow-tooltip />
              <el-table-column prop="chainHash" label="链上哈希" width="130">
                <template #default="{ row }">{{ row.chainHash || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-else description="点击「立即巡检」对全部链上存证做哈希比对" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>异常告警</span>
              <el-button size="small" @click="loadAlerts">刷新</el-button>
            </div>
          </template>
          <div style="display:flex;gap:12px;margin-bottom:10px">
            <div style="flex:1;background:#FCEBEB;border-radius:8px;padding:10px;text-align:center">
              <div style="font-size:24px;font-weight:bold;color:#A32D2D">{{ alerts.verifyFailTotal ?? 0 }}</div>
              <div style="font-size:12px;color:#909399">验真失败累计</div>
            </div>
            <div style="flex:1;background:#FAEEDA;border-radius:8px;padding:10px;text-align:center">
              <div style="font-size:24px;font-weight:bold;color:#854F0B">
                {{ alerts.lastMismatchCount != null ? (alerts.lastMismatchCount + (alerts.lastMissingCount || 0)) : '—' }}
              </div>
              <div style="font-size:12px;color:#909399">上次巡检异常数</div>
            </div>
          </div>
          <el-table :data="alerts.recentFailed || []" size="small" style="width:100%">
            <el-table-column prop="productId" label="商品ID" width="150" show-overflow-tooltip />
            <el-table-column prop="verifier" label="验真方" width="90" />
            <el-table-column prop="ip" label="来源IP" width="120" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.time) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(alerts.recentFailed || []).length" description="暂无验真失败记录" :image-size="50" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 权属转移趋势图（纯 CSS 柱状图，不引入图表依赖） -->
    <el-card style="margin-top:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
          <span>权属转移趋势</span>
          <div>
            <el-button-group>
              <el-button size="small" :type="trendDays === 7 ? 'primary' : ''" @click="changeTrend(7)">近 7 天</el-button>
              <el-button size="small" :type="trendDays === 14 ? 'primary' : ''" @click="changeTrend(14)">近 14 天</el-button>
              <el-button size="small" :type="trendDays === 30 ? 'primary' : ''" @click="changeTrend(30)">近 30 天</el-button>
            </el-button-group>
            <el-button size="small" style="margin-left:8px" @click="loadTrend">刷新</el-button>
          </div>
        </div>
      </template>
      <div v-if="trend.length" v-loading="trendLoading">
        <div style="display:flex;align-items:flex-end;gap:3px;height:170px;border-bottom:1px solid #e2e8f0;padding:0 4px">
          <div v-for="p in trend" :key="p.date"
               style="flex:1;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:flex-end"
               :title="`${p.date}：${p.count} 笔`">
            <span style="font-size:10px;color:#64748b;line-height:14px">{{ p.count || '' }}</span>
            <div :style="{ height: barPct(p.count) + '%', width: '72%', background: '#3b82f6', borderRadius: '3px 3px 0 0', minHeight: p.count ? '3px' : '0' }"></div>
          </div>
        </div>
        <div style="display:flex;gap:3px;padding:5px 4px 0">
          <div v-for="(p, i) in trend" :key="'label-' + p.date"
               style="flex:1;text-align:center;font-size:9px;color:#94a3b8;white-space:nowrap">
            {{ i % labelEvery === 0 ? p.date.slice(5) : '' }}
          </div>
        </div>
        <div style="text-align:center;color:#909399;font-size:12px;margin-top:6px">
          区间合计 {{ trendTotal }} 笔 · 单日峰值 {{ trendPeak }} 笔
        </div>
      </div>
      <el-empty v-else description="暂无交易趋势数据" :image-size="60" />
    </el-card>

    <!-- 最近操作日志 -->
    <el-card style="margin-top:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>最近操作日志（审计留痕）</span>
          <div>
            <el-button size="small" @click="load">刷新</el-button>
            <el-button size="small" type="primary" @click="$router.push('/regulator/logs')">查看全部</el-button>
          </div>
        </div>
      </template>
      <el-table :data="logs" style="width:100%" size="small">
        <el-table-column prop="logId" label="日志ID" width="110" />
        <el-table-column prop="operator" label="操作人" width="140" />
        <el-table-column prop="action" label="操作类型" width="140" />
        <el-table-column prop="detail" label="详情" show-overflow-tooltip />
        <el-table-column label="操作时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.time) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logs.length" description="暂无操作日志" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getDashboard, runIntegrityCheck, getAlerts, getTradeTrend } from '../../api/dashboard'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'

const data = reactive({})
const logs = ref([])
const loading = ref(false)
const checking = ref(false)
const integrity = reactive({ checkedCount: 0, passCount: 0, mismatchCount: 0, missingCount: 0, healthy: true, items: [], checkedAt: null })
const alerts = reactive({})

// 交易趋势
const trend = ref([])
const trendDays = ref(14)
const trendLoading = ref(false)
const trendMax = computed(() => Math.max(1, ...trend.value.map(p => p.count || 0)))
const trendPeak = computed(() => Math.max(0, ...trend.value.map(p => p.count || 0)))
const trendTotal = computed(() => trend.value.reduce((s, p) => s + (p.count || 0), 0))
const labelEvery = computed(() => trend.value.length > 20 ? 5 : (trend.value.length > 10 ? 2 : 1))
function barPct(c) { return Math.round((c || 0) / trendMax.value * 100) }

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

const TYPE_CN = { product: '登记', quality: '质检', repair: '维修', trade: '转移' }
function cnType(t) { return TYPE_CN[t] || t }

async function load() {
  loading.value = true
  try {
    const res = await getDashboard()
    const d = res.data || res || {}
    data.productCount = d.productCount
    data.tradeCount = d.tradeCount
    data.verifyCount = d.verifyCount
    data.chainRecordCount = d.chainRecordCount
    logs.value = d.recentOperations || []
  } catch (e) {
    ElMessage.error('监管看板数据加载失败')
  }
  loading.value = false
}

async function check() {
  checking.value = true
  try {
    const res = await runIntegrityCheck()
    const d = res.data || res || {}
    Object.assign(integrity, {
      checkedCount: d.checkedCount || 0,
      passCount: d.passCount || 0,
      mismatchCount: d.mismatchCount || 0,
      missingCount: d.missingCount || 0,
      healthy: !!d.healthy,
      items: d.items || [],
      checkedAt: d.checkedAt
    })
    if (d.healthy) {
      ElMessage.success(`巡检完成：${d.checkedCount} 条存证全部一致`)
    } else {
      ElMessage.warning(`巡检发现 ${(d.mismatchCount || 0) + (d.missingCount || 0)} 处异常，明细见列表`)
    }
    loadAlerts()   // 巡检结果同步到告警卡片
  } catch (e) {
    ElMessage.error('巡检失败：' + (e?.message || '请稍后重试'))
  }
  checking.value = false
}

async function loadAlerts() {
  try {
    const res = await getAlerts()
    Object.assign(alerts, res.data || res || {})
  } catch (e) {
    // 告警加载失败不打断页面，仅置空展示
    alerts.verifyFailTotal = 0
    alerts.recentFailed = []
  }
}

async function loadTrend() {
  trendLoading.value = true
  try {
    const res = await getTradeTrend(trendDays.value)
    trend.value = res.data || res || []
  } catch (e) {
    trend.value = []
  }
  trendLoading.value = false
}

function changeTrend(d) {
  trendDays.value = d
  loadTrend()
}

onMounted(() => { load(); loadAlerts(); loadTrend() })
</script>

<style scoped>
.metric { font-size:30px; font-weight:bold; text-align:center; padding:6px 0; }
.metric-sub { text-align:center; color:#909399; font-size:12px; }
</style>
