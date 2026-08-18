<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

marked.setOptions({ breaks: true, gfm: true })

// 让 AI 返回的链接在新标签页打开，并附带安全 rel（防 tabnabbing）
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const html = computed(() => {
  // 「~」在中文里常作区间分隔符（如 21℃~32℃、24~31℃），
  // 但 marked 会把它当成删除线标记，导致两个 ~ 之间的大段文字被画横线。
  // 这里把单独出现的 ~ 转义为字面量，仅保留成对的 ~~（真正的删除线）。
  const text = (props.content || '').replace(/~+/g, (m) => (m.length >= 2 ? m : '\\~'))
  const raw = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(raw)
})
</script>

<template>
  <div class="markdown-body text-sm leading-6 text-ink" v-html="html" />
</template>

<style>
.markdown-body > *:first-child {
  margin-top: 0;
}
.markdown-body > *:last-child {
  margin-bottom: 0;
}
.markdown-body p {
  margin: 0.4em 0;
}
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  font-weight: 600;
  margin: 0.8em 0 0.4em;
  line-height: 1.4;
}
.markdown-body h1 {
  font-size: 1.25em;
}
.markdown-body h2 {
  font-size: 1.15em;
}
.markdown-body h3 {
  font-size: 1.05em;
}
.markdown-body h4 {
  font-size: 1em;
}
.markdown-body h5 {
  font-size: 0.95em;
}
.markdown-body h6 {
  font-size: 0.9em;
  color: var(--color-muted);
}
.markdown-body ul,
.markdown-body ol {
  padding-left: 1.4em;
  margin: 0.4em 0;
}
.markdown-body ul {
  list-style: disc;
}
.markdown-body ol {
  list-style: decimal;
}
.markdown-body li {
  margin: 0.2em 0;
}
.markdown-body code {
  font-family: theme('fontFamily.mono');
  font-size: 0.85em;
  background: var(--color-code-bg);
  border-radius: 4px;
  padding: 0.1em 0.35em;
}
.markdown-body pre {
  background: var(--color-code-bg);
  border-radius: 8px;
  padding: 12px 14px;
  overflow-x: auto;
  margin: 0.6em 0;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
  font-size: 0.82em;
  line-height: 1.6;
}
.markdown-body blockquote {
  border-left: 2px solid var(--color-line-strong);
  padding-left: 0.8em;
  color: var(--color-muted);
  margin: 0.5em 0;
}
.markdown-body hr {
  border: none;
  border-top: 1px solid var(--color-line-strong);
  margin: 1em 0;
}
.markdown-body img {
  max-width: 100%;
  border-radius: 6px;
}
.markdown-body input[type='checkbox'] {
  margin-right: 0.4em;
  accent-color: var(--color-ink);
}
.markdown-body a {
  color: var(--color-blue-text);
  text-decoration: underline;
}
.markdown-body table {
  border-collapse: collapse;
  margin: 0.6em 0;
  width: 100%;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid var(--color-line);
  padding: 6px 10px;
  text-align: left;
}
.markdown-body th {
  background: var(--color-surface-muted);
  font-weight: 600;
}
</style>
