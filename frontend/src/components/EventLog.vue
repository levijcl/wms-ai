<script setup>
// Append-only event log (README §3.6, §7): a chronological trace of every dispatch action
// and its outcome — successes and, verbatim, the backend's guardrail rejections (the 400/409
// {error,message}). This is the seam where Phase B's AI reasoning trace will render. The
// store provides events newest-first; this component just renders them.
defineProps({
  events: { type: Array, required: true },
});

const time = (at) =>
  at.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
</script>

<template>
  <section class="log">
    <h2>Event log</h2>
    <ul v-if="events.length > 0" class="entries">
      <li v-for="event in events" :key="event.id" class="entry" :data-kind="event.kind">
        <time>{{ time(event.at) }}</time>
        <span class="message">{{ event.message }}</span>
      </li>
    </ul>
    <p v-else class="empty">no actions yet</p>
  </section>
</template>

<style scoped>
.entries { list-style: none; margin: 0; padding: 0; max-height: 280px; overflow-y: auto; font-family: ui-monospace, SFMono-Regular, monospace; font-size: 0.8rem; }
.entry { display: flex; gap: 0.6rem; padding: 0.3rem 0.5rem; border-left: 3px solid transparent; }
.entry time { color: #8c959f; flex: 0 0 auto; }
.entry[data-kind='success'] { border-left-color: #1a7f37; }
.entry[data-kind='success'] .message { color: #1a7f37; }
.entry[data-kind='error'] { border-left-color: #b42318; background: #fff5f5; }
.entry[data-kind='error'] .message { color: #b42318; }
.empty { color: #8c959f; font-style: italic; font-size: 0.85rem; }
</style>
