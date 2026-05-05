<template>
  <el-dialog
    v-model="visible"
    title="终端账户配置"
    width="480px"
    class="custom-dark-dialog"
    :close-on-click-modal="false"
  >
    <div class="px-6 py-4">
      <div class="flex flex-col items-center mb-8 relative group">
        <!-- 悬浮在外层，用来点击上传 -->
        <el-upload
          class="avatar-uploader relative"
          action="#"
          :show-file-list="false"
          :http-request="customUpload"
          :before-upload="beforeAvatarUpload"
        >
          <div class="relative w-24 h-24 rounded-full overflow-hidden border-2 border-gray-700 group-hover:border-blue-500 transition-colors shadow-lg">
            <img
              v-if="form.avatar"
              :src="form.avatar"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full bg-gray-800 flex items-center justify-center">
              <el-icon class="text-3xl text-gray-500"><User /></el-icon>
            </div>

            <!-- 悬浮的暗色遮罩和相机图标 -->
            <div class="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity cursor-pointer">
               <el-icon v-if="!uploading" class="text-2xl text-white"><Camera /></el-icon>
               <el-icon v-else class="text-2xl text-blue-500 is-loading"><Loading /></el-icon>
            </div>
          </div>
        </el-upload>
        <span class="text-xs text-gray-500 mt-3 font-mono">更换系统识别面容</span>
      </div>

      <div class="space-y-4">
        <!-- 用户名 (不可改) -->
        <div class="space-y-1">
          <label class="text-xs font-semibold text-gray-400 tracking-widest pl-1">系统主账号 (不可修改)</label>
          <div class="w-full bg-gray-900/50 border border-gray-800 rounded-lg h-10 px-4 flex items-center text-gray-500 cursor-not-allowed text-sm">
            {{ userStore.userInfo?.username }}
          </div>
        </div>

        <!-- 昵称 (可改) -->
        <div class="space-y-1">
          <label class="text-xs font-semibold text-gray-400 tracking-widest pl-1">终端公开代号</label>
          <vs-input 
            v-model="form.nickname" 
            placeholder="请输入新的代号" 
            class="fin-dark-input block w-full"
          >
            <template #icon>
              <EditPen class="w-4 h-4" />
            </template>
          </vs-input>
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="h-px bg-gray-800 my-6"></div>

      <!-- 修改密码区块 -->
      <div class="space-y-1 mb-2">
        <label class="text-xs font-semibold text-gray-400 tracking-widest pl-1">修改登录密码</label>
        <p class="text-[11px] text-gray-600 pl-1">留空则不修改密码</p>
      </div>
      <div class="space-y-3">
        <vs-input
          v-model="pwdForm.oldPassword"
          type="password"
          placeholder="当前密码"
          class="fin-dark-input block w-full"
        >
          <template #icon><Lock class="w-4 h-4" /></template>
        </vs-input>
        <vs-input
          v-model="pwdForm.newPassword"
          type="password"
          placeholder="新密码（至少 6 位）"
          class="fin-dark-input block w-full"
        >
          <template #icon><Lock class="w-4 h-4" /></template>
        </vs-input>
        <vs-input
          v-model="pwdForm.confirmPassword"
          type="password"
          placeholder="确认新密码"
          class="fin-dark-input block w-full"
        >
          <template #icon><Lock class="w-4 h-4" /></template>
        </vs-input>
      </div>
    </div>

    <template #footer>
      <div class="flex gap-4 justify-end">
        <vs-button type="transparent" color="#71717a" @click="visible = false" class="font-bold">
          取消操作
        </vs-button>
        <vs-button color="#2563eb" class="font-bold tracking-widest w-28" :loading="submitLoading" @click="submitUpdate">
          写入核心
        </vs-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { updateProfile, changePassword } from '../api/user'
import { User, Camera, Loading, EditPen, Lock } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const userStore = useUserStore()

const uploading = ref(false)
const submitLoading = ref(false)

const form = ref({
  avatar: '',
  nickname: ''
})

const pwdForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 当弹窗打开时，回填仓库里的当前数据，并清空密码表单
watch(visible, (val) => {
  if (val && userStore.userInfo) {
    form.value.avatar = userStore.userInfo.avatar || ''
    form.value.nickname = userStore.userInfo.nickname || ''
  }
  if (!val) {
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  }
})

// 限制上传的图片大小
const beforeAvatarUpload = (file) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp'
  const isLt2M = file.size / 1024 / 1024 < 5

  if (!isJpgOrPng) {
    ElMessage.error('上传头像图片只能是 JPG/PNG/WEBP 格式!')
  }
  if (!isLt2M) {
    ElMessage.error('上传头像图片大小不能超过 5MB!')
  }
  return isJpgOrPng && isLt2M
}

// 拦截原生的行为，执行我们自己带有 Token 的长连接上传
const customUpload = async (options) => {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    formData.append('app_from', 'en')

    // 访问您提供的云端对象存储接口，带上鉴权固定的 token
    const res = await axios.post('http://39.101.133.168:8828/cloud/api/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'token': 'pC134lIG1xDIbIi1ohnnbZi0+fEeMx8pywnIlrmTxdwROKkuwWqAWu9orpkpeXVqL98DPfeonNYpHv+mucA'
      }
    })
    
    // 解析返回结构适配真实的 API 响应: { code: 200, data: { url: "..." } }
    if (res.data && res.data.code === 200 && res.data.data) {
       // 提取 data.url 参数
       form.value.avatar = res.data.data.url
       ElMessage.success('云端图像装载完毕')
    } else {
       // 防止直接返回 URL 字符串或是其它嵌套格式的容错
       if (typeof res.data === 'string' && res.data.startsWith('http')) {
           form.value.avatar = res.data
           ElMessage.success('云端图像装载完毕')
       } else if (res.data.data && res.data.data.url) {
           form.value.avatar = res.data.data.url
       } else {
           throw new Error('未知返回结构')
       }
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('云资源上传中断，请检查网络链接')
  } finally {
    uploading.value = false
  }
}

// 保存修改回填到后端
const submitUpdate = async () => {
    const wantChangePwd = pwdForm.value.oldPassword || pwdForm.value.newPassword || pwdForm.value.confirmPassword

    if (wantChangePwd) {
      if (!pwdForm.value.oldPassword) { ElMessage.warning('请输入当前密码'); return }
      if (pwdForm.value.newPassword.length < 6) { ElMessage.warning('新密码长度不能少于 6 位'); return }
      if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) { ElMessage.warning('两次输入的新密码不一致'); return }
    }

    submitLoading.value = true
    try {
        const profileRes = await updateProfile({
            nickname: form.value.nickname,
            avatar: form.value.avatar
        })
        if (profileRes.code === 200 && userStore.userInfo) {
            userStore.userInfo.nickname = form.value.nickname
            userStore.userInfo.avatar = form.value.avatar
        }

        if (wantChangePwd) {
          await changePassword(pwdForm.value.oldPassword, pwdForm.value.newPassword)
          ElMessage.success('密码修改成功，即将重新登录')
          visible.value = false
          setTimeout(() => {
            userStore.clearAuth()
            window.location.href = '/login'
          }, 1500)
        } else {
          ElMessage.success('账户核心数据同步成功')
          visible.value = false
        }
    } catch(e) {
        console.error(e)
    } finally {
        submitLoading.value = false
    }
}
</script>

<style>
/* 将该黑暗系列注入到全局 body 里以防止 Element plus modal 挂载时样式丢失 */
.custom-dark-dialog {
  background-color: #18181b !important;
  border: 1px solid #27272a !important;
  border-radius: 16px !important;
}
.custom-dark-dialog .el-dialog__title {
  color: #f4f4f5 !important;
  font-weight: 700;
  letter-spacing: 0.1em;
}
.custom-dark-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #71717a;
}
.custom-dark-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #f4f4f5;
}

/* 输入框覆盖样式 */
.fin-dark-input .vs-input {
  width: 100% !important;
  background-color: rgba(9, 9, 11, 0.6) !important;
  box-shadow: 0 0 0 1px #27272a inset !important;
  color: #f4f4f5 !important;
  height: 48px;
  border-radius: 8px;
}
.fin-dark-input .vs-input-parent,
.fin-dark-input .vs-input-content {
  width: 100% !important;
  display: block;
}
.fin-dark-input .vs-input:focus {
  background-color: rgba(0,0,0,0.4) !important;
  box-shadow: 0 0 0 1px #3b82f6 inset !important;
}
</style>