/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema
    .createTable('music_categories', function (table) {
      table.increments('id').primary();
      table.string('code', 64).notNullable().unique();
      table.string('name', 255).notNullable();
      table.integer('sort_order').notNullable().defaultTo(0);
      table.boolean('is_active').notNullable().defaultTo(true);
      table.timestamps(true, true);
    })
    .then(() =>
      knex('music_categories').insert([
        { code: 'relax', name: '放松', sort_order: 1 },
        { code: 'focus', name: '专注', sort_order: 2 },
        { code: 'sleep', name: '睡眠', sort_order: 3 },
      ])
    );
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTableIfExists('music_categories');
};
