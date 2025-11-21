/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  const hasTable = await knex.schema.hasTable('device_registry');
  if (!hasTable) {
    return;
  }
  const addColumnIfMissing = async (columnName, builder) => {
    const exists = await knex.schema.hasColumn('device_registry', columnName);
    if (!exists) {
      await knex.schema.alterTable('device_registry', builder);
    }
  };
  await addColumnIfMissing('last_user_email', table => {
    table.string('last_user_email', 255).nullable().after('last_user_id');
  });
  await addColumnIfMissing('last_user_name', table => {
    table.string('last_user_name', 255).nullable().after('last_user_email');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  const hasTable = await knex.schema.hasTable('device_registry');
  if (!hasTable) {
    return;
  }
  const dropColumnIfExists = async columnName => {
    const exists = await knex.schema.hasColumn('device_registry', columnName);
    if (exists) {
      await knex.schema.alterTable('device_registry', table => {
        table.dropColumn(columnName);
      });
    }
  };
  await dropColumnIfExists('last_user_name');
  await dropColumnIfExists('last_user_email');
};
