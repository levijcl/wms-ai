<script setup>
// A compact key for the colors used across the panels (README §7), so a dispatcher can
// read the map and board at a glance. Mirrors the status/priority colors in WarehouseMap,
// OrderBoard, and TaskList.
import { Priority, WorkerStatus } from '@/lib/enums.js';
</script>

<template>
  <section class="legend">
    <h2>Legend</h2>
    <div class="groups">
      <div class="group">
        <span class="label">Worker</span>
        <span
          v-for="status in WorkerStatus"
          :key="status"
          class="swatch worker"
          :data-worker-status="status"
        >{{ status }}</span>
      </div>
      <div class="group">
        <span class="label">Priority</span>
        <span v-for="p in Priority" :key="p" class="swatch priority" :data-priority="p">{{ p }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.groups { display: flex; gap: 1.5rem; flex-wrap: wrap; }
.group { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.label { font-size: 0.75rem; color: #57606a; text-transform: uppercase; letter-spacing: 0.03em; }
.swatch { font-size: 0.72rem; padding: 0.12rem 0.5rem; border-radius: 10px; color: #fff; }
.swatch.worker[data-worker-status='IDLE'] { background: #1a7f37; }
.swatch.worker[data-worker-status='BUSY'] { background: #bc4c00; }
.swatch.worker[data-worker-status='OFFLINE'] { background: #6e7781; }
.swatch.priority { color: #fff; }
.swatch.priority[data-priority='URGENT'] { background: #b42318; }
.swatch.priority[data-priority='HIGH'] { background: #bc4c00; }
.swatch.priority[data-priority='NORMAL'] { background: #0969da; }
.swatch.priority[data-priority='LOW'] { background: #6e7781; }
</style>
