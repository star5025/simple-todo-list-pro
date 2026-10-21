import { ref, onMounted, onBeforeUnmount } from 'vue'

export function useMediaQuery(query) {
  const media = window.matchMedia(query)
  const matches = ref(media.matches)
  const update = event => { matches.value = event.matches }
  onMounted(() => {
    matches.value = media.matches
    media.addEventListener('change', update)
  })
  onBeforeUnmount(() => media.removeEventListener('change', update))
  return matches
}
