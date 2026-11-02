"""Live verification suite for WasteLens deployed on Embarko.
Verifies all public routes, security headers, unauthenticated guards,
Neon Auth authentication, business endpoints, idempotent logging, and tenant isolation.
"""
import base64
import json
import sys
import uuid
import requests

BASE_URL = "https://veshwik-wastelens.embarko.app"
AUTH_URL = "https://ep-rapid-firefly-aousvely.neonauth.c-2.ap-southeast-1.aws.neon.tech/wastelens/auth"
ORIGIN = "https://veshwik-wastelens.embarko.app"

ACCOUNT_A = {
    "email": "account-a@wastelens.demo",
    "password": "WasteLensDemo2026!",
    "expected_id": "af32b42e-6078-4f81-919c-d3a4b4e85683"
}

ACCOUNT_B = {
    "email": "account-b@wastelens.demo",
    "password": "WasteLensDemo2026!",
    "expected_id": "e8564d72-2a45-40bf-9cac-bfb803e19c56"
}

passed = 0
failed = 0

def check(name, condition, extra=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  [PASS] {name} {extra}".rstrip())
    else:
        failed += 1
        print(f"  [FAIL] {name} {extra}".rstrip())

def decode_jwt_sub(token):
    payload_b64 = token.split(".")[1]
    # Add base64 padding
    payload_b64 += "=" * ((4 - len(payload_b64) % 4) % 4)
    payload = json.loads(base64.urlsafe_b64decode(payload_b64.encode("utf-8")))
    return payload.get("sub")

import time

def authenticate(account):
    session = requests.Session()
    headers = {"Origin": ORIGIN, "Content-Type": "application/json"}
    
    last_err = None
    for attempt in range(4):
        try:
            # 1. Sign in
            res = session.post(
                f"{AUTH_URL}/sign-in/email",
                headers=headers,
                json={"email": account["email"], "password": account["password"]},
                timeout=15
            )
            if res.status_code != 200:
                raise RuntimeError(f"Sign-in failed for {account['email']}: HTTP {res.status_code} - {res.text}")
            
            # 2. Get JWT token
            token_res = session.get(f"{AUTH_URL}/token", headers=headers, timeout=15)
            if token_res.status_code != 200:
                raise RuntimeError(f"Token retrieval failed for {account['email']}: HTTP {token_res.status_code} - {token_res.text}")
            
            jwt = token_res.json().get("token")
            if not jwt:
                raise RuntimeError(f"No JWT found in token response for {account['email']}")
            return jwt
        except Exception as e:
            last_err = e
            time.sleep(1.5)
            
    raise RuntimeError(f"Failed to authenticate after 4 attempts: {last_err}")

def run():
    global passed, failed
    print("=" * 60)
    print("WasteLens Live Production Verification - Embarko")
    print(f"Target: {BASE_URL}")
    print(f"Auth:   {AUTH_URL}")
    print("=" * 60)

    # 1. Landing Page & Headers
    print("\n--- 1. Public HTTPS Landing Page & Security Headers ---")
    r = requests.get(f"{BASE_URL}/", timeout=15)
    check("Root page returns HTTP 200", r.status_code == 200, f"(HTTP {r.status_code})")
    check("Content-Type is text/html", "text/html" in r.headers.get("content-type", ""))
    check("Contains React root div", '<div id="root">' in r.text)
    check("CSP header present", "content-security-policy" in r.headers)
    check("X-Content-Type-Options: nosniff", r.headers.get("x-content-type-options") == "nosniff")
    check("X-Frame-Options: DENY", r.headers.get("x-frame-options") == "DENY")

    # 2. SPA Deep Routes (Fallback to index.html)
    print("\n--- 2. SPA Deep Routes Navigation ---")
    for route in ["/dashboard", "/entries/new", "/compare"]:
        r = requests.get(f"{BASE_URL}{route}", timeout=15)
        check(f"Route {route} returns 200 SPA shell", r.status_code == 200 and '<div id="root">' in r.text, f"(HTTP {r.status_code})")

    # 3. Unauthenticated API Guards
    print("\n--- 3. Unauthenticated API Challenges (HTTP 401) ---")
    r_ctx = requests.get(f"{BASE_URL}/api/context", timeout=15)
    check("GET /api/context is protected", r_ctx.status_code == 401, f"(HTTP {r_ctx.status_code})")
    
    r_exp = requests.get(f"{BASE_URL}/api/export", timeout=15)
    check("GET /api/export is protected", r_exp.status_code == 401, f"(HTTP {r_exp.status_code})")
    
    r_post = requests.post(f"{BASE_URL}/api/entries", json={}, timeout=15)
    check("POST /api/entries is protected", r_post.status_code == 401, f"(HTTP {r_post.status_code})")

    # 4. Neon Auth Sign-in for Account A
    print("\n--- 4. Live Neon Auth Authentication (Account A) ---")
    jwt_a = authenticate(ACCOUNT_A)
    check("Account A authenticated & JWT issued", bool(jwt_a), f"(JWT length: {len(jwt_a)})")
    sub_a = decode_jwt_sub(jwt_a)
    check("JWT sub claim matches Account A ID", sub_a == ACCOUNT_A["expected_id"], f"(sub: {sub_a})")

    headers_a = {
        "Authorization": f"Bearer {jwt_a}",
        "Content-Type": "application/json"
    }

    # 5. Authenticated App Context
    print("\n--- 5. Authenticated Context Retrieval ---")
    r_ctx_a = requests.get(f"{BASE_URL}/api/context", headers=headers_a, timeout=15)
    check("GET /api/context returns 200", r_ctx_a.status_code == 200, f"(HTTP {r_ctx_a.status_code})")
    ctx_data = r_ctx_a.json()
    check("Context today present", bool(ctx_data.get("today")), f"(today: {ctx_data.get('today')})")
    check("Context timezone is Asia/Kolkata", ctx_data.get("timezone") == "Asia/Kolkata")
    check("Context currentWeekStart present", bool(ctx_data.get("currentWeekStart")), f"(weekStart: {ctx_data.get('currentWeekStart')})")

    # 6. Weekly Summary
    print("\n--- 6. Weekly Summary Metrics ---")
    r_week = requests.get(f"{BASE_URL}/api/weeks/2026-11-02", headers=headers_a, timeout=15)
    check("GET /api/weeks/2026-11-02 returns 200", r_week.status_code == 200, f"(HTTP {r_week.status_code})")
    week_data = r_week.json()
    check("Summary contains weekStart", week_data.get("weekStart") == "2026-11-02")
    check("Summary contains totalGrams", "totalGrams" in week_data, f"(totalGrams: {week_data.get('totalGrams')})")
    check("Summary contains entryCount", "entryCount" in week_data, f"(entryCount: {week_data.get('entryCount')})")

    # 7. Period Comparison
    print("\n--- 7. Period Comparison ---")
    r_cmp = requests.get(f"{BASE_URL}/api/compare?weekA=2026-10-26&weekB=2026-11-02", headers=headers_a, timeout=15)
    check("GET /api/compare returns 200", r_cmp.status_code == 200, f"(HTTP {r_cmp.status_code})")
    cmp_data = r_cmp.json()
    check("Comparison includes weekA", "weekA" in cmp_data)
    check("Comparison includes weekB", "weekB" in cmp_data)

    # 8. Atomic Entry Logging & Idempotent Replay
    print("\n--- 8. Entry Persistence & Idempotent Replay ---")
    sub_id = str(uuid.uuid4())
    entry_payload = {
        "submissionId": sub_id,
        "entryDate": "2026-11-02",
        "category": "produce",
        "grams": 450,
        "stage": "preparation",
        "reason": "trimming"
    }
    # Initial POST
    r_create = requests.post(f"{BASE_URL}/api/entries", headers=headers_a, json=entry_payload, timeout=15)
    check("POST /api/entries returns 201 Created", r_create.status_code == 201, f"(HTTP {r_create.status_code})")
    create_data = r_create.json()
    created_entry = create_data.get("entry", {})
    check("Initial insert replayed flag is False", create_data.get("replayed") is False)
    check("Entry submissionId matches", created_entry.get("submissionId") == sub_id)
    check("Entry grams match", created_entry.get("grams") == 450)
    entry_id = created_entry.get("id")
    check("Entry ID assigned", bool(entry_id), f"(id: {entry_id})")

    # Idempotent Replay (Same UUID)
    r_replay = requests.post(f"{BASE_URL}/api/entries", headers=headers_a, json=entry_payload, timeout=15)
    check("Replayed POST returns 200/201", r_replay.status_code in (200, 201), f"(HTTP {r_replay.status_code})")
    replay_data = r_replay.json()
    replayed_entry = replay_data.get("entry", {})
    check("Replayed flag is True", replay_data.get("replayed") is True)
    check("Replayed entry ID matches original", replayed_entry.get("id") == entry_id)

    # 9. Bounded JSON Export
    print("\n--- 9. Bounded JSON Export ---")
    r_export = requests.get(f"{BASE_URL}/api/export", headers=headers_a, timeout=15)
    check("GET /api/export returns 200", r_export.status_code == 200, f"(HTTP {r_export.status_code})")
    check("Export Content-Disposition is attachment", "attachment" in r_export.headers.get("content-disposition", ""))
    export_json = r_export.json()
    check("Export has schemaVersion", export_json.get("schemaVersion") == 1)
    check("Export has timezone", export_json.get("timezone") == "Asia/Kolkata")
    export_entries = export_json.get("entries", [])
    check("Export contains entries list", isinstance(export_entries, list) and len(export_entries) > 0, f"(count: {len(export_entries)})")
    matching = [e for e in export_entries if e.get("submissionId") == sub_id]
    check("Created entry found in export", len(matching) == 1)

    # 10. Multi-Tenant Isolation with Account B
    print("\n--- 10. Tenant Isolation (Demo Account B) ---")
    jwt_b = authenticate(ACCOUNT_B)
    check("Account B authenticated & JWT issued", bool(jwt_b))
    sub_b = decode_jwt_sub(jwt_b)
    check("JWT sub claim matches Account B ID", sub_b == ACCOUNT_B["expected_id"], f"(sub: {sub_b})")

    headers_b = {
        "Authorization": f"Bearer {jwt_b}",
        "Content-Type": "application/json"
    }
    r_ctx_b = requests.get(f"{BASE_URL}/api/context", headers=headers_b, timeout=15)
    check("Account B context returns 200", r_ctx_b.status_code == 200)

    # Account B export should NOT contain Account A's entry
    r_export_b = requests.get(f"{BASE_URL}/api/export", headers=headers_b, timeout=15)
    check("Account B export returns 200", r_export_b.status_code == 200)
    export_b_json = r_export_b.json()
    export_b_entries = export_b_json.get("entries", [])
    leaked = [e for e in export_b_entries if e.get("submissionId") == sub_id]
    check("Account A's entry is NOT visible to Account B (RLS verified)", len(leaked) == 0, f"(leaked: {len(leaked)})")

    # Summary
    print("\n" + "=" * 60)
    print(f"VERIFICATION RESULTS: {passed} PASSED, {failed} FAILED")
    print("=" * 60)
    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    run()
