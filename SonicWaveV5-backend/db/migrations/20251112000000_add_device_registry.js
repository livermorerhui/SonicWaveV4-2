/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  const hasDeviceRegistry = await knex.schema.hasTable('device_registry');
  if (!hasDeviceRegistry) {
    await knex.schema.createTable('device_registry', table => {
      table.string('device_id', 191).primary();
      table.timestamp('first_seen_at').notNullable().defaultTo(knex.fn.now());
      table.timestamp('last_seen_at').notNullable().defaultTo(knex.fn.now());
      table.string('last_user_id', 255);
      table.string('last_ip', 45);
      table.string('device_model', 255);
      table.string('os_version', 64);
      table.string('app_version', 64);
      table.boolean('offline_allowed').notNullable().defaultTo(true);
      table.json('metadata');
      table.timestamp('updated_at').notNullable().defaultTo(knex.fn.now());
    });
    await knex.raw(
      'ALTER TABLE device_registry MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    );
    await knex.schema.alterTable('device_registry', table => {
      table.index(['offline_allowed', 'last_seen_at'], 'idx_device_registry_status');
    });
  }

  const hasAppUsageLogs = await knex.schema.hasTable('app_usage_logs');
  if (hasAppUsageLogs) {
    const ensureColumn = async (columnName, builder) => {
      const exists = await knex.schema.hasColumn('app_usage_logs', columnName);
      if (!exists) {
        await knex.schema.alterTable('app_usage_logs', builder);
      }
    };

    await ensureColumn('device_id', table => {
      table.string('device_id', 191).nullable().after('user_id');
    });
    await ensureColumn('ip_address', table => {
      table.string('ip_address', 45).nullable().after('device_id');
    });
    await ensureColumn('device_model', table => {
      table.string('device_model', 255).nullable().after('ip_address');
    });
    await ensureColumn('os_version', table => {
      table.string('os_version', 64).nullable().after('device_model');
    });
    await ensureColumn('app_version', table => {
      table.string('app_version', 64).nullable().after('os_version');
    });
  }
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  const hasDeviceRegistry = await knex.schema.hasTable('device_registry');
  if (hasDeviceRegistry) {
    await knex.schema.dropTable('device_registry');
  }

  const dropColumnIfExists = async columnName => {
    const exists = await knex.schema.hasColumn('app_usage_logs', columnName);
    if (exists) {
      await knex.schema.alterTable('app_usage_logs', table => {
        table.dropColumn(columnName);
      });
    }
  };

  await dropColumnIfExists('app_version');
  await dropColumnIfExists('os_version');
  await dropColumnIfExists('device_model');
  await dropColumnIfExists('ip_address');
  await dropColumnIfExists('device_id');
};
