<script setup>
import { computed, reactive, ref } from 'vue';
import { Priority } from '@/lib/enums.js';

// The human dispatcher's controls (README §3.6, Phase A). The planner makes exactly one
// kind of decision — assign a PENDING order to an IDLE worker — plus inject new orders.
// Everything after assignment (PICKING → PICKED → SHIPPED, task → DONE, freeing the
// worker) is the operator's work, simulated by the floor; it has no controls here. The
// panel never pre-disables a guardrail-rejectable action (an out-of-stock order stays
// assignable) — it lets the call fail and the event log explain why (README §6); the only
// gating is the obvious "can't assign nothing".
const props = defineProps({
  orders: { type: Array, required: true },
  workers: { type: Array, required: true },
});

const emit = defineEmits(['assign', 'submitOrder', 'aiDispatch']);

const pendingOrders = computed(() => props.orders.filter((o) => o.status === 'PENDING'));
const idleWorkers = computed(() => props.workers.filter((w) => w.status === 'IDLE'));

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

    <fieldset class="assign">
      <legend>Assign — the planner's decision</legend>
      <select v-model="assignOrderId" data-testid="assign-order">
        <option value="">— PENDING order —</option>
        <option v-for="o in pendingOrders" :key="o.id" :value="o.id">
          {{ o.customer }} ({{ o.priority }})
        </option>
      </select>
      <select v-model="assignWorkerId" data-testid="assign-worker">
        <option value="">— IDLE worker —</option>
        <option v-for="w in idleWorkers" :key="w.id" :value="w.id">
          {{ w.name }} ({{ w.currentZone }})
        </option>
      </select>
      <button type="button" data-testid="assign-btn" :disabled="!canAssign" @click="submitAssign">
        Assign
      </button>
      <button type="button" class="ai" data-testid="run-ai" @click="emit('aiDispatch')">
        Run AI dispatch
      </button>
      <p class="hint">
        Assign by hand, or let the AI pick the single best order. Either way the floor then
        executes the pick automatically.
      </p>
    </fieldset>

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
fieldset { border: 1px solid #e1e4e8; border-radius: 6px; margin: 0; }
legend { font-size: 0.8rem; color: #57606a; padding: 0 0.3rem; }
select, input, button { font: inherit; padding: 0.3rem 0.4rem; border: 1px solid #d0d7de; border-radius: 6px; }
button { background: #0969da; color: #fff; border-color: #0969da; cursor: pointer; }
button:disabled { background: #b6c2cf; border-color: #b6c2cf; cursor: not-allowed; }
button.link { background: none; border: none; color: #0969da; padding: 0.2rem; cursor: pointer; }
button.ai { background: #6639ba; border-color: #6639ba; }
.hint { font-size: 0.75rem; color: #8c959f; margin: 0.4rem 0 0; }
.new-order { margin-top: 0.75rem; border-top: 1px dashed #e1e4e8; padding-top: 0.6rem; }
.row { display: flex; gap: 0.4rem; margin-bottom: 0.4rem; align-items: center; flex-wrap: wrap; }
.row input { flex: 1 1 auto; }
</style>
