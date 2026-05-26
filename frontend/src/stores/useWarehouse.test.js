import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createWarehouseStore, POLL_INTERVAL_MS } from './useWarehouse.js';

const SNAPSHOT = {
  stocks: [{ sku: 'SKU-1001', quantity: 100, location: 'ZONE-1' }],
  orders: [{ id: 'O1', customer: 'Acme', items: [], priority: 'URGENT', dueAt: null, status: 'PENDING' }],
  workers: [{ id: 'WK-1', name: 'Alice', currentZone: 'ZONE-1', status: 'IDLE' }],
  tasks: [],
};

/** A fake client whose every method is a spy resolving to a success result by default. */
function fakeClient(overrides = {}) {
  return {
    getState: vi.fn().mockResolvedValue({ ok: true, status: 200, data: SNAPSHOT }),
    assign: vi.fn().mockResolvedValue({ ok: true, status: 200, data: { orderId: 'O1', workerId: 'WK-1' } }),
    submitOrder: vi.fn().mockResolvedValue({ ok: true, status: 200, data: { id: 'O9', status: 'PENDING' } }),
    setOrderStatus: vi.fn().mockResolvedValue({ ok: true, status: 200, data: { id: 'O1', status: 'PICKING' } }),
    setTaskStatus: vi.fn().mockResolvedValue({ ok: true, status: 200, data: { id: 'T1', status: 'DONE' } }),
    setWorkerStatus: vi.fn().mockResolvedValue({ ok: true, status: 200, data: { id: 'WK-1', status: 'IDLE' } }),
    releaseStock: vi.fn().mockResolvedValue({ ok: true, status: 200, data: null }),
    ...overrides,
  };
}

describe('useWarehouse store', () => {
  it('starts with empty lists and an empty event log', () => {
    const store = createWarehouseStore(fakeClient());

    expect(store.stocks.value).toEqual([]);
    expect(store.orders.value).toEqual([]);
    expect(store.workers.value).toEqual([]);
    expect(store.tasks.value).toEqual([]);
    expect(store.events.value).toEqual([]);
  });

  describe('refresh', () => {
    it('populates the four lists from getState and marks the backend reachable', async () => {
      const store = createWarehouseStore(fakeClient());

      await store.refresh();

      expect(store.stocks.value).toEqual(SNAPSHOT.stocks);
      expect(store.orders.value).toEqual(SNAPSHOT.orders);
      expect(store.workers.value).toEqual(SNAPSHOT.workers);
      expect(store.tasks.value).toEqual(SNAPSHOT.tasks);
      expect(store.reachable.value).toBe(true);
    });

    it('marks the backend unreachable without wiping the last good snapshot', async () => {
      const client = fakeClient();
      const store = createWarehouseStore(client);
      await store.refresh(); // seed a good snapshot

      client.getState.mockResolvedValueOnce({ ok: false, status: 0, error: 'NetworkError', message: 'down' });
      await store.refresh();

      expect(store.reachable.value).toBe(false);
      expect(store.orders.value).toEqual(SNAPSHOT.orders); // last good data preserved
    });
  });

  describe('polling', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('refreshes immediately on start and then on each interval tick', async () => {
      const client = fakeClient();
      const store = createWarehouseStore(client);

      store.startPolling();
      expect(client.getState).toHaveBeenCalledTimes(1); // immediate

      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
      expect(client.getState).toHaveBeenCalledTimes(2);

      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
      expect(client.getState).toHaveBeenCalledTimes(3);
    });

    it('stops refreshing after stopPolling', async () => {
      const client = fakeClient();
      const store = createWarehouseStore(client);

      store.startPolling();
      store.stopPolling();
      const callsAtStop = client.getState.mock.calls.length;

      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);
      expect(client.getState).toHaveBeenCalledTimes(callsAtStop);
    });
  });

  describe('commands append to the event log and then refresh', () => {
    it('assign: logs a success entry and re-reads state (command-then-refresh)', async () => {
      const client = fakeClient();
      const store = createWarehouseStore(client);

      const result = await store.assign('O1', 'WK-1');

      expect(client.assign).toHaveBeenCalledWith('O1', 'WK-1');
      expect(client.getState).toHaveBeenCalled(); // refreshed after the command
      expect(result.ok).toBe(true);
      expect(store.events.value).toHaveLength(1);
      expect(store.events.value[0].kind).toBe('success');
      expect(store.events.value[0].message).toContain('O1');
      expect(store.events.value[0].message).toContain('WK-1');
    });

    it('assign: a 409 guardrail rejection logs the backend message verbatim as an error', async () => {
      const client = fakeClient({
        assign: vi.fn().mockResolvedValue({
          ok: false,
          status: 409,
          error: 'IllegalStateException',
          message: 'insufficient stock for SKU-2002',
        }),
      });
      const store = createWarehouseStore(client);

      const result = await store.assign('SEED-ORD-4', 'WK-1');

      expect(result.ok).toBe(false);
      expect(store.events.value[0].kind).toBe('error');
      expect(store.events.value[0].message).toContain('insufficient stock for SKU-2002');
      expect(store.events.value[0].message).toContain('409');
    });

    it('newest event is first in the log', async () => {
      const store = createWarehouseStore(fakeClient());

      await store.setWorkerStatus('WK-1', 'IDLE');
      await store.assign('O1', 'WK-1');

      expect(store.events.value).toHaveLength(2);
      expect(store.events.value[0].message).toContain('O1'); // the assign, logged last, shown first
    });

    it('submitOrder logs a success entry and refreshes', async () => {
      const client = fakeClient();
      const store = createWarehouseStore(client);

      await store.submitOrder({ customer: 'Acme', items: [], priority: 'HIGH', dueAt: null });

      expect(client.submitOrder).toHaveBeenCalled();
      expect(client.getState).toHaveBeenCalled();
      expect(store.events.value[0].kind).toBe('success');
    });
  });
});
