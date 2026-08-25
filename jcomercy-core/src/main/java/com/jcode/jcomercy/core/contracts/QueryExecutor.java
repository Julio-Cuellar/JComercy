package com.jcode.jcomercy.core.contracts;

public interface QueryExecutor {

    <R> R execute(Query<R> query);
}
