package com.jcode.jcomercy.sales.infra.persistence;

import com.jcode.jcomercy.sales.domain.port.FolioGeneratorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** El folio de cada ticket: consecutivo por sucursal, sin repetidos ni huecos aunque se pidan a la vez. */
@SpringBootTest
class JdbcFolioGeneratorAdapterIntegrationTest {

    @Autowired private FolioGeneratorPort folios;

    @Test
    @DisplayName("Los folios de una sucursal son consecutivos y empiezan en 1")
    void foliosAreConsecutiveAndStartAtOne() {
        String company = "cmp_folio_" + UUID.randomUUID();

        assertThat(folios.next(company, "br_1")).isEqualTo("TCK-000001");
        assertThat(folios.next(company, "br_1")).isEqualTo("TCK-000002");
        assertThat(folios.next(company, "br_1")).isEqualTo("TCK-000003");
    }

    @Test
    @DisplayName("Cada sucursal y cada empresa lleva su propia cuenta")
    void eachBranchAndCompanyCountsOnItsOwn() {
        String company = "cmp_folio_" + UUID.randomUUID();
        String other = "cmp_folio_" + UUID.randomUUID();

        assertThat(folios.next(company, "br_1")).isEqualTo("TCK-000001");
        assertThat(folios.next(company, "br_2")).isEqualTo("TCK-000001");
        assertThat(folios.next(other, "br_1")).isEqualTo("TCK-000001");
        assertThat(folios.next(company, "br_1")).isEqualTo("TCK-000002");
    }

    @Test
    @DisplayName("Con veinte cobros a la vez no se repite ningún folio ni queda un hueco")
    void concurrentRequestsNeverRepeatNorSkipAFolio() throws Exception {
        String company = "cmp_folio_" + UUID.randomUUID();
        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return folios.next(company, "br_1");
                }));
            }
            start.countDown();
            TreeSet<String> folioSet = new TreeSet<>();
            for (Future<String> result : results) {
                folioSet.add(result.get());
            }

            assertThat(folioSet).hasSize(callers);
            assertThat(folioSet.first()).isEqualTo("TCK-000001");
            assertThat(folioSet.last()).isEqualTo("TCK-%06d".formatted(callers));
        } finally {
            pool.shutdownNow();
        }
    }
}
