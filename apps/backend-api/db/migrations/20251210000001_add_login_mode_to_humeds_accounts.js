exports.up = function (knex) {
  return knex.schema.alterTable('humeds_accounts', function (table) {
    table.string('login_mode', 32).notNullable().defaultTo('UNKNOWN');
  });
};

exports.down = function (knex) {
  return knex.schema.alterTable('humeds_accounts', function (table) {
    table.dropColumn('login_mode');
  });
};
