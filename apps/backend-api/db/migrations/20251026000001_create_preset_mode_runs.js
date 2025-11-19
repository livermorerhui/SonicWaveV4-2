/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function up(knex) {
  await knex.schema.createTable('preset_mode_runs', function (table) {
    table.increments('id').primary();
    table.string('user_id', 255).notNullable();
    table.string('user_name', 255);
    table.string('user_email', 255);
    table.integer('customer_id');
    table.string('customer_name', 255);
    table.string('preset_mode_id', 100).notNullable();
    table.string('preset_mode_name', 255).notNullable();
    table.integer('intensity_scale_pct');
    table.integer('total_duration_sec');
    table.dateTime('start_time').notNullable();
    table.dateTime('stop_time');
    table.string('stop_reason', 50);
    table.text('stop_detail');
    table.timestamp('created_at').defaultTo(knex.fn.now());
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function down(knex) {
  await knex.schema.dropTableIfExists('preset_mode_runs');
};
