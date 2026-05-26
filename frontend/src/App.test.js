import { mount } from '@vue/test-utils';
import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';

// Mock the store so we can assert App routes each DispatchPanel emit to the right command
// (this guards the @assign / @advance-order / @free-worker wiring, the easy place for a
// kebab/camel event-name mismatch to slip through).
const mocks = vi.hoisted(() => ({
  assign: vi.fn(),
  setOrderStatus: vi.fn(),
  setTaskStatus: vi.fn(),
  setWorkerStatus: vi.fn(),
  submitOrder: vi.fn(),
  startPolling: vi.fn(),
  stopPolling: vi.fn(),
}));

vi.mock('@/stores/useWarehouse.js', () => ({
  useWarehouse: () => ({
    stocks: ref([{ sku: 'SKU-1001', quantity: 100, location: 'ZONE-1' }]),
    orders: ref([
      { id: 'O1', customer: 'Acme', items: [], priority: 'URGENT', dueAt: null, status: 'PENDING' },
      { id: 'O2', customer: 'Globex', items: [], priority: 'HIGH', dueAt: null, status: 'PICKING' },
    ]),
    workers: ref([
      { id: 'WK-1', name: 'Alice', currentZone: 'ZONE-1', status: 'IDLE' },
      { id: 'WK-5', name: 'Erin', currentZone: 'ZONE-2', status: 'BUSY' },
    ]),
    tasks: ref([{ id: 'T1', orderId: 'O2', workerId: 'WK-5', assignedAt: null, status: 'ASSIGNED' }]),
    reachable: ref(true),
    events: ref([]),
    ...mocks,
  }),
}));

const App = (await import('./App.vue')).default;

describe('App — dispatch wiring', () => {
  it('starts polling on mount', () => {
    mount(App);
    expect(mocks.startPolling).toHaveBeenCalled();
  });

  it('routes an assign emit to store.assign', async () => {
    const wrapper = mount(App);

    await wrapper.get('[data-testid="assign-order"]').setValue('O1');
    await wrapper.get('[data-testid="assign-worker"]').setValue('WK-1');
    await wrapper.get('[data-testid="assign-btn"]').trigger('click');

    expect(mocks.assign).toHaveBeenCalledWith('O1', 'WK-1');
  });

  it('routes advance-order to store.setOrderStatus with the next status', async () => {
    const wrapper = mount(App);

    await wrapper.get('[data-advance-order="O2"]').trigger('click');

    expect(mocks.setOrderStatus).toHaveBeenCalledWith('O2', 'PICKED');
  });

  it('routes advance-task to store.setTaskStatus', async () => {
    const wrapper = mount(App);

    await wrapper.get('[data-advance-task="T1"]').trigger('click');

    expect(mocks.setTaskStatus).toHaveBeenCalledWith('T1', 'PICKING');
  });

  it('routes free-worker to store.setWorkerStatus(id, IDLE)', async () => {
    const wrapper = mount(App);

    await wrapper.get('[data-free-worker="WK-5"]').trigger('click');

    expect(mocks.setWorkerStatus).toHaveBeenCalledWith('WK-5', 'IDLE');
  });
});
