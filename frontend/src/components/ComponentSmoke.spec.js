import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { ElTag, ElButton } from 'element-plus'

/**
 * 组件渲染冒烟测试：确认 jsdom + Vue Test Utils + Element Plus 集成正常，
 * 后续业务组件测试可复用此挂载模式。
 */
describe('Element Plus 集成冒烟', () => {
  it('el-tag 能渲染文本', () => {
    const wrapper = mount(
      defineComponent({
        render() {
          return h(ElTag, { type: 'danger' }, () => '高风险')
        }
      })
    )
    expect(wrapper.text()).toContain('高风险')
    expect(wrapper.find('.el-tag').exists()).toBe(true)
  })

  it('el-button 能渲染并响应点击', async () => {
    let clicked = 0
    const wrapper = mount(
      defineComponent({
        render() {
          return h(
            ElButton,
            { type: 'primary', onClick: () => clicked++ },
            () => '查询'
          )
        }
      })
    )
    expect(wrapper.text()).toContain('查询')
    await wrapper.find('button').trigger('click')
    expect(clicked).toBe(1)
  })

  it('el-tag 不同 type 渲染对应 class', () => {
    const wrapper = mount(
      defineComponent({
        render() {
          return h(ElTag, { type: 'success' }, () => '正常')
        }
      })
    )
    expect(wrapper.find('.el-tag--success').exists()).toBe(true)
  })
})
