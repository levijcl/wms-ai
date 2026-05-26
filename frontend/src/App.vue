<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useWarehouse } from '@/stores/useWarehouse.js';

// Task 1 scaffold: boot the store, poll GET /api/state, and show a raw snapshot so the
// data path is verifiable. The map / board / task list / dispatch panel / event log
// panels replace this dump in Tasks 2–3.
const store = useWarehouse();

onMounted(() => store.startPolling());
onUnmounted(() => store.stopPolling());
</script>

<template>
  <main class="console">
    <header>
      <h1>WMS-AI Dispatch Console</h1>
      <p :class="['conn', store.reachable.value ? 'ok' : 'down']">
        {{ store.reachable.value ? 'backend connected' : 'backend unreachable' }}
      </p>
    </header>

    <section class="snapshot">
      <p>{{ store.stocks.value.length }} stocks · {{ store.orders.value.length }} orders ·
        {{ store.workers.value.length }} workers · {{ store.tasks.value.length }} tasks</p>
      <pre>{{ JSON.stringify({
        stocks: store.stocks.value,
        orders: store.orders.value,
        workers: store.workers.value,
        tasks: store.tasks.value,
      }, null, 2) }}</pre>
    </section>
  </main>
</template>

<style>
body { margin: 0; font-family: system-ui, sans-serif; background: #f4f5f7; color: #1f2430; }
.console { max-width: 1100px; margin: 0 auto; padding: 1rem; }
header { display: flex; align-items: baseline; gap: 1rem; }
.conn.ok { color: #1a7f37; }
.conn.down { color: #b42318; }
.snapshot pre { background: #fff; border: 1px solid #e1e4e8; border-radius: 6px; padding: 0.75rem; overflow: auto; }
</style>
