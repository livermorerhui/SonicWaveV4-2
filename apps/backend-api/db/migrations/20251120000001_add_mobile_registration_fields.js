/**
 * @param { import('knex').Knex } knex
 */
exports.up = async function up(knex) {
  const addColumnIfMissing = async (columnName, builder) => {
    const exists = await knex.schema.hasColumn('users', columnName);
    if (!exists) {
      await knex.schema.alterTable('users', builder);
    }
  };

  await addColumnIfMissing('mobile', table => {
    table.string('mobile', 20).nullable();
    table.unique(['mobile']);
  });

  await addColumnIfMissing('mobile_verified', table => {
    table.boolean('mobile_verified').notNullable().defaultTo(false);
  });

  await addColumnIfMissing('birthday', table => {
    table.date('birthday').nullable();
  });

  await addColumnIfMissing('org_name', table => {
    table.string('org_name', 255).nullable();
  });

  const hasAccountType = await knex.schema.hasColumn('users', 'account_type');
  if (hasAccountType) {
    await knex.raw(
      "ALTER TABLE `users` MODIFY COLUMN `account_type` ENUM('normal','test','personal','org') NOT NULL DEFAULT 'personal'"
    );
  } else {
    await knex.schema.alterTable('users', table => {
      table
        .enu('account_type', ['normal', 'test', 'personal', 'org'])
        .notNullable()
        .defaultTo('personal');
    });
  }

  const [emailColumns] = await knex.raw("SHOW COLUMNS FROM `users` LIKE 'email'");
  const emailColumn = emailColumns && emailColumns[0];
  if (emailColumn && emailColumn.Null === 'NO') {
    await knex.raw("ALTER TABLE `users` MODIFY COLUMN `email` VARCHAR(255) NULL");
  }
};

/**
 * @param { import('knex').Knex } knex
 */
exports.down = async function down(knex) {
  const dropColumnIfExists = async columnName => {
    const exists = await knex.schema.hasColumn('users', columnName);
    if (exists) {
      await knex.schema.alterTable('users', table => {
        table.dropColumn(columnName);
      });
    }
  };

  await dropColumnIfExists('org_name');
  await dropColumnIfExists('birthday');
  await dropColumnIfExists('mobile_verified');
  await dropColumnIfExists('mobile');

  const hasAccountType = await knex.schema.hasColumn('users', 'account_type');
  if (hasAccountType) {
    await knex.raw(
      "UPDATE users SET account_type = 'normal' WHERE account_type NOT IN ('normal','test') OR account_type IS NULL"
    );
    await knex.raw(
      "ALTER TABLE `users` MODIFY COLUMN `account_type` ENUM('normal','test') NOT NULL DEFAULT 'normal'"
    );
  }

  const [emailColumns] = await knex.raw("SHOW COLUMNS FROM `users` LIKE 'email'");
  const emailColumn = emailColumns && emailColumns[0];
  if (emailColumn && emailColumn.Null === 'YES') {
    await knex.raw(
      "UPDATE users SET email = CONCAT('user', id, '@example.com') WHERE email IS NULL OR email = ''"
    );
    await knex.raw("ALTER TABLE `users` MODIFY COLUMN `email` VARCHAR(255) NOT NULL");
  }
};
