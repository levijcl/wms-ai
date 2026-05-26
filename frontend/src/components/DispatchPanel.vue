<script setup>
import { computed, reactive, ref } from 'vue';
import { Priority } from '@/lib/enums.js';
import { nextOrderStatus, nextTaskStatus } from '@/lib/transitions.js';

// The human dispatcher's controls (README §3.6, Phase A). This panel only gathers a
// choice and emits it; App wires each emit to the store, which POSTs, refreshes, and logs
// the outcome. It deliberately does NOT pre-disable actions that a guardrail might reject
// (e.g. an out-of-stock order stays assignable) — it lets the call fail and the event log
// explain why (README §6). The only gating is the obvious "can't assign nothing".
const props = defineProps({
  orders: { type: Array, required: true },
  workers: { type: Array, required: true },
  tasks: { type: Array, required: true },
});

const emit = defineEmits(['assign', 'advanceOrder', 'advanceTask', 'freeWorker', 'submitOrder']);

const pendingOrders = computed(() => props.orders.filter((o) => o.status === 'PENDING'));
const idleWorkers = computed(() => props.workers.filter((w) => w.status === 'IDLE'));
const busyWorkers = computed(() => props.workers.filter((w) => w.status === 'BUSY'));
const advanceableOrders = computed(() =>
  props.orders.flatMap((o) => {
    const next = nextOrderStatus(o.status);
    return next ? [{ ...o, next }] : [];
  }),
);
const advanceableTasks = computed(() =>
  props.tasks.flatMap((t) => {
    const next = nextTaskStatus(t.status);
    return next ? [{ ...t, next }] : [];
  }),
);

// --- assign ---
const assignOrderId = ref('');
const assignWorkerId = ref('');
const canAssign = computed(() => assignOrderId.value !== '' && assignWorkerId.value !== '');
function submitAssign() {
  emit('assign', { orderId: assignOrderId.value, workerId: assignWorkerId.value });
}

// --- new order form ---
const draft = reactive({
  customer: '',
  priority: 'NORMAL',
  due: '', // datetime-local string
  items: [{ sku: '', quantity: 1 }],
});
function addItem() {
  draft.items.push({ sku: '', quantity: 1 });
}
function removeItem(index) {
  draft.items.splice(index, 1);
}
function submitOrder() {
  emit('submitOrder', {
    customer: draft.customer,
    priority: draft.priority,
    dueAt: draft.due ? new Date(draft.due).toISOString() : null,
    items: draft.items.map((line) => ({ sku: line.sku, quantity: Number(line.quantity) })),
  });
}
</script>

<template>
  <section class="dispatch">
    <h2>Dispatch panel</h2>

    <div class="controls">
      <!-- Assign a PENDING order to an IDLE worker -->
      <fieldset>
        <legend>Assign</legend>
        <select v-model="assignOrderId" data-testid="assign-order">
          <option value="">— order —</option>
          <option v-for="o in pendingOrders" :key="o.id" :value="o.id">
            {{ o.customer }} ({{ o.priority }})
          </option>
        </select>
        <select v-model="assignWorkerId" data-testid="assign-worker">
          <option value="">— worker —</option>
          <option v-for="w in idleWorkers" :key="w.id" :value="w.id">
            {{ w.name }} ({{ w.currentZone }})
          </option>
        </select>
        <button type="button" data-testid="assign-btn" :disabled="!canAssign" @click="submitAssign">
          Assign
        </button>
      </fieldset>

      <!-- Advance lifecycle: orders, tasks, free workers -->
      <fieldset>
        <legend>Advance</legend>
        <div class="advance-group">
          <button
            v-for="o in advanceableOrders"
            :key="o.id"
            type="button"
            :data-advance-order="o.id"
            @click="emit('advanceOrder', { id: o.id, status: o.next })"
          >
            {{ o.customer }}: {{ o.status }} → {{ o.next }}
          </button>
        </div>
        <div class="advance-group">
          <button
            v-for="t in advanceableTasks"
            :key="t.id"
            type="button"
            :data-advance-task="t.id"
            @click="emit('advanceTask', { id: t.id, status: t.next })"
          >
            task {{ t.orderId }}→{{ t.workerId }}: {{ t.status }} → {{ t.next }}
          </button>
        </div>
        <div class="advance-group">
          <button
            v-for="w in busyWorkers"
            :key="w.id"
            type="button"
            :data-free-worker="w.id"
            @click="emit('freeWorker', { id: w.id })"
          >
            Free {{ w.name }} → IDLE
          </button>
        </div>
      </fieldset>
    </div>

    <!-- Inject a new order -->
    <form class="new-order" data-testid="submit-order" @submit.prevent="submitOrder">
      <legend>New order</legend>
      <div class="row">
        <input v-model="draft.customer" data-testid="new-customer" placeholder="customer" required />
        <select v-model="draft.priority" data-testid="new-priority">
          <option v-for="p in Priority" :key="p" :value="p">{{ p }}</option>
        </select>
        <input v-model="draft.due" type="datetime-local" data-testid="new-due" />
      </div>
      <div v-for="(item, i) in draft.items" :key="i" class="row item">
        <input v-model="item.sku" :data-testid="`item-sku-${i}`" placeholder="SKU" required />
        <input v-model.number="item.quantity" type="number" min="1" :data-testid="`item-qty-${i}`" />
        <button v-if="draft.items.length > 1" type="button" class="link" @click="removeItem(i)">remove</button>
      </div>
      <div class="row">
        <button type="button" class="link" data-testid="add-item" @click="addItem">+ item</button>
        <button type="submit">Submit order</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.dispatch { background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; padding: 0.75rem; }
.controls { display: flex; gap: 1rem; flex-wrap: wrap; }
fieldset { border: 1px solid #e1e4e8; border-radius: 6px; flex: 1 1 280px; }
legend { font-size: 0.8rem; color: #57606a; padding: 0 0.3rem; }
select, input, button { font: inherit; padding: 0.3rem 0.4rem; border: 1px solid #d0d7de; border-radius: 6px; }
button { background: #0969da; color: #fff; border-color: #0969da; cursor: pointer; }
button:disabled { background: #b6c2cf; border-color: #b6c2cf; cursor: not-allowed; }
button.link { background: none; border: none; color: #0969da; padding: 0.2rem; cursor: pointer; }
.advance-group { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 0.4rem; }
.advance-group button { background: #57606a; border-color: #57606a; font-size: 0.78rem; }
.new-order { margin-top: 0.75rem; border-top: 1px dashed #e1e4e8; padding-top: 0.6rem; }
.row { display: flex; gap: 0.4rem; margin-bottom: 0.4rem; align-items: center; flex-wrap: wrap; }
.row input { flex: 1 1 auto; }
</style>
