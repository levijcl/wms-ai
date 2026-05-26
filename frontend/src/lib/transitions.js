// The single forward step the dispatch panel offers for an order or task. These mirror
// the backend's forward path so the "advance" buttons only ever offer a legal next move;
// the backend remains the authority and still rejects anything illegal (README §6). PENDING
// orders advance via assignment, not a button, so they have no forward step here.

const ORDER_FORWARD = {
  ASSIGNED: 'PICKING',
  PICKING: 'PICKED',
  PICKED: 'SHIPPED',
};

const TASK_FORWARD = {
  ASSIGNED: 'PICKING',
  PICKING: 'DONE',
};

export function nextOrderStatus(status) {
  return ORDER_FORWARD[status] ?? null;
}

export function nextTaskStatus(status) {
  return TASK_FORWARD[status] ?? null;
}
