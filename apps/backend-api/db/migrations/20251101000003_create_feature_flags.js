/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  await knex.schema.createTable('app_feature_flags', table => {
    table.increments('id').primary();
    table.string('feature_key', 100).notNullable().unique();
    table.boolean('enabled').notNullable().defaultTo(false);
    table.json('metadata');
    table
      .integer('updated_by')
      .unsigned()
      .references('id')
      .inTable('users')
      .onDelete('SET NULL');
    table.timestamp('updated_at').notNullable().defaultTo(knex.fn.now());
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  await knex.schema.dropTableIfExists('app_feature_flags');
};
