/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema.table('customers', function(table) {
    // Drop the column only if it exists to prevent errors.
    return knex.schema.hasColumn('customers', 'user_id').then(function(exists) {
      if (exists) {
        // We might need to drop foreign key first if it was created.
        // Let's try dropping the column directly. If it fails, we'll know a FK exists.
        table.dropColumn('user_id');
      }
    });
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  // This is a one-off cleanup migration. 
  // The down function is intentionally left empty as we don't want to recreate the bad column.
  return Promise.resolve();
};