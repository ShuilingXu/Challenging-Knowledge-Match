import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ScreenJoinQr } from './App'

const render = (props) => renderToStaticMarkup(createElement(ScreenJoinQr, props))

describe('大屏常驻加入二维码', () => {
  it('渲染固定角落的二维码卡片，并附上扫码说明', () => {
    const html = render({ url: 'https://live.example.com/join/venue-1', caption: '扫码加入', hint: '手机扫码答题 / 抢答' })
    expect(html).toContain('class="screen-join-qr"')
    expect(html).toContain('class="screen-join-qr__code"')
    expect(html).toContain('扫码加入')
    expect(html).toContain('手机扫码答题 / 抢答')
    expect(html).toContain('aria-label="扫码加入二维码"')
  })

  it('把加入链接编码进二维码，扫码即可进入参与者页面', () => {
    const html = render({ url: 'https://live.example.com/join/venue-1' })
    expect(html).toContain('<svg')
    expect(html).toContain('扫码加入：https://live.example.com/join/venue-1')
    expect(html).toContain('>扫码加入<')
    expect(html).not.toContain('<small>')
  })
})
