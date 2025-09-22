/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema.table('user_operations', function(table) {
    table.renameColumn('customer', 'customer_name');
    table.integer('customer_id').nullable(); // Add new nullable integer column
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  return knex.schema.table('user_operations', function(table) {
    table.dropColumn('customer_id');
    table.renameColumn('customer_name', 'customer');
  });
};