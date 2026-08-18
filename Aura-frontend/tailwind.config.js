/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 暖色单色系（设计系统唯一色彩来源）。全部指向 CSS 变量，
        // 由 main.css 的 :root（亮色）与 html.dark（暗色）统一供给。
        canvas: 'var(--color-canvas)',
        surface: 'var(--color-surface)',
        'surface-muted': 'var(--color-surface-muted)',
        line: 'var(--color-line)',
        'line-strong': 'var(--color-line-strong)',
        // ink 是正文色（暗色下变亮）；ink-solid 是品牌/按钮的实底色，暗色下保持深底白字
        ink: 'var(--color-ink)',
        'ink-solid': 'var(--color-ink-solid)',
        muted: 'var(--color-muted)',
        faint: 'var(--color-faint)',
        // 灰调柔色（状态语义）
        'red-bg': 'var(--color-red-bg)',
        'red-text': 'var(--color-red-text)',
        'red-solid': 'var(--color-red-solid)',
        'blue-bg': 'var(--color-blue-bg)',
        'blue-text': 'var(--color-blue-text)',
        'green-bg': 'var(--color-green-bg)',
        'green-text': 'var(--color-green-text)',
        'yellow-bg': 'var(--color-yellow-bg)',
        'yellow-text': 'var(--color-yellow-text)',
      },
      fontFamily: {
        sans: [
          '-apple-system',
          'BlinkMacSystemFont',
          'PingFang SC',
          'HarmonyOS Sans SC',
          'Microsoft YaHei',
          'Source Han Sans SC',
          'Segoe UI',
          'sans-serif',
        ],
        serif: ['Songti SC', 'Noto Serif SC', 'Source Han Serif SC', 'Georgia', 'serif'],
        mono: ['JetBrains Mono', 'SF Mono', 'ui-monospace', 'Menlo', 'Consolas', 'monospace'],
      },
      borderRadius: {
        sm: '4px',
        DEFAULT: '8px',
        lg: '12px',
      },
      boxShadow: {
        // 极轻、低透明度，禁止重投影
        lift: '0 2px 8px rgba(0, 0, 0, 0.04)',
      },
    },
  },
  plugins: [],
}
