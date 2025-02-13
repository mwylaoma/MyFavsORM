package work.myfavs.framework.orm.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import work.myfavs.framework.orm.DBTemplate;
import work.myfavs.framework.orm.meta.clause.Cond;
import work.myfavs.framework.orm.meta.clause.Sql;
import work.myfavs.framework.orm.meta.enumeration.GenerationType;
import work.myfavs.framework.orm.meta.schema.AttributeMeta;
import work.myfavs.framework.orm.meta.schema.ClassMeta;
import work.myfavs.framework.orm.meta.schema.Metadata;
import work.myfavs.framework.orm.repository.monitor.SqlAnalysis;
import work.myfavs.framework.orm.repository.monitor.SqlExecutedContext;
import work.myfavs.framework.orm.repository.monitor.SqlExecutingContext;
import work.myfavs.framework.orm.util.DBUtil;
import work.myfavs.framework.orm.util.ReflectUtil;
import work.myfavs.framework.orm.util.exception.DBException;

/**
 * 仓储基类
 *
 * @param <TModel>
 */
@Slf4j
public class Repository<TModel>
    extends Query {

  protected Class<TModel> modelClass;

  private ClassMeta      classMeta;
  private AttributeMeta  primaryKey;
  private GenerationType strategy;

  /**
   * 构造方法
   *
   * @param dbTemplate DBTemplate
   */
  public Repository(DBTemplate dbTemplate) {

    super(dbTemplate);
    this.modelClass = ReflectUtil.getActualClassArg(this.getClass(), 0);
    this.classMeta = Metadata.get(this.modelClass);
    this.strategy = this.classMeta.getStrategy();
    this.primaryKey = this.classMeta.getPrimaryKey();
  }

  /**
   * 根据主键获取记录
   *
   * @param id 主键
   *
   * @return 记录
   */
  public TModel getById(Object id) {

    Sql sql = this.dialect.select(modelClass).where(Cond.eq(primaryKey.getColumnName(), id));
    return super.get(modelClass, sql);
  }

  /**
   * 根据指定字段获取记录
   *
   * @param field 字段名
   * @param param 参数
   *
   * @return 记录
   */
  public TModel getByField(String field, Object param) {

    Sql sql = this.dialect.select(modelClass).where(Cond.eq(field, param));
    return this.get(sql);
  }

  /**
   * 根据SQL获取记录
   *
   * @param sql    SQL语句
   * @param params 参数
   *
   * @return 记录
   */
  public TModel get(String sql, List<Object> params) {

    return super.get(this.modelClass, sql, params);
  }

  /**
   * 根据SQL获取记录
   *
   * @param sql SQL
   *
   * @return 记录
   */
  public TModel get(Sql sql) {

    return super.get(this.modelClass, sql);
  }


  /**
   * 根据SQL查询实体集合
   *
   * @param sql    SQL语句
   * @param params 参数
   *
   * @return 实体集合
   */
  public List<TModel> find(String sql, List<Object> params) {

    return super.find(modelClass, sql, params);
  }

  /**
   * 根据SQL查询实体集合
   *
   * @param sql SQL
   *
   * @return 实体集合
   */
  public List<TModel> find(Sql sql) {

    return super.find(modelClass, sql);
  }

  /**
   * 根据字段查询实体集合
   *
   * @param field 字段名
   * @param param 参数
   *
   * @return 实体集合
   */
  public List<TModel> findByField(String field, Object param) {

    Sql sql = this.dialect.select(modelClass).where(Cond.eq(field, param));
    return super.find(this.modelClass, sql);
  }

  /**
   * 根据字段查询实体集合
   *
   * @param field  字段名
   * @param params 参数集合
   *
   * @return 实体集合
   */
  public List<TModel> findByField(String field, List<Object> params) {

    Sql sql = this.dialect.select(modelClass).where(Cond.in(field, params, false));
    return super.find(this.modelClass, sql);
  }

  /**
   * 根据多个主键ID查询实体集合
   *
   * @param ids 主键ID集合
   *
   * @return 实体集合
   */
  public List<TModel> findByIds(Collection<Object> ids) {

    Sql sql = this.dialect.select(modelClass).where(Cond.in(primaryKey.getColumnName(), new ArrayList<>(ids), false));
    return super.find(this.modelClass, sql);
  }

  /**
   * 执行一个SQL语句
   *
   * @param sql SQL
   *
   * @return 影响行数
   */
  public int execute(Sql sql) {

    return this.execute(sql.getSql().toString(), sql.getParams());
  }

  /**
   * 执行多个SQL语句
   *
   * @param sqlList SQL集合
   *
   * @return 返回多个影响行数
   */
  public int[] execute(List<Sql> sqlList) {

    int   sqlCnt  = sqlList.size();
    int[] results = new int[sqlCnt];
    for (int i = 0;
         i < sqlCnt;
         i++) {
      results[i] = execute(sqlList.get(i));
    }
    return results;
  }

  /**
   * 执行一个SQL语句
   *
   * @param sql    SQL语句
   * @param params 参数
   *
   * @return 影响行数
   */
  public int execute(String sql, List<Object> params) {

    int               result;
    Connection        conn  = null;
    PreparedStatement pstmt = null;

    SqlAnalysis         sqlAnalysis         = new SqlAnalysis();
    StopWatch           stopWatch           = new StopWatch();
    SqlExecutingContext sqlExecutingContext = new SqlExecutingContext(new Sql(sql, params));
    super.beforeExecute(sqlExecutingContext);

    try {
      super.showSql(sql, params);
      stopWatch.start(StrUtil.format("[{}]SQL EXECUTE", getThreadInfo()));

      conn = this.dbTemplate.createConnection();

      pstmt = params != null && params.size() > 0
          ? DBUtil.getPs(conn, false, sql, params)
          : DBUtil.getPs(conn, false, sql);
      result = pstmt.executeUpdate();

      stopWatch.stop();
      sqlAnalysis.setElapsed(stopWatch.getLastTaskTimeMillis());
      sqlAnalysis.setAffectedRows(result);
      super.showAffectedRows(result);
    } catch (Exception ex) {
      sqlAnalysis.setHasError(true);
      sqlAnalysis.setThrowable(ex);

      throw new DBException(ex);
    } finally {
      this.dbTemplate.release(conn, pstmt);

      if (stopWatch.isRunning()) {
        stopWatch.stop();
      }
      this.afterQuery(new SqlExecutedContext(sqlExecutingContext.getSql(), sqlAnalysis));
    }

    return result;
  }

  /**
   * 创建实体
   *
   * @param entity 实体
   *
   * @return 影响行数
   */
  public int create(TModel entity) {

    int            result          = 0;
    GenerationType strategy;
    String         pkFieldName;
    boolean        autoGeneratedPK = false;

    Sql               sql;
    Connection        conn  = null;
    PreparedStatement pstmt = null;
    ResultSet         rs    = null;

    if (entity == null) {
      return result;
    }

    pkFieldName = primaryKey.getFieldName();
    strategy = classMeta.getStrategy();

    if (strategy == GenerationType.UUID) {
      BeanUtil.setFieldValue(entity, pkFieldName, this.dbTemplate.nextUUID());
    } else if (strategy == GenerationType.SNOW_FLAKE) {
      BeanUtil.setFieldValue(entity, pkFieldName, this.dbTemplate.nextSnowFakeId());
    } else if (strategy == GenerationType.IDENTITY) {
      autoGeneratedPK = true;
    }

    sql = this.dialect.insert(this.modelClass, entity);

    SqlAnalysis         sqlAnalysis         = new SqlAnalysis();
    StopWatch           stopWatch           = new StopWatch();
    SqlExecutingContext sqlExecutingContext = new SqlExecutingContext(sql);
    super.beforeExecute(sqlExecutingContext);

    try {
      super.showSql(sql.getSql().toString(), sql.getParams());
      stopWatch.start(StrUtil.format("[{}]SQL EXECUTE", getThreadInfo()));

      conn = this.dbTemplate.createConnection();
      pstmt = DBUtil.getPs(conn, autoGeneratedPK, sql);
      result = pstmt.executeUpdate();

      stopWatch.stop();
      sqlAnalysis.setElapsed(stopWatch.getLastTaskTimeMillis());
      sqlAnalysis.setAffectedRows(result);
      super.showAffectedRows(result);

      if (autoGeneratedPK) {
        rs = pstmt.getGeneratedKeys();
        if (rs.next()) {
          BeanUtil.setFieldValue(entity, pkFieldName, rs.getObject(1));
        }
      }
    } catch (Exception ex) {
      sqlAnalysis.setHasError(true);
      sqlAnalysis.setThrowable(ex);

      throw new DBException(ex);
    } finally {
      this.dbTemplate.release(conn, pstmt, rs);

      if (stopWatch.isRunning()) {
        stopWatch.stop();
      }
      this.afterQuery(new SqlExecutedContext(sqlExecutingContext.getSql(), sqlAnalysis));
    }

    return result;
  }

  /**
   * 批量创建实体
   *
   * @param entities 实体集合
   *
   * @return 影响行数
   */
  public int create(Collection<TModel> entities) {

    int                 result          = 0;
    GenerationType      strategy;
    String              pkFieldName;
    Object              pkVal;
    boolean             autoGeneratedPK = false;
    List<AttributeMeta> updateAttributes;
    Sql                 sql;
    List<List<Object>>  paramsList;
    List<Object>        params;

    if (entities == null || entities.isEmpty()) {
      return result;
    }

    pkFieldName = primaryKey.getFieldName();
    strategy = classMeta.getStrategy();
    updateAttributes = this.classMeta.getUpdateAttributes();
    sql = this.dialect.insert(modelClass);
    paramsList = new LinkedList<>();

    if (strategy == GenerationType.IDENTITY) {
      autoGeneratedPK = true;
    }

    for (Iterator<TModel> iterator = entities.iterator();
         iterator.hasNext(); ) {
      TModel entity = iterator.next();
      params = new LinkedList<>();

      if (strategy == GenerationType.UUID) {
        pkVal = this.dbTemplate.nextUUID();
        BeanUtil.setFieldValue(entity, pkFieldName, pkVal);
        params.add(pkVal);
      } else if (strategy == GenerationType.SNOW_FLAKE) {
        pkVal = this.dbTemplate.nextSnowFakeId();
        BeanUtil.setFieldValue(entity, pkFieldName, pkVal);
        params.add(pkVal);
      } else if (strategy == GenerationType.ASSIGNED) {
        pkVal = BeanUtil.getFieldValue(entity, pkFieldName);
        params.add(pkVal);
      }

      for (AttributeMeta attributeMeta : updateAttributes) {
        params.add(BeanUtil.getFieldValue(entity, attributeMeta.getFieldName()));
      }
      paramsList.add(params);
    }

    Connection        conn  = null;
    PreparedStatement pstmt = null;
    ResultSet         rs    = null;

    SqlAnalysis         sqlAnalysis         = new SqlAnalysis();
    StopWatch           stopWatch           = new StopWatch();
    SqlExecutingContext sqlExecutingContext = new SqlExecutingContext(sql.getSql().toString(), Collections.singletonList(paramsList));
    super.beforeExecute(sqlExecutingContext);

    try {
      super.showBatchSql(sql.getSql().toString(), paramsList);
      stopWatch.start(StrUtil.format("[{}]SQL EXECUTE", getThreadInfo()));

      conn = this.dbTemplate.createConnection();
      pstmt = DBUtil.getPsForUpdate(conn, autoGeneratedPK, sql.getSql().toString(), paramsList, this.dbTemplate.getBatchSize());
      int[] exeRes = pstmt.executeBatch();
      pstmt.clearBatch();

      for (int exeRe : exeRes) {
        result += exeRe;
      }

      stopWatch.stop();
      sqlAnalysis.setElapsed(stopWatch.getLastTaskTimeMillis());
      sqlAnalysis.setAffectedRows(result);
      super.showAffectedRows(result);

      if (autoGeneratedPK) {
        rs = pstmt.getGeneratedKeys();
        for (Iterator<TModel> iterator = entities.iterator();
             iterator.hasNext(); ) {
          TModel tModel = iterator.next();
          if (rs.next()) {
            BeanUtil.setFieldValue(tModel, pkFieldName, rs.getObject(1));
          }
        }
      }
    } catch (SQLException e) {
      sqlAnalysis.setHasError(true);
      sqlAnalysis.setThrowable(e);

      throw new DBException(e);
    } finally {
      this.dbTemplate.release(conn, pstmt, rs);

      if (stopWatch.isRunning()) {
        stopWatch.stop();
      }
      this.afterQuery(new SqlExecutedContext(sqlExecutingContext.getSql(), sqlAnalysis));
    }

    return result;
  }

  /**
   * 更新实体
   *
   * @param entity 实体
   *
   * @return 影响行数
   */
  public int update(TModel entity) {

    if (entity == null) {
      return 0;
    }
    return execute(this.dialect.update(this.modelClass, entity));
  }

  /**
   * 更新实体
   *
   * @param entity  实体
   * @param columns 需要更新的列
   *
   * @return 影响行数
   */
  public int update(TModel entity, String[] columns) {

    if (entity == null) {
      return 0;
    }
    List<TModel> entities = new ArrayList<>();
    entities.add(entity);
    return update(entities);
  }

  /**
   * 更新实体
   *
   * @param entities 实体集合
   * @param columns  需要更新的列
   *
   * @return 影响行数
   */
  public int update(Collection<TModel> entities, String[] columns) {

    int                 result = 0;
    List<AttributeMeta> updateAttributes;

    Sql                sql;
    List<List<Object>> paramsList;
    List<Object>       params;

    Connection        conn  = null;
    PreparedStatement pstmt = null;

    if (entities == null) {
      return result;
    }

    if (columns == null || columns.length == 0) {
      updateAttributes = this.classMeta.getUpdateAttributes();
    } else {
      updateAttributes = new LinkedList<>();
      for (String column : columns) {
        AttributeMeta attributeMeta = this.classMeta.getQueryAttributes().get(column.toUpperCase());
        if (attributeMeta == null) {
          continue;
        }
        if (attributeMeta.isPrimaryKey()) {
          continue;
        }
        updateAttributes.add(attributeMeta);
      }
    }

    if (updateAttributes.isEmpty()) {
      throw new DBException("没有匹配到需要更新的Column");
    }

    sql = Sql.Update(this.classMeta.getTableName()).append(" SET ");
    for (AttributeMeta updateAttribute : updateAttributes) {
      sql.append(StrUtil.format("{} = ?,", updateAttribute.getColumnName()));
    }
    sql.getSql().deleteCharAt(sql.getSql().lastIndexOf(","));
    sql.append(StrUtil.format(" WHERE {} = ?", primaryKey.getColumnName()));

    paramsList = new LinkedList<>();

    for (Iterator<TModel> iterator = entities.iterator();
         iterator.hasNext(); ) {
      TModel entity = iterator.next();
      params = new LinkedList<>();

      for (AttributeMeta attributeMeta : updateAttributes) {
        params.add(BeanUtil.getFieldValue(entity, attributeMeta.getFieldName()));
      }

      params.add(BeanUtil.getFieldValue(entity, primaryKey.getFieldName()));
      paramsList.add(params);
    }

    SqlAnalysis         sqlAnalysis         = new SqlAnalysis();
    StopWatch           stopWatch           = new StopWatch();
    SqlExecutingContext sqlExecutingContext = new SqlExecutingContext(sql.getSql().toString(), Collections.singletonList(paramsList));
    super.beforeExecute(sqlExecutingContext);

    try {

      super.showBatchSql(sql.getSql().toString(), paramsList);
      stopWatch.start(StrUtil.format("[{}]SQL EXECUTE", getThreadInfo()));

      conn = this.dbTemplate.createConnection();
      pstmt = DBUtil.getPsForUpdate(conn, false, sql.getSql().toString(), paramsList, this.dbTemplate.getBatchSize());

      int[] exeRes = pstmt.executeBatch();
      pstmt.clearBatch();
      for (int exeRe : exeRes) {
        result += exeRe;
      }

      stopWatch.stop();
      sqlAnalysis.setElapsed(stopWatch.getLastTaskTimeMillis());
      sqlAnalysis.setAffectedRows(result);
      super.showAffectedRows(result);
    } catch (SQLException e) {
      sqlAnalysis.setHasError(true);
      sqlAnalysis.setThrowable(e);

      throw new DBException(e);
    } finally {
      this.dbTemplate.release(conn, pstmt);

      if (stopWatch.isRunning()) {
        stopWatch.stop();
      }
      this.afterQuery(new SqlExecutedContext(sqlExecutingContext.getSql(), sqlAnalysis));
    }

    return result;
  }

  /**
   * 更新实体
   *
   * @param entities 实体集合
   *
   * @return 影响行数
   */
  public int update(List<TModel> entities) {

    return this.update(entities, null);
  }

  /**
   * 删除记录
   *
   * @param entity 实体
   *
   * @return 影响行数
   */
  public int delete(TModel entity) {

    String pkFieldName;
    Object pkVal;

    if (entity == null) {
      return 0;
    }

    pkFieldName = primaryKey.getFieldName();
    pkVal = BeanUtil.getFieldValue(entity, pkFieldName);

    return deleteById(pkVal);
  }

  /**
   * 批量删除记录
   *
   * @param entities 实体集合
   *
   * @return 影响行数
   */
  public int delete(List<TModel> entities) {

    String       pkFieldName;
    Object       pkVal;
    List<Object> ids;

    if (entities == null || entities.size() == 0) {
      return 0;
    }

    pkFieldName = primaryKey.getFieldName();
    ids = new ArrayList<>();
    for (TModel entity : entities) {
      pkVal = BeanUtil.getFieldValue(entity, pkFieldName);
      if (pkVal == null) {
        continue;
      }

      ids.add(pkVal);
    }

    if (ids.isEmpty()) {
      return 0;
    }

    return deleteByIds(ids);
  }

  /**
   * 根据ID删除记录
   *
   * @param id ID值
   *
   * @return 影响行数
   */
  public int deleteById(Object id) {

    if (id == null) {
      return 0;
    }
    String pkColumnName = primaryKey.getColumnName();
    Sql    sql          = Sql.Delete(this.classMeta.getTableName()).where(Cond.eq(pkColumnName, id));
    return execute(sql);
  }

  /**
   * 根据ID集合删除记录
   *
   * @param ids ID集合
   *
   * @return 影响行数
   */
  public int deleteByIds(Collection ids) {

    if (ids == null || ids.size() == 0) {
      return 0;
    }

    String pkColumnName = primaryKey.getColumnName();
    Sql    sql          = Sql.Delete(this.classMeta.getTableName()).where(Cond.in(pkColumnName, new ArrayList<Object>(ids)));
    return execute(sql);
  }

}
