// 01_create_initial_tables.js

exports.up = function(knex) {
  return knex.schema
    .createTableIfNotExists('users', function (table) {
      table.increments('id').primary();
      table.string('username', 255).notNullable().unique();
      table.string('email', 255).notNullable().unique();
      table.string('password', 255).notNullable();
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    .createTableIfNotExists('app_usage_logs', function (table) {
      table.increments('id').primary();
      table.string('user_id', 255).notNullable();
      table.bigInteger('launch_time').notNullable();
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    .createTableIfNotExists('user_operations', function (table) {
      table.increments('id').primary();
      table.string('user_id', 255).notNullable();
      table.string('user_name', 255);
      table.string('email', 255);
      table.string('customer', 255);
      table.integer('frequency');
      table.integer('intensity');
      table.integer('operation_time');
      table.dateTime('start_time').notNullable();
      table.dateTime('stop_time');
      table.timestamp('created_at').defaultTo(knex.fn.now());
    });
};

exports.down = function(knex) {
  return knex.schema
    .dropTableIfExists('user_operations')
    .dropTableIfExists('app_usage_logs')
    .dropTableIfExists('users');
};
