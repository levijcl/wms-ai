import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import EventLog from './EventLog.vue';

const EVENTS = [
  { id: 2, at: new Date('2026-05-26T12:05:00Z'), kind: 'error', message: '409 IllegalStateException: insufficient stock' },
  { id: 1, at: new Date('2026-05-26T12:00:00Z'), kind: 'success', message: 'Assigned order O1 → worker WK-1' },
];

describe('EventLog', () => {
  it('renders each event message with a kind on the entry', () => {
    const wrapper = mount(EventLog, { props: { events: EVENTS } });

    const entries = wrapper.findAll('[data-kind]');
    expect(entries).toHaveLength(2);
    expect(entries[0].attributes('data-kind')).toBe('error');
    expect(entries[0].text()).toContain('insufficient stock');
    expect(entries[1].attributes('data-kind')).toBe('success');
    expect(entries[1].text()).toContain('Assigned order O1');
  });

  it('renders entries in the order given (store provides newest first)', () => {
    const wrapper = mount(EventLog, { props: { events: EVENTS } });

    const entries = wrapper.findAll('[data-kind]');
    expect(entries[0].text()).toContain('insufficient stock'); // newest, shown first
  });

  it('renders an ai entry (the AI dispatcher trace) with its own kind', () => {
    const wrapper = mount(EventLog, {
      props: { events: [{ id: 3, at: new Date(), kind: 'ai', message: 'AI: chose the URGENT order first.' }] },
    });

    const entry = wrapper.get('[data-kind="ai"]');
    expect(entry.text()).toContain('chose the URGENT order first');
  });

  it('shows an empty-state message when there are no events', () => {
    const wrapper = mount(EventLog, { props: { events: [] } });

    expect(wrapper.findAll('[data-kind]')).toHaveLength(0);
    expect(wrapper.text().toLowerCase()).toContain('no');
  });
});
