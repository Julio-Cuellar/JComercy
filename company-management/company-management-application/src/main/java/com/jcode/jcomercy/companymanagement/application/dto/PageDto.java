package com.jcode.jcomercy.companymanagement.application.dto;

import java.util.List;

/** Una página de resultados con el cursor de la siguiente ({@code nextCursor} es nulo en la última). */
public record PageDto<T>(List<T> items, Page page) {

    public record Page(int limit, String nextCursor) { }
}
