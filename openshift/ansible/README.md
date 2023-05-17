# Ansible playbook to create all required secrets

Fetches all secrets from Keeper Password Manager

Requires Ansible vault password and collections:

- keepersecurity.keeper_secrets_manager
- kubernetes.core

Changes playbook variables:

```yaml
namespace: ""
server: "api.ock.fmi.fi:6443"
serverName: "api-ock-fmi-fi:6443"
```

Login to cluster and run:

```bash
ansible-playbook create-secrets.yaml --vault-id tiuha@prompt
```
