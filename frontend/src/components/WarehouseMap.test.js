import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import WarehouseMap from './WarehouseMap.vue';

const WORKERS = [
  { id: 'WK-1', name: 'Alice', currentZone: 'ZONE-1', status: 'IDLE' },
  { id: 'WK-3', name: 'Carol', currentZone: 'ZONE-1', status: 'IDLE' },
  { id: 'WK-5', name: 'Erin', currentZone: 'ZONE-2', status: 'BUSY' },
  { id: 'WK-6', name: 'Frank', currentZone: 'ZONE-3', status: 'OFFLINE' },
];
const STOCKS = [
  { sku: 'SKU-1001', quantity: 100, location: 'ZONE-1' },
  { sku: 'SKU-2002', quantity: 0, location: 'ZONE-2' },
];

function zoneSection(wrapper, zone) {
  return wrapper.get(`[data-zone="${zone}"]`);
}

describe('WarehouseMap', () => {
  it('renders one section per zone present in workers or stock', () => {
    const wrapper = mount(WarehouseMap, { props: { workers: WORKERS, stocks: STOCKS } });

    expect(wrapper.findAll('[data-zone]')).toHaveLength(3); // ZONE-1, ZONE-2, ZONE-3
    expect(zoneSection(wrapper, 'ZONE-1').exists()).toBe(true);
  });

  it('groups workers under their zone', () => {
    const wrapper = mount(WarehouseMap, { props: { workers: WORKERS, stocks: STOCKS } });

    const zone1 = zoneSection(wrapper, 'ZONE-1');
    expect(zone1.text()).toContain('Alice');
    expect(zone1.text()).toContain('Carol');
    expect(zone1.text()).not.toContain('Erin');
  });

  it('colors each worker card by status via a data-status attribute', () => {
    const wrapper = mount(WarehouseMap, { props: { workers: WORKERS, stocks: STOCKS } });

    const alice = wrapper.get('[data-worker="WK-1"]');
    expect(alice.attributes('data-status')).toBe('IDLE');
    expect(wrapper.get('[data-worker="WK-5"]').attributes('data-status')).toBe('BUSY');
    expect(wrapper.get('[data-worker="WK-6"]').attributes('data-status')).toBe('OFFLINE');
  });

  it('shows stock per SKU inside its zone', () => {
    const wrapper = mount(WarehouseMap, { props: { workers: WORKERS, stocks: STOCKS } });

    const zone1 = zoneSection(wrapper, 'ZONE-1');
    expect(zone1.text()).toContain('SKU-1001');
    expect(zone1.text()).toContain('100');

    const zone2 = zoneSection(wrapper, 'ZONE-2');
    expect(zone2.text()).toContain('SKU-2002');
  });
});
