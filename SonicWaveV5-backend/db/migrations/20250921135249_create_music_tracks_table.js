/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema.createTableIfNotExists('music_tracks', function(table) {
    table.increments('id').primary();
    table.string('title', 255).notNullable();
    table.string('artist', 255);
    table.string('file_key', 1024).notNullable();
    table.integer('uploader_id').notNullable();
    table.foreign('uploader_id').references('id').inTable('users').onDelete('CASCADE');
    table.timestamps(true, true);
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  return knex.schema.dropTableIfExists('music_tracks');
};