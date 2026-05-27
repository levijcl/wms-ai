import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import Legend from './Legend.vue';

describe('Legend', () => {
  it('explains every worker status with a color swatch', () => {
    const wrapper = mount(Legend);

    for (const status of ['IDLE', 'BUSY', 'OFFLINE']) {
      const swatch = wrapper.get(`[data-worker-status="${status}"]`);
      expect(swatch.text()).toContain(status);
    }
  });

  it('explains every priority level', () => {
    const wrapper = mount(Legend);

    for (const priority of ['LOW', 'NORMAL', 'HIGH', 'URGENT']) {
      expect(wrapper.get(`[data-priority="${priority}"]`).text()).toContain(priority);
    }
  });
});
