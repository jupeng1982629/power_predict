export async function fetchDemoSummary() {
  const response = await fetch('/api/demo/summary')

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }

  return response.json()
}