<script setup lang="ts">
import SideNav from './SideNav.vue'
import TopBar from './TopBar.vue'
import BottomTabBar from './BottomTabBar.vue'
</script>

<template>
  <div class="flex h-screen overflow-hidden">
    <SideNav class="hidden md:flex" />
    <div class="flex min-w-0 flex-1 flex-col">
      <TopBar />
      <main class="flex-1 overflow-y-auto pb-20 md:pb-0">
        <router-view />
      </main>
    </div>
    <BottomTabBar class="md:hidden" />
  </div>
</template>
