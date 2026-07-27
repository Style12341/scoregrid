#!/bin/bash
#
# Creates one database per service inside the single Postgres instance, each
# owned by its own login role.
#
# Why roles and not just databases: hard rule 1 says a service touches only its
# own database. With separate containers the network enforced that. With one
# container, ownership and CONNECT grants enforce it instead — auth_service
# cannot open a connection to scoregrid_tournament at all, and PostgreSQL has
# no cross-database joins, so there is no way around it short of dblink.
#
# Runs only on first initialisation of an empty data volume.

set -euo pipefail

create_service_database() {
  local db_name="$1"
  local role_name="$2"
  local role_password="$3"

  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    -v db_name="$db_name" \
    -v role_name="$role_name" \
    -v role_password="$role_password" <<-'EOSQL'
		-- :'x' quotes as a literal, :"x" quotes as an identifier. Both matter:
		-- without them a password containing a quote breaks the script.
		CREATE ROLE :"role_name" WITH LOGIN PASSWORD :'role_password';
		CREATE DATABASE :"db_name" OWNER :"role_name";

		-- Every role can connect to every database by default. Close that.
		REVOKE CONNECT ON DATABASE :"db_name" FROM PUBLIC;
		GRANT CONNECT ON DATABASE :"db_name" TO :"role_name";
	EOSQL

  echo "  created database '$db_name' owned by role '$role_name'"
}

echo "ScoreGrid: provisioning per-service databases…"

create_service_database \
  "${POSTGRES_AUTH_DB:-scoregrid_auth}" \
  "${POSTGRES_AUTH_USER:-auth_service}" \
  "${POSTGRES_AUTH_PASSWORD:-scoregrid}"

create_service_database \
  "${POSTGRES_TOURNAMENT_DB:-scoregrid_tournament}" \
  "${POSTGRES_TOURNAMENT_USER:-tournament_service}" \
  "${POSTGRES_TOURNAMENT_PASSWORD:-scoregrid}"

echo "ScoreGrid: Postgres ready."
