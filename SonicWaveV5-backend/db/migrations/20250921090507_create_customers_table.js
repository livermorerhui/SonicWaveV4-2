/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema.createTable('customers', function(table) {
    table.increments('id').primary();
    table.string('name').notNullable();
    table.date('date_of_birth');
    table.string('gender');
    table.string('phone');
    table.string('email').unique();
    table.decimal('height');
    table.decimal('weight');
    table.timestamps(true, true);
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  return knex.schema.dropTable('customers');
};