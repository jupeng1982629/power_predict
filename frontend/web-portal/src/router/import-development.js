const viewModules = {
  'common/404': require('@/views/common/404.vue').default,
  'common/login': require('@/views/common/login.vue').default,
  'main': require('@/views/main.vue').default,
  'common/home': require('@/views/common/home.vue').default,
  'common/plants': require('@/views/common/plants.vue').default,
  'common/forecast': require('@/views/common/forecast.vue').default,
  'common/theme': require('@/views/common/theme.vue').default,
  'demo/echarts': require('@/views/demo/echarts.vue').default,
  'demo/ueditor': require('@/views/demo/ueditor.vue').default
}

module.exports = file => {
  const component = viewModules[file]
  if (!component) {
    throw new Error('Unknown route view: ' + file)
  }
  return component
}
