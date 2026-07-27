/*
 * Creates one user per service inside the single MongoDB instance, each scoped
 * with readWrite on its own database only.
 *
 * Why this file exists: before consolidation, prediction and score ran as two
 * separate mongod containers with no authentication, and the network kept them
 * apart. Sharing one instance removes that wall, so the isolation required by
 * hard rule 1 has to come from credentials. A user with readWrite on
 * scoregrid_prediction cannot read scoregrid_score, and $lookup cannot cross
 * databases either.
 *
 * Runs only on first initialisation of an empty data volume.
 */

const services = [
  {
    db: process.env.MONGO_PREDICTION_DB || "scoregrid_prediction",
    user: process.env.MONGO_PREDICTION_USER || "prediction_service",
    password: process.env.MONGO_PREDICTION_PASSWORD || "scoregrid",
  },
  {
    db: process.env.MONGO_SCORE_DB || "scoregrid_score",
    user: process.env.MONGO_SCORE_USER || "score_service",
    password: process.env.MONGO_SCORE_PASSWORD || "scoregrid",
  },
];

for (const service of services) {
  const database = db.getSiblingDB(service.db);

  database.createUser({
    user: service.user,
    pwd: service.password,
    roles: [{ role: "readWrite", db: service.db }],
  });

  /*
   * Creating a user does not create the database — Mongo only materialises it
   * on first write. Touch a marker collection so the database exists straight
   * away and connecting with the scoped user does not look like a failure.
   */
  database.createCollection("_provisioned");

  print(`  created user '${service.user}' with readWrite on '${service.db}'`);
}
