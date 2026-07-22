<template>
  <div class="site-wrapper site-page--login">
    <div class="site-content__wrapper">
      <div class="site-content">
        <div class="brand-info">
          <h2 class="brand-info__text">光伏发电智能预测系统</h2>
          <p class="brand-info__intro">基于人人开源前端基座改造，已接入网关与统一认证，登录后可直接进入系统。</p>
        </div>
        <div class="login-main">
          <h3 class="login-title">统一认证登录</h3>
          <el-alert title="请使用 Keycloak 账号登录" type="info" :closable="false" show-icon></el-alert>
          <el-button class="login-btn-submit" type="primary" @click="handleLogin" :loading="loading">去认证</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import { login as keycloakLogin } from '@/auth/keycloak'

  export default {
    data () {
      return {
        loading: false
      }
    },
    methods: {
      async handleLogin () {
        this.loading = true
        try {
          await keycloakLogin()
        } catch (e) {
          this.loading = false
          this.$message.error(e && e.message ? e.message : '跳转认证失败')
        }
      }
    }
  }
</script>

<style lang="scss">
  .site-wrapper.site-page--login {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    background-color: rgba(38, 50, 56, .6);
    overflow: hidden;
    &:before {
      position: fixed;
      top: 0;
      left: 0;
      z-index: -1;
      width: 100%;
      height: 100%;
      content: "";
      background-image: url(~@/assets/img/login_bg.jpg);
      background-size: cover;
    }
    .site-content__wrapper {
      position: absolute;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      padding: 0;
      margin: 0;
      overflow-x: hidden;
      overflow-y: auto;
      background-color: transparent;
    }
    .site-content {
      min-height: 100%;
      padding: 30px 500px 30px 30px;
    }
    .brand-info {
      margin: 220px 100px 0 90px;
      color: #fff;
    }
    .brand-info__text {
      margin:  0 0 22px 0;
      font-size: 48px;
      font-weight: 400;
      text-transform : uppercase;
    }
    .brand-info__intro {
      margin: 10px 0;
      font-size: 16px;
      line-height: 1.58;
      opacity: .6;
    }
    .login-main {
      position: absolute;
      top: 0;
      right: 0;
      padding: 150px 60px 180px;
      width: 470px;
      min-height: 100%;
      background-color: #fff;
    }
    .login-title {
      font-size: 16px;
      margin-bottom: 16px;
    }
    .login-btn-submit {
      width: 100%;
      margin-top: 20px;
    }
  }
</style>
