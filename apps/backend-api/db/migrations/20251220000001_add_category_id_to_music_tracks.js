/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.table('music_tracks', function (table) {
    table
      .integer('category_id')
      .unsigned()
      .nullable()
      .references('id')
      .inTable('music_categories')
      .onDelete('SET NULL')
      .index();
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.table('music_tracks', function (table) {
    table.dropColumn('category_id');
  });
};
