import { describe, expect, it } from 'vitest';
import { formatCountdown, groupByZone, ORDER_STATUSES } from './format.js';

describe('formatCountdown', () => {
  const now = new Date('2026-05-26T12:00:00Z');

  it('shows an em dash when there is no due time', () => {
    expect(formatCountdown(null, now)).toBe('—');
    expect(formatCountdown(undefined, now)).toBe('—');
  });

  it('reports "overdue" once the deadline has passed', () => {
    expect(formatCountdown('2026-05-26T11:59:00Z', now)).toBe('overdue');
    expect(formatCountdown('2026-05-26T12:00:00Z', now)).toBe('overdue');
  });

  it('shows hours and minutes within a day', () => {
    expect(formatCountdown('2026-05-26T14:00:00Z', now)).toBe('2h 0m');
    expect(formatCountdown('2026-05-26T13:30:00Z', now)).toBe('1h 30m');
  });

  it('shows just minutes under an hour', () => {
    expect(formatCountdown('2026-05-26T12:30:00Z', now)).toBe('30m');
  });

  it('shows days and hours when more than a day out', () => {
    expect(formatCountdown('2026-05-27T14:00:00Z', now)).toBe('1d 2h');
  });

  it('accepts a Date as well as an ISO string', () => {
    expect(formatCountdown(new Date('2026-05-26T14:00:00Z'), now)).toBe('2h 0m');
  });
});

describe('groupByZone', () => {
  it('groups workers by currentZone, zones sorted, preserving worker order', () => {
    const workers = [
      { id: 'WK-2', currentZone: 'ZONE-2' },
      { id: 'WK-1', currentZone: 'ZONE-1' },
      { id: 'WK-3', currentZone: 'ZONE-1' },
    ];

    expect(groupByZone(workers)).toEqual([
      { zone: 'ZONE-1', workers: [{ id: 'WK-1', currentZone: 'ZONE-1' }, { id: 'WK-3', currentZone: 'ZONE-1' }] },
      { zone: 'ZONE-2', workers: [{ id: 'WK-2', currentZone: 'ZONE-2' }] },
    ]);
  });

  it('returns an empty array for no workers', () => {
    expect(groupByZone([])).toEqual([]);
  });
});

describe('ORDER_STATUSES', () => {
  it('lists the lifecycle columns in flow order', () => {
    expect(ORDER_STATUSES).toEqual(['PENDING', 'ASSIGNED', 'PICKING', 'PICKED', 'SHIPPED', 'CANCELLED']);
  });
});
