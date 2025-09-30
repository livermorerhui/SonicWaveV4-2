/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function(knex) {
  return knex.schema
    // 1. users table
    .createTable('users', function (table) {
      table.increments('id').primary();
      table.string('username', 255).notNullable().unique();
      table.string('email', 255).notNullable().unique();
      table.string('password', 255).notNullable();
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    // 2. app_usage_logs table
    .createTable('app_usage_logs', function (table) {
      table.increments('id').primary();
      table.string('user_id', 255).notNullable();
      table.bigInteger('launch_time').notNullable();
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    // 3. user_operations table (final version)
    .createTable('user_operations', function (table) {
      table.increments('id').primary();
      table.string('user_id', 255).notNullable();
      table.string('user_name', 255);
      table.string('user_email', 255); // Renamed from email
      table.string('customer_name', 255); // Renamed from customer
      table.integer('customer_id'); // Added column
      table.integer('frequency');
      table.integer('intensity');
      table.integer('operation_time');
      table.dateTime('start_time').notNullable();
      table.dateTime('stop_time');
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    // 4. refresh_tokens table
    .createTable('refresh_tokens', function (table) {
      table.increments('id').primary();
      table.integer('user_id').unsigned().notNullable();
      table.foreign('user_id').references('id').inTable('users').onDelete('CASCADE');
      table.string('token_hash', 255).notNullable().unique();
      table.dateTime('expires_at').notNullable();
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    // 5. customers table (final version)
    .createTable('customers', function(table) {
      table.increments('id').primary();
      table.string('name').notNullable();
      table.date('date_of_birth');
      table.string('gender');
      table.string('phone');
      table.string('email').unique();
      table.decimal('height');
      table.decimal('weight');
      table.integer('user_id').unsigned().notNullable();
      table.foreign('user_id').references('id').inTable('users').onDelete('CASCADE');
      table.timestamps(true, true);
    })
    // 6. music_tracks table
    .createTable('music_tracks', function(table) {
      table.increments('id').primary();
      table.string('title', 255).notNullable();
      table.string('artist', 255);
      table.string('file_key', 1024).notNullable();
      table.integer('uploader_id').unsigned().notNullable();
      table.foreign('uploader_id').references('id').inTable('users').onDelete('CASCADE');
      table.timestamps(true, true);
    })
    // 7. client_logs table
    .createTable('client_logs', function (table) {
      table.increments('id').primary();
      table.string('log_level', 20).notNullable();
      table.string('request_url', 255).notNullable();
      table.string('request_method', 10).notNullable();
      table.integer('response_code');
      table.boolean('is_successful').notNullable();
      table.bigInteger('duration_ms').notNullable();
      table.text('error_message');
      table.text('device_info');
      table.timestamp('created_at').defaultTo(knex.fn.now());
    })
    // 8. user_sessions table
    .createTable('user_sessions', function (table) {
      table.increments('id').primary();
      table.string('user_id', 255).notNullable();
      table.dateTime('login_time').notNullable();
      table.dateTime('last_heartbeat_time');
      table.dateTime('logout_time');
      table.boolean('is_active').notNullable().defaultTo(true);
      table.string('ip_address', 45);
      table.text('device_info');
      table.timestamp('created_at').defaultTo(knex.fn.now());
    });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function(knex) {
  return knex.schema
    .dropTableIfExists('client_logs')
    .dropTableIfExists('music_tracks')
    .dropTableIfExists('customers')
    .dropTableIfExists('refresh_tokens')
    .dropTableIfExists('user_operations')
    .dropTableIfExists('app_usage_logs')
    .dropTableIfExists('user_sessions') // Added this line
    .dropTableIfExists('users');
};