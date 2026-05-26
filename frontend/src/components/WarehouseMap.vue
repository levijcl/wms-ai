<script setup>
import { computed } from 'vue';

// Warehouse map by zone (README §3.6, §7): each worker is a card colored by status, and
// each zone shows the stock physically held there. Zones are the union of every zone a
// worker is in or a SKU is stored in, so an empty-but-stocked zone still appears.
const props = defineProps({
  workers: { type: Array, required: true },
  stocks: { type: Array, required: true },
});

const zones = computed(() => {
  const names = new Set();
  for (const w of props.workers) names.add(w.currentZone);
  for (const s of props.stocks) names.add(s.location);
  return [...names].sort();
});

const workersIn = (zone) => props.workers.filter((w) => w.currentZone === zone);
const stocksIn = (zone) => props.stocks.filter((s) => s.location === zone);
</script>

<template>
  <section class="map">
    <h2>Warehouse map</h2>
    <div class="zones">
      <section v-for="zone in zones" :key="zone" class="zone" :data-zone="zone">
        <h3>{{ zone }}</h3>

        <ul class="workers">
          <li
            v-for="worker in workersIn(zone)"
            :key="worker.id"
            class="worker"
            :data-worker="worker.id"
            :data-status="worker.status"
          >
            <span class="worker-name">{{ worker.name }}</span>
            <span class="badge">{{ worker.status }}</span>
          </li>
          <li v-if="workersIn(zone).length === 0" class="empty">no workers</li>
        </ul>

        <ul class="stocks">
          <li v-for="stock in stocksIn(zone)" :key="stock.sku" class="stock" :data-sku="stock.sku">
            <span>{{ stock.sku }}</span>
            <span :class="['qty', { zero: stock.quantity === 0 }]">{{ stock.quantity }}</span>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped>
.zones { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.zone { flex: 1 1 220px; background: #fff; border: 1px solid #e1e4e8; border-radius: 8px; padding: 0.75rem; }
.zone h3 { margin: 0 0 0.5rem; font-size: 0.95rem; color: #57606a; }
.workers, .stocks { list-style: none; margin: 0; padding: 0; }
.worker { display: flex; justify-content: space-between; align-items: center; padding: 0.4rem 0.5rem; border-radius: 6px; margin-bottom: 0.35rem; color: #fff; }
.worker[data-status='IDLE'] { background: #1a7f37; }
.worker[data-status='BUSY'] { background: #bc4c00; }
.worker[data-status='OFFLINE'] { background: #6e7781; }
.badge { font-size: 0.7rem; opacity: 0.9; }
.stocks { margin-top: 0.5rem; border-top: 1px dashed #e1e4e8; padding-top: 0.5rem; }
.stock { display: flex; justify-content: space-between; font-size: 0.85rem; padding: 0.15rem 0; }
.qty.zero { color: #b42318; font-weight: 600; }
.empty { color: #8c959f; font-size: 0.8rem; font-style: italic; }
</style>
