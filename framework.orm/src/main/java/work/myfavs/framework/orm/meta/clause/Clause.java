package work.myfavs.framework.orm.meta.clause;

import java.util.ArrayList;
import java.util.List;

public class Clause {

  protected StringBuilder sql;
  protected List<Object>  params;

  public Clause() {

    sql = new StringBuilder();
    params = new ArrayList<>();
  }

  public Clause(String sql) {

    this();
    this.sql.append(sql);
  }

  public Clause(String sql,
                List params) {

    this(sql);
    this.params.addAll(params);
  }

}
