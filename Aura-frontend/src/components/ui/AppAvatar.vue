<script setup lang="ts">
import { computed } from 'vue'
import { assetUrl } from '@/utils/asset'
import { initialOf } from '@/utils/format'

const props = withDefaults(
  defineProps<{ src?: string | null; name?: string; size?: number }>(),
  { size: 36 },
)

const imgSrc = computed(() => assetUrl(props.src))
const fallback = computed(() => initialOf(props.name || ''))
</script>

<template>
  <div
    class="flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface-muted font-medium text-muted"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.42}px` }"
  >
    <img v-if="imgSrc" :src="imgSrc" :alt="name" class="h-full w-full object-cover" />
    <span v-else>{{ fallback }}</span>
  </div>
</template>
