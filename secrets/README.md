# Docker secrets

This folder holds the secret files used by `docker-compose.yml`. Everything here except this README is git-ignored.

| File | Content |
|---|---|
| `db_password.txt` | Password of the MySQL user `eruplan` |
| `db_root_password.txt` | MySQL root password |
| `firebase_credentials.json` | Firebase service-account JSON |

See [Docker.md](../Docker.md) for how to generate them.
