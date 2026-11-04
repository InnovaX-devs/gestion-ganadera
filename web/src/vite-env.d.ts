/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  readonly VITE_CARENCIA_DIAS_EXTRA?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
