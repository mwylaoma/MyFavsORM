package work.myfavs.framework.orm.util;

import cn.hutool.core.bean.BeanUtil;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import work.myfavs.framework.orm.meta.Record;
import work.myfavs.framework.orm.meta.schema.AttributeMeta;
import work.myfavs.framework.orm.meta.schema.Metadata;
import work.myfavs.framework.orm.repository.handler.PropertyHandlerFactory;

/**
 * 数据库类型转换
 */
@Slf4j
public class DBConvert {

  /**
   * 把ResultSet转换为指定类型的List
   *
   * @param modelClass Class
   * @param rs         ResultSet
   * @param <TModel>   Class TModel
   *
   * @return List
   */
  public static <TModel> List<TModel> toList(Class<TModel> modelClass, ResultSet rs)
      throws SQLException {

    final Map<String, AttributeMeta> attrMetas;

    rs.beforeFirst();

    if (modelClass == Record.class) {
      return toRecordList(modelClass, rs);
    }

    attrMetas = Metadata.get(modelClass).getQueryAttributes();

    if (attrMetas.isEmpty() && rs.getMetaData().getColumnCount() == 1) {
      return toScalarList(modelClass, rs);
    }

    return toEntityList(modelClass, rs, attrMetas);
  }

  private static <TModel> List<TModel> toEntityList(Class<TModel> modelClass, ResultSet rs, Map<String, AttributeMeta> attrMetas)
      throws SQLException {

    final List<TModel> result;

    final ResultSetMetaData metaData;
    final int               columnCount;

    TModel        model;
    String        colName;
    Object        columnValue;
    AttributeMeta attributeMeta;

    result = new ArrayList<>();
    metaData = rs.getMetaData();
    columnCount = metaData.getColumnCount();

    while (rs.next()) {
      model = ReflectUtil.newInstance(modelClass);
      for (int i = 1;
           i <= columnCount;
           i++) {
        colName = metaData.getColumnLabel(i);
        attributeMeta = attrMetas.get(colName.toUpperCase());
        if (attributeMeta == null) {
          continue;
        }
        columnValue = PropertyHandlerFactory.convert(rs, colName, attributeMeta.getFieldType());
        BeanUtil.setFieldValue(model, attributeMeta.getFieldName(), columnValue);
      }
      result.add(model);
    }

    return result;
  }

  private static <TModel> List<TModel> toScalarList(Class<TModel> modelClass, ResultSet rs)
      throws SQLException {

    final List<TModel> result;

    final ResultSetMetaData metaData;
    String                  colName;

    result = new ArrayList<>();
    metaData = rs.getMetaData();

    while (rs.next()) {
      colName = metaData.getColumnLabel(1);
      result.add(PropertyHandlerFactory.convert(rs, colName, modelClass));
    }
    return result;
  }

  private static <TModel> List<TModel> toRecordList(Class<TModel> modelClass, ResultSet rs)
      throws SQLException {

    final List<TModel> result;

    final ResultSetMetaData metaData;
    final int               columnCount;

    TModel tModel;
    String colName;
    Object colValue;

    result = new ArrayList<>();
    metaData = rs.getMetaData();
    columnCount = metaData.getColumnCount();

    while (rs.next()) {
      tModel = ReflectUtil.newInstance(modelClass);
      for (int i = 1;
           i <= columnCount;
           i++) {
        colName = metaData.getColumnLabel(i);
        colValue = rs.getObject(i);
        ((Record) tModel).put(colName, rs.wasNull()
            ? null
            : colValue);
      }
      result.add(tModel);
    }

    return result;
  }

}
