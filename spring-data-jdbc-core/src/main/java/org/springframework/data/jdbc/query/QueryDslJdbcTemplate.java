package org.springframework.data.jdbc.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.jdbc.support.DatabaseType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

import com.mysema.query.sql.DerbyTemplates;
import com.mysema.query.sql.H2Templates;
import com.mysema.query.sql.HSQLDBTemplates;
import com.mysema.query.sql.MySQLTemplates;
import com.mysema.query.sql.OracleTemplates;
import com.mysema.query.sql.PostgresTemplates;
import com.mysema.query.sql.RelationalPath;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.SQLQueryImpl;
import com.mysema.query.sql.SQLServerTemplates;
import com.mysema.query.sql.SQLTemplates;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.sql.dml.SQLInsertClause;
import com.mysema.query.sql.dml.SQLUpdateClause;
import com.mysema.query.types.Expression;

public class QueryDslJdbcTemplate implements QueryDslJdbcOperations {

	private JdbcTemplate jdbcTemplate;
	
	private SQLTemplates dialect;

	public QueryDslJdbcTemplate(DataSource dataSource) {
		this(new JdbcTemplate(dataSource));
	}

	public QueryDslJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		DatabaseType dbType = null;
		try {
			dbType = DatabaseType.fromMetaData(jdbcTemplate.getDataSource());
		} catch (MetaDataAccessException e) {
			throw new DataAccessResourceFailureException("Unable to determine database type: ", e);
		}
		if (dbType == DatabaseType.DERBY) {
			this.dialect = new DerbyTemplates();			
		}
		else if (dbType == DatabaseType.H2) {
			this.dialect = new H2Templates();			
		}
		else if (dbType == DatabaseType.HSQL) {
			this.dialect = new HSQLDBTemplates();			
		}
		else if (dbType == DatabaseType.MYSQL) {
			this.dialect = new MySQLTemplates();			
		}
		else if (dbType == DatabaseType.ORACLE) {
			this.dialect = new OracleTemplates();			
		}
		else if (dbType == DatabaseType.POSTGRES) {
			this.dialect = new PostgresTemplates();			
		}
		else if (dbType == DatabaseType.SQLSERVER) {
			this.dialect = new SQLServerTemplates();			
		}
		else {
			throw new InvalidDataAccessResourceUsageException(dbType + " is an unsupported database");			
		}
	}

	public JdbcOperations getJdbcOperations() {
		return this.jdbcTemplate;
	}

	public SQLQuery newSqlQuery() {
		return new SQLQueryImpl(this.dialect);
	}
	
	public <T> T queryForObject(final SQLQuery sqlQuery, final RowMapper<T> rowMapper, final Expression<?>... cols) {
		List<T> results = query(sqlQuery, rowMapper, cols);
		if (results.size() == 0) {
			return null;
		}
		if (results.size() > 1) {
			throw new IncorrectResultSizeDataAccessException(1, results.size());
		}
		return results.get(0);
	}
	
	public <T> List<T> query(final SQLQuery sqlQuery, final RowMapper<T> rowMapper, final Expression<?>... projection) {
		List<T> results = jdbcTemplate.execute(new ConnectionCallback<List<T>>() {
			public List<T> doInConnection(Connection con) throws SQLException,
					DataAccessException {
				SQLQuery liveQuery = sqlQuery.clone(con);
				RowMapperResultSetExtractor<T> extractor = 
					new RowMapperResultSetExtractor<T>(rowMapper);
				ResultSet resultSet = liveQuery.getResults(projection);
				List<T> list = extractor.extractData(resultSet);
				JdbcUtils.closeResultSet(resultSet);
				return list;
			}});
		return results;
	}
	
	public long insert(final RelationalPath<?> entity, final SqlInsertCallback callBack) {
		Long rowsAffected = jdbcTemplate.execute(new ConnectionCallback<Long>() {
			public Long doInConnection(Connection con) throws SQLException,
					DataAccessException {
				SQLInsertClause sqlClause = new SQLInsertClause(con, dialect, entity);
				return Long.valueOf(callBack.doInSqlInsertClause(sqlClause));
			}});
		return rowsAffected.longValue();
	}

	public long update(final RelationalPath<?> entity, final SqlUpdateCallback callBack) {
		Long rowsAffected = jdbcTemplate.execute(new ConnectionCallback<Long>() {
			public Long doInConnection(Connection con) throws SQLException,
					DataAccessException {
				SQLUpdateClause sqlClause = new SQLUpdateClause(con, dialect, entity);
				return Long.valueOf(callBack.doInSqlUpdateClause(sqlClause));
			}});
		return rowsAffected.longValue();
	}

	public long delete(final RelationalPath<?> entity, final SqlDeleteCallback callBack) {
		Long rowsAffected = jdbcTemplate.execute(new ConnectionCallback<Long>() {
			public Long doInConnection(Connection con) throws SQLException,
					DataAccessException {
				SQLDeleteClause sqlClause = new SQLDeleteClause(con, dialect, entity);
				return Long.valueOf(callBack.doInSqlDeleteClause(sqlClause));
			}});
		return rowsAffected.longValue();
	}
}