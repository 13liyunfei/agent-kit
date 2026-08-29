import { defineConfig } from 'vitepress'

const enNav = [
  { text: 'Guide', link: '/guide/' },
  { text: 'Architecture', link: '/architecture' },
  { text: 'Build', link: '/build' }
]

const zhNav = [
  { text: '指南', link: '/zh/guide/' },
  { text: '架构', link: '/zh/architecture' },
  { text: '构建', link: '/zh/build' }
]

const enSidebar = [
  {
    text: 'Getting started',
    items: [
      { text: 'Introduction', link: '/guide/' },
      { text: 'Quick start', link: '/guide/quickstart' }
    ]
  },
  {
    text: 'Core capabilities',
    items: [
      { text: 'Tool calling loop', link: '/guide/toolcalling' },
      { text: 'Planning & DAG', link: '/guide/planning' },
      { text: 'Evaluation', link: '/guide/eval' },
      { text: 'Extension SPI', link: '/guide/extension' }
    ]
  },
  {
    text: 'Production capabilities',
    items: [
      { text: 'Session & streaming', link: '/guide/session' },
      { text: 'Structured output', link: '/guide/struct' },
      { text: 'MCP integration', link: '/guide/mcp' },
      { text: 'Checkpoint & HITL', link: '/guide/checkpoint' },
      { text: 'Observability & routing', link: '/guide/obs' },
      { text: 'Security', link: '/guide/security' }
    ]
  }
]

const zhSidebar = [
  {
    text: '开始',
    items: [
      { text: '简介', link: '/zh/guide/' },
      { text: '快速开始', link: '/zh/guide/quickstart' }
    ]
  },
  {
    text: '核心能力',
    items: [
      { text: '工具调用循环', link: '/zh/guide/toolcalling' },
      { text: '任务拆解与 DAG', link: '/zh/guide/planning' },
      { text: '评估与回归', link: '/zh/guide/eval' },
      { text: '扩展点 SPI', link: '/zh/guide/extension' }
    ]
  },
  {
    text: '生产级能力',
    items: [
      { text: '会话与流式', link: '/zh/guide/session' },
      { text: '结构化输出', link: '/zh/guide/struct' },
      { text: 'MCP 接入', link: '/zh/guide/mcp' },
      { text: '检查点与人机协作', link: '/zh/guide/checkpoint' },
      { text: '可观测与路由', link: '/zh/guide/obs' },
      { text: '安全', link: '/zh/guide/security' }
    ]
  }
]

export default defineConfig({
  base: '/agent-kit/',
  title: 'agent-kit',
  description: 'Reusable multi-agent capability kit — plug in as a Maven component, extend via SPI',

  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: enNav,
        sidebar: enSidebar,
        outlineTitle: 'On this page',
        lastUpdatedText: 'Last updated'
      }
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: zhNav,
        sidebar: zhSidebar,
        outlineTitle: '本页目录',
        lastUpdatedText: '最后更新'
      }
    }
  },

  themeConfig: {
    search: {
      provider: 'local'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/13liyunfei/agent-kit' }
    ],
    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2026 13liyunfei'
    },
    editLink: {
      pattern: 'https://github.com/13liyunfei/agent-kit/edit/main/site/:path',
      text: 'Edit this page on GitHub'
    }
  },

  markdown: {
    languages: ['java', 'xml', 'bash']
  }
})
