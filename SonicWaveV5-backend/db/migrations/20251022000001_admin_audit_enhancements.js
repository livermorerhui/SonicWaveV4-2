/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  const hasRole = await knex.schema.hasColumn('users', 'role');
  if (!hasRole) {
    await knex.schema.alterTable('users', table => {
      table.enu('role', ['user', 'admin']).notNullable().defaultTo('user');
    });
  }

  const hasAccountType = await knex.schema.hasColumn('users', 'account_type');
  if (!hasAccountType) {
    await knex.schema.alterTable('users', table => {
      table.enu('account_type', ['normal', 'test']).notNullable().defaultTo('normal');
    });
  }

  const hasUpdatedAt = await knex.schema.hasColumn('users', 'updated_at');
  if (!hasUpdatedAt) {
    await knex.schema.alterTable('users', table => {
      table.timestamp('updated_at').nullable().defaultTo(knex.raw('CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'));
    });
  } else {
    await knex.raw(
      'ALTER TABLE users MODIFY COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
    );
  }

  const hasDeletedAt = await knex.schema.hasColumn('users', 'deleted_at');
  if (!hasDeletedAt) {
    await knex.schema.alterTable('users', table => {
      table.timestamp('deleted_at').nullable();
    });
  }

  await knex.schema.createTable('audit_logs', table => {
    table.increments('id').primary();
    table
      .integer('actor_id')
      .unsigned()
      .nullable()
      .references('id')
      .inTable('users')
      .onDelete('SET NULL');
    table.string('action', 100).notNullable();
    table.string('target_type', 100).notNullable();
    table.string('target_id', 100).nullable();
    table.json('metadata').nullable();
    table.string('ip', 45).nullable();
    table.string('user_agent', 512).nullable();
    table.timestamp('created_at').defaultTo(knex.fn.now());
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  await knex.schema.dropTableIfExists('audit_logs');

  const dropColumnIfExists = async columnName => {
    const exists = await knex.schema.hasColumn('users', columnName);
    if (exists) {
      await knex.schema.alterTable('users', table => {
        table.dropColumn(columnName);
      });
    }
  };

  await dropColumnIfExists('deleted_at');
  await dropColumnIfExists('updated_at');
  await dropColumnIfExists('account_type');
  await dropColumnIfExists('role');
};
