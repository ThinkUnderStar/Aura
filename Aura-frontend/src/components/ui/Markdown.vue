<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

marked.setOptions({ breaks: true, gfm: true })

const html = computed(() => {
  const raw = marked.parse(props.content || '', { async: false }) as string
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
.markdown-body h3 {
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
  background: #f1f0ec;
  border-radius: 4px;
  padding: 0.1em 0.35em;
}
.markdown-body pre {
  background: #f1f0ec;
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
  border-left: 2px solid #e0dfda;
  padding-left: 0.8em;
  color: #787774;
  margin: 0.5em 0;
}
.markdown-body a {
  color: #1f6c9f;
  text-decoration: underline;
}
.markdown-body table {
  border-collapse: collapse;
  margin: 0.6em 0;
  width: 100%;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid #eaeaea;
  padding: 6px 10px;
  text-align: left;
}
.markdown-body th {
  background: #f9f9f8;
  font-weight: 600;
}
</style>
