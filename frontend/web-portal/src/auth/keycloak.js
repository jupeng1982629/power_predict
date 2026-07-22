import Vue from 'vue'
import Keycloak from 'keycloak-js'

function createKeycloakInstance () {
  const KeycloakFactory = (Keycloak && Keycloak.default) ? Keycloak.default : Keycloak
  if (typeof KeycloakFactory !== 'function') {
    return null
  }

  const config = {
    url: 'http://localhost:18081',
    realm: 'power-predict',
    clientId: 'web-portal'
  }

  try {
    return new KeycloakFactory(config)
  } catch (newError) {
    try {
      return KeycloakFactory(config)
    } catch (callError) {
      return null
    }
  }
}

const keycloak = createKeycloakInstance()

if (!keycloak) {
  // eslint-disable-next-line no-console
  console.error('Failed to initialize keycloak-js instance. Check keycloak-js package compatibility.')
}

const DEFAULT_REDIRECT_URI = `${window.location.origin}${window.location.pathname}`
const ACCESS_TOKEN_STORAGE_KEY = 'pp_access_token'
let initialized = false

function replaceCurrentUrlHash (nextHash) {
  const safeHash = nextHash || ''
  const nextUrl = `${window.location.origin}${window.location.pathname}${window.location.search || ''}${safeHash}`
  window.history.replaceState({}, document.title, nextUrl)
}

function normalizeKeycloakCallbackHash () {
  const currentHash = window.location.hash || ''
  if (!currentHash) {
    return
  }

  let normalizedHash = currentHash

  // Vue hash mode may rewrite callback fragment to '#/state=...' which Keycloak cannot parse.
  if (/^#\/(code|state|session_state|error|iss)=/i.test(normalizedHash)) {
    normalizedHash = `#${normalizedHash.slice(2)}`
  }

  // In some browsers/flows, issuer may appear after a second '#', fold it back as '&iss='.
  const secondHashIndex = normalizedHash.indexOf('#', 1)
  if (secondHashIndex > 0) {
    const issuerPart = normalizedHash.slice(secondHashIndex + 1)
    if (/^(https?:\/\/|https?%3A%2F%2F)/i.test(issuerPart)) {
      normalizedHash = `${normalizedHash.slice(0, secondHashIndex)}&iss=${issuerPart}`
    }
  }

  if (normalizedHash !== currentHash) {
    replaceCurrentUrlHash(normalizedHash)
  }
}

function hasAuthParamsInSearch (search) {
  return /(^|[?&])(code|state|session_state|error|iss)=/i.test(search || '')
}

function hasAuthParamsInHash (hash) {
  const hashText = hash || ''
  return /^#\/?(code|state|session_state|error|iss)=/i.test(hashText) ||
    /([?&])(code|state|session_state|error|iss)=/i.test(hashText)
}

function hasOAuthCallbackParams () {
  const search = window.location.search || ''
  const hash = window.location.hash || ''
  const processableInSearch = /(^|[?&])(code|state|session_state|error)=/i.test(search)
  const processableInHash = /^#\/?(code|state|session_state|error)=/i.test(hash) || /([?&])(code|state|session_state|error)=/i.test(hash)
  return processableInSearch || processableInHash
}

function buildCleanHash (hash) {
  const normalizedHash = hash || '#/home'
  const hasLoginRequiredError = /(^#\/?error=login_required)|([?&])error=login_required/i.test(normalizedHash)

  if (/^#\/?(code|state|session_state|error|iss)=/i.test(normalizedHash)) {
    return hasLoginRequiredError ? '#/login' : '#/home'
  }

  const cleanHash = normalizedHash
    .replace(/[?&](code|state|session_state|error|iss)=[^&]*/gi, '')
    .replace(/[?&]+$/, '')

  if (hasLoginRequiredError) {
    return '#/login'
  }

  return !cleanHash || cleanHash === '#' || cleanHash === '#/' ? '#/home' : cleanHash
}

function cleanAuthCallbackUrl () {
  const search = window.location.search || ''
  const hash = window.location.hash || ''
  const hasCallbackInSearch = hasAuthParamsInSearch(search)
  const hasCallbackInHash = hasAuthParamsInHash(hash)

  if (!hasCallbackInSearch && !hasCallbackInHash) {
    return
  }

  const finalHash = buildCleanHash(hash)
  const cleanUrl = `${window.location.origin}${window.location.pathname}${finalHash}`
  window.history.replaceState({}, document.title, cleanUrl)
}

function syncTokenToCookie () {
  if (keycloak.authenticated && keycloak.token) {
    window.sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, keycloak.token)
    // Keep a lightweight cookie marker for legacy checks while storing the JWT in sessionStorage.
    Vue.cookie.set('token', '1')
  } else {
    window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    Vue.cookie.delete('token')
  }
}

export async function initAuth () {
  if (!keycloak) {
    window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    Vue.cookie.delete('token')
    return false
  }

  normalizeKeycloakCallbackHash()

  const hasProcessableCallback = hasOAuthCallbackParams()

  if (!initialized) {
    const initOptions = {
      pkceMethod: 'S256',
      checkLoginIframe: false,
      useNonce: false,
      redirectUri: DEFAULT_REDIRECT_URI
    }

    try {
      await keycloak.init(initOptions)
    } catch (initError) {
      // keycloak-js may reject with an undefined error even after token exchange succeeds.
      if (!keycloak.token) {
        throw initError
      }
      keycloak.authenticated = true
    }
    initialized = true
  }

  syncTokenToCookie()
  if (hasProcessableCallback || hasAuthParamsInHash(window.location.hash || '') || hasAuthParamsInSearch(window.location.search || '')) {
    cleanAuthCallbackUrl()
  }

  if (!keycloak.authenticated && hasProcessableCallback) {
    window.location.hash = '#/login'
    return false
  }

  if (keycloak.authenticated) {
    const hashAfterAuth = window.location.hash || ''
    if (!hashAfterAuth || hashAfterAuth === '#' || hashAfterAuth === '#/' || hashAfterAuth === '#/login' || /^#\/?(code|state|session_state|error|iss)=/i.test(hashAfterAuth)) {
      window.location.hash = '#/home'
    }
  }
  return !!keycloak.authenticated
}

export async function login () {
  if (!keycloak) {
    throw new Error('Keycloak SDK initialization failed')
  }

  normalizeKeycloakCallbackHash()

  if (!initialized) {
    await keycloak.init({
      pkceMethod: 'S256',
      checkLoginIframe: false,
      useNonce: false,
      redirectUri: DEFAULT_REDIRECT_URI
    })
    initialized = true
  }

  await keycloak.login({
    responseMode: 'query',
    redirectUri: DEFAULT_REDIRECT_URI
  })
}

export async function logout () {
  window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
  Vue.cookie.delete('token')
  if (!keycloak) {
    window.location.href = `${window.location.origin}${window.location.pathname}#/login`
    return
  }

  await keycloak.logout({
    redirectUri: `${window.location.origin}${window.location.pathname}`
  })
}
