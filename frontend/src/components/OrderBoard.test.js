import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import OrderBoard from './OrderBoard.vue';

const ORDERS = [
  {
    id: 'O1',
    customer: 'Acme',
    items: [{ sku: 'SKU-1001', quantity: 2 }],
    priority: 'URGENT',
    dueAt: null,
    status: 'PENDING',
  },
  {
    id: 'O2',
    customer: 'Globex',
    items: [{ sku: 'SKU-2001', quantity: 5 }],
    priority: 'NORMAL',
    dueAt: null,
    status: 'SHIPPED',
  },
];

function column(wrapper, status) {
  return wrapper.get(`[data-status="${status}"]`);
}

describe('OrderBoard', () => {
  it('renders a column for every lifecycle status', () => {
    const wrapper = mount(OrderBoard, { props: { orders: ORDERS } });

    expect(wrapper.findAll('[data-status]')).toHaveLength(6);
    for (const status of ['PENDING', 'ASSIGNED', 'PICKING', 'PICKED', 'SHIPPED', 'CANCELLED']) {
      expect(column(wrapper, status).exists()).toBe(true);
    }
  });

  it('places each order card in the column matching its status', () => {
    const wrapper = mount(OrderBoard, { props: { orders: ORDERS } });

    expect(column(wrapper, 'PENDING').text()).toContain('Acme');
    expect(column(wrapper, 'PENDING').text()).not.toContain('Globex');
    expect(column(wrapper, 'SHIPPED').text()).toContain('Globex');
  });

  it('shows the customer, priority, item lines, and a due countdown on a card', () => {
    const wrapper = mount(OrderBoard, { props: { orders: ORDERS } });

    const card = wrapper.get('[data-order="O1"]');
    expect(card.text()).toContain('Acme');
    expect(card.attributes('data-priority')).toBe('URGENT');
    expect(card.text()).toContain('SKU-1001');
    expect(card.text()).toContain('2'); // quantity
    expect(card.get('[data-testid="countdown"]').text()).toBe('—'); // null dueAt
  });

  it('renders a future dueAt as a non-overdue countdown, not an em dash', () => {
    const future = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString();
    const wrapper = mount(OrderBoard, {
      props: { orders: [{ ...ORDERS[0], dueAt: future }] },
    });

    const countdown = wrapper.get('[data-order="O1"] [data-testid="countdown"]').text();
    expect(countdown).not.toBe('—');
    expect(countdown).not.toBe('overdue');
    expect(countdown).toMatch(/h/);
  });
});
