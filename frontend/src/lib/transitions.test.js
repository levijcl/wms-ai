import { describe, expect, it } from 'vitest';
import { nextOrderStatus, nextTaskStatus } from './transitions.js';

describe('nextOrderStatus', () => {
  it('advances along the picking path', () => {
    expect(nextOrderStatus('ASSIGNED')).toBe('PICKING');
    expect(nextOrderStatus('PICKING')).toBe('PICKED');
    expect(nextOrderStatus('PICKED')).toBe('SHIPPED');
  });

  it('has no advance button for PENDING (assignment handles it) or terminal states', () => {
    expect(nextOrderStatus('PENDING')).toBeNull();
    expect(nextOrderStatus('SHIPPED')).toBeNull();
    expect(nextOrderStatus('CANCELLED')).toBeNull();
  });
});

describe('nextTaskStatus', () => {
  it('advances ASSIGNED → PICKING → DONE', () => {
    expect(nextTaskStatus('ASSIGNED')).toBe('PICKING');
    expect(nextTaskStatus('PICKING')).toBe('DONE');
  });

  it('has no advance button for terminal states', () => {
    expect(nextTaskStatus('DONE')).toBeNull();
    expect(nextTaskStatus('CANCELLED')).toBeNull();
  });
});
