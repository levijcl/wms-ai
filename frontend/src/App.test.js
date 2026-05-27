import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// A single mutable fake store the mock returns. App reads its fields at mount, so each
// test sets the fields it cares about (loaded / reachable / lists) before mounting.
// Plain values (not refs) are fine: App renders them statically in these tests.
const store = vi.hoisted(() => ({
  stocks: [],
  orders: [],
  workers: [],
  tasks: [],
  reachable: true,
  loaded: true,
  events: [],
  assign: vi.fn(),
  setOrderStatus: vi.fn(),
  setTaskStatus: vi.fn(),
  setWorkerStatus: vi.fn(),
  submitOrder: vi.fn(),
  startPolling: vi.fn(),
  stopPolling: vi.fn(),
}));

vi.mock('@/stores/useWarehouse.js', () => ({ useWarehouse: () => store }));

const App = (await import('./App.vue')).default;

beforeEach(() => {
  Object.assign(store, {
    stocks: [{ sku: 'SKU-1001', quantity: 100, location: 'ZONE-1' }],
    orders: [
      { id: 'O1', customer: 'Acme', items: [], priority: 'URGENT', dueAt: null, status: 'PENDING' },
      { id: 'O2', customer: 'Globex', items: [], priority: 'HIGH', dueAt: null, status: 'PICKING' },
    ],
    workers: [
      { id: 'WK-1', name: 'Alice', currentZone: 'ZONE-1', status: 'IDLE' },
      { id: 'WK-5', name: 'Erin', currentZone: 'ZONE-2', status: 'BUSY' },
    ],
    tasks: [{ id: 'T1', orderId: 'O2', workerId: 'WK-5', assignedAt: null, status: 'ASSIGNED' }],
    reachable: true,
    loaded: true,
    events: [],
  });
  vi.clearAllMocks();
});

describe('App — dispatch wiring', () => {
  it('starts polling on mount', () => {
    mount(App);
    expect(store.startPolling).toHaveBeenCalled();
  });

  it('routes an assign emit to store.assign', async () => {
    const wrapper = mount(App);
    await wrapper.get('[data-testid="assign-order"]').setValue('O1');
    await wrapper.get('[data-testid="assign-worker"]').setValue('WK-1');
    await wrapper.get('[data-testid="assign-btn"]').trigger('click');
    expect(store.assign).toHaveBeenCalledWith('O1', 'WK-1');
  });

  it('routes a submit-order emit to store.submitOrder', async () => {
    const wrapper = mount(App);
    await wrapper.get('[data-testid="new-customer"]').setValue('Wayne Ent');
    await wrapper.get('[data-testid="item-sku-0"]').setValue('SKU-1001');
    await wrapper.get('[data-testid="submit-order"]').trigger('submit');
    expect(store.submitOrder).toHaveBeenCalledTimes(1);
    expect(store.submitOrder.mock.calls[0][0].customer).toBe('Wayne Ent');
  });
});

describe('App — connection and load states', () => {
  it('shows the panels and the legend once loaded and reachable', () => {
    const wrapper = mount(App);

    expect(wrapper.find('[data-testid="loading"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="offline-banner"]').exists()).toBe(false);
    expect(wrapper.find('.legend').exists()).toBe(true);
    expect(wrapper.find('[data-status="PENDING"]').exists()).toBe(true); // board rendered
  });

  it('shows a loading state before the first snapshot arrives', () => {
    store.loaded = false;
    const wrapper = mount(App);

    expect(wrapper.find('[data-testid="loading"]').exists()).toBe(true);
    expect(wrapper.find('[data-status="PENDING"]').exists()).toBe(false); // no panels yet
  });

  it('shows an unreachable banner when the backend is down, without crashing', () => {
    store.reachable = false;
    const wrapper = mount(App);

    expect(wrapper.find('[data-testid="offline-banner"]').exists()).toBe(true);
  });

  it('keeps showing the last-good panels under the banner when the backend drops after a load', () => {
    store.reachable = false;
    store.loaded = true; // we had data before the backend dropped
    const wrapper = mount(App);

    expect(wrapper.find('[data-testid="offline-banner"]').exists()).toBe(true);
    expect(wrapper.find('[data-status="PENDING"]').exists()).toBe(true); // stale data still visible
  });
});
