require('dotenv').config()
const http = require('http')
const { Client, GatewayIntentBits, Events } = require('discord.js')

const PORT = 3100

if (!process.env.DISCORD_TOKEN || !process.env.CHANNEL_ID) {
  console.error('[Watcher] Missing DISCORD_TOKEN or CHANNEL_ID in the .env file next to discord_watcher.js')
  process.exit(1)
}

const NAMES = ['test events', 'spoke', 'wemmbu', 'flame', 'test', 'parrot']
const SUFFIXES = ['e', 'm', 'o']
const PATTERN = /(?<!\w)(?:test events|spoke|wemmbu|flame|test|parrot|e|m|o)(?!\w)|\d+/gi

function detect(text) {
  return (text.match(PATTERN) || []).map(s => s.toLowerCase())
}

function parseTarget(matches) {
  const nameIdx = matches.findIndex(m => NAMES.includes(m))
  if (nameIdx === -1) return null

  let best = -1
  let bestDist = Infinity
  matches.forEach((m, i) => {
    if (!SUFFIXES.includes(m)) return
    const d = Math.abs(i - nameIdx)
    if (d < bestDist || (d === bestDist && i > nameIdx)) {
      best = i
      bestDist = d
    }
  })
  const name = matches[nameIdx]
  return best === -1 ? name : `${name} ${matches[best]}`
}

let latest = { id: Date.now(), target: '' }

const server = http
  .createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/latest') {
      res.writeHead(200, { 'Content-Type': 'text/plain' })
      return res.end(`${latest.id}|${latest.target}`)
    }
    res.writeHead(404)
    res.end()
  })
  .listen(PORT, '127.0.0.1', () => console.log(`[Watcher] Serving http://127.0.0.1:${PORT}/latest`))

server.on('error', e => {
  console.error(`[Watcher] Could not open port ${PORT}: ${e.message} (is another watcher already running?)`)
  process.exit(1)
})

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
})

client.on(Events.MessageCreate, msg => {
  if (msg.channelId !== process.env.CHANNEL_ID) return
  if (msg.author.id === client.user.id) return

  const matches = detect(msg.content)
  if (!matches.length) return

  console.log(`[Discord] "${msg.content}" -> ${JSON.stringify(matches)}`)
  const target = parseTarget(matches)
  if (!target) {
    console.log('[Discord] Matched, but no model name found to click.')
    return
  }
  latest = { id: Math.max(Date.now(), latest.id + 1), target }
  console.log(`[Watcher] New target: "${target}"`)
})

client.once(Events.ClientReady, c => {
  console.log(`[Discord] Logged in as ${c.user.tag}, watching channel ${process.env.CHANNEL_ID}`)
})

client.on(Events.Error, e => console.error('[Discord] Error:', e.message))

client.login(process.env.DISCORD_TOKEN).catch(e => {
  console.error('[Discord] Login failed:', e.message)
  process.exit(1)
})
