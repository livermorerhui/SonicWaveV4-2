/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  // This is a multi-step operation to fix a broken state, following the correct order.
  // Step 1: Drop the foreign key constraint.
  return knex.schema.table('customers', function(table) {
    // The constraint name 'customers_user_id_foreign' is knex's default naming.
    table.dropForeign('user_id', 'customers_user_id_foreign');
  })
  .then(function () {
    // Step 2: Drop the column itself.
    return knex.schema.table('customers', function(table) {
      table.dropColumn('user_id');
    });
  })
  .then(function () {
    // Step 3: Re-create the column and constraint correctly.
    return knex.schema.table('customers', function(table) {
      table.integer('user_id').notNullable();
      table.foreign('user_id').references('id').inTable('users').onDelete('CASCADE');
      table.index('user_id');
    });
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