"""Embarko release commands for WasteLens.
Credentials remain in memory and server-side settings; never uploaded in source archive.
"""
from pathlib import Path
import json, os, re, subprocess, sys
import requests

ops_dir = Path(__file__).resolve().parent
code_dir = ops_dir.parent
backend_dir = code_dir / 'backend'
os.chdir(code_dir)

api = 'https://ship.embarko.ai'

token = os.environ.get('DEPLOY_TOKEN')
if not token:
    cred_file = Path.home() / '.embarko/credentials'
    if cred_file.exists():
        raw = cred_file.read_text().strip()
        if raw.startswith('{'):
            obj = json.loads(raw)
            token = obj.get('token') or obj.get('deployToken')
        else:
            token = raw.removeprefix('DEPLOY_TOKEN=').strip().strip('"').strip("'")

if not token or any(c.isspace() for c in token):
    raise SystemExit('A valid Embarko deployment credential is required.')

session = requests.Session()
session.headers.update({
    'Authorization': 'Bearer ' + token,
    'X-Agent-Name': 'codex'
})

state_file = backend_dir / 'target/embarko-release-state.json'
if not state_file.exists() and (code_dir / 'target/embarko-release-state.json').exists():
    state_file = code_dir / 'target/embarko-release-state.json'
state_file.parent.mkdir(parents=True, exist_ok=True)
(code_dir / 'target').mkdir(parents=True, exist_ok=True)

if len(sys.argv) < 2:
    raise SystemExit('Usage: python embarko-release.py <deploy|settings|status|logs>')

action = sys.argv[1]

def safe_reply(response):
    try:
        data = response.json()
    except ValueError:
        raise SystemExit(f'Embarko returned a non-JSON response; HTTP {response.status_code}')
    if response.status_code >= 400:
        print(f'Embarko request failed; HTTP {response.status_code} code {data.get("code", "unspecified")}: {data.get("error")}')
        raise SystemExit(1)
    return data

def save(data):
    app = data.get('app', {})
    if app.get('id'):
        embarko_json = json.dumps({'appId': app['id']}, indent=2) + '\n'
        (backend_dir / 'embarko.json').write_text(embarko_json)
        (code_dir / 'embarko.json').write_text(embarko_json)
    state = {k: data[k] for k in ['status', 'action', 'links'] if k in data}
    state['app'] = {k: app[k] for k in ['id', 'name', 'claimed', 'expiresAt'] if k in app}
    state_file.write_text(json.dumps(state, indent=2) + '\n')
    (code_dir / 'target/embarko-release-state.json').write_text(json.dumps(state, indent=2) + '\n')
    print(json.dumps(state, indent=2))

if action == 'deploy':
    if state_file.exists():
        name = json.loads(state_file.read_text())['app']['name']
    else:
        name = None
        for candidate in ['wastelens', 'veshwik-wastelens', 'wastelens-demo', 'wastelens-2026']:
            result = session.get(f'{api}/api/apps/{candidate}', timeout=30)
            if result.status_code == 404:
                name = candidate
                break
            if result.status_code != 200:
                safe_reply(result)
        if not name:
            raise SystemExit('No unused suggested app name is available.')
    
    # 1. Package source archive
    subprocess.run([sys.executable, str(code_dir / 'package-embarko.py')], check=True)
    archive_path = backend_dir / 'target/wastelens-embarko.tar.gz'
    
    print(f'Deploying to Embarko as app "{name}"...')
    with archive_path.open('rb') as archive:
        response = session.post(
            f'{api}/apps',
            headers={
                'X-App-Name': name,
                'X-App-Type': 'food-waste-tracking'
            },
            files={'source': ('wastelens.tar.gz', archive, 'application/gzip')},
            timeout=180
        )
    save(safe_reply(response))

elif action == 'settings':
    if not state_file.exists():
        raise SystemExit('No release state found. Run deploy first or create app.')
    state = json.loads(state_file.read_text())
    name = state['app']['name']
    
    settings = {}
    env_file = code_dir / '.env.local'
    if not env_file.exists():
        env_file = code_dir / '.env.neon'
    if not env_file.exists():
        raise SystemExit('.env.local or .env.neon required to configure server settings.')
        
    for line in env_file.read_text(encoding='utf-8').splitlines():
        line = line.strip().replace('\ufeff', '')
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            key = k.strip()
            val = v.strip()
            if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                val = val[1:-1]
            settings[key] = val
            
    keys_to_set = [
        'DB_JDBC_URL',
        'DB_USER',
        'DB_PASSWORD',
        'NEON_AUTH_BASE_URL',
        'NEON_AUTH_JWKS_URL',
        'NEON_AUTH_ISSUER'
    ]
    
    values = [{'key': k, 'value': settings[k]} for k in keys_to_set if k in settings]
    values.append({'key': 'SPRING_PROFILES_ACTIVE', 'value': 'production'})
    
    print(f'Sending {len(values)} server-side settings to Embarko app "{name}" (values redacted)...')
    response = session.put(
        f'{api}/api/apps/{name}/env-vars',
        json={'vars': values, 'apply': True},
        timeout=30
    )
    if response.status_code == 422 and response.json().get('code') == 'no_current_deployment':
        print('Server settings saved; deploy will apply them.')
    else:
        safe_reply(response)
        print('Server-side settings successfully saved and applied to Embarko.')

elif action == 'status':
    if not state_file.exists():
        raise SystemExit('No release state found.')
    state = json.loads(state_file.read_text())
    name = state['app']['name']
    data = safe_reply(session.get(f'{api}/apps/{name}/status', timeout=30))
    save(data)

elif action == 'logs':
    if not state_file.exists():
        raise SystemExit('No release state found.')
    state = json.loads(state_file.read_text())
    name = state['app']['name']
    stream = sys.argv[2] if len(sys.argv) > 2 else 'stderr'
    data = safe_reply(session.get(f'{api}/apps/{name}/logs', params={'stream': stream}, timeout=30))
    logs = data.get('logs', '')
    logs = re.sub(r'(?i)(password|token|secret|key)([=:]\s*)[^\s,;]+', r'\1\2[REDACTED]', logs)
    (backend_dir / 'target/embarko-diagnostics.log').write_text(logs, encoding='utf-8')
    print(logs[-10000:])

else:
    raise SystemExit(f'Unknown release command: {action}')
