import { describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'

describe('Flowora locale resources', () => {
  it('contains the same core navigation keys in both supported locales', () => {
    const zh = i18n.global.getLocaleMessage('zh-CN') as {
      nav: Record<string, string>
      common: { appName: string }
    }
    const en = i18n.global.getLocaleMessage('en-US') as {
      nav: Record<string, string>
      common: { appName: string }
    }

    expect(Object.keys(zh.nav)).toEqual(Object.keys(en.nav))
    expect(zh.common.appName).toBe('Flowora ERP')
    expect(en.common.appName).toBe('Flowora ERP')
    expect(i18n.global.t('auth.usernamePlaceholder', 'zh-CN')).toBe('you@example.com')
    expect(i18n.global.t('auth.usernamePlaceholder', 'en-US')).toBe('you@example.com')
  })
})
