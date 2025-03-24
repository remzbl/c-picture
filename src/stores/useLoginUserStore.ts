import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

/**
 * 存储登录用户信息的状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
    token: '',

  })

  /**
   * 远程获取登录用户信息
   */
  async function fetchLoginUser(userData :Any) {
    //const res = await getLoginUserUsingGet()
    console.log('Login user data:', userData); // 调试输出
    if (userData) {
      // 如果传入了用户数据，直接更新
      loginUser.value = userData.data.data;
      console.log('Login user data:', loginUser.value);
      return;
    }

  }

  /**
   * 设置登录用户
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  // 返回
  return { loginUser, fetchLoginUser, setLoginUser }
}, {

  persist: true

})
