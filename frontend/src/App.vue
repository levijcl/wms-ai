<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useWarehouse } from '@/stores/useWarehouse.js';
import WarehouseMap from '@/components/WarehouseMap.vue';
import OrderBoard from '@/components/OrderBoard.vue';
import TaskList from '@/components/TaskList.vue';
import DispatchPanel from '@/components/DispatchPanel.vue';
import EventLog from '@/components/EventLog.vue';

// The console renders entirely from one polled GET /api/state snapshot (README §3.6).
// The dispatch panel emits a dispatcher's choice; App routes it to the matching store
// command, which POSTs, refreshes, and logs the outcome (success or guardrail message).
const store = useWarehouse();
const { stocks, orders, workers, tasks, reachable, events, startPolling, stopPolling } = store;

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
.two-col { display: flex; gap: 1rem; align-items: flex-start; }
.two-col > section { flex: 1 1 0; }
</style>
