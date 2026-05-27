<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useWarehouse } from '@/stores/useWarehouse.js';
import WarehouseMap from '@/components/WarehouseMap.vue';
import OrderBoard from '@/components/OrderBoard.vue';
import TaskList from '@/components/TaskList.vue';
import DispatchPanel from '@/components/DispatchPanel.vue';
import EventLog from '@/components/EventLog.vue';
import Legend from '@/components/Legend.vue';

// The console renders entirely from one polled GET /api/state snapshot (README §3.6).
// The dispatch panel emits a dispatcher's choice; App routes it to the matching store
// command, which POSTs, refreshes, and logs the outcome (success or guardrail message).
const store = useWarehouse();
const { stocks, orders, workers, tasks, reachable, loaded, events, startPolling, stopPolling } = store;

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

    <!-- Backend down: a banner, never a blank screen. Last-good panels stay visible below. -->
    <p v-if="!reachable" class="offline-banner" data-testid="offline-banner">
      Backend unreachable — showing the last snapshot. Retrying every poll…
    </p>

    <!-- First load still in flight (and the backend is up): a loading line, no panels yet. -->
    <p v-if="!loaded && reachable" class="loading" data-testid="loading">Loading warehouse state…</p>

    <template v-if="loaded">
      <Legend />
      <WarehouseMap :workers="workers" :stocks="stocks" />
      <OrderBoard :orders="orders" />

      <div class="two-col">
        <TaskList :tasks="tasks" />
        <EventLog :events="events" />
      </div>

      <DispatchPanel
        :orders="orders"
        :workers="workers"
        :tasks="tasks"
        @assign="({ orderId, workerId }) => store.assign(orderId, workerId)"
        @advance-order="({ id, status }) => store.setOrderStatus(id, status)"
        @advance-task="({ id, status }) => store.setTaskStatus(id, status)"
        @free-worker="({ id }) => store.setWorkerStatus(id, 'IDLE')"
        @submit-order="(draft) => store.submitOrder(draft)"
      />
    </template>
  </main>
</template>

<style>
body { margin: 0; font-family: system-ui, sans-serif; background: #f4f5f7; color: #1f2430; }
.console { max-width: 1200px; margin: 0 auto; padding: 1rem; }
.console > section, .console > .two-col { margin-bottom: 1.25rem; }
.console h2 { font-size: 1rem; margin: 0 0 0.5rem; }
header { display: flex; align-items: baseline; gap: 1rem; margin-bottom: 1rem; }
header h1 { font-size: 1.25rem; margin: 0; }
.conn.ok { color: #1a7f37; }
.conn.down { color: #b42318; }
.offline-banner { background: #fff5f5; border: 1px solid #ffcdca; color: #b42318; padding: 0.5rem 0.75rem; border-radius: 6px; }
.loading { color: #57606a; }
.two-col { display: flex; gap: 1rem; align-items: flex-start; }
.two-col > section { flex: 1 1 0; }
</style>
