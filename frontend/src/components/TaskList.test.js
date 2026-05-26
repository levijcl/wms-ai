import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import TaskList from './TaskList.vue';

const TASKS = [
  { id: 'T1', orderId: 'O1', workerId: 'WK-1', assignedAt: '2026-05-26T12:00:00Z', status: 'ASSIGNED' },
  { id: 'T2', orderId: 'O2', workerId: 'WK-2', assignedAt: '2026-05-26T12:05:00Z', status: 'DONE' },
];

describe('TaskList', () => {
  it('renders a row per task linking its order and worker with the task status', () => {
    const wrapper = mount(TaskList, { props: { tasks: TASKS } });

    const row = wrapper.get('[data-task="T1"]');
    expect(row.text()).toContain('O1');
    expect(row.text()).toContain('WK-1');
    expect(row.attributes('data-status')).toBe('ASSIGNED');
    expect(wrapper.get('[data-task="T2"]').attributes('data-status')).toBe('DONE');
  });

  it('shows an empty-state message when there are no tasks', () => {
    const wrapper = mount(TaskList, { props: { tasks: [] } });

    expect(wrapper.findAll('[data-task]')).toHaveLength(0);
    expect(wrapper.text().toLowerCase()).toContain('no');
  });
});
