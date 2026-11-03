package com.jcode.jcomercy.core.contracts;

public interface CommandDispatcher {

    <R> R dispatch(Command<R> command);
}
