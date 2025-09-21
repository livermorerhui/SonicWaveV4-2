/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema.table('customers', function(table) {
    table.integer('user_id').notNullable();
    table.foreign('user_id').references('id').inTable('users').onDelete('CASCADE');
    table.index('user_id');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  return knex.schema.table('customers', function(table) {
    table.dropIndex('user_id');
    table.dropForeign('user_id');
    table.dropColumn('user_id');
  });
};