import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  assign,
  getState,
  releaseStock,
  setOrderStatus,
  setTaskStatus,
  setWorkerStatus,
  submitOrder,
} from './client.js';

/** Build a fake Response-like object the client's fetch wrapper understands. */
function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

describe('api/client', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('getState', () => {
    it('GETs /api/state and resolves the four-list snapshot as data', async () => {
      const snapshot = { stocks: [], orders: [], workers: [], tasks: [] };
      fetch.mockResolvedValue(jsonResponse(200, snapshot));

      const result = await getState();

      expect(fetch).toHaveBeenCalledWith('/api/state', expect.objectContaining({ method: 'GET' }));
      expect(result).toEqual({ ok: true, status: 200, data: snapshot });
    });

    it('resolves (does not throw) with {status,error,message} on a non-2xx response', async () => {
      fetch.mockResolvedValue(
        jsonResponse(409, { error: 'IllegalStateException', message: 'insufficient stock' }),
      );

      const result = await getState();

      expect(result).toEqual({
        ok: false,
        status: 409,
        error: 'IllegalStateException',
        message: 'insufficient stock',
      });
    });

    it('reports unreachable (status 0) when fetch itself rejects', async () => {
      fetch.mockRejectedValue(new TypeError('Failed to fetch'));

      const result = await getState();

      expect(result.ok).toBe(false);
      expect(result.status).toBe(0);
      expect(result.message).toMatch(/Failed to fetch/);
    });
  });

  describe('commands', () => {
    it('assign POSTs {orderId, workerId} to /api/dispatch/assign as JSON', async () => {
      const dispatchResult = { task: { id: 'T1' }, orderId: 'O1', workerId: 'WK-1' };
      fetch.mockResolvedValue(jsonResponse(200, dispatchResult));

      const result = await assign('O1', 'WK-1');

      expect(fetch).toHaveBeenCalledWith('/api/dispatch/assign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: 'O1', workerId: 'WK-1' }),
      });
      expect(result).toEqual({ ok: true, status: 200, data: dispatchResult });
    });

    it('submitOrder POSTs the NewOrder draft to /api/orders', async () => {
      const draft = { customer: 'Acme', items: [{ sku: 'SKU-1001', quantity: 2 }], priority: 'HIGH', dueAt: '2026-05-26T12:00:00Z' };
      fetch.mockResolvedValue(jsonResponse(200, { id: 'O9', ...draft, status: 'PENDING' }));

      await submitOrder(draft);

      expect(fetch).toHaveBeenCalledWith('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(draft),
      });
    });

    it('setOrderStatus POSTs {status} to /api/orders/{id}/status', async () => {
      fetch.mockResolvedValue(jsonResponse(200, { id: 'O1', status: 'PICKING' }));

      await setOrderStatus('O1', 'PICKING');

      expect(fetch).toHaveBeenCalledWith('/api/orders/O1/status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'PICKING' }),
      });
    });

    it('setTaskStatus POSTs {status} to /api/tasks/{id}/status', async () => {
      fetch.mockResolvedValue(jsonResponse(200, { id: 'T1', status: 'DONE' }));

      await setTaskStatus('T1', 'DONE');

      expect(fetch).toHaveBeenCalledWith('/api/tasks/T1/status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'DONE' }),
      });
    });

    it('setWorkerStatus POSTs {status} to /api/workers/{id}/status', async () => {
      fetch.mockResolvedValue(jsonResponse(200, { id: 'WK-1', status: 'IDLE' }));

      await setWorkerStatus('WK-1', 'IDLE');

      expect(fetch).toHaveBeenCalledWith('/api/workers/WK-1/status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'IDLE' }),
      });
    });

    it('releaseStock POSTs {sku, quantity} to /api/inventory/release', async () => {
      fetch.mockResolvedValue(jsonResponse(200, null));

      await releaseStock('SKU-2002', 3);

      expect(fetch).toHaveBeenCalledWith('/api/inventory/release', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sku: 'SKU-2002', quantity: 3 }),
      });
    });

    it('surfaces a 400 as a resolved error result the caller can log verbatim', async () => {
      fetch.mockResolvedValue(
        jsonResponse(400, { error: 'IllegalArgumentException', message: 'unknown order id' }),
      );

      const result = await assign('nope', 'WK-1');

      expect(result).toEqual({
        ok: false,
        status: 400,
        error: 'IllegalArgumentException',
        message: 'unknown order id',
      });
    });
  });
});
