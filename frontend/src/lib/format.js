// Pure display helpers shared by the visualization panels. Kept framework-free so the
// formatting rules (countdowns, zone grouping, column order) are unit-tested directly.

/** The order-board columns, in lifecycle flow order (README §3.2, §7). */
export const ORDER_STATUSES = ['PENDING', 'ASSIGNED', 'PICKING', 'PICKED', 'SHIPPED', 'CANCELLED'];

/**
 * Human countdown to a `dueAt` deadline, derived from the server's ISO-8601 timestamp.
 *   no due time → "—"   ·   past → "overdue"   ·   "Xd Yh" / "Xh Ym" / "Xm" otherwise.
 */
export function formatCountdown(dueAt, now = new Date()) {
  if (dueAt === null || dueAt === undefined) {
    return '—';
  }
  const due = dueAt instanceof Date ? dueAt : new Date(dueAt);
  const diffMs = due.getTime() - now.getTime();
  if (diffMs <= 0) {
    return 'overdue';
  }
  const totalMinutes = Math.floor(diffMs / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours >= 24) {
    return `${Math.floor(hours / 24)}d ${hours % 24}h`;
  }
  if (hours >= 1) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m`;
}

/**
 * Group workers by `currentZone` for the warehouse map. Zones are sorted; within a zone
 * the workers keep their incoming order. Returns `[{ zone, workers }]`.
 */
export function groupByZone(workers) {
  const byZone = new Map();
  for (const worker of workers) {
    if (!byZone.has(worker.currentZone)) {
      byZone.set(worker.currentZone, []);
    }
    byZone.get(worker.currentZone).push(worker);
  }
  return [...byZone.keys()]
    .sort()
    .map((zone) => ({ zone, workers: byZone.get(zone) }));
}
