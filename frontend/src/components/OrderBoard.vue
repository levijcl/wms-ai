<script setup>
import { formatCountdown, ORDER_STATUSES } from '@/lib/format.js';

// Kanban order board (README §3.6, §7): one column per lifecycle status, each card
// carrying customer, a priority color, a dueAt countdown, and the item lines. The
// countdown is recomputed on every render, so it refreshes each ~1.5s poll.
const props = defineProps({
  orders: { type: Array, required: true },
});

const ordersIn = (status) => props.orders.filter((o) => o.status === status);
</script>

<template>
  <section class="board">
    <h2>Order board</h2>
    <div class="columns">
      <section v-for="status in ORDER_STATUSES" :key="status" class="column" :data-status="status">
        <h3>{{ status }} <span class="count">{{ ordersIn(status).length }}</span></h3>

        <article
          v-for="order in ordersIn(status)"
          :key="order.id"
          class="card"
          :data-order="order.id"
          :data-priority="order.priority"
        >
          <header>
            <span class="customer">{{ order.customer }}</span>
            <span class="priority">{{ order.priority }}</span>
          </header>
          <p class="due">due in <span data-testid="countdown">{{ formatCountdown(order.dueAt) }}</span></p>
          <ul class="items">
            <li v-for="item in order.items" :key="item.sku">{{ item.sku }} × {{ item.quantity }}</li>
          </ul>
        </article>

        <p v-if="ordersIn(status).length === 0" class="empty">—</p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.columns { display: flex; gap: 0.75rem; overflow-x: auto; }
.column { flex: 1 1 160px; min-width: 160px; }
.column h3 { font-size: 0.8rem; color: #57606a; margin: 0 0 0.5rem; text-transform: uppercase; letter-spacing: 0.03em; }
.count { background: #eaeef2; border-radius: 10px; padding: 0 0.4rem; font-size: 0.7rem; }
.card { background: #fff; border: 1px solid #e1e4e8; border-left-width: 4px; border-radius: 6px; padding: 0.5rem; margin-bottom: 0.5rem; }
.card[data-priority='URGENT'] { border-left-color: #b42318; }
.card[data-priority='HIGH'] { border-left-color: #bc4c00; }
.card[data-priority='NORMAL'] { border-left-color: #0969da; }
.card[data-priority='LOW'] { border-left-color: #6e7781; }
.card header { display: flex; justify-content: space-between; font-size: 0.85rem; }
.customer { font-weight: 600; }
.priority { font-size: 0.7rem; color: #57606a; }
.due { font-size: 0.75rem; color: #57606a; margin: 0.3rem 0; }
.items { list-style: none; margin: 0; padding: 0; font-size: 0.8rem; color: #1f2430; }
.empty { color: #c2c8cf; text-align: center; }
</style>
