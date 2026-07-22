const viewModules = {
  'common/404': () => import('@/views/common/404.vue'),
  'common/login': () => import('@/views/common/login.vue'),
  'main': () => import('@/views/main.vue'),
  'common/home': () => import('@/views/common/home.vue'),
  'common/plants': () => import('@/views/common/plants.vue'),
  'common/forecast': () => import('@/views/common/forecast.vue'),
  'common/theme': () => import('@/views/common/theme.vue'),
  'demo/echarts': () => import('@/views/demo/echarts.vue'),
  'demo/ueditor': () => import('@/views/demo/ueditor.vue')
}

module.exports = file => {
  const importer = viewModules[file]
  if (!importer) {
    return () => Promise.reject(new Error('Unknown route view: ' + file))
  }
  return importer
}
