const TABLE = 'customers';
const REL_TABLE = 'user_customers';

/**
 * @param { import('knex').Knex } knex
 */
exports.up = async function up(knex) {
  const hasRelTable = await knex.schema.hasTable(REL_TABLE);
  if (!hasRelTable) {
    await knex.schema.createTable(REL_TABLE, table => {
        table.increments('id').primary();
        table
          .integer('user_id')
          .unsigned()
          .notNullable()
          .references('id')
          .inTable('users')
          .onDelete('CASCADE');
        table
          .integer('customer_id')
          .unsigned()
          .notNullable()
          .references('id')
          .inTable(TABLE)
          .onDelete('CASCADE');
        table.unique(['user_id', 'customer_id'], 'user_customer_unique');
        table.timestamp('created_at').defaultTo(knex.fn.now());
      });
  }

  const rows = await knex(TABLE)
    .select(
      'id',
      'user_id',
      'email',
      'name',
      'date_of_birth',
      'gender',
      'phone',
      'height',
      'weight'
    )
    .orderBy(['email', 'id']);

  const canonicalByEmail = new Map();
  const duplicatesToDelete = [];

  for (const row of rows) {
    const emailKey = row.email || `__anon_${row.id}`;
    let canonical = canonicalByEmail.get(emailKey);

    if (!canonical) {
      canonical = { id: row.id, data: { ...row } };
      canonicalByEmail.set(emailKey, canonical);
    }

    if (row.user_id) {
      await knex(REL_TABLE)
        .insert({ user_id: row.user_id, customer_id: canonical.id })
        .onConflict(['user_id', 'customer_id'])
        .ignore();
    }

    if (canonical.id !== row.id) {
      const updatePayload = {};
      const fields = ['name', 'date_of_birth', 'gender', 'phone', 'height', 'weight'];
      for (const field of fields) {
        const canonicalValue = canonical.data[field];
        const incomingValue = row[field];
        if ((canonicalValue === null || canonicalValue === '' || canonicalValue === 0) && incomingValue) {
          updatePayload[field] = incomingValue;
          canonical.data[field] = incomingValue;
        }
      }
      if (Object.keys(updatePayload).length > 0) {
        await knex(TABLE).where({ id: canonical.id }).update(updatePayload);
      }
      duplicatesToDelete.push(row.id);
    }
  }

  if (duplicatesToDelete.length) {
    await knex(TABLE).whereIn('id', duplicatesToDelete).del();
  }

  const [[{ db }]] = await knex.raw('SELECT DATABASE() AS db');

  const dropUniqueIfExists = async indexName => {
    const [rows] = await knex.raw(
      'SELECT COUNT(1) AS total FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME = ?',
      [db, TABLE, indexName]
    );
    if (rows[0]?.total) {
      await knex.schema.alterTable(TABLE, table => {
        table.dropUnique(['email'], indexName);
      });
    }
  };

  await dropUniqueIfExists('customers_email_unique');
  await dropUniqueIfExists('email');

  const hasUserIdColumn = await knex.schema.hasColumn(TABLE, 'user_id');
  if (hasUserIdColumn) {
    try {
      await knex.raw('ALTER TABLE ?? DROP FOREIGN KEY ??', [TABLE, 'customers_user_id_foreign']);
    } catch (err) {
      await knex.raw('ALTER TABLE ?? DROP FOREIGN KEY ??', [TABLE, 'customers_ibfk_1']).catch(() => null);
    }

    await knex.schema.alterTable(TABLE, table => {
      table.dropColumn('user_id');
    });
  }

  await knex.schema.alterTable(TABLE, table => {
    table.unique(['email'], 'customers_email_unique');
  }).catch(() => null);
};

/**
 * @param { import('knex').Knex } knex
 */
exports.down = async function down(knex) {
  const hasUserIdColumn = await knex.schema.hasColumn(TABLE, 'user_id');
  if (!hasUserIdColumn) {
    await knex.schema.alterTable(TABLE, table => {
      table.integer('user_id').unsigned().nullable();
    });
  }

  const relations = await knex(REL_TABLE).select('user_id', 'customer_id').orderBy('id');
  for (const relation of relations) {
    await knex(TABLE)
      .where({ id: relation.customer_id })
      .update({ user_id: relation.user_id });
  }

  await knex.schema.alterTable(TABLE, table => {
    table.dropUnique(['email'], 'customers_email_unique');
  }).catch(() => null);

  await knex.raw('ALTER TABLE ?? DROP FOREIGN KEY ??', [TABLE, 'customers_user_id_foreign']).catch(() => null);

  await knex.schema.alterTable(TABLE, table => {
    table.foreign('user_id').references('users.id').onDelete('CASCADE');
  }).catch(() => null);

  await knex.schema.alterTable(TABLE, table => {
    table.unique(['email']);
  }).catch(() => null);

  const hasRelTable = await knex.schema.hasTable(REL_TABLE);
  if (hasRelTable) {
    await knex.schema.dropTable(REL_TABLE);
  }
};
