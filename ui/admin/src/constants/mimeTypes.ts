export const commonMimeTypesMapping = new Map([
  ["json", "application/json"],
  ["pdf", "application/pdf"],
  ["xml", "application/xml"],
  ["zip", "application/zip"],
  ["mp3", "audio/mpeg"],
  ["gif", "image/gif"],
  ["jpg", "image/jpeg"],
  ["jpeg", "image/jpeg"],
  ["png", "image/png"],
  ["svg", "image/svg+xml"],
  ["csv", "text/csv"],
  ["html", "text/html"],
  ["txt", "text/plain"],
  ["mp4", "video/mp4"]
])

export const commonMimeTypes = [...new Set([...commonMimeTypesMapping.values()])]