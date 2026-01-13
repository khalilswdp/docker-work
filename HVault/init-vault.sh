#!/bin/sh
set -eu

echo "[vault-init] Waiting for Vault..."
# wait until health endpoint responds
until wget -qO- http://vault:8200/v1/sys/health >/dev/null 2>&1; do
  sleep 1
done

export VAULT_ADDR="http://vault:8200"
export VAULT_TOKEN="root"

echo "[vault-init] Vault is up. Enabling KV v2 at secret/ (idempotent)..."
vault secrets enable -path=secret kv-v2 >/dev/null 2>&1 || true

echo "[vault-init] Writing secrets to secret/myapp..."
vault kv put secret/myapp \
  db.username="demo_user" \
  db.password="demo_pass" \
  ssh.host="example.internal" \
  ssh.port="22"

echo "[vault-init] Verifying..."
vault kv get secret/myapp

echo "[vault-init] Done."