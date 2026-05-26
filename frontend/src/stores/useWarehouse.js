import { ref } from 'vue';
import { apiClient } from '@/api/client.js';

// Single source of truth for the console: one polled `GET /api/state` snapshot feeds
// every panel (README §3.6, §7). Commands POST and then immediately re-read state
// (command-then-refresh) so the UI never shows an optimistic change a guardrail
// rejected. Every command's outcome — success or the verbatim backend {error,message}
// — is appended to an append-only event log (the seam Phase B's AI trace renders into).

export const POLL_INTERVAL_MS = 1500;

let nextEventId = 0;

/**
 * Build a warehouse store backed by `client`. The default singleton uses the real API
 * client; tests inject a fake. Returns refs the components render from plus the
 * command/polling methods that drive the system.
 */
export function createWarehouseStore(client = apiClient) {
  const stocks = ref([]);
  const orders = ref([]);
  const workers = ref([]);
  const tasks = ref([]);
  const reachable = ref(true);
  const events = ref([]);

  let timer = null;

  function logEvent(kind, message) {
    events.value.unshift({ id: nextEventId++, at: new Date(), kind, message });
  }

  /** Re-read the whole snapshot. On a network failure, flag unreachable but keep the last good data. */
  async function refresh() {
    const result = await client.getState();
    if (result.ok) {
      stocks.value = result.data.stocks ?? [];
      orders.value = result.data.orders ?? [];
      workers.value = result.data.workers ?? [];
      tasks.value = result.data.tasks ?? [];
      reachable.value = true;
    } else if (result.status === 0) {
      reachable.value = false;
    }
    return result;
  }

  /**
   * Run a command, log its outcome, then refresh so the panels reflect the new state.
   * `describe` turns a successful result into a human log line; a failure logs the
   * backend message verbatim alongside the status code (README §6).
   */
  async function command(call, describe) {
    const result = await call();
    if (result.ok) {
      logEvent('success', describe(result));
    } else {
      logEvent('error', `${result.status} ${result.error}: ${result.message}`);
    }
    await refresh();
    return result;
  }

  function startPolling() {
    if (timer !== null) {
      return;
    }
    refresh();
    timer = setInterval(refresh, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  return {
    stocks,
    orders,
    workers,
    tasks,
    reachable,
    events,
    refresh,
    startPolling,
    stopPolling,
    assign: (orderId, workerId) =>
      command(() => client.assign(orderId, workerId), () => `Assigned order ${orderId} → worker ${workerId}`),
    submitOrder: (draft) =>
      command(() => client.submitOrder(draft), (r) => `Submitted order ${r.data?.id ?? ''} for ${draft.customer}`),
    setOrderStatus: (id, status) =>
      command(() => client.setOrderStatus(id, status), () => `Order ${id} → ${status}`),
    setTaskStatus: (id, status) =>
      command(() => client.setTaskStatus(id, status), () => `Task ${id} → ${status}`),
    setWorkerStatus: (id, status) =>
      command(() => client.setWorkerStatus(id, status), () => `Worker ${id} → ${status}`),
    releaseStock: (sku, quantity) =>
      command(() => client.releaseStock(sku, quantity), () => `Released ${quantity} × ${sku}`),
  };
}

let singleton = null;

/** The shared store instance every component renders from (created lazily on first use). */
export function useWarehouse() {
  if (singleton === null) {
    singleton = createWarehouseStore();
  }
  return singleton;
}
