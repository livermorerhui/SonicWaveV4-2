exports.up = function (knex) {
  return knex.schema.createTable('humeds_accounts', function (table) {
    table.increments('id').primary();
    table.integer('user_id').unsigned().notNullable().index();
    table.string('mobile', 32).notNullable().index();
    table.string('region_code', 8).notNullable().defaultTo('86');
    table.text('token_jwt').nullable();
    table.timestamp('token_expires_at').nullable();
    table.string('status', 32).notNullable().defaultTo('active');
    table.timestamp('last_login_at').nullable();
    table.timestamp('created_at').notNullable().defaultTo(knex.fn.now());
    table
      .timestamp('updated_at')
      .notNullable()
      .defaultTo(knex.raw('CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'));

    table.unique(['user_id', 'region_code']);
  });
};

exports.down = function (knex) {
  return knex.schema.dropTableIfExists('humeds_accounts');
};
