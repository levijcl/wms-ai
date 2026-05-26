// The backend enum value sets, mirrored for use in form controls. Kept as plain string
// arrays so they bind directly to <select> options and POST back the exact strings the
// REST layer converts to OrderStatus / Priority / WorkerStatus / TaskStatus.

export const Priority = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
export const WorkerStatus = ['IDLE', 'BUSY', 'OFFLINE'];
export const TaskStatus = ['ASSIGNED', 'PICKING', 'DONE', 'CANCELLED'];
