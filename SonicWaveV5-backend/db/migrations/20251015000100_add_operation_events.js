/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  await knex.schema.alterTable('user_operations', function (table) {
    table.string('stop_reason', 50);
    table.text('stop_detail');
  });

  await knex.schema.createTable('user_operation_events', function (table) {
    table.increments('id').primary();
    table
      .integer('operation_id')
      .unsigned()
      .notNullable()
      .references('id')
      .inTable('user_operations')
      .onDelete('CASCADE');
    table.string('event_type', 50).notNullable();
    table.integer('frequency');
    table.integer('intensity');
    table.integer('time_remaining');
    table.text('extra_detail');
    table.timestamp('created_at').defaultTo(knex.fn.now());
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  await knex.schema.dropTableIfExists('user_operation_events');

  await knex.schema.alterTable('user_operations', function (table) {
    table.dropColumn('stop_reason');
    table.dropColumn('stop_detail');
  });
};
