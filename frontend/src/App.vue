<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useWarehouse } from '@/stores/useWarehouse.js';
import WarehouseMap from '@/components/WarehouseMap.vue';
import OrderBoard from '@/components/OrderBoard.vue';
import TaskList from '@/components/TaskList.vue';

// The console renders entirely from one polled GET /api/state snapshot (README §3.6).
// Task 2 wires in the read-only panels; the dispatch panel + event log arrive in Task 3.
const { stocks, orders, workers, tasks, reachable, startPolling, stopPolling } = useWarehouse();

onMounted(() => startPolling());
onUnmounted(() => stopPolling());
</script>

<template>
  <main class="console">
    <header>
      <h1>WMS-AI Dispatch Console</h1>
      <p :class="['conn', reachable ? 'ok' : 'down']">
        {{ reachable ? 'backend connected' : 'backend unreachable' }}
      </p>
    </header>

    <WarehouseMap :workers="workers" :stocks="stocks" />
    <OrderBoard :orders="orders" />
    <TaskList :tasks="tasks" />
  </main>
</template>

<style>
body { margin: 0; font-family: system-ui, sans-serif; background: #f4f5f7; color: #1f2430; }
.console { max-width: 1200px; margin: 0 auto; padding: 1rem; }
.console > section { margin-bottom: 1.25rem; }
.console h2 { font-size: 1rem; margin: 0 0 0.5rem; }
header { display: flex; align-items: baseline; gap: 1rem; margin-bottom: 1rem; }
header h1 { font-size: 1.25rem; margin: 0; }
.conn.ok { color: #1a7f37; }
.conn.down { color: #b42318; }
</style>
