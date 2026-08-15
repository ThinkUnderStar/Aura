/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 暖色单色系（设计系统唯一色彩来源）
        canvas: '#F7F6F3',
        surface: '#FFFFFF',
        'surface-muted': '#F9F9F8',
        line: '#EAEAEA',
        'line-strong': '#D9D8D4',
        ink: '#111111',
        muted: '#787774',
        faint: '#9B9A96',
        // 灰调柔色（状态语义）
        'red-bg': '#FDEBEC',
        'red-text': '#9F2F2D',
        'blue-bg': '#E1F3FE',
        'blue-text': '#1F6C9F',
        'green-bg': '#EDF3EC',
        'green-text': '#346538',
        'yellow-bg': '#FBF3DB',
        'yellow-text': '#956400',
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
