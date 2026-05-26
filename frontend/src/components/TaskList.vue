<script setup>
// Picking-task list (README §3.6, §7): each task links an order to the worker fulfilling
// it, with its current TaskStatus. Read-only here; advancing a task lives in the dispatch
// panel (Task 3).
defineProps({
  tasks: { type: Array, required: true },
});
</script>

<template>
  <section class="tasks">
    <h2>Picking tasks</h2>
    <ul v-if="tasks.length > 0" class="task-list">
      <li
        v-for="task in tasks"
        :key="task.id"
        class="task"
        :data-task="task.id"
        :data-status="task.status"
      >
        <span class="link">{{ task.orderId }} → {{ task.workerId }}</span>
        <span class="badge">{{ task.status }}</span>
      </li>
    </ul>
    <p v-else class="empty">no picking tasks yet</p>
  </section>
</template>

<style scoped>
.task-list { list-style: none; margin: 0; padding: 0; }
.task { display: flex; justify-content: space-between; align-items: center; background: #fff; border: 1px solid #e1e4e8; border-radius: 6px; padding: 0.4rem 0.6rem; margin-bottom: 0.35rem; font-size: 0.85rem; }
.badge { font-size: 0.7rem; padding: 0.1rem 0.45rem; border-radius: 10px; color: #fff; }
.task[data-status='ASSIGNED'] .badge { background: #0969da; }
.task[data-status='PICKING'] .badge { background: #bc4c00; }
.task[data-status='DONE'] .badge { background: #1a7f37; }
.task[data-status='CANCELLED'] .badge { background: #6e7781; }
.empty { color: #8c959f; font-style: italic; font-size: 0.85rem; }
</style>
