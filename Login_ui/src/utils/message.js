import { ref } from 'vue'

/**
 * 消息提示 composable
 * 使用方式：
 * import { useMessage } from '@/utils/message'
 * const { showMessage, showSuccess, showError, showInfo, showConfirm } = useMessage()
 * 
 * 在模板中：
 * <MessageToast v-if="showToast" :message="toastMessage" :type="toastType" @confirm="handleConfirm" @cancel="handleCancel" ... />
 */
export const useMessage = () => {
  const showToast = ref(false)
  const toastMessage = ref('')
  const toastType = ref('success')
  const toastRedirect = ref(null)
  const toastDuration = ref(2000)
  let confirmResolve = null
  let confirmReject = null

  const showMessage = (message, type = 'success', redirectTo = null, duration = 2000) => {
    toastMessage.value = message
    toastType.value = type
    toastRedirect.value = redirectTo
    toastDuration.value = duration
    showToast.value = true
  }

  const showSuccess = (message, redirectTo = null, duration = 2000) => {
    showMessage(message, 'success', redirectTo, duration)
  }

  const showError = (message, duration = 2000) => {
    showMessage(message, 'error', null, duration)
  }

  const showInfo = (message, duration = 2000) => {
    showMessage(message, 'info', null, duration)
  }

  // 显示确认对话框，返回 Promise
  const showConfirm = (message) => {
    return new Promise((resolve) => {
      confirmResolve = resolve
      showMessage(message, 'confirm', null, 0)
    })
  }

  const handleConfirm = () => {
    if (confirmResolve) {
      confirmResolve(true)
      confirmResolve = null
    }
    hideMessage()
  }

  const handleCancel = () => {
    if (confirmResolve) {
      confirmResolve(false)
      confirmResolve = null
    }
    hideMessage()
  }

  const hideMessage = () => {
    showToast.value = false
  }

  return {
    showToast,
    toastMessage,
    toastType,
    toastRedirect,
    toastDuration,
    showMessage,
    showSuccess,
    showError,
    showInfo,
    showConfirm,
    handleConfirm,
    handleCancel,
    hideMessage
  }
}
