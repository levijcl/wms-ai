// Thin fetch wrappers over the REST layer (05-web-api.md). Every call resolves to a
// uniform result object — never throws on an HTTP error — so the store can append a
// guardrail rejection to the event log instead of swallowing it (README §6):
//
//   success → { ok: true,  status, data }
//   4xx/409 → { ok: false, status, error, message }   (the backend's ApiError body)
//   network → { ok: false, status: 0, error, message } (backend unreachable)
//
// The UI never pre-checks a guardrail; it issues the call and shows whatever comes back.

async function request(method, path, body) {
  let response;
  try {
    response = await fetch(path, {
      method,
      ...(body === undefined
        ? {}
        : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    });
  } catch (err) {
    // fetch only rejects on a network-level failure (backend down, DNS, CORS).
    return { ok: false, status: 0, error: 'NetworkError', message: String(err?.message ?? err) };
  }

  const payload = await response.json().catch(() => null);

  if (response.ok) {
    return { ok: true, status: response.status, data: payload };
  }
  return {
    ok: false,
    status: response.status,
    error: payload?.error ?? 'Error',
    message: payload?.message ?? `Request failed with status ${response.status}`,
  };
}

export function getState() {
  return request('GET', '/api/state');
}

export function assign(orderId, workerId) {
  return request('POST', '/api/dispatch/assign', { orderId, workerId });
}

export function submitOrder(draft) {
  return request('POST', '/api/orders', draft);
}

export function setOrderStatus(id, status) {
  return request('POST', `/api/orders/${id}/status`, { status });
}

export function setTaskStatus(id, status) {
  return request('POST', `/api/tasks/${id}/status`, { status });
}

export function setWorkerStatus(id, status) {
  return request('POST', `/api/workers/${id}/status`, { status });
}

export function releaseStock(sku, quantity) {
  return request('POST', '/api/inventory/release', { sku, quantity });
}

/** The whole client as one object, convenient for dependency injection in the store. */
export const apiClient = {
  getState,
  assign,
  submitOrder,
  setOrderStatus,
  setTaskStatus,
  setWorkerStatus,
  releaseStock,
};
