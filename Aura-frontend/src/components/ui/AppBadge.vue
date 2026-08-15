<script setup lang="ts">
import { BADGE_TONE, type BadgeTone } from '@/constants/enums'

withDefaults(defineProps<{ tone?: BadgeTone }>(), { tone: 'gray' })
</script>

<template>
  <span
    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium uppercase tracking-wide"
    :class="`${BADGE_TONE[tone].bg} ${BADGE_TONE[tone].text}`"
  >
    <slot />
  </span>
</template>
