package work.myfavs.framework.orm.repository.handler.impls;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import work.myfavs.framework.orm.repository.handler.PropertyHandler;

/**
 * Created by tanqimin on 2016/1/29.
 */
public class DatePropertyHandler
    extends PropertyHandler<Date> {

  @Override
  public Date convert(ResultSet rs, String columnName, Class<Date> clazz)
      throws SQLException {

    Date date = rs.getDate(columnName);
    return rs.wasNull()
        ? null
        : new Date(date.getTime());
  }

  @Override
  public void addParameter(PreparedStatement ps, int paramIndex, Date param)
      throws SQLException {

    ps.setDate(paramIndex, new java.sql.Date(param.getTime()));
  }

}
