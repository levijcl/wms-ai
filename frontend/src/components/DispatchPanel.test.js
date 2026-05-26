import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DispatchPanel from './DispatchPanel.vue';

const ORDERS = [
  { id: 'O1', customer: 'Acme', items: [], priority: 'URGENT', dueAt: null, status: 'PENDING' },
  { id: 'O2', customer: 'Globex', items: [], priority: 'HIGH', dueAt: null, status: 'PICKING' },
  { id: 'O3', customer: 'Initech', items: [], priority: 'LOW', dueAt: null, status: 'SHIPPED' },
];
const WORKERS = [
  { id: 'WK-1', name: 'Alice', currentZone: 'ZONE-1', status: 'IDLE' },
  { id: 'WK-5', name: 'Erin', currentZone: 'ZONE-2', status: 'BUSY' },
];
const TASKS = [
  { id: 'T1', orderId: 'O2', workerId: 'WK-5', assignedAt: null, status: 'ASSIGNED' },
  { id: 'T2', orderId: 'O9', workerId: 'WK-1', assignedAt: null, status: 'DONE' },
];

function panel() {
  return mount(DispatchPanel, { props: { orders: ORDERS, workers: WORKERS, tasks: TASKS } });
}

describe('DispatchPanel — assign', () => {
  it('lists only PENDING orders and only IDLE workers as assign options', () => {
    const wrapper = panel();

    const orderOpts = wrapper.get('[data-testid="assign-order"]').findAll('option[value]:not([value=""])');
    const workerOpts = wrapper.get('[data-testid="assign-worker"]').findAll('option[value]:not([value=""])');

    expect(orderOpts.map((o) => o.attributes('value'))).toEqual(['O1']); // O2/O3 not PENDING
    expect(workerOpts.map((o) => o.attributes('value'))).toEqual(['WK-1']); // WK-5 is BUSY
  });

  it('emits assign with the chosen order and worker', async () => {
    const wrapper = panel();

    await wrapper.get('[data-testid="assign-order"]').setValue('O1');
    await wrapper.get('[data-testid="assign-worker"]').setValue('WK-1');
    await wrapper.get('[data-testid="assign-btn"]').trigger('click');

    expect(wrapper.emitted('assign')).toBeTruthy();
    expect(wrapper.emitted('assign')[0][0]).toEqual({ orderId: 'O1', workerId: 'WK-1' });
  });
});

describe('DispatchPanel — advance', () => {
  it('emits advanceOrder with the next legal status for a non-terminal order', async () => {
    const wrapper = panel();

    await wrapper.get('[data-advance-order="O2"]').trigger('click'); // PICKING → PICKED
    expect(wrapper.emitted('advanceOrder')[0][0]).toEqual({ id: 'O2', status: 'PICKED' });
  });

  it('offers no advance button for a terminal (SHIPPED) order', () => {
    const wrapper = panel();
    expect(wrapper.find('[data-advance-order="O3"]').exists()).toBe(false);
  });

  it('emits advanceTask for a non-terminal task only', async () => {
    const wrapper = panel();

    await wrapper.get('[data-advance-task="T1"]').trigger('click'); // ASSIGNED → PICKING
    expect(wrapper.emitted('advanceTask')[0][0]).toEqual({ id: 'T1', status: 'PICKING' });
    expect(wrapper.find('[data-advance-task="T2"]').exists()).toBe(false); // DONE is terminal
  });

  it('emits freeWorker for a BUSY worker', async () => {
    const wrapper = panel();

    await wrapper.get('[data-free-worker="WK-5"]').trigger('click');
    expect(wrapper.emitted('freeWorker')[0][0]).toEqual({ id: 'WK-5' });
    expect(wrapper.find('[data-free-worker="WK-1"]').exists()).toBe(false); // already IDLE
  });
});

describe('DispatchPanel — submit order', () => {
  it('emits submitOrder with a NewOrder draft assembled from the form', async () => {
    const wrapper = panel();

    await wrapper.get('[data-testid="new-customer"]').setValue('Wayne Ent');
    await wrapper.get('[data-testid="new-priority"]').setValue('HIGH');
    await wrapper.get('[data-testid="new-due"]').setValue('2026-06-01T10:00');
    await wrapper.get('[data-testid="item-sku-0"]').setValue('SKU-1001');
    await wrapper.get('[data-testid="item-qty-0"]').setValue('2');
    await wrapper.get('[data-testid="submit-order"]').trigger('submit');

    const draft = wrapper.emitted('submitOrder')[0][0];
    expect(draft.customer).toBe('Wayne Ent');
    expect(draft.priority).toBe('HIGH');
    expect(draft.items).toEqual([{ sku: 'SKU-1001', quantity: 2 }]);
    expect(draft.dueAt).toBe(new Date('2026-06-01T10:00').toISOString());
  });

  it('supports adding a second item line', async () => {
    const wrapper = panel();

    await wrapper.get('[data-testid="add-item"]').trigger('click');
    await wrapper.get('[data-testid="new-customer"]').setValue('Stark');
    await wrapper.get('[data-testid="item-sku-0"]').setValue('SKU-1001');
    await wrapper.get('[data-testid="item-qty-0"]').setValue('1');
    await wrapper.get('[data-testid="item-sku-1"]').setValue('SKU-3001');
    await wrapper.get('[data-testid="item-qty-1"]').setValue('3');
    await wrapper.get('[data-testid="submit-order"]').trigger('submit');

    const draft = wrapper.emitted('submitOrder')[0][0];
    expect(draft.items).toEqual([
      { sku: 'SKU-1001', quantity: 1 },
      { sku: 'SKU-3001', quantity: 3 },
    ]);
  });
});
